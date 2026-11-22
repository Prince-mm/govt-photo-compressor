package com.prince.govtcompressor.core

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 24-bit uncompressed BMP writer (Android has no BMP encoder). */
object BmpWriter {
    fun encode24(src: Bitmap): ByteArray {
        val w = src.width
        val h = src.height
        val rowSize = ((w * 3 + 3) / 4) * 4
        val pixelDataSize = rowSize * h
        val fileSize = 54 + pixelDataSize
        val out = ByteArrayOutputStream(fileSize)
        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
        header.put(0x42.toByte()); header.put(0x4D.toByte()) // "BM"
        header.putInt(fileSize)
        header.putInt(0)
        header.putInt(54)
        header.putInt(40) // DIB header size
        header.putInt(w)
        header.putInt(h)
        header.putShort(1)
        header.putShort(24)
        header.putInt(0) // no compression
        header.putInt(pixelDataSize)
        header.putInt(2835) // ~72 DPI
        header.putInt(2835)
        header.putInt(0)
        header.putInt(0)
        out.write(header.array())
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val row = ByteArray(rowSize)
        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                row[x * 3] = (p and 0xFF).toByte()        // B
                row[x * 3 + 1] = ((p shr 8) and 0xFF).toByte()  // G
                row[x * 3 + 2] = ((p shr 16) and 0xFF).toByte() // R
            }
            out.write(row)
        }
        return out.toByteArray()
    }
}
