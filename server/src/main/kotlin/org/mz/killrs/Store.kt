package org.mz.killrs

interface Store {
    suspend fun ready()
    suspend fun products(limit: Int, after: String?): ProductPage
    suspend fun product(id: String): Product?
    suspend fun saveProduct(id: String, input: ProductInput, expectedRevision: Long, actor: String): Product
    suspend fun deleteProduct(id: String, expectedRevision: Long, actor: String)
    suspend fun profile(identity: Identity): CustomerProfile?
    suspend fun saveProfile(identity: Identity, input: ProfileInput): CustomerProfile
    suspend fun cart(uid: String): CartInput
    suspend fun saveCart(uid: String, input: CartInput): CartInput
    suspend fun quote(input: CartInput): Quote
    suspend fun createDraft(uid: String, key: String, input: CartInput): DraftResult
    suspend fun draft(id: String): OrderDraft?
}
