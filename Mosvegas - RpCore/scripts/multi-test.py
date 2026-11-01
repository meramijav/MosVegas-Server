"""Local two-player Minecraft harness. Uses the existing runtime and argfiles."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent
SERVER = ROOT / "run-multi-server"
LAUNCHER_JAVA = Path(os.environ["APPDATA"]) / ".minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/java.exe"
# Without the official launcher runtime, any Java 17+ from JAVA_HOME works.
JAVA = LAUNCHER_JAVA if LAUNCHER_JAVA.exists() or not os.environ.get("JAVA_HOME") else Path(os.environ["JAVA_HOME"]) / "bin/java.exe"
CLIENTS = (("run-multi-op", "MosvegasOp"), ("run-multi-player", "MosvegasCitizen"))


def offline_uuid(name):
    return str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3))


def prepare():
    # The offline server derives UUIDs from names, regardless of launcher UUIDs.
    (SERVER / "ops.json").write_text(json.dumps([{
        "uuid": offline_uuid("MosvegasOp"), "name": "MosvegasOp",
        "level": 4, "bypassesPlayerLimit": False,
    }], indent=2), encoding="utf-8")
    for folder, name in CLIENTS:
        directory = ROOT / folder
        argfile = directory / "java-args.txt"
        lines = argfile.read_text(encoding="cp1251").splitlines()
        for flag in ("--server", "--port", "--quickPlayMultiplayer", "--quickPlayPath"):
            while '"' + flag + '"' in lines:
                i = lines.index('"' + flag + '"')
                del lines[i:i + 2]
        i = lines.index('"--uuid"')
        lines[i + 1] = '"' + offline_uuid(name).replace("-", "") + '"'
        lines += ['"--quickPlayMultiplayer"', '"127.0.0.1:25565"',
                  '"--quickPlayPath"', '"quickplay.json"']
        argfile.write_text("\n".join(lines) + "\n", encoding="cp1251")
        options_file = directory / "options.txt"
        options = options_file.read_text(encoding="utf-8").splitlines() if options_file.exists() else []
        updates = {"lang": "ru_ru", "pauseOnLostFocus": "false", "renderDistance": "6",
                   "simulationDistance": "4", "maxFps": "60", "onboardAccessibility": "false",
                   "soundCategory_master": "0.3", "guiScale": "2", "fullscreen": "false"}
        options = [line for line in options if line.split(":", 1)[0] not in updates]
        options += [key + ":" + value for key, value in updates.items()]
        options_file.write_text("\n".join(options) + "\n", encoding="utf-8")


def server_host():
    queue = SERVER / "console-queue"
    queue.mkdir(exist_ok=True)
    with (SERVER / "server-out.log").open("wb") as output:
        process = subprocess.Popen([str(JAVA), "@" + str(SERVER / "java-args.txt")],
                                   cwd=SERVER, stdin=subprocess.PIPE, stdout=output,
                                   stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        (SERVER / "server.pid").write_text(str(process.pid))
        while process.poll() is None:
            for pending in sorted(queue.glob("*.pending")):
                for line in pending.read_text(encoding="utf-8-sig").splitlines():
                    if line.strip() and not line.lstrip().startswith("#"):
                        process.stdin.write((line.lstrip("/") + "\n").encode("utf-8"))
                        process.stdin.flush()
                pending.rename(pending.with_suffix(".sent"))
            time.sleep(0.25)


def start_server():
    (SERVER / "server-out.log").write_text("", encoding="utf-8")
    log = SERVER / "host.log"
    with log.open("wb") as output:
        process = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "host"], cwd=ROOT,
                                   stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
    print("Server host PID:", process.pid)


def start_clients(selected=None):
    targets = [(folder, name) for folder, name in CLIENTS if not selected or selected == name]
    for index, (folder, name) in enumerate(targets):
        directory = ROOT / folder
        server_log = SERVER / "server-out.log"
        offset = server_log.stat().st_size if server_log.exists() else 0
        with (directory / "client-out.log").open("wb") as output:
            # javaw has no console; its game window remains visible on the caller's desktop.
            process = subprocess.Popen([str(JAVA.with_name("javaw.exe")), "@" + str(directory / "java-args.txt")],
                                       cwd=directory, stdout=output, stderr=subprocess.STDOUT)
        (directory / "client.pid").write_text(str(process.pid))
        print(name + " PID: " + str(process.pid), flush=True)
        # Sequential login avoids the observed 1.20.4 registry-codec cache race.
        if index < len(targets) - 1:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                with server_log.open("rb") as log:
                    log.seek(offset)
                    joined = (name + " joined the game").encode() in log.read()
                if joined:
                    break
                if process.poll() is not None:
                    raise RuntimeError(name + " exited before joining the server")
                time.sleep(0.5)
            else:
                raise RuntimeError(name + " has not joined yet; inspect its client-out.log")


def send(commands):
    queue = SERVER / "console-queue"
    queue.mkdir(exist_ok=True)
    temporary = queue / (str(time.time_ns()) + ".tmp")
    temporary.write_text(commands, encoding="utf-8")
    temporary.rename(temporary.with_suffix(".pending"))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["prepare", "host", "server", "clients", "command", "file"])
    parser.add_argument("value", nargs="?")
    args = parser.parse_args()
    if args.action == "prepare": prepare()
    elif args.action == "host": server_host()
    elif args.action == "server": start_server()
    elif args.action == "clients": start_clients(args.value)
    elif args.action == "command": send(args.value)
    elif args.action == "file": send(Path(args.value).read_text(encoding="utf-8-sig"))
