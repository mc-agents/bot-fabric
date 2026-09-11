#!/usr/bin/env python3
"""Stand-in for mcp-server: listens on :8765, speaks the bot protocol, runs a script.

Usage: rpc-harness.py <script> [--port 8765] [--out dev/out]

Script lines:
  connect <host> <port> <username> [spawnTimeoutMs]
  call <tool> <json args> [deadlineMs]
  cancel <id> <reason>
  disconnect <reason>
  ping
  sleep <seconds>
  expect <n>          wait until n results have arrived
"""
import json
import os
import socket
import struct
import sys
import threading
import time
import uuid

JSON_FRAME = 0
BLOB_FRAME = 1


class Link:
    def __init__(self, conn, out_dir):
        self.conn = conn
        self.out_dir = out_dir
        self.results = {}
        self.events = []
        self.blobs = {}
        self.lock = threading.Lock()
        self.alive = True

    def send(self, message):
        payload = json.dumps(message).encode()
        self.conn.sendall(struct.pack(">IB", len(payload) + 1, JSON_FRAME) + payload)
        print(f"--> {json.dumps(message)}", flush=True)

    def read_frames(self):
        buffer = b""
        while self.alive:
            try:
                chunk = self.conn.recv(65536)
            except OSError:
                break
            if not chunk:
                break
            buffer += chunk
            while len(buffer) >= 4:
                (length,) = struct.unpack(">I", buffer[:4])
                if len(buffer) < 4 + length:
                    break
                frame = buffer[4:4 + length]
                buffer = buffer[4 + length:]
                self.on_frame(frame[0], frame[1:])
        self.alive = False

    def on_frame(self, kind, payload):
        if kind == BLOB_FRAME:
            blob_id = str(uuid.UUID(bytes=payload[:16]))
            content = payload[16:]
            path = os.path.join(self.out_dir, blob_id + ".bin")
            with open(path, "wb") as handle:
                handle.write(content)
            with self.lock:
                self.blobs[blob_id] = path
            print(f"<-- blob {blob_id} {len(content)} bytes -> {path}", flush=True)
            return

        message = json.loads(payload.decode())
        kind_name = message.get("t")
        if kind_name == "result":
            self.name_blobs(message.get("blobs") or [])
        with self.lock:
            if kind_name == "result":
                self.results[message["id"]] = message
            elif kind_name == "event":
                self.events.append(message)
        print(f"<-- {json.dumps(message)[:1200]}", flush=True)

    def name_blobs(self, blobs):
        """A blob frame arrives before the result that says what it is, so rename it after."""
        for blob in blobs:
            with self.lock:
                path = self.blobs.get(blob.get("id"))
            if path is None:
                continue
            suffix = {"image/png": ".png", "image/jpeg": ".jpg"}.get(blob.get("mime"))
            if suffix is None:
                continue
            renamed = os.path.splitext(path)[0] + suffix
            os.replace(path, renamed)
            with self.lock:
                self.blobs[blob["id"]] = renamed
            print(f"<-- blob {blob['id']} is {blob.get('width')}x{blob.get('height')} -> {renamed}",
                  flush=True)

    def wait_result(self, call_id, timeout):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                if call_id in self.results:
                    return self.results[call_id]
            if not self.alive:
                return None
            time.sleep(0.05)
        return None


def run(script_path, port, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))
    server.listen(1)
    print(f"listening on :{port}", flush=True)

    conn, address = server.accept()
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    print(f"bot connected from {address}", flush=True)

    link = Link(conn, out_dir)
    threading.Thread(target=link.read_frames, daemon=True).start()

    deadline = time.time() + 10
    while not link.results and time.time() < deadline:
        time.sleep(0.05)
        break
    time.sleep(0.3)

    link.send({
        "t": "helloOk",
        "protocol": 1,
        "sessionId": str(uuid.uuid4()),
        "heartbeatMs": 5000,
        "repeatFlushMs": 1000,
        "limits": {},
        "acceptedTools": [],
        "rejectedTools": [],
    })

    failures = 0
    with open(script_path) as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            verb, _, rest = line.partition(" ")
            if verb == "sleep":
                time.sleep(float(rest))
                continue
            if verb == "ping":
                link.send({"t": "ping", "nonce": str(uuid.uuid4()), "ackEventSeq": 0})
                continue

            call_id = str(uuid.uuid4())
            if verb == "connect":
                host, port_text, username, *tail = rest.split()
                spawn = int(tail[0]) if tail else 30000
                link.send({"t": "connect", "id": call_id, "host": host, "port": int(port_text),
                           "username": username, "spawnTimeoutMs": spawn})
                timeout = spawn / 1000 + 5
            elif verb == "call":
                tool, _, args_text = rest.partition(" ")
                args_text = args_text.strip() or "{}"
                deadline_ms = 30000
                link.send({"t": "call", "id": call_id, "tool": tool,
                           "args": json.loads(args_text), "deadlineMs": deadline_ms})
                timeout = deadline_ms / 1000 + 5
            elif verb == "disconnect":
                link.send({"t": "disconnect", "id": call_id, "reason": rest or "done"})
                timeout = 15
            else:
                print(f"unknown script verb: {verb}", flush=True)
                continue

            result = link.wait_result(call_id, timeout)
            if result is None:
                print(f"!!! no result for {verb} within {timeout}s", flush=True)
                failures += 1
            elif not result.get("ok"):
                failures += 1

    time.sleep(0.5)
    link.alive = False
    conn.close()
    server.close()
    print(f"done, {failures} failure(s)", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    args = sys.argv[1:]
    script = args[0]
    port_arg = 8765
    out = "dev/out"
    if "--port" in args:
        port_arg = int(args[args.index("--port") + 1])
    if "--out" in args:
        out = args[args.index("--out") + 1]
    sys.exit(run(script, port_arg, out))
