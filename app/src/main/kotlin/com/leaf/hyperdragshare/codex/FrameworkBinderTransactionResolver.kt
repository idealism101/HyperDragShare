package com.leaf.hyperdragshare.codex

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.zip.ZipFile

/** Resolves hidden AIDL transaction constants from the framework installed on this ROM. */
object FrameworkBinderTransactionResolver {
    private const val INPUT_MANAGER_STUB = "Landroid/hardware/input/IInputManager\$Stub;"
    private const val CANCEL_CURRENT_TOUCH = "TRANSACTION_cancelCurrentTouch"
    private const val DEX_HEADER_SIZE = 0x70
    private const val STRING_IDS_SIZE_OFFSET = 0x38
    private const val STRING_IDS_OFFSET = 0x3c
    private const val TYPE_IDS_SIZE_OFFSET = 0x40
    private const val TYPE_IDS_OFFSET = 0x44
    private const val FIELD_IDS_SIZE_OFFSET = 0x50
    private const val FIELD_IDS_OFFSET = 0x54
    private const val CLASS_DEFS_SIZE_OFFSET = 0x60
    private const val CLASS_DEFS_OFFSET = 0x64
    private const val FIELD_ID_SIZE = 8
    private const val CLASS_DEF_SIZE = 32
    private const val VALUE_BYTE = 0x00
    private const val VALUE_SHORT = 0x02
    private const val VALUE_CHAR = 0x03
    private const val VALUE_INT = 0x04
    private const val VALUE_LONG = 0x06
    private const val VALUE_FLOAT = 0x10
    private const val VALUE_DOUBLE = 0x11
    private const val VALUE_METHOD_TYPE = 0x15
    private const val VALUE_METHOD_HANDLE = 0x16
    private const val VALUE_STRING = 0x17
    private const val VALUE_TYPE = 0x18
    private const val VALUE_FIELD = 0x19
    private const val VALUE_METHOD = 0x1a
    private const val VALUE_ENUM = 0x1b
    private const val VALUE_ARRAY = 0x1c
    private const val VALUE_ANNOTATION = 0x1d
    private const val VALUE_NULL = 0x1e
    private const val VALUE_BOOLEAN = 0x1f

    @JvmStatic
    @Throws(IOException::class)
    fun resolveCancelCurrentTouchTransactionCode(): Int {
        var lastFailure: IOException? = null
        for (archivePath in frameworkArchivePaths()) {
            val archive = File(archivePath)
            if (!archive.isFile || !archive.canRead()) {
                continue
            }
            try {
                ZipFile(archive).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory || !isDexEntry(entry.name)) {
                            continue
                        }
                        zipFile.getInputStream(entry).use { input ->
                            val transactionCode = findStaticInt(
                                input.readAllBytes(),
                                INPUT_MANAGER_STUB,
                                CANCEL_CURRENT_TOUCH,
                            )
                            if (transactionCode > 0) {
                                return transactionCode
                            }
                        }
                    }
                }
            } catch (error: IOException) {
                lastFailure = error
            }
        }
        val failure = lastFailure
        if (failure != null) {
            throw IOException(
                "Unable to resolve IInputManager transaction from this ROM",
                failure,
            )
        }
        throw IOException("IInputManager transaction is unavailable on this ROM")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun findStaticInt(dex: ByteArray?, classDescriptor: String?, fieldName: String?): Int {
        if (dex == null || classDescriptor == null || fieldName == null) {
            return -1
        }
        return DexReader(dex).findStaticInt(classDescriptor, fieldName)
    }

    private fun frameworkArchivePaths(): Set<String> {
        val result = LinkedHashSet<String>()
        val bootClassPath = System.getenv("BOOTCLASSPATH")
        if (bootClassPath != null) {
            val entries = bootClassPath.split(":")
            for (entry in entries) {
                if (entry.endsWith("/framework.jar")) {
                    result.add(entry)
                }
            }
        }
        result.add("/system/framework/framework.jar")
        return result
    }

    private fun isDexEntry(name: String?): Boolean =
        name != null && name.startsWith("classes") && name.endsWith(".dex")

    private class DexReader(private val data: ByteArray) {
        init {
            requireRange(0, DEX_HEADER_SIZE)
            if (data[0] != 'd'.code.toByte() || data[1] != 'e'.code.toByte() ||
                data[2] != 'x'.code.toByte() || data[3] != '\n'.code.toByte()
            ) {
                throw IOException("Not a DEX file")
            }
        }

        private val stringIdsSize: Int = readInt(STRING_IDS_SIZE_OFFSET)
        private val stringIdsOffset: Int = readInt(STRING_IDS_OFFSET)
        private val typeIdsSize: Int = readInt(TYPE_IDS_SIZE_OFFSET)
        private val typeIdsOffset: Int = readInt(TYPE_IDS_OFFSET)
        private val fieldIdsSize: Int = readInt(FIELD_IDS_SIZE_OFFSET)
        private val fieldIdsOffset: Int = readInt(FIELD_IDS_OFFSET)
        private val classDefsSize: Int = readInt(CLASS_DEFS_SIZE_OFFSET)
        private val classDefsOffset: Int = readInt(CLASS_DEFS_OFFSET)

        init {
            requireTable(stringIdsOffset, stringIdsSize, 4)
            requireTable(typeIdsOffset, typeIdsSize, 4)
            requireTable(fieldIdsOffset, fieldIdsSize, FIELD_ID_SIZE)
            requireTable(classDefsOffset, classDefsSize, CLASS_DEF_SIZE)
        }

        private val strings: Array<String?> = arrayOfNulls(stringIdsSize)

        @Throws(IOException::class)
        fun findStaticInt(classDescriptor: String, fieldName: String): Int {
            for (classNumber in 0 until classDefsSize) {
                val classDefOffset = offsetOf(classDefsOffset, classNumber, CLASS_DEF_SIZE)
                val classIndex = readInt(classDefOffset)
                if (classDescriptor != typeDescriptor(classIndex)) {
                    continue
                }
                return findStaticIntInClass(classDefOffset, fieldName)
            }
            return -1
        }

        @Throws(IOException::class)
        private fun findStaticIntInClass(classDefOffset: Int, fieldName: String): Int {
            val classDataOffset = readInt(classDefOffset + 24)
            val staticValuesOffset = readInt(classDefOffset + 28)
            if (classDataOffset == 0 || staticValuesOffset == 0) {
                return -1
            }
            val classData = Cursor(classDataOffset)
            val staticFieldCount = readUleb128(classData)
            readUleb128(classData)
            readUleb128(classData)
            readUleb128(classData)

            var fieldIndex = 0
            var targetPosition = -1
            for (position in 0 until staticFieldCount) {
                fieldIndex += readUleb128(classData)
                readUleb128(classData)
                if (fieldName == fieldName(fieldIndex)) {
                    targetPosition = position
                }
            }
            if (targetPosition < 0) {
                return -1
            }

            val values = Cursor(staticValuesOffset)
            val valueCount = readUleb128(values)
            for (position in 0 until valueCount) {
                val value = readEncodedValue(values)
                if (position == targetPosition) {
                    return value ?: -1
                }
            }
            return -1
        }

        @Throws(IOException::class)
        private fun typeDescriptor(typeIndex: Int): String {
            if (typeIndex < 0 || typeIndex >= typeIdsSize) {
                throw IOException("Invalid DEX type index")
            }
            return stringAt(readInt(offsetOf(typeIdsOffset, typeIndex, 4)))
        }

        @Throws(IOException::class)
        private fun fieldName(fieldIndex: Int): String {
            if (fieldIndex < 0 || fieldIndex >= fieldIdsSize) {
                throw IOException("Invalid DEX field index")
            }
            val fieldOffset = offsetOf(fieldIdsOffset, fieldIndex, FIELD_ID_SIZE)
            return stringAt(readInt(fieldOffset + 4))
        }

        @Throws(IOException::class)
        private fun stringAt(stringIndex: Int): String {
            if (stringIndex < 0 || stringIndex >= stringIdsSize) {
                throw IOException("Invalid DEX string index")
            }
            val cached = strings[stringIndex]
            if (cached != null) {
                return cached
            }
            val cursor = Cursor(readInt(offsetOf(stringIdsOffset, stringIndex, 4)))
            readUleb128(cursor)
            val start = cursor.offset
            while (cursor.offset < data.size && data[cursor.offset] != 0.toByte()) {
                cursor.offset++
            }
            if (cursor.offset >= data.size) {
                throw IOException("Unterminated DEX string")
            }
            val value = String(data, start, cursor.offset - start, StandardCharsets.UTF_8)
            strings[stringIndex] = value
            return value
        }

        @Throws(IOException::class)
        private fun readEncodedValue(cursor: Cursor): Int? {
            val header = readByte(cursor)
            val valueType = header and 0x1f
            val byteCount = (header ushr 5) + 1
            when (valueType) {
                VALUE_BYTE, VALUE_SHORT, VALUE_INT, VALUE_LONG, VALUE_CHAR ->
                    return readIntegralValue(cursor, valueType, byteCount)
                VALUE_FLOAT, VALUE_DOUBLE, VALUE_METHOD_TYPE, VALUE_METHOD_HANDLE, VALUE_STRING,
                VALUE_TYPE, VALUE_FIELD, VALUE_METHOD, VALUE_ENUM -> {
                    skipBytes(cursor, byteCount)
                    return null
                }
                VALUE_ARRAY -> {
                    val arraySize = readUleb128(cursor)
                    for (index in 0 until arraySize) {
                        readEncodedValue(cursor)
                    }
                    return null
                }
                VALUE_ANNOTATION -> {
                    readUleb128(cursor)
                    val annotationSize = readUleb128(cursor)
                    for (index in 0 until annotationSize) {
                        readUleb128(cursor)
                        readEncodedValue(cursor)
                    }
                    return null
                }
                VALUE_NULL, VALUE_BOOLEAN -> return null
                else -> throw IOException("Unsupported DEX encoded value")
            }
        }

        @Throws(IOException::class)
        private fun readIntegralValue(cursor: Cursor, valueType: Int, byteCount: Int): Int? {
            if (byteCount > 8) {
                throw IOException("Invalid DEX integral value")
            }
            var value = 0L
            for (index in 0 until byteCount) {
                value = value or (readByte(cursor).toLong() shl (index * 8))
            }
            if (valueType != VALUE_CHAR && byteCount < 8 &&
                (value and (1L shl (byteCount * 8 - 1))) != 0L
            ) {
                value = value or (-1L shl (byteCount * 8))
            }
            return if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                value.toInt()
            } else {
                null
            }
        }

        @Throws(IOException::class)
        private fun readUleb128(cursor: Cursor): Int {
            var value = 0
            var shift = 0
            while (shift < 35) {
                val next = readByte(cursor)
                value = value or ((next and 0x7f) shl shift)
                if ((next and 0x80) == 0) {
                    return value
                }
                shift += 7
            }
            throw IOException("Invalid DEX ULEB128 value")
        }

        @Throws(IOException::class)
        private fun readInt(offset: Int): Int {
            requireRange(offset, 4)
            return (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                ((data[offset + 3].toInt() and 0xff) shl 24)
        }

        @Throws(IOException::class)
        private fun readByte(cursor: Cursor): Int {
            requireRange(cursor.offset, 1)
            return data[cursor.offset++].toInt() and 0xff
        }

        @Throws(IOException::class)
        private fun skipBytes(cursor: Cursor, count: Int) {
            requireRange(cursor.offset, count)
            cursor.offset += count
        }

        @Throws(IOException::class)
        private fun offsetOf(base: Int, index: Int, itemSize: Int): Int {
            val offset = base.toLong() + index.toLong() * itemSize
            if (offset > Integer.MAX_VALUE) {
                throw IOException("Invalid DEX table offset")
            }
            requireRange(offset.toInt(), itemSize)
            return offset.toInt()
        }

        @Throws(IOException::class)
        private fun requireTable(offset: Int, count: Int, itemSize: Int) {
            if (count < 0) {
                throw IOException("Invalid DEX table size")
            }
            val size = count.toLong() * itemSize
            if (size > Integer.MAX_VALUE) {
                throw IOException("Invalid DEX table length")
            }
            requireRange(offset, size.toInt())
        }

        @Throws(IOException::class)
        private fun requireRange(offset: Int, count: Int) {
            val end = offset.toLong() + count
            if (offset < 0 || count < 0 || end > data.size) {
                throw IOException("Invalid DEX offset")
            }
        }
    }

    private class Cursor(@JvmField var offset: Int)
}
