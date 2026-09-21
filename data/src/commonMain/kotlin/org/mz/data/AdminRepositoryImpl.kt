package org.mz.data


import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeout
import org.mz.data.domain.AdminRepository
import org.mz.killrs.shared.Constants.ADMIN_EMAIL
import org.mz.killrs.shared.domain.Product
import org.mz.killrs.shared.util.RequestState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AdminRepositoryImpl : AdminRepository {
    override fun getCurrentUserId() = Firebase.auth.currentUser?.uid

    private fun isAuthorizedAdmin(): Boolean =
        Firebase.auth.currentUser?.email.equals(ADMIN_EMAIL, ignoreCase = true)

    override suspend fun createNewProduct(
        product: Product,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val currentUserId = getCurrentUserId()
            if (currentUserId != null && isAuthorizedAdmin()) {
                val firestore = Firebase.firestore
                val productCollection = firestore.collection(collectionPath = "product")
                productCollection.document(product.id)
                    .set(product.copy(title = product.title.lowercase()))
                onSuccess()
            } else {
                onError("Administrator access is required.")
            }
        } catch (e: Exception) {
            onError("Error while creating a new product: ${e.message}")
        }
    }


    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadImageToStorage(file: File): String? {
        if (getCurrentUserId() == null || !isAuthorizedAdmin()) {
            throw IllegalStateException("Administrator access is required.")
        }

        val storage = Firebase.storage.reference
        val imagePath = storage.child(path = "images/${Uuid.random().toHexString()}")
        return try {
            withTimeout(timeMillis = 20000L) {
                imagePath.putFile(file)
                imagePath.getDownloadUrl()
            }
        } catch (error: Exception) {
            val details = error.causeMessages().lowercase()
            val message = when {
                "billing account" in details || "httpresult: 402" in details ->
                    "Image uploads are unavailable because Firebase billing is closed. " +
                            "Reopen or attach an active Google Cloud billing account, then try again."
                "permission" in details || "unauthorized" in details ->
                    "Firebase Storage denied this upload. Check the Storage security rules."
                "timeout" in details ->
                    "The image upload timed out. Check your connection and try a smaller image."
                else -> "Image upload failed: ${error.message ?: "Unknown Firebase Storage error."}"
            }
            throw IllegalStateException(message, error)
        }
    }

    private fun Throwable.causeMessages(): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = this
        while (current != null) {
            current.message?.let(messages::add)
            current = current.cause
        }
        return messages.joinToString(" ")
    }

    override suspend fun deleteImageFromStorage(
        downloadUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            if (!isAuthorizedAdmin()) {
                onError("Administrator access is required.")
                return
            }
            val storagePath = extractFirebaseStoragePath(downloadUrl)
            if (storagePath != null) {
                Firebase.storage.reference(storagePath).delete()
                onSuccess()
            } else {
                onError("Storage Path is null.")
            }
        } catch (e: Exception) {
            onError("Error while deleting a thumbnail: $e")
        }
    }



    override fun readLastTenProducts(): Flow<RequestState<List<Product>>> = channelFlow {
        try {
            val userId = getCurrentUserId()
            if (userId != null) {
                val database = Firebase.firestore
                database.collection(collectionPath = "product")
                    .orderBy("createdAt", Direction.DESCENDING)
                    .limit(15)
                    .snapshots
                    .collectLatest { query ->
                        val products = query.documents.map { document ->
                            Product(
                                id = document.id,
                                title = document.get(field = "title"),
                                amountOfSeeds = document.get(field = "amountOfSeeds"),
                                description = document.get(field = "description"),
                                thumbnail = document.get(field = "thumbnail"),
                                category = document.get(field = "category"),
                                strains = document.get(field = "strains"),
                                price = document.get(field = "price"),
                                isPopular = document.get(field = "isPopular"),
                                isDiscounted = document.get(field = "isDiscounted"),
                                isNew = document.get(field = "isNew"),
                                createdAt = document.get(field = "createdAt")
                            )
                        }
                        send(RequestState.Success(data = products.map { it.copy(title = it.title.uppercase()) }))
                    }
            } else {
                send(RequestState.Error("User is not available."))
            }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the last 10 items from the database: ${e.message}"))
        }
    }

    override suspend fun readProductById(id: String): RequestState<Product> {
        return try {
            val userId = getCurrentUserId()
            if (userId != null) {
                val database = Firebase.firestore
                val productDocument = database.collection(collectionPath = "product")
                    .document(id)
                    .get()
                if (productDocument.exists) {
                    val product = Product(
                        id = productDocument.id,
                        title = productDocument.get(field = "title"),
                        amountOfSeeds = productDocument.get(field = "amountOfSeeds"),
                        description = productDocument.get(field = "description"),
                        thumbnail = productDocument.get(field = "thumbnail"),
                        category = productDocument.get(field = "category"),
                        price = productDocument.get(field = "price"),
                        isPopular = productDocument.get(field = "isPopular"),
                        isDiscounted = productDocument.get(field = "isDiscounted"),
                        isNew = productDocument.get(field = "isNew"),
                        strains = productDocument.get(field = "strains"),
                        createdAt = productDocument.get(field = "createdAt")
                    )
                    RequestState.Success(product.copy(title = product.title.uppercase()))
                } else {
                    RequestState.Error("Selected product not found.")
                }
            } else {
                RequestState.Error("User is not available.")
            }
        } catch (e: Exception) {
            RequestState.Error("Error while reading a selected product: ${e.message}")
        }
    }

    private fun extractFirebaseStoragePath(downloadUrl: String): String? {
        val startIndex = downloadUrl.indexOf("/o/") + 3
        if (startIndex < 3) return null

        val endIndex = downloadUrl.indexOf("?", startIndex)
        val encodedPath = if (endIndex != -1) {
            downloadUrl.substring(startIndex, endIndex)
        } else {
            downloadUrl.substring(startIndex)
        }

        return decodeFirebasePath(encodedPath)
    }

    private fun decodeFirebasePath(encodedPath: String): String {
        return encodedPath
            .replace("%2F", "/")
            .replace("%20", " ")
    }

    override suspend fun updateProductThumbnail(
        productId: String,
        downloadUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val userId = getCurrentUserId()
            if (userId != null && isAuthorizedAdmin()) {
                val database = Firebase.firestore
                val productCollection = database.collection(collectionPath = "product")
                val existingProduct = productCollection
                    .document(productId)
                    .get()
                if (existingProduct.exists) {
                    productCollection.document(productId)
                        .update("thumbnail" to downloadUrl)
                    onSuccess()
                } else {
                    onError("Selected Product not found.")
                }
            } else {
                onError("Administrator access is required.")
            }
        } catch (e: Exception) {
            onError("Error while updating a thumbnail image: ${e.message}")
        }
    }

    override suspend fun updateProduct(
        product: Product,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val userId = getCurrentUserId()
            if (userId != null && isAuthorizedAdmin()) {
                val database = Firebase.firestore
                val productCollection = database.collection(collectionPath = "product")
                val existingProduct = productCollection
                    .document(product.id)
                    .get()
                if (existingProduct.exists) {
                    productCollection.document(product.id)
                        .update(product.copy(title = product.title.lowercase()))
                    onSuccess()
                } else {
                    onError("Selected Product not found.")
                }
            } else {
                onError("Administrator access is required.")
            }
        } catch (e: Exception) {
            onError("Error while updating a thumbnail image: ${e.message}")
        }
    }

    override suspend fun deleteProduct(
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val userId = getCurrentUserId()
            if (userId != null && isAuthorizedAdmin()) {
                val database = Firebase.firestore
                val productCollection = database.collection(collectionPath = "product")
                val existingProduct = productCollection
                    .document(productId)
                    .get()
                if (existingProduct.exists) {
                    productCollection.document(productId)
                        .delete()
                    onSuccess()
                } else {
                    onError("Selected Product not found.")
                }
            } else {
                onError("Administrator access is required.")
            }
        } catch (e: Exception) {
            onError("Error while updating a thumbnail image: ${e.message}")
        }
    }

    override fun searchProductsByTitle(searchQuery: String): Flow<RequestState<List<Product>>> =
        channelFlow {
            try {
                val userId = getCurrentUserId()
                if (userId != null) {
                    val database = Firebase.firestore

//                    val queryText = searchQuery.trim().lowercase()
//                    val endText = queryText + "\uf8ff"

                    database.collection(collectionPath = "product")
//                        .orderBy("title")
//                        .startAt(queryText)
//                        .endAt(endText)
                        .snapshots
                        .collectLatest { query ->
                            val products = query.documents.map { document ->
                                Product(
                                    id = document.id,
                                    title = document.get(field = "title"),
                                    amountOfSeeds = document.get(field = "amountOfSeeds"),
                                    description = document.get(field = "description"),
                                    thumbnail = document.get(field = "thumbnail"),
                                    category = document.get(field = "category"),
                                    price = document.get(field = "price"),
                                    isPopular = document.get(field = "isPopular"),
                                    isDiscounted = document.get(field = "isDiscounted"),
                                    isNew = document.get(field = "isNew"),
                                    strains = document.get(field = "strains"),
                                    createdAt = document.get(field = "createdAt")
                                )
                            }
                            send(
                                RequestState.Success(
                                    products
                                        .filter { it.title.contains(searchQuery) }
                                        .map { it.copy(title = it.title.uppercase()) }
                                )
                            )
                        }
                } else {
                    send(RequestState.Error("User is not available."))
                }
            } catch (e: Exception) {
                send(RequestState.Error("Error while searching products: ${e.message}"))
            }
        }

}
