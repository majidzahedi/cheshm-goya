package ir.cheshmgoya.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DiscoveredServer(val name: String, val url: String, val serverId: String)

/** Pairing payload encoded in the QR code on the server's page. */
@Serializable
data class PairingInfo(val url: String, val token: String, val id: String = "", val name: String = "")

object Pairing {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(qr: String): PairingInfo? = runCatching { json.decodeFromString<PairingInfo>(qr) }.getOrNull()
        ?.takeIf { it.url.startsWith("http") && it.token.isNotBlank() }
}

/**
 * Finds the Cheshm-Goya server on the local network with mDNS/DNS-SD
 * (service type `_cheshmgoya._tcp`), so a changed IP address doesn't break things.
 */
class ServerDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers

    fun start() {
        if (listener != null) return
        lock = wifi?.createMulticastLock("cheshmgoya-mdns")?.apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {
                _servers.value = _servers.value.filterNot { it.name == info.serviceName }
            }
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    @Suppress("DEPRECATION") // resolveService is fine on all our API levels
    private fun resolve(info: NsdServiceInfo) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(s: NsdServiceInfo) {
                val host = s.host?.hostAddress ?: return
                val h = if (host.contains(':')) "[$host]" else host
                val id = s.attributes["id"]?.decodeToString().orEmpty()
                val server = DiscoveredServer(s.serviceName, "http://$h:${s.port}", id)
                _servers.value = _servers.value.filterNot { it.name == server.name } + server
            }
        })
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        lock?.release()
        lock = null
    }

    companion object {
        private const val TAG = "ServerDiscovery"
        const val SERVICE_TYPE = "_cheshmgoya._tcp."
    }
}
