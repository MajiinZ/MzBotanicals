package org.mz.killrs.profile

import ContentWithMessageBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.IconPrimary
import org.mz.killrs.shared.Resources
import org.mz.killrs.shared.Surface
import org.mz.killrs.shared.SurfaceBrand
import org.mz.killrs.shared.SurfaceError
import org.mz.killrs.shared.TextPrimary
import org.mz.killrs.shared.TextWhite
import org.mz.killrs.shared.component.InfoCard
import org.mz.killrs.shared.component.LoadingCard
import org.mz.killrs.shared.component.PrimaryButton
import org.mz.killrs.shared.component.ProfileForm
import org.mz.killrs.shared.util.DisplayResult
import rememberMessageBarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navigateBack: () -> Unit,
) {
    val viewModel = koinViewModel<ProfileViewModel>()
    val screenReady = viewModel.screenReady
    val screenState = viewModel.screenState
    val isFormValid = viewModel.isFormValid
    val messageBarState = rememberMessageBarState()

    Scaffold(
        containerColor = Surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Profile",
                        fontFamily = Exo2FontRegular(),
                        fontSize = FontSize.LARGE,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(Resources.Icon.BackArrow),
                            contentDescription = "Back Arrow icon",
                            tint = IconPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Surface,
                    scrolledContainerColor = Surface,
                    navigationIconContentColor = IconPrimary,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = IconPrimary
                )
            )
        }
    ) { padding ->
        ContentWithMessageBar(
            contentBackgroundColor = Surface,
            modifier = Modifier
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ),
            messageBarState = messageBarState,
            errorMaxLines = 2,
            errorContainerColor = SurfaceError,
            errorContentColor = TextWhite,
            successContainerColor = SurfaceBrand,
            successContentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = 12.dp,
                        bottom = 24.dp
                    )
                    .imePadding()
            ) {
                screenReady.DisplayResult(
                    onLoading = { LoadingCard(modifier = Modifier.fillMaxSize()) },
                    onSuccess = {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = viewModel.profileImageUrl,
                                    fallback = painterResource(Resources.Image.KillrsLogo),
                                    error = painterResource(Resources.Image.KillrsLogo),
                                    contentDescription = "Profile picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(88.dp).clip(CircleShape)
                                )
                                if (viewModel.isSavingPhoto) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                    Text("Saving photo…", color = TextPrimary)
                                }
                                ProfilePhotoPicker(
                                    enabled = !viewModel.isSavingPhoto,
                                    hasPhoto = viewModel.profileImageUrl != null,
                                    onSelected = { file ->
                                        viewModel.savePhoto(file,
                                            onSuccess = { messageBarState.addSuccess("Profile photo updated") },
                                            onError = { messageBarState.addError(it) })
                                    }
                                )
                                if (viewModel.profileImageUrl != null) {
                                    TextButton(
                                        enabled = !viewModel.isSavingPhoto,
                                        onClick = {
                                            viewModel.savePhoto(null,
                                                onSuccess = { messageBarState.addSuccess("Profile photo removed") },
                                                onError = { messageBarState.addError(it) })
                                        }
                                    ) { Text("Remove photo") }
                                }
                            }
                            ProfileForm(
                                modifier = Modifier.weight(1f),
                                state = screenState.state.abbreviation,
                                onStateSelect = viewModel::updateState,
                                firstName = screenState.firstName,
                                onFirstNameChanged = viewModel::updateFirstName,
                                lastName = screenState.lastName,
                                onLastNameChanged = viewModel::updateLastName,
                                city = screenState.city ?: "",
                                onCityChanged = viewModel::updateCity,
                                zipCode = screenState.zipCode,
                                onPostalCodeChanged = viewModel::updatePostalCode
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Update",
                                icon = Resources.Icon.Seed,
                                enabled = isFormValid,
                                onClick = {
                                    viewModel.updateCustomer(
                                        onSuccess = {
                                            messageBarState.addSuccess("Successfully updated!")
                                        },
                                        onError = { message ->
                                            messageBarState.addError(message)
                                        }
                                    )
                                }
                            )
                        }
                    },
                    onError = { message ->
                        InfoCard(
                            image = Resources.Image.KillrsLogo,
                            title = "Oops!",
                            subtitle = message
                        )
                    }
                )
            }
        }
    }
}
