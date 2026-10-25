package org.mz.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.mz.killrs.shared.BorderIdle
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.SurfaceBrand
import org.mz.killrs.shared.SurfaceLighter
import org.mz.killrs.shared.TextPrimary
import org.mz.killrs.shared.component.LoadingCard
import org.mz.killrs.shared.component.PrimaryButton
import org.mz.killrs.shared.domain.MockOrder
import org.mz.killrs.shared.util.RequestState
import org.mz.killrs.shared.util.formatUsd
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
fun OrdersScreen(navigateToShop: () -> Unit) {
    val viewModel = koinViewModel<OrdersViewModel>()
    val orders by viewModel.orders.collectAsState()

    when (val state = orders) {
        RequestState.Idle, RequestState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingCard()
                OrderText(
                    text = "Loading your orders...",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        is RequestState.Error -> OrdersMessage(
            title = "Couldn't load your orders",
            message = state.message,
            actionLabel = "Try again",
            onAction = viewModel::retry
        )
        is RequestState.Success -> {
            if (state.data.isEmpty()) {
                OrdersMessage(
                    title = "No orders yet",
                    message = "Complete a mock AeroPay checkout and your order will appear here.",
                    actionLabel = "Browse products",
                    onAction = navigateToShop
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        OrderText(
                            text = "Mock orders saved on this device. No payment was charged.",
                            secondary = true
                        )
                    }
                    items(state.data, key = { it.id }) { order ->
                        OrderCard(order)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = Exo2FontRegular(),
            fontSize = FontSize.EXTRA_MEDIUM,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            color = TextPrimary.copy(alpha = 0.65f),
            fontFamily = Exo2FontRegular(),
            fontSize = FontSize.REGULAR,
            textAlign = TextAlign.Center
        )
        PrimaryButton(text = actionLabel, onClick = onAction)
    }
}

@Composable
private fun OrderCard(order: MockOrder) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLighter, shape)
            .border(1.dp, BorderIdle, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Order #${order.id.takeLast(8).uppercase()}",
                color = TextPrimary,
                fontFamily = Exo2FontRegular(),
                fontSize = FontSize.EXTRA_REGULAR,
                fontWeight = FontWeight.SemiBold
            )
            OrderText(text = orderDate(order.createdAt), secondary = true)
        }
        OrderText(
            text = "AeroPay · Mock payment complete",
            modifier = Modifier
                .background(SurfaceBrand, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            bold = true
        )
        order.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OrderText(text = item.title, bold = true)
                    OrderText(
                        text = "${item.quantity} × ${formatUsd(item.unitPrice)}",
                        secondary = true
                    )
                }
                OrderText(text = formatUsd(item.unitPrice * item.quantity))
            }
        }
        HorizontalDivider(color = BorderIdle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OrderText(text = "Order total", bold = true)
            OrderText(text = formatUsd(order.totalAmount), bold = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OrderText(text = "Shipping details", bold = true)
            OrderText(text = order.customerName)
            OrderText(text = order.shippingAddress, secondary = true)
            OrderText(text = order.email, secondary = true)
        }
    }
}

@Composable
private fun OrderText(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    secondary: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier,
        color = TextPrimary.copy(alpha = if (secondary) 0.65f else 1f),
        fontFamily = Exo2FontRegular(),
        fontSize = FontSize.REGULAR,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    )
}

@OptIn(ExperimentalTime::class)
private fun orderDate(createdAt: Long): String =
    Instant.fromEpochMilliseconds(createdAt).toString().take(16).replace('T', ' ') + " UTC"
