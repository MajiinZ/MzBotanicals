package org.mz.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mz.home.domain.BottomBarDestination
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.domain.Customer
import org.mz.killrs.shared.util.RequestState

private val NavigationBackground = Color(0xFFFBFAF4)
private val NavigationSelected = Color(0xFFE2ECD5)
private val NavigationGreen = Color(0xFF124735)
private val NavigationMuted = Color(0xFF747970)

@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    customer: RequestState<Customer>,
    selected: BottomBarDestination,
    onSelect: (BottomBarDestination) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavigationBackground)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomBarDestination.entries.forEach { destination ->
            val isSelected = selected == destination
            val animatedTint by animateColorAsState(
                targetValue = if (isSelected) NavigationGreen else NavigationMuted
            )
            val animatedBackground by animateColorAsState(
                targetValue = if (isSelected) NavigationSelected else Color.Transparent
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(animatedBackground)
                    .clickable { onSelect(destination) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(destination.icon),
                        contentDescription = destination.title,
                        tint = animatedTint
                    )

                    if (
                        destination == BottomBarDestination.Cart &&
                        customer.isSuccess() &&
                        customer.getSuccessData().cart.isNotEmpty()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .offset(x = 3.dp, y = (-2).dp)
                                .clip(CircleShape)
                                .background(NavigationGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = destination.title,
                    color = animatedTint,
                    fontFamily = Exo2FontRegular(),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
