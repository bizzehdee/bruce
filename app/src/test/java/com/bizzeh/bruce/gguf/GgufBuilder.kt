package com.bizzeh.bruce.gguf

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes GGUF headers for tests; tensor data is omitted because the reader never reads it. */
class GgufBuilder(private val version: Int = 3) {
    private val keyValues = ByteArrayOutputStream()
    private val tensors = ByteArrayOutputStream()
    private var keyValueCount = 0L
    private var tensorCount = 0L

    fun string(key: String, value: String) = keyValue(key, 8) { writeString(value) }

    fun uint32(key: String, value: Int) = keyValue(key, 4) { writeInt(value) }

    fun int32(key: String, value: Int) = keyValue(key, 5) { writeInt(value) }

    fun uint64(key: String, value: Long) = keyValue(key, 10) { writeLong(value) }

    fun int64(key: String, value: Long) = keyValue(key, 11) { writeLong(value) }

    fun scalar(key: String, type: Int, bytes: Int) = keyValue(key, type) { write(ByteArray(bytes)) }

    fun stringArray(key: String, vararg values: String) = keyValue(key, 9) {
        writeInt(8)
        writeLong(values.size.toLong())
        values.forEach { writeString(it) }
    }

    fun scalarArray(key: String, elementType: Int, count: Long, elementBytes: Int) = keyValue(key, 9) {
        writeInt(elementType)
        writeLong(count)
        write(ByteArray((count * elementBytes).toInt()))
    }

    fun rawKeyValue(key: String, type: Int, body: ByteArrayOutputStream.() -> Unit) = keyValue(key, type, body)

    fun tensor(name: String, vararg dims: Long) = apply {
        tensorCount++
        tensors.writeString(name)
        tensors.writeInt(dims.size)
        dims.forEach { tensors.writeLong(it) }
        tensors.writeInt(0)
        tensors.writeLong(0)
    }

    fun build(tensorCountOverride: Long? = null, keyValueCountOverride: Long? = null): ByteArray =
        ByteArrayOutputStream().apply {
            write("GGUF".toByteArray())
            writeInt(version)
            writeLong(tensorCountOverride ?: tensorCount)
            writeLong(keyValueCountOverride ?: keyValueCount)
            write(keyValues.toByteArray())
            write(tensors.toByteArray())
        }.toByteArray()

    private fun keyValue(key: String, type: Int, body: ByteArrayOutputStream.() -> Unit) = apply {
        keyValueCount++
        keyValues.writeString(key)
        keyValues.writeInt(type)
        keyValues.body()
    }

    companion object {
        fun ByteArrayOutputStream.writeInt(value: Int) =
            write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())

        fun ByteArrayOutputStream.writeLong(value: Long) =
            write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())

        fun ByteArrayOutputStream.writeString(value: String) {
            val bytes = value.toByteArray()
            writeLong(bytes.size.toLong())
            write(bytes)
        }
    }
}
