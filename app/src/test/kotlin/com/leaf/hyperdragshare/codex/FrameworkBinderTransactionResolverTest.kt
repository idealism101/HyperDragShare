package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameworkBinderTransactionResolverTest {
    @Test
    fun resolvesTransactionFromCurrentDexStaticFieldData() {
        assertEquals(
            71,
            FrameworkBinderTransactionResolver.findStaticInt(
                dexWithCancelCurrentTouch(71),
                STUB_DESCRIPTOR,
                "TRANSACTION_cancelCurrentTouch",
            ),
        )
    }

    @Test
    fun missingTransactionFieldDoesNotProduceACode() {
        assertEquals(
            -1,
            FrameworkBinderTransactionResolver.findStaticInt(
                dexWithCancelCurrentTouch(71),
                STUB_DESCRIPTOR,
                "TRANSACTION_notPresent",
            ),
        )
    }

    private companion object {
        private const val STUB_DESCRIPTOR = "Landroid/hardware/input/IInputManager\$Stub;"

        private fun dexWithCancelCurrentTouch(transactionCode: Int): ByteArray {
            val dex = ByteArray(320)
            dex[0] = 'd'.code.toByte()
            dex[1] = 'e'.code.toByte()
            dex[2] = 'x'.code.toByte()
            dex[3] = '\n'.code.toByte()
            dex[4] = '0'.code.toByte()
            dex[5] = '3'.code.toByte()
            dex[6] = '9'.code.toByte()

            val stringIdsOffset = 112
            val typeIdsOffset = 124
            val fieldIdsOffset = 132
            val classDefsOffset = 140
            val stringsOffset = 172
            val descriptorOffset = stringsOffset
            val typeOffset = writeString(dex, descriptorOffset, STUB_DESCRIPTOR)
            val fieldNameOffset = writeString(dex, typeOffset, "I")
            writeString(dex, fieldNameOffset, "TRANSACTION_cancelCurrentTouch")

            putInt(dex, 0x38, 3)
            putInt(dex, 0x3c, stringIdsOffset)
            putInt(dex, 0x40, 2)
            putInt(dex, 0x44, typeIdsOffset)
            putInt(dex, 0x50, 1)
            putInt(dex, 0x54, fieldIdsOffset)
            putInt(dex, 0x60, 1)
            putInt(dex, 0x64, classDefsOffset)

            putInt(dex, stringIdsOffset, descriptorOffset)
            putInt(dex, stringIdsOffset + 4, typeOffset)
            putInt(dex, stringIdsOffset + 8, fieldNameOffset)
            putInt(dex, typeIdsOffset, 0)
            putInt(dex, typeIdsOffset + 4, 1)
            putShort(dex, fieldIdsOffset, 0)
            putShort(dex, fieldIdsOffset + 2, 1)
            putInt(dex, fieldIdsOffset + 4, 2)

            val classDataOffset = 300
            val staticValuesOffset = 306
            putInt(dex, classDefsOffset, 0)
            putInt(dex, classDefsOffset + 24, classDataOffset)
            putInt(dex, classDefsOffset + 28, staticValuesOffset)
            dex[classDataOffset] = 1
            dex[classDataOffset + 4] = 0
            dex[classDataOffset + 5] = 0x18
            dex[staticValuesOffset] = 1
            dex[staticValuesOffset + 1] = 0x04
            dex[staticValuesOffset + 2] = transactionCode.toByte()
            return dex
        }

        private fun writeString(target: ByteArray, offset: Int, value: String): Int {
            var cursor = offset
            target[cursor++] = value.length.toByte()
            for (index in 0 until value.length) {
                target[cursor++] = value[index].code.toByte()
            }
            target[cursor++] = 0
            return cursor
        }

        private fun putInt(target: ByteArray, offset: Int, value: Int) {
            target[offset] = value.toByte()
            target[offset + 1] = (value ushr 8).toByte()
            target[offset + 2] = (value ushr 16).toByte()
            target[offset + 3] = (value ushr 24).toByte()
        }

        private fun putShort(target: ByteArray, offset: Int, value: Int) {
            target[offset] = value.toByte()
            target[offset + 1] = (value ushr 8).toByte()
        }
    }
}
