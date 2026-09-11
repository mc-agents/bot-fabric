package net.mcagents.botfabric.rpc;

import com.google.gson.JsonObject;
import net.mcagents.botfabric.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RpcClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric/rpc");
    private static final byte[] POISON = new byte[0];

    private final BotConfig config;
    private final AtomicBoolean running = new AtomicBoolean();
    private final BlockingQueue<byte[]> outbound = new ArrayBlockingQueue<>(256);

    private volatile Socket socket;
    private volatile boolean linked;
    private volatile Dispatcher dispatcher;

    public RpcClient(BotConfig config) {
        this.config = config;
    }

    public BotConfig config() {
        return config;
    }

    public boolean linked() {
        return linked;
    }

    public void start(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread.ofPlatform().name("botfabric-rpc").daemon().start(this::runForever);
    }

    public void stop() {
        running.set(false);
        closeQuietly();
    }

    private void runForever() {
        while (running.get()) {
            try {
                runOnce();
            } catch (IOException e) {
                LOGGER.warn("rpc link lost: {}", e.toString());
            } catch (RuntimeException e) {
                LOGGER.error("rpc link failed", e);
            }
            linked = false;
            dispatcher.onUnlinked();
            if (!running.get()) {
                return;
            }
            try {
                Thread.sleep(config.reconnectDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runOnce() throws IOException {
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.connect(new InetSocketAddress(config.host(), config.port()), 5000);
        socket = s;
        outbound.clear();
        LOGGER.info("linked to {}:{}", config.host(), config.port());

        Thread writer = Thread.ofPlatform().name("botfabric-rpc-writer").daemon().start(() -> pumpOutbound(s));
        try {
            linked = true;
            send(dispatcher.hello());
            DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(s.getInputStream()));
            while (running.get() && !s.isClosed()) {
                Frame frame = FrameCodec.read(in);
                if (frame.type() != Frame.TYPE_JSON) {
                    throw new IOException("server sent a non-JSON frame: " + frame.type());
                }
                dispatcher.handle(this, Json.parse(frame.payload()));
            }
        } finally {
            linked = false;
            closeQuietly();
            outbound.offer(POISON);
            try {
                writer.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pumpOutbound(Socket s) {
        try {
            OutputStream out = s.getOutputStream();
            while (true) {
                byte[] frame = outbound.take();
                if (frame == POISON) {
                    return;
                }
                FrameCodec.write(out, frame);
            }
        } catch (IOException e) {
            LOGGER.debug("writer stopped: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void send(JsonObject message) {
        try {
            enqueue(FrameCodec.encodeJson(Json.encode(message)));
        } catch (IOException e) {
            LOGGER.warn("dropping outbound frame: {}", e.toString());
        }
    }

    public void sendBlob(Blob blob) {
        try {
            enqueue(FrameCodec.encodeBlob(blob.id(), blob.content()));
        } catch (IOException e) {
            LOGGER.warn("dropping blob frame: {}", e.toString());
        }
    }

    public void log(String level, String message) {
        JsonObject object = new JsonObject();
        object.addProperty("t", "log");
        object.addProperty("level", level);
        object.addProperty("message", message);
        send(object);
    }

    private void enqueue(byte[] frame) {
        if (!linked) {
            return;
        }
        if (!outbound.offer(frame)) {
            LOGGER.warn("outbound queue full, dropping a frame");
        }
    }

    private void closeQuietly() {
        Socket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }
}
