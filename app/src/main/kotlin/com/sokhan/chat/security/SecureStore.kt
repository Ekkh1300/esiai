package com.sokhan.chat.security

/**
 * Abstraction over the secure, on-device storage of provider API keys.
 *
 * Implementation contract:
 *  - Values must never be returned in plaintext from backups.
 *  - `read` for a missing id returns null rather than throwing.
 */
interface SecureStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun delete(key: String)
    fun deleteAll()
}

/**
 * Test double used by unit tests (the Keystore implementation requires an
 * Android runtime).
 */
class InMemorySecureStore : SecureStore {
    private val values = HashMap<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }

    override fun delete(key: String) {
        values.remove(key)
    }

    override fun deleteAll() {
        values.clear()
    }
}
