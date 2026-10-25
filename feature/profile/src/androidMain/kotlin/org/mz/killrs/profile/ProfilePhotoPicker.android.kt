package org.mz.killrs.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.gitlive.firebase.storage.File

@Composable
actual fun ProfilePhotoPicker(enabled: Boolean, hasPhoto: Boolean, onSelected: (File) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSelected(File(uri))
    }
    TextButton(
        enabled = enabled,
        onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ) { Text(if (hasPhoto) "Change photo" else "Add profile photo") }
}
