package org.mz.killrs.auth

import ContentWithMessageBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.mz.killrs.auth.component.GoogleAuthenticationButton
import org.mz.killrs.shared.Alpha
import org.mz.killrs.shared.Black
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.Resources
import org.mz.killrs.shared.SurfaceBrand
import org.mz.killrs.shared.SurfaceError
import org.mz.killrs.shared.TextPrimary
import org.mz.killrs.shared.TextSecondary
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

    Scaffold { padding ->
        ContentWithMessageBar(
            contentBackgroundColor = Black, // <- Set to black
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Black) // <- Set Box background to black
            ) {
                // Background logo image
                Image(
                    painter = painterResource(Resources.Image.KillrsLogo),
                    contentDescription = "Background Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.03f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 24.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "MZBotanicals",
                            textAlign = TextAlign.Center,
                            fontFamily = Exo2FontRegular(),
                            fontSize = FontSize.EXTRA_LARGE,
                            color = TextSecondary
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(Alpha.HALF),
                            text = "Sign in to continue",
                            textAlign = TextAlign.Center,
                            fontFamily = Exo2FontRegular(),
                            fontSize = FontSize.MEDIUM,
                            color = TextPrimary
                        )
                    }

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
