package org.mz.data

import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DesktopMockOrderStorage(
    private val directory: Path = Path.of(System.getProperty("user.home"), ".mzbotanicals", "mock-orders")
) : MockOrderStorage {
    override suspend fun read(customerId: String): String? {
        val path = orderFile(customerId)
        return if (Files.exists(path)) Files.readString(path, StandardCharsets.UTF_8) else null
    }

    override suspend fun write(customerId: String, value: String) {
        Files.createDirectories(directory)
        val target = orderFile(customerId)
        val temporary = Files.createTempFile(directory, "orders-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(value.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun orderFile(customerId: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(customerId.toByteArray(StandardCharsets.UTF_8))
        val name = digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return directory.resolve("$name.json")
    }
}
