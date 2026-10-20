// DetailsScreen.kt
package org.mz.killrs

import org.mz.killrs.shared.domain.displayDescription

import ContentWithMessageBar
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.mz.killrs.components.NumberOfSeedsChip
import org.mz.killrs.shared.*
import org.mz.killrs.shared.component.InfoCard
import org.mz.killrs.shared.component.LoadingCard
import org.mz.killrs.shared.component.PrimaryButton
import org.mz.killrs.shared.component.QuantityCounter
import org.mz.killrs.shared.domain.QuantityCounterSize
import org.mz.killrs.shared.util.DisplayResult
import rememberMessageBarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    navigateBack: () -> Unit,
    navigateToCart: () -> Unit
) {
    val messageBarState = rememberMessageBarState()
    val viewModel = koinViewModel<DetailsViewModel>()
    val product by viewModel.product.collectAsState()
    val selectedAmount = viewModel.selectedAmountOfSeeds // String? in your VM
    val quantity = viewModel.quantity

    Scaffold(
        containerColor = Surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Details",
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
                actions = {
                    QuantityCounter(
                        size = QuantityCounterSize.Large,
                        value = quantity,
                        onMinusClick = { viewModel::updateQuantity },
                        onPlusClick = { viewModel.updateQuantity(it) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = IconPrimary
                )
            )
        }
    ) { padding ->
        product.DisplayResult(
            onLoading = { LoadingCard(modifier = Modifier.fillMaxSize()) },
            onSuccess = { selectedProduct ->
                val description = buildString {
                    append(selectedProduct.displayDescription.trim().ifBlank {
                        "Explore ${selectedProduct.title} in the MZBotanicals collection. " +
                            "Review the product information and choose a pack size to add it to your cart."
                    })
                    val category = selectedProduct.category.trim()
                    val strains = selectedProduct.strains.orEmpty().filter { it.isNotBlank() }
                    if (category.isNotEmpty() || strains.isNotEmpty()) {
                        append("\n\nPRODUCT DETAILS")
                        if (category.isNotEmpty()) append("\nCategory: $category")
                        if (strains.isNotEmpty()) append("\nStrains: ${strains.joinToString(", ")}")
                    }
                    append("\n\nYOUR SELECTION\nChoose a seed pack size below, then set the number of packs. " +
                        "You can review your selection in the cart before checkout.")
                }
                var focusedDetail by rememberSaveable(selectedProduct.thumbnail, selectedProduct.title) {
                    mutableStateOf<String?>(null)
                }
                focusedDetail?.let { detail ->
                    AlertDialog(
                        onDismissRequest = { focusedDetail = null },
                        containerColor = Surface,
                        title = { Text(detail, color = TextPrimary) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                if (detail == "Product image") {
                                    AsyncImage(
                                        model = selectedProduct.thumbnail,
                                        error = painterResource(Resources.Image.KillrsLogo),
                                        fallback = painterResource(Resources.Image.KillrsLogo),
                                        contentDescription = selectedProduct.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().height(320.dp)
                                    )
                                } else {
                                    Text(
                                        text = when (detail) {
                                            "Price" -> "$${selectedProduct.price}"
                                            "Product name" -> selectedProduct.title
                                            else -> description
                                        },
                                        style = if (detail == "Description") MaterialTheme.typography.bodyLarge
                                            else MaterialTheme.typography.headlineMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { focusedDetail = null }) { Text("Close") }
                        }
                    )
                }
                ContentWithMessageBar(
                    contentBackgroundColor = Surface,
                    modifier = Modifier
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding()
                        ),
                    messageBarState = messageBarState
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(top = 12.dp)
                    ) {
                        // Product Image
                        AsyncImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BorderIdle, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClickLabel = "Enlarge product image") {
                                    focusedDetail = "Product image"
                                },
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(selectedProduct.thumbnail)
                                .crossfade(true)
                                .build(),
                            error = painterResource(Resources.Image.KillrsLogo),
                            fallback = painterResource(Resources.Image.KillrsLogo),
                            contentDescription = selectedProduct.title,
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Tap the image or a detail to take a closer look",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Price
                        Text(
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "View price") {
                                focusedDetail = "Price"
                            },
                            text = "$${selectedProduct.price}",
                            fontSize = FontSize.MEDIUM,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title
                        Text(
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "View product name") {
                                focusedDetail = "Product name"
                            },
                            text = selectedProduct.title,
                            fontSize = FontSize.EXTRA_MEDIUM,
                            fontWeight = FontWeight.Medium,
                            fontFamily = Exo2FontRegular(),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description
                        Text(
                            text = "Product overview",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Read full description") {
                                focusedDetail = "Description"
                            },
                            text = description,
                            fontSize = FontSize.REGULAR,
                            color = TextPrimary,
                            fontWeight = FontWeight.Normal,
                            lineHeight = FontSize.REGULAR * 1.3
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Seeds Selection (fixed 4 seed chips + reset chip below)
                        val fixedSeedOptions = listOf(0, 3, 5, 10)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            fixedSeedOptions.forEach { seeds ->
                                NumberOfSeedsChip(
                                    amountOfSeeds = seeds,
                                    isSelected = selectedAmount == seeds,
                                    onClick = {
                                        viewModel.updateSelectedAmountOfSeeds(seeds)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NumberOfSeedsChip(
                                amountOfSeeds = null, // Reset chip
                                isSelected = selectedAmount == null,
                                onClick = { viewModel.updateSelectedAmountOfSeeds(null) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Add to Cart Button
                        PrimaryButton(
                            icon = Resources.Icon.ShoppingCart,
                            text = "Add to Cart",
                            enabled = selectedAmount != null,
                            onClick = {
                                viewModel.addItemToCart(
                                    onSuccess = { messageBarState.addSuccess("Product added to cart.") },
                                    onError = { message -> messageBarState.addError(message) }
                                )
                            }
                        )
                    }
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
