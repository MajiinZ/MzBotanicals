package org.mz.data

/**
 * Safe local fallback for the optional MZ data service.
 * Production and development builds can override it with MZ_API_URL or mz.api.url.
 */
internal object MongoApiConfig {
    const val DEFAULT_ORIGIN: String = "http://127.0.0.1:8765"
}
