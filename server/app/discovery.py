"""mDNS / DNS-SD advertisement so the tablet finds the server on the LAN."""
from __future__ import annotations

import socket

from .logging_utils import log

SERVICE_TYPE = "_cheshmgoya._tcp.local."


def lan_ip() -> str:
    """Best guess of this computer's LAN address (no packet is actually sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


class Advertiser:
    def __init__(self, name: str, port: int, server_id: str):
        self.name = name
        self.port = port
        self.server_id = server_id
        self._zc = None
        self._info = None

    async def start(self) -> None:
        try:
            from zeroconf import IPVersion
            from zeroconf.asyncio import AsyncServiceInfo, AsyncZeroconf

            ip = lan_ip()
            self._info = AsyncServiceInfo(
                SERVICE_TYPE,
                f"cheshmgoya-{self.server_id}.{SERVICE_TYPE}",
                addresses=[socket.inet_aton(ip)],
                port=self.port,
                properties={"id": self.server_id, "v": "1"},  # never the token
                server=f"cheshmgoya-{self.server_id}.local.",
            )
            self._zc = AsyncZeroconf(ip_version=IPVersion.V4Only)
            await self._zc.async_register_service(self._info)
            log.info("mDNS: advertising %s on %s:%d", SERVICE_TYPE, ip, self.port)
        except Exception as e:
            log.warning("mDNS advertisement unavailable: %s", e)

    async def stop(self) -> None:
        if self._zc and self._info:
            await self._zc.async_unregister_service(self._info)
            await self._zc.async_close()
