package baaahs

import baaahs.util.Clock
import baaahs.util.JsClock
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.dom.get
import org.w3c.xhr.XMLHttpRequest

actual fun doRunBlocking(block: suspend () -> Unit) {
    GlobalScope.promise { block() }
    return
}

val resourcesBase = document["resourcesBase"]

actual fun getResource(name: String): String {
    val xhr = XMLHttpRequest()
    xhr.open("GET", "$resourcesBase/$name", false)
    xhr.send()

    if (xhr.status.toInt() == 200) {
        return xhr.responseText
    }

    throw Exception("failed to load resource ${name}: ${xhr.status} ${xhr.responseText}")
}

actual val internalTimerClock: Clock = JsClock

actual fun decodeBase64(s: String): ByteArray {
    return window.atob(s).encodeToByteArray()
}
