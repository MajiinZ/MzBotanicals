package org.mz.killrs.profile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gitlive.firebase.storage.File

@Composable
actual fun ProfilePhotoPicker(enabled: Boolean, hasPhoto: Boolean, onSelected: (File) -> Unit) {
    Text("Change your profile photo in the Android app")
}
