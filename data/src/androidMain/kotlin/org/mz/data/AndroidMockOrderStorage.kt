package org.mz.data

import android.content.Context

class AndroidMockOrderStorage(context: Context) : MockOrderStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "mzbotanicals_mock_orders",
        Context.MODE_PRIVATE
    )

    override suspend fun read(customerId: String): String? = preferences.getString(key(customerId), null)

    override suspend fun write(customerId: String, value: String) {
        val key = key(customerId)
        val previous = preferences.getString(key, null)
        if (!preferences.edit().putString(key, value).commit()) {
            // A failed commit can still update SharedPreferences' memory cache. Restore it so
            // a retry cannot mistake an unsaved order for a durable successful checkout.
            val rollback = preferences.edit()
            if (previous == null) rollback.remove(key) else rollback.putString(key, previous)
            rollback.commit()
            error("Unable to save your demo order on this device. Please try again.")
        }
    }

    private fun key(customerId: String): String = "orders_$customerId"
}
