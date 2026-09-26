"""Shared-token authentication and LAN-only access."""
from __future__ import annotations

import hmac
import ipaddress
import secrets
import uuid
from pathlib import Path

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import settings

# Private ranges, Tailscale's CGNAT range and loopback. Everything else is refused.
_ALLOWED_NETS = [ipaddress.ip_network(n) for n in (
    "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "169.254.0.0/16",
    "::1/128", "fc00::/7", "fe80::/10", "fd7a:115c:a1e0::/48",
)]


def is_lan_address(host: str | None) -> bool:
    if not host:
        return False
    try:
        ip = ipaddress.ip_address(host.split("%")[0])
    except ValueError:
        return host == "testclient"  # FastAPI's TestClient
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return any(ip in net for net in _ALLOWED_NETS)


def is_loopback(host: str | None) -> bool:
    if host == "testclient":
        return True
    try:
        ip = ipaddress.ip_address((host or "").split("%")[0])
    except ValueError:
        return False
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return ip.is_loopback


def _read_or_create(path: Path, factory) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        value = path.read_text().strip()
        if value:
            return value
    value = factory()
    path.write_text(value)
    try:
        path.chmod(0o600)
    except OSError:
        pass
    return value


def get_token() -> str:
    return _read_or_create(settings.token_file, lambda: secrets.token_urlsafe(32))


def rotate_token() -> str:
    settings.token_file.unlink(missing_ok=True)
    return get_token()


def get_server_id() -> str:
    return _read_or_create(settings.server_id_file, lambda: uuid.uuid4().hex[:12])


_bearer = HTTPBearer(auto_error=False)


def require_token(creds: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> None:
    if creds is None or not hmac.compare_digest(creds.credentials.encode(), get_token().encode()):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid token", headers={"WWW-Authenticate": "Bearer"})


def require_loopback(request: Request) -> None:
    """The pairing page shows the token, so only the server computer itself may open it."""
    if not is_loopback(request.client.host if request.client else None):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "pairing page is only available on the server computer")
