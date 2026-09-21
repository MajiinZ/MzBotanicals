package org.mz.killrs.manage_product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.storage.File
import kotlinx.coroutines.launch
import org.mz.data.domain.AdminRepository
import org.mz.killrs.shared.domain.Product
import org.mz.killrs.shared.domain.ProductCategory
import org.mz.killrs.shared.util.RequestState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
data class ManageProductState(
    val id: String = Uuid.random().toHexString(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val title: String = "",
    val description: String = "",
    val thumbnail: String = "",
    val category: ProductCategory = ProductCategory.Sativa,
    val strains: String = "",
    val amountOfSeeds: Int? = null,
    val price: Double = 0.0,
    val isNew: Boolean = false,
    val isPopular: Boolean = false,
    val isDiscounted: Boolean = false
)

class ManageProductViewModel(
    private val adminRepository: AdminRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val productId = savedStateHandle.get<String>("id") ?: ""

    var screenState by mutableStateOf(ManageProductState())
        private set

    var thumbnailUploaderState: RequestState<Unit> by mutableStateOf(RequestState.Idle)
        private set

    val isEditing: Boolean get() = productId.isNotEmpty()

    val isFormValid: Boolean
        get() = screenState.title.isNotBlank() &&
                screenState.description.isNotBlank() &&
                screenState.amountOfSeeds != null &&
                (screenState.amountOfSeeds ?: -1) >= 0 &&
                screenState.price > 0.0


    init {
        if (isEditing) {
            viewModelScope.launch {
                val result = adminRepository.readProductById(productId)
                if (result.isSuccess()) {
                    val product = result.getSuccessData()
                    updateFromProduct(product)
                    updateThumbnailUploaderState(RequestState.Success(Unit))
                }
            }
        }
    }

    private fun updateFromProduct(product: Product) {
        screenState = screenState.copy(
            id = product.id,
            createdAt = product.createdAt,
            title = product.title,
            description = product.description,
            thumbnail = product.thumbnail,
            category = ProductCategory.valueOf(product.category),
            strains = product.strains?.let {
                it.joinToString(",")
            } ?: run {
                ""
            },
            amountOfSeeds = product.amountOfSeeds?.toIntOrNull(),
            price = product.price,
            isNew = product.isNew,
            isPopular = product.isPopular,
            isDiscounted = product.isDiscounted
        )
    }

    // === State update functions ===


    // === Upload Thumbnail ===
    fun uploadThumbnailToStorage(file: File?, onSuccess: () -> Unit) {
        if (file == null) {
            updateThumbnailUploaderState(RequestState.Error("File is null. Error while selecting an image."))
            return
        }
        updateThumbnailUploaderState(RequestState.Loading)

        viewModelScope.launch {
            try {
                val downloadUrl = adminRepository.uploadImageToStorage(file)
                if (downloadUrl.isNullOrEmpty()) throw Exception("Failed to retrieve download URL after upload.")

                if (isEditing) {
                    adminRepository.updateProductThumbnail(
                        productId = productId,
                        downloadUrl = downloadUrl,
                        onSuccess = {
                            onSuccess()
                            updateThumbnailUploaderState(RequestState.Success(Unit))
                            updateThumbnail(downloadUrl)

                        },
                        onError = { msg -> updateThumbnailUploaderState(RequestState.Error(msg)) }
                    )
                } else {
                    updateThumbnail(downloadUrl)
                    updateThumbnailUploaderState(RequestState.Success(Unit))
                    onSuccess()
                }
            } catch (e: Exception) {
                updateThumbnailUploaderState(RequestState.Error("Error while uploading: ${e.message}"))
            }
        }
    }

    // === Create or Update Product ===
    fun createNewProduct(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isFormValid) {
            onError("Add a title, description, valid stock amount, and price before saving.")
            return
        }
        viewModelScope.launch {
            adminRepository.createNewProduct(
                product = screenState.toProduct(),
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    fun updateProduct(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (isFormValid)
            viewModelScope.launch {
                adminRepository.updateProduct(
                    product = Product(
                        id = productId,
                        createdAt = screenState.createdAt,
                        title = screenState.title,
                        description = screenState.description,
                        thumbnail = screenState.thumbnail,
                        category = screenState.category.name,
                        strains = screenState.strains.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        amountOfSeeds = screenState.amountOfSeeds?.toString(),
                        price = screenState.price,
                        isNew = screenState.isNew,
                        isPopular = screenState.isPopular,
                        isDiscounted = screenState.isDiscounted
                    ),
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        else {
            onError("Form is not valid.")
        }
    }

    // === Delete operations ===
    fun deleteThumbnailFromStorage(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            adminRepository.deleteImageFromStorage(
                downloadUrl = screenState.thumbnail,
                onSuccess = {
                    if (isEditing) {
                        viewModelScope.launch {
                            adminRepository.updateProductThumbnail(
                                productId = productId,
                                downloadUrl = "",
                                onSuccess = {
                                    updateThumbnail("")
                                    updateThumbnailUploaderState(RequestState.Idle)
                                    onSuccess()
                                },
                                onError = { onError(it) }
                            )
                        }
                    } else {
                        updateThumbnail("")
                        updateThumbnailUploaderState(RequestState.Idle)
                        onSuccess()
                    }
                },
                onError = onError
            )
        }
    }

    fun deleteProduct(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        productId.takeIf { it.isNotEmpty() }?.let { id ->
            viewModelScope.launch {
                adminRepository.deleteProduct(
                    productId = id,
                    onSuccess = {
                        deleteThumbnailFromStorage(
                            onSuccess = {},
                            onError = {}
                        )
                        onSuccess()
                    },
                    onError = { message -> onError(message) }
                )
            }
        }
    }

    init {
        productId.takeIf { it.isNotEmpty() }?.let { id ->
            viewModelScope.launch {
                val selectedProduct = adminRepository.readProductById(id)
                if (selectedProduct.isSuccess()) {
                    val product = selectedProduct.getSuccessData()
                    updateId(product.id)
                    updateCreatedAt(product.createdAt)
                    updateTitle(product.title)
                    updateDescription(product.description)
                    updateThumbnail(product.thumbnail)
                    updateCategory(ProductCategory.valueOf(product.category))
                    updateStrains(product.strains?.joinToString(",") ?: "")
                }
            }
        }
    }

    private fun updateId(value: String) {
        screenState = screenState.copy(id = value)
    }

    fun updateCreatedAt(value: Long) {
        screenState = screenState.copy(createdAt = value)

    }

    fun updateTitle(value: String) {
        screenState = screenState.copy(title = value)
    }

    fun updateDescription(value: String) {
        screenState = screenState.copy(description = value)
    }

    fun updateThumbnail(value: String) {
        screenState = screenState.copy(thumbnail = value)
    }

    fun updateCategory(value: ProductCategory) {
        screenState = screenState.copy(category = value)
    }

    fun updateStrains(value: String) {
        screenState = screenState.copy(strains = value)
    }

    private fun updateState(update: ManageProductState.() -> ManageProductState) {
        screenState = screenState.update()
    }

    fun updateThumbnailUploaderState(value: RequestState<Unit>) {
        thumbnailUploaderState = value
    }

    fun updateAmountOfSeeds(value: Int?) {
        screenState = screenState.copy(amountOfSeeds = value)
    }

    fun updatePrice(value: Double) {
        screenState = screenState.copy(price = value)
    }

    fun updateNew(value: Boolean) {
        screenState = screenState.copy(isNew = value)
    }

    fun updatePopular(value: Boolean) {
        screenState = screenState.copy(isPopular = value)
    }

    fun updateDiscounted(value: Boolean) {
        screenState = screenState.copy(isDiscounted = value)
    }


    // === Helper to convert state to domain Product ===
    private fun ManageProductState.toProduct(): Product = Product(
        id = id,
        createdAt = createdAt,
        title = title,
        description = description,
        thumbnail = thumbnail,
        category = category.name,
        strains = strains.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        amountOfSeeds = amountOfSeeds?.toString(),
        price = price,
        isNew = isNew,
        isPopular = isPopular,
        isDiscounted = isDiscounted
    )
}
