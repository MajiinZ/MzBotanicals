package org.mz.home

import ContentWithMessageBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.mz.home.component.BottomBar
import org.mz.home.component.CustomDrawer
import org.mz.home.domain.BottomBarDestination
import org.mz.home.domain.CustomDrawerState
import org.mz.home.domain.isOpened
import org.mz.home.domain.opposite
import org.mz.killrs.CartScreen
import org.mz.killrs.DetailsScreen
import org.mz.killrs.categories.CategoriesScreen
import org.mz.killrs.category_search.CategorySearchScreen
import org.mz.killrs.profile.ProfileScreen
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.FontSize
import org.mz.killrs.shared.IconPrimary
import org.mz.killrs.shared.Resources
import org.mz.killrs.shared.Surface
import org.mz.killrs.shared.SurfaceLighter
import org.mz.killrs.shared.TextPrimary
import org.mz.killrs.shared.domain.ProductCategory
import org.mz.killrs.shared.navigation.Screen
import org.mz.killrs.shared.util.RequestState
import org.mz.killrs.shared.util.getScreenWidth
import org.mz.products_overview.ProductsOverviewScreen
import rememberMessageBarState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGraphScreen(
    navigateToAuth: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToAdmin: () -> Unit,
    navigateToDetails: (String) -> Unit,
    navigateToCart: () -> Unit,
    navigateToCategorySearch: (String) -> Unit,
    navigateToCheckout: (String) -> Unit
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState()

    val selectedDestination by remember {
        derivedStateOf {
            val route = currentRoute.value?.destination?.route.orEmpty()
            when {
                route.contains(BottomBarDestination.Cart.screen.toString()) -> BottomBarDestination.Cart
                route.contains(BottomBarDestination.Profile.screen.toString()) -> BottomBarDestination.Profile
                route.contains(BottomBarDestination.Categories.screen.toString()) -> BottomBarDestination.Categories
                else -> BottomBarDestination.ProductsOverview
            }
        }
    }

    val screenWidth = remember { getScreenWidth() }
    val drawerState = remember { mutableStateOf(CustomDrawerState.Closed) }

    val offSetValue by remember { derivedStateOf { (screenWidth / 1.5).dp } }
    val animatedOffset by animateDpAsState(
        targetValue = if (drawerState.value.isOpened()) offSetValue else 0.dp
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (drawerState.value.isOpened()) 0.9f else 1f
    )
    val animatedRadius by animateDpAsState(
        targetValue = if (drawerState.value.isOpened()) 20.dp else 0.dp
    )

    val viewModel = koinViewModel<HomeGraphViewModel>()
    val customer by viewModel.customer.collectAsState()
    val totalAmount by viewModel.totalAmountFlow.collectAsState(RequestState.Loading)
    val messageBarState = rememberMessageBarState()

    fun navigateFromDrawer(destination: Screen) {
        drawerState.value = CustomDrawerState.Closed
        navController.navigate(destination) {
            launchSingleTop = true
            popUpTo<Screen.ProductsOverview> { saveState = true }
            restoreState = true
        }
    }

    val screenTitle by remember {
        derivedStateOf {
            val route = currentRoute.value?.destination?.route.orEmpty()
            when {
                route.contains(Screen.Orders.toString()) -> "Orders"
                route.contains(Screen.Settings.toString()) -> "Settings"
                else -> selectedDestination.title
            }
        }
    }

    LaunchedEffect(Unit) {
        println("TOTAL AMOUNT: $totalAmount")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLighter)
            .systemBarsPadding()
    ) {
        CustomDrawer(
            customer = customer,
            onProfileClick = { navigateFromDrawer(Screen.Profile) },
            onCategoriesClick = { navigateFromDrawer(Screen.Categories) },
            onCartClick = { navigateFromDrawer(Screen.Cart) },
            onOrdersClick = { navigateFromDrawer(Screen.Orders) },
            onAdminPanelClick = {
                drawerState.value = CustomDrawerState.Closed
                navigateToAdmin()
            },
            onSettingsClick = { navigateFromDrawer(Screen.Settings) },
            onSignOutClick = {
                drawerState.value = CustomDrawerState.Closed
                viewModel.signOut(
                    onSuccess = navigateToAuth,
                    onError = { message -> messageBarState.addError(message) }
                )
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(animatedRadius))
                .offset(x = animatedOffset)
                .scale(animatedScale)
                .shadow(20.dp, RoundedCornerShape(20.dp))
        ) {
            Scaffold(
                containerColor = Surface,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            AnimatedContent(targetState = screenTitle) { title ->
                                Text(
                                    text = title,
                                    fontFamily = Exo2FontRegular(),
                                    fontSize = FontSize.LARGE,
                                    color = TextPrimary
                                )
                            }
                        },
                        actions = {
                            AnimatedVisibility(
                                visible = selectedDestination == BottomBarDestination.Cart,
                            ) {


                                if (totalAmount.isSuccess()) {


                                } else if (totalAmount.isError()) {
                                    messageBarState.addError("Error fetching total amount.")
                                }

                                IconButton(
                                    onClick = {
                                        if (totalAmount.isSuccess()) {
                                            navigateToCheckout(
                                                totalAmount.getSuccessData().toString()
                                            )

                                        } else if (totalAmount.isError()) {
                                            messageBarState.addError("Something went wrong")
                                        }
                                    }

                                ) {
                                    Icon(
                                        painter = painterResource(Resources.Icon.ShoppingCartFilled),
                                        contentDescription = "Right Arrow",
                                        tint = IconPrimary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                drawerState.value = drawerState.value.opposite()
                            }) {
                                Icon(
                                    painter = painterResource(Resources.Icon.Menu),
                                    contentDescription = "Menu Icon",
                                    tint = IconPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Surface,
                            titleContentColor = TextPrimary,
                            navigationIconContentColor = TextPrimary,
                            actionIconContentColor = IconPrimary,
                            scrolledContainerColor = Surface
                        )
                    )
                }
            ) { padding ->
                ContentWithMessageBar(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding()
                        ),
                    messageBarState = messageBarState,
                    errorMaxLines = 3,
                    contentBackgroundColor = Surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding()
                            )
                    ) {
                        NavHost(
                            modifier = Modifier.weight(1f),
                            navController = navController,
                            startDestination = Screen.ProductsOverview
                        ) {
                            composable<Screen.ProductsOverview> {
                                ProductsOverviewScreen(
                                    navigateToDetails = navigateToDetails
                                )
                            }
                            composable<Screen.Cart> {
                                CartScreen(
                                    navigateToDetails = navigateToDetails,
                                    navigateToCheckout = navigateToCheckout,
                                    navigateToProfile = { navigateFromDrawer(Screen.Profile) },
                                    navigateToCategories = navigateToCategorySearch,
                                    navigateToCart = { navigateFromDrawer(Screen.Cart) },
                                    navigateToPayment = {
                                        navigateToCheckout(
                                            totalAmount.getSuccessData().toString()
                                        )
                                    }

                                )
                            }
                            composable<Screen.Profile> {
                                ProfileScreen(
                                    navigateBack = { navController.navigateUp() }
                                )
                            }
                            composable<Screen.Categories> {
                                CategoriesScreen(
                                    navigateToCategories = { categoryName ->
                                        navController.navigate(Screen.CategorySearch(categoryName))
                                    }
                                )
                            }
                            composable<Screen.Orders> {
                               OrdersScreen(navigateToShop = { navigateFromDrawer(Screen.ProductsOverview) }
                                )
                            }
                            composable<Screen.Settings> {
                                SettingsScreen()
                            }
                            composable<Screen.Details> {
                                DetailsScreen(
                                    navigateBack = { navController.popBackStack() },
                                    navigateToCart = { navigateFromDrawer(Screen.Cart) }
                                )
                            }
                            composable<Screen.CategorySearch> {
                                val category =
                                    ProductCategory.valueOf(it.toRoute<Screen.CategorySearch>().category.toString())
                                CategorySearchScreen(
                                    category = category,
                                    navigateBack = { navController.navigateUp() },
                                    navigateToDetails = { id ->
                                        navController.navigate(Screen.Details(id))
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.padding(12.dp)) {
                            BottomBar(
                                onSelect = { destination ->
                                    navController.navigate(destination.screen) {
                                        launchSingleTop = true
                                        popUpTo<Screen.ProductsOverview> {
                                            saveState = true
                                            inclusive = false
                                        }
                                        restoreState = true
                                    }
                                },
                                selected = selectedDestination,
                                customer = customer
                            )
                        }
                    }
                }
            }
        }
    }
}
