package org.killrs.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mz.admin.AdminPanelViewModel
import org.mz.data.AdminRepositoryImpl
import org.mz.data.BackendClient
import org.mz.data.CustomerRepositoryImpl
import org.mz.data.OrderRepositoryImpl
import org.mz.data.ProductRepositoryImpl
import org.mz.data.domain.AdminRepository
import org.mz.data.domain.CustomerRepository
import org.mz.data.domain.OrderRepository
import org.mz.data.domain.ProductRepository
import org.mz.home.HomeGraphViewModel
import org.mz.killrs.CartViewModel
import org.mz.killrs.DetailsViewModel
import org.mz.killrs.auth.AuthViewModel
import org.mz.killrs.manage_product.ManageProductViewModel
import org.mz.killrs.profile.ProfileViewModel
import org.mz.products_overview.ProductsOverviewViewmodel
import org.mz.killrs.categories.CategoriesViewModel
import org.mz.killrs.category_search.CategorySearchViewmodel
import org.mz.killrs.CheckoutViewModel


val sharedModule = module {
    single { BackendClient() }
    single<CustomerRepository>{CustomerRepositoryImpl(get())}
    single<AdminRepository>{ AdminRepositoryImpl(get()) }
    single<ProductRepository>{ ProductRepositoryImpl() }
    single<OrderRepository>{ OrderRepositoryImpl() }

    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeGraphViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ManageProductViewModel)
    viewModelOf(::AdminPanelViewModel)
    viewModelOf(::ProductsOverviewViewmodel)
    viewModelOf(::DetailsViewModel)
    viewModelOf(::CartViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::CategorySearchViewmodel)
    viewModelOf(::CheckoutViewModel)
}



expect val targetModule: Module

fun initializeKoin(
    config: (KoinApplication.() -> Unit)? = null,
) {
    startKoin {
        config?.invoke(this)
        modules(sharedModule, targetModule)
    }
}
