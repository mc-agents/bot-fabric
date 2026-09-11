package net.mcagents.botfabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.mcagents.botfabric.event.EventPump;
import net.mcagents.botfabric.rpc.Dispatcher;
import net.mcagents.botfabric.rpc.RpcClient;
import net.mcagents.botfabric.session.Session;
import net.mcagents.botfabric.task.TaskScheduler;
import net.mcagents.botfabric.tool.ToolRegistry;
import net.mcagents.botfabric.tools.GetPositionTool;
import net.mcagents.botfabric.tools.PressDialogButtonTool;
import net.mcagents.botfabric.tools.ReadWindowTool;
import net.mcagents.botfabric.tools.RunCommandTool;
import net.mcagents.botfabric.tools.ScreenshotTool;
import net.mcagents.botfabric.tools.SendChatTool;
import net.mcagents.botfabric.tools.WaitTicksTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotFabricClient implements ClientModInitializer {
    public static final String CATALOG_VERSION = "1.0.0";
    public static final String AGENT_VERSION = versionOf("botfabric");
    public static final String MINECRAFT_VERSION = versionOf("minecraft");

    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric");

    private static String versionOf(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void onInitializeClient() {
        BotConfig config = BotConfig.fromEnvironment();
        if (!config.enabled()) {
            LOGGER.info("rpc disabled, running as a plain client");
            return;
        }

        TaskScheduler scheduler = new TaskScheduler();
        ToolRegistry tools = new ToolRegistry();

        RpcClient client = new RpcClient(config);
        Session session = new Session(client);
        EventPump events = new EventPump(client);

        tools.register(new GetPositionTool());
        tools.register(new SendChatTool());
        tools.register(new ReadWindowTool());
        tools.register(new WaitTicksTool(scheduler));
        tools.register(new ScreenshotTool(scheduler));
        tools.register(new PressDialogButtonTool(scheduler));
        tools.register(new RunCommandTool(scheduler, events));

        Dispatcher dispatcher = new Dispatcher(tools, scheduler, session, config.botName());

        events.register();
        boolean[] configured = new boolean[1];
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (!configured[0]) {
                configured[0] = true;
                minecraft.options.pauseOnLostFocus = false;
            }
            if (minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
                minecraft.setScreen(null);
            }
            scheduler.tick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> session.report("ready"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> session.report("disconnected"));

        LOGGER.info("dialling {}:{} as {} with {} tools",
                config.host(), config.port(), config.botName(), tools.all().size());
        client.start(dispatcher);
    }
}
