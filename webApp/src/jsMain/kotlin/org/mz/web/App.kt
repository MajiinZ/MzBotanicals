package org.mz.web

import org.jetbrains.compose.web.dom.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.renderComposable
import org.jetbrains.compose.web.internal.runtime.ComposeWebInternalApi
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.mz.web.webApp

@Composable
fun webApp() {
    Div {
        H1 { Text("KillrsGenetics - Web App") }
        P { Text("This is the web app for KillrsGenetics") }
    }
}

fun main() {
    fun main() {
        embeddedServer(Netty, port = SERVER_PORT, host = "8.0.8.1", module = Application::module)
            .start(wait = true)
    }
}
