package org.mz.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.storage
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.mz.data.domain.AdminRepository
import org.mz.killrs.shared.domain.Product
import org.mz.killrs.shared.util.RequestState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AdminRepositoryImpl(private val backend: BackendClient) : AdminRepository {
    override fun getCurrentUserId() = Firebase.auth.currentUser?.uid
    override suspend fun createNewProduct(product: Product, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        backend.saveProduct(product, 0)
    }
    override suspend fun updateProduct(product: Product, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        backend.saveProduct(product, product.revision)
    }
    override suspend fun deleteProduct(productId: String, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        val current = backend.product(productId)
        backend.request(HttpMethod.Delete, "/admin/products/${backend.safeId(productId)}", revision = current["revision"]!!.jsonPrimitive.long)
    }
    override suspend fun updateProductThumbnail(productId: String, downloadUrl: String, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        require(downloadUrl.isNotBlank()) { "Upload a replacement image before removing the current thumbnail." }
        val current = backend.product(productId).toProduct()
        backend.saveProduct(current.copy(thumbnail = downloadUrl), current.revision)
    }
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun uploadImageToStorage(file: File): String? {
        if (!backend.isAdmin()) return null
        return try {
            withTimeout(20_000) {
                val image = Firebase.storage.reference.child("images/${Uuid.random().toHexString()}")
                image.putFile(file)
                image.getDownloadUrl()
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { null }
    }
    override suspend fun deleteImageFromStorage(downloadUrl: String, onSuccess: () -> Unit, onError: (String) -> Unit) = backendAction(onSuccess, onError) {
        require(backend.isAdmin()) { "Administrator access is required." }
        val url = Url(downloadUrl)
        require(url.protocol.name == "https" && url.host == "firebasestorage.googleapis.com") { "Invalid product image URL." }
        val path = url.encodedPath.substringAfter("/o/", "").decodeURLPart()
        require(Regex("images/[A-Za-z0-9_-]{1,128}").matches(path)) { "Invalid product image path." }
        // Do not remove an image that is still used by a catalog item.
        val references = Firebase.firestore.collection("product").where { "thumbnail" equalTo downloadUrl }.get()
        require(references.documents.isEmpty()) { "Replace or remove the product before deleting its image." }
        Firebase.storage.reference.child(path).delete()
    }
    override fun readLastTenProducts(): Flow<RequestState<List<Product>>> = flow {
        try {
            Firebase.firestore.collection("product").orderBy("createdAt", Direction.DESCENDING).limit(15).snapshots.collect { query ->
                emit(RequestState.Success(query.documents.map { it.toProduct() }))
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { emit(RequestState.Error("Unable to load products.")) }
    }
    override suspend fun readProductById(id: String): RequestState<Product> = try {
        RequestState.Success(backend.product(id).toProduct())
    } catch (e: CancellationException) { throw e }
      catch (e: BackendFailure) { RequestState.Error(e.message) }
      catch (_: Exception) { RequestState.Error("Unable to load product." ) }
    override fun searchProductsByTitle(searchQuery: String): Flow<RequestState<List<Product>>> = flow {
        try {
            Firebase.firestore.collection("product").snapshots.collect { query ->
                emit(RequestState.Success(query.documents.map { it.toProduct() }.filter { it.title.contains(searchQuery, ignoreCase = true) }))
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { emit(RequestState.Error("Unable to search products.")) }
    }
    private fun DocumentSnapshot.toProduct() = Product(
        id = id, title = get<String>("title").uppercase(), description = get("description"), price = get("price"),
        thumbnail = get("thumbnail"), category = get("category"), amountOfSeeds = get("amountOfSeeds"), strains = get("strains"),
        isPopular = get("isPopular"), isNew = get("isNew"), isDiscounted = get("isDiscounted"), createdAt = get("createdAt"),
        revision = get<Long?>("revision") ?: 0,
    )
    private fun JsonObject.toProduct() = Product(
        id = getValue("id").jsonPrimitive.content, title = getValue("title").jsonPrimitive.content.uppercase(),
        description = getValue("description").jsonPrimitive.content, price = getValue("priceMinor").jsonPrimitive.long / 100.0,
        thumbnail = getValue("thumbnail").jsonPrimitive.content, category = getValue("category").jsonPrimitive.content,
        amountOfSeeds = get("amountOfSeeds")?.jsonPrimitive?.contentOrNull,
        strains = getValue("strains").jsonArray.map { it.jsonPrimitive.content },
        isPopular = getValue("isPopular").jsonPrimitive.boolean, isNew = getValue("isNew").jsonPrimitive.boolean,
        isDiscounted = getValue("isDiscounted").jsonPrimitive.boolean, createdAt = getValue("createdAt").jsonPrimitive.long,
        revision = getValue("revision").jsonPrimitive.long,
    )
}
