package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import org.mz.data.domain.ProductRepository
import org.mz.killrs.shared.domain.Product
import org.mz.killrs.shared.domain.ProductCategory
import org.mz.killrs.shared.util.RequestState

class ProductRepositoryImpl : ProductRepository {

    override fun getCurrentUserId(): String? {
        return Firebase.auth.currentUser?.uid
    }

    override fun readAllProducts(): Flow<RequestState<List<Product>>> = channelFlow {
        try {
            if (getCurrentUserId() != null) {
                Firebase.firestore
                    .collection("product")
                    .snapshots
                    .collectLatest { query ->
                        val products = query.documents.map { document ->
                            Product(
                                id = document.id,
                                title = document.get("title"),
                                amountOfSeeds = document.get("amountOfSeeds"),
                                description = document.get("description"),
                                thumbnail = document.get("thumbnail"),
                                category = document.get("category"),
                                strains = document.get("strains"),
                                price = document.get("price"),
                                isPopular = document.get("isPopular"),
                                isDiscounted = document.get("isDiscounted"),
                                isNew = document.get("isNew"),
                                createdAt = document.get("createdAt")
                            )
                        }
                            .sortedByDescending { it.createdAt }
                            .map { it.copy(title = it.title.uppercase()) }

                        send(RequestState.Success(products))
                    }
            } else {
                send(RequestState.Error("User is not available."))
            }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading products: ${e.message}"))
        }
    }

    override fun readNewAndDiscountedProducts(): Flow<RequestState<List<Product>>> = channelFlow {
        try {
            val userId = getCurrentUserId()
            if (userId != null) {
                Firebase.firestore
                    .collection("product")
                    .where { "isDiscounted" equalTo true }
                    .snapshots
                    .collectLatest { query ->
                        val products = query.documents.map { document ->
                            Product(
                                id = document.id,
                                title = document.get("title"),
                                amountOfSeeds = document.get("amountOfSeeds"),
                                description = document.get("description"),
                                thumbnail = document.get("thumbnail"),
                                category = document.get("category"),
                                strains = document.get("strains"),
                                price = document.get("price"),
                                isPopular = document.get("isPopular"),
                                isDiscounted = document.get("isDiscounted"),
                                isNew = document.get("isNew"),
                                createdAt = document.get("createdAt")
                            )
                        }
                        send(RequestState.Success(products.map { it.copy(title = it.title.uppercase()) }))
                    }
            } else {
                send(RequestState.Error("User is not available."))
            }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading discounted products: ${e.message}"))
        }
    }

    override fun readNewProducts(): Flow<RequestState<List<Product>>> = channelFlow {
        try {
            val userId = getCurrentUserId()
            if (userId != null) {
                Firebase.firestore
                    .collection("product")
                    .where { "isNew" equalTo true }
                    .snapshots
                    .collectLatest { query ->
                        val products = query.documents.map { document ->
                            Product(
                                id = document.id,
                                title = document.get("title"),
                                amountOfSeeds = document.get("amountOfSeeds"),
                                description = document.get("description"),
                                thumbnail = document.get("thumbnail"),
                                category = document.get("category"),
                                strains = document.get("strains"),
                                price = document.get("price"),
                                isPopular = document.get("isPopular"),
                                isDiscounted = document.get("isDiscounted"),
                                isNew = document.get("isNew"),
                                createdAt = document.get("createdAt")
                            )
                        }
                        send(RequestState.Success(products.map { it.copy(title = it.title.uppercase()) }))
                    }
            } else {
                send(RequestState.Error("User is not available."))
            }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading new products: ${e.message}"))
        }
    }

    override fun readProductByIdFlow(id: String): Flow<RequestState<Product>> = channelFlow {
        try {
            val userId = getCurrentUserId()
            if (userId != null) {
                Firebase.firestore
                    .collection("product")
                    .document(id)
                    .snapshots
                    .collectLatest { document ->
                        val product = Product(
                            id = document.id,
                            title = document.get("title"),
                            amountOfSeeds = document.get("amountOfSeeds"),
                            description = document.get("description"),
                            thumbnail = document.get("thumbnail"),
                            category = document.get("category"),
                            strains = document.get("strains"),
                            price = document.get("price"),
                            isPopular = document.get("isPopular"),
                            isDiscounted = document.get("isDiscounted"),
                            isNew = document.get("isNew"),
                            createdAt = document.get("createdAt")
                        )
                        send(RequestState.Success(product.copy(title = product.title.uppercase())))
                    }
            } else {
                send(RequestState.Error("User is not available."))
            }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading product: ${e.message}"))
        }
    }

    override fun readProductsByIdsFlow(ids: List<String>): Flow<RequestState<List<Product>>> = channelFlow {
        try {
            val userId = getCurrentUserId()
            if (userId != null) {
                val database = Firebase.firestore
                val productCollection = database.collection("product")

                val allProducts = mutableListOf<Product>()
                val chunks = ids.chunked(10)

                chunks.forEachIndexed { index, chunk ->
                    productCollection
                        .where { "id" inArray chunk }
                        .snapshots
                        .collectLatest { query ->
                            val products = query.documents.map { document ->
                                Product(
                                    id = document.id,
                                    title = document.get("title"),
                                    createdAt = document.get("createdAt"),
                                    description = document.get("description"),
                                    thumbnail = document.get("thumbnail"),
                                    category = document.get("category"),
                                    price = document.get("price"),
                                    isPopular = document.get("isPopular"),
                                    isDiscounted = document.get("isDiscounted"),
                                    isNew = document.get("isNew")
                                )
                            }
                            allProducts.addAll(products.map { it.copy(title = it.title.uppercase()) })

                            if (index == chunks.lastIndex) {
                                send(RequestState.Success(allProducts))
                            }
                        }
                }
            } else {
                send(RequestState.Error("User is not available."))
            }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading products by ids: ${e.message}"))
        }
    }

    override fun readProductsByCategoryFlow(category: ProductCategory): Flow<RequestState<List<Product>>> =
        channelFlow {
            try {
                val userId = getCurrentUserId()
                if (userId != null) {
                    Firebase.firestore
                        .collection("product")
                        .where { "category" equalTo category.name }
                        .snapshots
                        .collectLatest { query ->
                            val products = query.documents.map { document ->
                                Product(
                                    id = document.id,
                                    title = document.get("title"),
                                    amountOfSeeds = document.get("amountOfSeeds"),
                                    description = document.get("description"),
                                    thumbnail = document.get("thumbnail"),
                                    category = document.get("category"),
                                    strains = document.get("strains"),
                                    price = document.get("price"),
                                    isPopular = document.get("isPopular"),
                                    isDiscounted = document.get("isDiscounted"),
                                    isNew = document.get("isNew"),
                                    createdAt = document.get("createdAt")
                                )
                            }
                            send(RequestState.Success(products.map { it.copy(title = it.title.uppercase()) }))
                        }
                } else {
                    send(RequestState.Error("User is not available."))
                }
            } catch (e: Exception) {
                send(RequestState.Error("Error while reading products by category: ${e.message}"))
            }
        }
}
