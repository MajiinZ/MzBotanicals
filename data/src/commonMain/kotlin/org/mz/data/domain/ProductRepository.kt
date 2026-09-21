// ProductRepository.kt
package org.mz.data.domain

import kotlinx.coroutines.flow.Flow
import org.mz.killrs.shared.domain.Product
import org.mz.killrs.shared.domain.ProductCategory
import org.mz.killrs.shared.util.RequestState

interface ProductRepository {
    fun getCurrentUserId(): String?
    fun readAllProducts(): Flow<RequestState<List<Product>>>
    fun readNewAndDiscountedProducts(): Flow<RequestState<List<Product>>>
    fun readNewProducts(): Flow<RequestState<List<Product>>>
    fun readProductByIdFlow(id: String): Flow<RequestState<Product>>
    //fun getProduct(productId: String): Flow<RequestState<Product>>
    fun readProductsByIdsFlow(ids: List<String>): Flow<RequestState<List<Product>>>
    fun readProductsByCategoryFlow(category: ProductCategory): Flow<RequestState<List<Product>>>
}
