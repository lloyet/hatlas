#!/usr/bin/env python3
"""Minimal Source RCON client (stdlib only) for hatlas-backup.sh.

Usage: rcon.py <command> [<command> ...]
Env:   MC_RCON_HOST (default 127.0.0.1), MC_RCON_PORT (default 25575),
       MC_RCON_PASSWORD (required), MC_RCON_TIMEOUT seconds (default 120).

Prints each reply on its own line. Exit codes: 0 ok, 2 cannot connect (server not
running), 3 authentication failed, 4 other error.
"""
import os
import socket
import struct
import sys

AUTH, EXEC = 3, 2


def send(sock, req_id, kind, body):
    payload = body.encode("utf-8")
    packet = struct.pack("<ii", req_id, kind) + payload + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(packet)) + packet)


def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("connection closed by the server")
        data += chunk
    return data


def recv(sock):
    (length,) = struct.unpack("<i", recv_exact(sock, 4))
    data = recv_exact(sock, length)
    req_id, _kind = struct.unpack("<ii", data[:8])
    return req_id, data[8:-2].decode("utf-8", errors="replace")


def main(argv):
    if not argv:
        print(__doc__.strip(), file=sys.stderr)
        return 4
    password = os.environ.get("MC_RCON_PASSWORD")
    if not password:
        print("MC_RCON_PASSWORD is not set", file=sys.stderr)
        return 4
    host = os.environ.get("MC_RCON_HOST", "127.0.0.1")
    port = int(os.environ.get("MC_RCON_PORT", "25575"))
    timeout = float(os.environ.get("MC_RCON_TIMEOUT", "120"))
    try:
        sock = socket.create_connection((host, port), timeout=5)
    except OSError as e:
        print(f"cannot connect to RCON {host}:{port}: {e}", file=sys.stderr)
        return 2
    try:
        sock.settimeout(timeout)
        send(sock, 1, AUTH, password)
        req_id, _ = recv(sock)
        if req_id == -1:
            print("RCON authentication failed (wrong MC_RCON_PASSWORD)", file=sys.stderr)
            return 3
        for i, command in enumerate(argv, start=10):
            send(sock, i, EXEC, command)
            try:
                _, body = recv(sock)
            except ConnectionError:
                body = ""  # "stop" closes the connection before replying
            print(body)
        return 0
    except (OSError, ConnectionError) as e:
        print(f"RCON error: {e}", file=sys.stderr)
        return 4
    finally:
        sock.close()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
