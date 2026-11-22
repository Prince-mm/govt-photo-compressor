package com.prince.govtcompressor.core

import android.graphics.Bitmap
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal baseline-TIFF decoder (Android has no built-in TIFF support).
 * Supports: little/big endian, uncompressed + PackBits, chunky planar,
 * 1-bit bilevel and 8-bit samples, BlackIsZero / WhiteIsZero / RGB / Palette.
 * Returns null for anything it cannot decode (caller reports it honestly).
 */
object TiffDecoder {

    fun decode(input: InputStream): Bitmap? {
        return try {
            val data = input.readBytes()
            decodeBytes(data)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBytes(data: ByteArray): Bitmap? {
        if (data.size < 8) return null
        val little = when {
            data[0] == 0x49.toByte() && data[1] == 0x49.toByte() -> true
            data[0] == 0x4D.toByte() && data[1] == 0x4D.toByte() -> false
            else -> return null
        }
        val order = if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val buf = ByteBuffer.wrap(data).order(order)
        if (buf.getShort(2) != 42.toShort()) return null
        val ifdOff = buf.getInt(4)
        if (ifdOff < 0 || ifdOff + 2 > data.size) return null
        val entries = buf.getShort(ifdOff).toInt() and 0xFFFF
        val tags = mutableMapOf<Int, Any>()
        for (i in 0 until entries) {
            val p = ifdOff + 2 + i * 12
            if (p + 12 > data.size) break
            val tag = buf.getShort(p).toInt() and 0xFFFF
            val type = buf.getShort(p + 2).toInt() and 0xFFFF
            val count = buf.getInt(p + 4)
            val valOff = p + 8
            val typeSize = when (type) { 1, 2, 6, 7 -> 1; 3, 8 -> 2; 4, 9, 11 -> 4; 5, 10, 12 -> 8; else -> 1 }
            val total = count * typeSize
            val srcPos = if (total <= 4) valOff else buf.getInt(valOff)
            if (srcPos < 0 || srcPos + total > data.size) continue
            val values: Any = when (type) {
                3 -> { // SHORT
                    val arr = IntArray(count)
                    val b = ByteBuffer.wrap(data, srcPos, total).order(order)
                    for (j in arr.indices) arr[j] = b.getShort().toInt() and 0xFFFF
                    if (count == 1) arr[0] else arr
                }
                4 -> { // LONG
                    val arr = IntArray(count)
                    val b = ByteBuffer.wrap(data, srcPos, total).order(order)
                    for (j in arr.indices) arr[j] = b.getInt()
                    if (count == 1) arr[0] else arr
                }
                1, 2, 6, 7 -> {
                    val arr = ByteArray(count)
                    data.copyInto(arr, 0, srcPos, srcPos + count)
                    if (count == 1) arr[0].toInt() and 0xFF else arr
                }
                else -> continue
            }
            tags[tag] = values
        }
        fun tagInt(tag: Int, def: Int): Int = when (val v = tags[tag]) {
            is Int -> v
            is IntArray -> v.firstOrNull() ?: def
            is ByteArray -> v.firstOrNull()?.toInt()?.and(0xFF) ?: def
            else -> def
        }
        fun tagInts(tag: Int): IntArray = when (val v = tags[tag]) {
            is Int -> intArrayOf(v)
            is IntArray -> v
            else -> intArrayOf()
        }
        val width = tagInt(256, 0)
        val height = tagInt(257, 0)
        if (width <= 0 || height <= 0 || width > 20000 || height > 20000) return null
        val compression = tagInt(259, 1)
        if (compression != 1 && compression != 32773) return null // none / PackBits only
        val photo = tagInt(262, 1)
        val bits = tagInt(258, 8)
        val spp = tagInt(277, 1)
        val planar = tagInt(284, 1)
        if (planar != 1) return null
        val stripOffsets = tagInts(273)
        val stripCounts = tagInts(279)
        val rowsPerStrip = tagInt(278, height)
        if (stripOffsets.isEmpty()) return null

        // Assemble raw strip data.
        val raw = ByteArray(width * height * spp * (if (bits == 1) 1 else 1) + 1024)
        var rawLen = 0
        // For 1-bit images the packed size differs; compute per-strip copy instead.
        val packed = ByteArray(stripOffsets.zip(stripCounts).sumOf { it.second })
        var pLen = 0
        for (i in stripOffsets.indices) {
            val off = stripOffsets[i]
            val cnt = if (i < stripCounts.size) stripCounts[i] else 0
            if (off < 0 || cnt <= 0 || off + cnt > data.size) continue
            if (compression == 32773) {
                var p = off
                val end = off + cnt
                while (p < end) {
                    val n = data[p++].toInt()
                    when {
                        n in 0..127 -> {
                            val c = n + 1
                            for (k in 0 until c) {
                                if (p < end && pLen < packed.size) packed[pLen++] = data[p++]
                            }
                        }
                        n in 129..255 -> {
                            val c = 257 - n
                            val b = if (p < end) data[p++] else 0
                            repeat(c) { if (pLen < packed.size) packed[pLen++] = b }
                        }
                        else -> { /* 128 = no-op */ }
                    }
                }
            } else {
                data.copyInto(packed, pLen, off, off + cnt)
                pLen += cnt
            }
        }

        val pixels = IntArray(width * height)
        val colorMap = tags[320] as? IntArray
        when {
            bits == 1 && spp == 1 -> {
                val rowBytes = (width + 7) / 8
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val byteIdx = y * rowBytes + x / 8
                        val bit = if (byteIdx < pLen) (packed[byteIdx].toInt() shr (7 - x % 8)) and 1 else 0
                        val white = if (photo == 0) bit == 0 else bit == 1
                        pixels[y * width + x] = if (white) -0x1 else -0x1000000
                    }
                }
            }
            bits == 8 && spp == 1 -> {
                for (i in pixels.indices) {
                    val v = if (i < pLen) packed[i].toInt() and 0xFF else 0
                    val g = if (photo == 0) 255 - v else v
                    pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                }
            }
            bits == 8 && spp >= 3 -> {
                for (i in pixels.indices) {
                    val base = i * spp
                    val r = if (base < pLen) packed[base].toInt() and 0xFF else 0
                    val g = if (base + 1 < pLen) packed[base + 1].toInt() and 0xFF else 0
                    val b = if (base + 2 < pLen) packed[base + 2].toInt() and 0xFF else 0
                    val a = if (spp >= 4 && base + 3 < pLen) packed[base + 3].toInt() and 0xFF else 255
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bits == 8 && spp == 1 && photo == 3 && colorMap != null -> {
                for (i in pixels.indices) {
                    val idx = if (i < pLen) packed[i].toInt() and 0xFF else 0
                    val r = (colorMap.getOrElse(idx) { 0 } shr 8) and 0xFF
                    val g = (colorMap.getOrElse(idx + 256) { 0 } shr 8) and 0xFF
                    val b = (colorMap.getOrElse(idx + 512) { 0 } shr 8) and 0xFF
                    pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            else -> return null
        }
        @Suppress("UNUSED_VARIABLE")
        val unused = rawLen
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
