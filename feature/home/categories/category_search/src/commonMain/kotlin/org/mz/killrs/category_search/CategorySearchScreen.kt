package org.mz.killrs.category_search

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.Resources
import org.mz.killrs.shared.component.InfoCard
import org.mz.killrs.shared.component.LoadingCard
import org.mz.killrs.shared.component.ProductCard
import org.mz.killrs.shared.domain.ProductCategory
import org.mz.killrs.shared.util.DisplayResult

private val CategoryBackground = Color(0xFFEAF4EE)
private val CategorySurface = Color(0xFFFFFFFF)
private val CategoryBrand = Color(0xFF00452C)
private val CategoryMuted = Color(0xFF587064)
private val CategoryBorder = Color(0xFFC5DCCF)
private val CategoryPill = Color(0xFFD7EADD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySearchScreen(
    category: ProductCategory,
    navigateToDetails: (String) -> Unit,
    navigateBack: () -> Unit,
) {
    val viewModel = koinViewModel<CategorySearchViewmodel>()
    val filteredProducts by viewModel.filteredProducts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        containerColor = CategoryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CATEGORY COLLECTION",
                            fontFamily = Exo2FontRegular(),
                            fontSize = FontSize.EXTRA_SMALL,
                            fontWeight = FontWeight.Bold,
                            color = CategoryMuted
                        )
                        Text(
                            text = category.title,
                            fontFamily = Exo2FontRegular(),
                            fontSize = FontSize.EXTRA_MEDIUM,
                            fontWeight = FontWeight.Bold,
                            color = CategoryBrand,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(Resources.Icon.BackArrow),
                            contentDescription = "Go back",
                            tint = CategoryBrand
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CategoryBackground,
                    scrolledContainerColor = CategoryBackground,
                    navigationIconContentColor = CategoryBrand,
                    titleContentColor = CategoryBrand
                )
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CategoryBackground)
                .padding(
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding()
                )
        ) {
            CategorySearchHeader(
                category = category,
                query = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                onClearQuery = { viewModel.updateSearchQuery("") }
            )

            filteredProducts.DisplayResult(
                modifier = Modifier.fillMaxSize(),
                backgroundColor = CategoryBackground,
                onLoading = {
                    LoadingCard(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CategoryBackground)
                    )
                },
                onSuccess = { products ->
                    if (products.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = 28.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                ResultsHeader(
                                    count = products.size,
                                    isSearching = searchQuery.isNotBlank()
                                )
                            }
                            items(
                                items = products,
                                key = { it.id }
                            ) { product ->
                                ProductCard(
                                    modifier = Modifier.shadow(
                                        elevation = 5.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        clip = false
                                    ),
                                    product = product,
                                    onClick = navigateToDetails
                                )
                            }
                        }
                    } else {
                        EmptySearchState(
                            query = searchQuery,
                            category = category
                        )
                    }
                },
                onError = { message ->
                    InfoCard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        image = Resources.Image.KillrsLogo,
                        title = "We couldn't load this collection",
                        subtitle = message
                    )
                },
                transitionSpec = fadeIn() togetherWith fadeOut()
            )
        }
    }
}

@Composable
private fun CategorySearchHeader(
    category: ProductCategory,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CategorySurface),
        border = BorderStroke(1.dp, CategoryBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(category.color)
                )
                Text(
                    text = "${category.title} genetics",
                    fontFamily = Exo2FontRegular(),
                    fontSize = FontSize.SMALL,
                    fontWeight = FontWeight.Bold,
                    color = CategoryBrand
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Find the right strain",
                fontFamily = Exo2FontRegular(),
                fontSize = FontSize.EXTRA_MEDIUM,
                fontWeight = FontWeight.Bold,
                color = CategoryBrand
            )
            Text(
                text = "Search the collection by product name.",
                modifier = Modifier.padding(top = 4.dp),
                fontFamily = Exo2FontRegular(),
                fontSize = FontSize.REGULAR,
                color = CategoryMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false
                    )
                    .border(1.dp, CategoryBorder, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                placeholder = {
                    Text(
                        text = "Search ${category.title.lowercase()} products",
                        fontFamily = Exo2FontRegular(),
                        fontSize = FontSize.REGULAR
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Resources.Icon.Search),
                        contentDescription = null,
                        tint = CategoryBrand
                    )
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                painter = painterResource(Resources.Icon.Close),
                                contentDescription = "Clear search",
                                tint = CategoryMuted
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CategorySurface,
                    unfocusedContainerColor = CategorySurface,
                    focusedTextColor = CategoryBrand,
                    unfocusedTextColor = CategoryBrand,
                    focusedPlaceholderColor = CategoryMuted,
                    unfocusedPlaceholderColor = CategoryMuted,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = CategoryBrand
                )
            )
        }
    }
}

@Composable
private fun ResultsHeader(
    count: Int,
    isSearching: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = if (isSearching) "Search results" else "Available products",
                fontFamily = Exo2FontRegular(),
                fontSize = FontSize.EXTRA_REGULAR,
                fontWeight = FontWeight.Bold,
                color = CategoryBrand
            )
            Text(
                text = "Tap a product to view details",
                fontFamily = Exo2FontRegular(),
                fontSize = FontSize.SMALL,
                color = CategoryMuted
            )
        }

        Surface(
            color = CategoryPill,
            contentColor = CategoryBrand,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "$count ${if (count == 1) "item" else "items"}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontFamily = Exo2FontRegular(),
                fontSize = FontSize.SMALL,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptySearchState(
    query: String,
    category: ProductCategory,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CategorySurface),
        border = BorderStroke(1.dp, CategoryBorder)
    ) {
        InfoCard(
            modifier = Modifier.padding(24.dp),
            image = Resources.Image.KillrsLogo,
            title = if (query.isBlank()) "Collection coming soon" else "No matching products",
            subtitle = if (query.isBlank()) {
                "There are no ${category.title.lowercase()} products available yet."
            } else {
                "Try a different product name or clear your search."
            }
        )
    }
}
