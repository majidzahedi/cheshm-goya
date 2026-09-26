"""Pairing: a QR code with the server address and shared token.

Shown only on the server computer itself (http://localhost:8765/pair) or in the
terminal with `python -m app.pairing`, so nobody else on the network can read the token.
"""
from __future__ import annotations

import base64
import html
import io
import json

import qrcode

from .config import settings
from .discovery import lan_ip
from .security import get_server_id, get_token


def pairing_payload(host: str | None = None) -> dict:
    return {
        "url": f"http://{host or settings.public_host or lan_ip()}:{settings.port}",
        "token": get_token(),
        "id": get_server_id(),
        "name": settings.server_name,
    }


def qr_png(data: str) -> bytes:
    img = qrcode.make(data, box_size=10, border=3)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def pairing_page(host: str | None = None) -> str:
    p = pairing_payload(host)
    data = json.dumps(p, ensure_ascii=False)
    png = base64.b64encode(qr_png(data)).decode()
    return f"""<!doctype html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>اتصال تبلت به سرور چشم‌گویا</title>
<style>
 body{{font-family:Vazirmatn,Tahoma,sans-serif;background:#101418;color:#eceff1;margin:0;padding:24px;display:flex;flex-direction:column;align-items:center}}
 .card{{background:#1f262e;border-radius:16px;padding:24px;max-width:560px;text-align:center}}
 img{{background:#fff;border-radius:12px;padding:8px;width:340px;max-width:100%}}
 code{{direction:ltr;display:inline-block;background:#000;padding:4px 8px;border-radius:6px;word-break:break-all}}
</style></head><body><div class="card">
<h1>اتصال تبلت به سرور</h1>
<p>در تبلت: تنظیمات ← هوش مصنوعی ← «سرور محلی» ← «اتصال به سرور». سپس این کد را جلوی دوربین جلوی تبلت بگیرید.</p>
<img alt="QR" src="data:image/png;base64,{png}">
<p>ورود دستی — آدرس: <code>{html.escape(p['url'])}</code></p>
<p>توکن: <code>{html.escape(p['token'])}</code></p>
<p style="opacity:.7">این صفحه فقط روی همین کامپیوتر باز می‌شود. توکن را با کسی به اشتراک نگذارید.</p>
</div></body></html>"""


if __name__ == "__main__":
    q = qrcode.QRCode(border=1)
    q.add_data(json.dumps(pairing_payload(), ensure_ascii=False))
    q.print_ascii(invert=True)
    print(json.dumps(pairing_payload(), ensure_ascii=False, indent=2))
