package org.mz.products_overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.mz.data.domain.ProductRepository
import org.mz.killrs.shared.util.RequestState

class ProductsOverviewViewmodel(
    private val productRepository: ProductRepository,
) : ViewModel() {

    val products = productRepository.readAllProducts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RequestState.Loading
    )
}
