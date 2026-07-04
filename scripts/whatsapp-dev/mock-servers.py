#!/usr/bin/env python3
"""
Mocks locais para testar o api-whatsapp SEM Meta real.

- Meta-mock  (porta 9199): finge ser o WhatsApp Cloud API. Responde os POST de
  envio ({phoneNumberId}/messages) com um wamid fake e os de upload ({..}/media)
  com um media id fake — no shape que o WhatsAppCloudClient espera.
- ERP-echo   (porta 9198): finge ser o ERP. Recebe o callback
  POST /api/modulos/whatsapp/comando e imprime o ComandoCallbackDTO recebido.

Uso:  python3 mock-servers.py     (Ctrl+C para sair)
Requer apenas Python 3 (biblioteca padrao).
"""
import itertools
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

_wamid = itertools.count(1)
_media = itertools.count(1)


def _body(handler):
    # JDK HttpClient manda POST body como Transfer-Encoding: chunked (sem Content-Length)
    if "chunked" in handler.headers.get("Transfer-Encoding", "").lower():
        parts = []
        while True:
            line = handler.rfile.readline().strip()
            if not line:
                continue
            size = int(line.split(b";")[0], 16)
            if size == 0:
                handler.rfile.readline()  # CRLF final
                break
            parts.append(handler.rfile.read(size))
            handler.rfile.readline()  # CRLF apos o chunk
        return b"".join(parts)
    n = int(handler.headers.get("Content-Length", 0) or 0)
    return handler.rfile.read(n) if n else b""


def _pretty(raw):
    try:
        txt = json.dumps(json.loads(raw), ensure_ascii=False, indent=2)
    except Exception:
        txt = raw.decode("utf-8", "replace")
    print("  " + txt.replace("\n", "\n  "))


def _reply(handler, status, obj):
    data = json.dumps(obj).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


class MetaMock(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"  # JDK HttpClient nao le headers de respostas HTTP/1.0

    def log_message(self, *a):
        pass

    def do_POST(self):
        raw = _body(self)
        if self.path.endswith("/media"):
            resp, kind = {"id": f"media-fake-{next(_media)}"}, "UPLOAD MEDIA"
        else:
            resp = {"messaging_product": "whatsapp", "contacts": [],
                    "messages": [{"id": f"wamid.fake.{next(_wamid)}"}]}
            kind = "SEND MESSAGE"
        print(f"\n\033[36m[META-MOCK] {kind}  POST {self.path}\033[0m")
        _pretty(raw)
        _reply(self, 200, resp)


class ErpEcho(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def do_POST(self):
        raw = _body(self)
        print(f"\n\033[32m[ERP-ECHO]  callback  POST {self.path}\033[0m")
        _pretty(raw)
        _reply(self, 200, {"ok": True})


def _serve(port, handler, name):
    ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()
    _ = name


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(line_buffering=True)  # flush a cada linha (log ao vivo)
    except Exception:
        pass
    threading.Thread(target=_serve, args=(9199, MetaMock, "meta"), daemon=True).start()
    threading.Thread(target=_serve, args=(9198, ErpEcho, "erp"), daemon=True).start()
    print("Meta-mock  -> http://localhost:9199   (WhatsApp Cloud API falso)")
    print("ERP-echo   -> http://localhost:9198   (callback do ERP)")
    print("Ctrl+C para sair.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nBye.")
