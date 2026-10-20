package org.mz.killrs.auth

import ContentWithMessageBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.mz.killrs.auth.component.GoogleAuthenticationButton
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.Resources
import org.mz.killrs.shared.SurfaceBrand
import org.mz.killrs.shared.SurfaceError
import org.mz.killrs.shared.TextWhite
import rememberMessageBarState

@Composable
fun AuthenticationScreen(
    navigateToHome: () -> Unit,
    ///navigateToManageProduct: () -> Unit,
) {

    val scope = rememberCoroutineScope()
    val viewModel = koinViewModel<AuthViewModel>()
    val messageBarState = rememberMessageBarState()
    var loadingState by remember { mutableStateOf(false) }

    val brandGreen = Color(0xFF003D27)
    val brandCream = Color(0xFFFFF8DF)
    Scaffold(containerColor = brandGreen) { padding ->
        ContentWithMessageBar(
            contentBackgroundColor = brandGreen,
            modifier = Modifier
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ),
            messageBarState = messageBarState,
            errorMaxLines = 2,
            errorContentColor = TextWhite,
            errorContainerColor = SurfaceError,
            successContainerColor = SurfaceBrand
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = brandGreen)
            ) {
                val compactHeight = maxHeight < 480.dp
                Image(
                    painter = painterResource(Resources.Image.WelcomeArtwork),
                    contentDescription = null,
                    contentScale = if (compactHeight) ContentScale.Fit else ContentScale.Crop,
                    modifier = if (compactHeight) Modifier.fillMaxHeight().fillMaxWidth(0.45f)
                        else Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .align(if (compactHeight) Alignment.CenterEnd else Alignment.BottomCenter)
                        .fillMaxWidth(if (compactHeight) 0.55f else 1f)
                        .widthIn(max = 480.dp)
                        .heightIn(max = maxHeight)
                        .padding(24.dp)
                        .background(Color(0xE6002C1D), RoundedCornerShape(24.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "MZBotanicals",
                            textAlign = TextAlign.Center,
                            fontFamily = Exo2FontRegular(),
                            fontSize = FontSize.EXTRA_LARGE,
                            color = brandCream
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            modifier = Modifier
                                .fillMaxWidth(),
                            text = "Sign in to continue",
                            textAlign = TextAlign.Center,
                            fontFamily = Exo2FontRegular(),
                            fontSize = FontSize.MEDIUM,
                            color = brandCream
                        )
                    Spacer(Modifier.height(24.dp))

                    GoogleAuthenticationButton(
                        loading = loadingState,
                        onClick = { loadingState = true },
                        onResult = { result ->
                            result.onSuccess { user ->
                                viewModel.createCustomer(
                                    user = user,
                                    onSuccess = {
                                        scope.launch {
                                            loadingState = false
                                            messageBarState.addSuccess("Authentication successful")
                                            delay(2000)
                                            navigateToHome()
                                        }
                                    },
                                    onError = { message ->
                                        scope.launch {
                                            loadingState = false
                                            messageBarState.addError(message)
                                        }
                                    },
                                )
                            }.onFailure { error ->
                                val message = when {
                                    error.message?.contains("network", ignoreCase = true) == true ->
                                        "No internet connection"

                                    error.message?.contains("idtoken is null", ignoreCase = true) == true ->
                                        "Google Sign-In could not create an ID token. Check the Firebase OAuth configuration."

                                    else -> error.message ?: "Google Sign-In failed. Please try again."
                                }
                                messageBarState.addError(message)
                                loadingState = false
                            }
                        }
                    )
                }
            }
        }
    }
}
