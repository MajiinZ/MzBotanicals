package org.mz.home.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.mz.killrs.shared.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mz.home.domain.DrawerItem
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.TextPrimary
import org.mz.killrs.shared.TextSecondary
import org.mz.killrs.shared.domain.Customer
import org.mz.killrs.shared.util.RequestState

@Composable
fun CustomDrawer(
    onProfileClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onCartClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onAdminPanelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    customer: RequestState<Customer>

) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.6f)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        val profile = customer.getSuccessDataOrNull()
        Column(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Open profile", onClick = onProfileClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = profile?.profileImageUrl,
                fallback = painterResource(Resources.Image.KillrsLogo),
                error = painterResource(Resources.Image.KillrsLogo),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = profile?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?.takeIf { it.isNotEmpty() } ?: "My Profile",
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "MZBotanicals",
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontFamily = Exo2FontRegular(),
            fontSize = FontSize.KINDA_EXTRA_LARGE
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Premium Genetics",
            textAlign = TextAlign.Center,
            color = TextPrimary,
            fontFamily = Exo2FontRegular(),
            fontSize = FontSize.REGULAR
        )
        Spacer(modifier = Modifier.height(24.dp))
        DrawerItem.entries.take(6).forEach { item ->
            DrawerItemCard(
                drawerItem = item,
                onClick = {
                    when (item) {
                        DrawerItem.Profile -> onProfileClick()
                        DrawerItem.Categories -> onCategoriesClick()
                        DrawerItem.CartFilled -> onCartClick()
                        DrawerItem.Orders -> onOrdersClick()
                        DrawerItem.Settings -> onSettingsClick()
                        DrawerItem.SignOut -> {
                            onSignOutClick()
                        }

                        else -> {}
                    }

                }
            )
            Spacer(modifier = Modifier.height(12.dp))

        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedContent(targetState = customer) { customerState ->
            if (customerState.isSuccess() && customerState.getSuccessData().isAdmin == true) {
                DrawerItemCard(
                    drawerItem = DrawerItem.AdminPanel,
                    onClick = { onAdminPanelClick() }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

    }
}
