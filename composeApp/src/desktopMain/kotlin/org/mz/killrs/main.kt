package org.mz.killrs

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.killrs.di.initializeKoin

fun main() {
    initializeDesktopFirebase()
    initializeKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MZBotanicals",
        ) {
            App()
        }
    }
}
