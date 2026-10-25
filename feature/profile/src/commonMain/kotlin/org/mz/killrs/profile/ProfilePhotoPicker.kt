package org.mz.killrs.profile

import androidx.compose.runtime.Composable
import dev.gitlive.firebase.storage.File

@Composable
expect fun ProfilePhotoPicker(enabled: Boolean, hasPhoto: Boolean, onSelected: (File) -> Unit)
