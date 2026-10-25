package org.mz.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.mz.data.MockOrderRepository
import org.mz.killrs.shared.util.RequestState

class OrdersViewModel(
    repository: MockOrderRepository
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val orders = refresh.flatMapLatest { repository.observeOrders() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RequestState.Loading
        )

    fun retry() {
        refresh.value += 1
    }
}
