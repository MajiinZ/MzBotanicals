package org.mz.killrs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopDispatcherTest {
    @Test
    fun firebaseCallbacksCanDispatchToTheDesktopUiThread() = runBlocking {
        withContext(Dispatchers.Main) {
            assertTrue(SwingUtilities.isEventDispatchThread())
        }
    }
}
