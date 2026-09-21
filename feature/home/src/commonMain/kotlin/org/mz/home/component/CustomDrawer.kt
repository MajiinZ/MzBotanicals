package org.mz.home.component

import androidx.compose.animation.AnimatedContent
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
            .padding(horizontal = 12.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))
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
        Spacer(modifier = Modifier.height(50.dp))
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
        Spacer(modifier = Modifier.weight(1f))
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
