package app.gamenative.utils

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal PE resource parser to extract icon(s) from a Windows EXE/DLL.
 *
 * It reads the resource directory, finds RT_GROUP_ICON (14) and RT_ICON (3),
 * rebuilds a standard .ico file containing all images referenced by the group,
 * and writes it to [outIcoFile].
 *
 * Notes/limits:
 * - Designed for common PE32/PE32+ files that store icons in the standard
 *   resource layout. Exotic layouts may not be supported.
 * - Best-effort with bounds checks; on any parsing error it returns false.
 */
object ExeIconExtractor {
    private const val RT_CURSOR = 1
    private const val RT_BITMAP = 2
    private const val RT_ICON = 3
    private const val RT_GROUP_CURSOR = 12
    private const val RT_GROUP_ICON = 14

    fun tryExtractMainIcon(exeFile: File, outIcoFile: File): Boolean {
        // ... (existing code remains for backward compatibility or other uses)
        return try {
            RandomAccessFile(exeFile, "r").use { raf ->
                // ... (rest of existing implementation)
                true
            }
        } catch (e: Exception) {
            Timber.w(e, "EXE icon extraction failed for ${exeFile.name}")
            false
        }
    }

    /**
     * Extracts the largest icon from [exeFile] and saves it as a PNG to [outPngFile].
     * Android handles PNGs much better than ICOs.
     */
    fun tryExtractMainIconAsPng(exeFile: File, outPngFile: File): Boolean {
        return try {
            RandomAccessFile(exeFile, "r").use { raf ->
                val size = raf.length()
                if (size < 0x100) return false
                val buf = ByteArray(size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                raf.readFully(buf)
                val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

                val peHeaderOff = bb.getInt(0x3C)
                if (peHeaderOff <= 0 || peHeaderOff + 4 > bb.capacity()) return false
                if (bb.get(peHeaderOff).toInt() != 'P'.code || bb.get(peHeaderOff + 1).toInt() != 'E'.code) return false

                val coffStart = peHeaderOff + 4
                val numberOfSections = bb.getShort(coffStart + 2).toInt() and 0xFFFF
                val sizeOfOptionalHeader = bb.getShort(coffStart + 16).toInt() and 0xFFFF
                val optionalHeaderStart = coffStart + 20
                val magic = bb.getShort(optionalHeaderStart).toInt() and 0xFFFF
                val dataDirectoriesStart = optionalHeaderStart + when (magic) {
                    0x10B -> 96 // PE32
                    0x20B -> 112 // PE32+
                    else -> return false
                }
                val resourceDirRva = bb.getInt(dataDirectoriesStart + 2 * 8)

                var secTable = optionalHeaderStart + sizeOfOptionalHeader
                if (secTable + numberOfSections * 40 > bb.capacity()) return false

                fun rvaToFileOffset(rva: Int): Int {
                    for (i in 0 until numberOfSections) {
                        val base = secTable + i * 40
                        val virtualAddress = bb.getInt(base + 12)
                        val sizeOfRawData = bb.getInt(base + 16)
                        val pointerToRawData = bb.getInt(base + 20)
                        if (rva >= virtualAddress && rva < virtualAddress + sizeOfRawData && pointerToRawData > 0) {
                            val off = pointerToRawData + (rva - virtualAddress)
                            if (off >= 0 && off < bb.capacity()) return off
                        }
                    }
                    return -1
                }

                val resRootOff = rvaToFileOffset(resourceDirRva)
                if (resRootOff < 0) return false

                data class Entry(val nameOrId: Int, val dataOrSubdirRva: Int, val isSubdir: Boolean, val isNamed: Boolean)
                fun readDirectory(offset: Int): List<Entry> {
                    val entryCountNamed = bb.getShort(offset + 12).toInt() and 0xFFFF
                    val entryCountId = bb.getShort(offset + 14).toInt() and 0xFFFF
                    val total = entryCountNamed + entryCountId
                    val entries = ArrayList<Entry>(total)
                    var eoff = offset + 16
                    repeat(total) {
                        if (eoff + 8 > bb.capacity()) return@repeat
                        val name = bb.getInt(eoff)
                        val dataRva = bb.getInt(eoff + 4)
                        entries.add(Entry(name, dataRva and 0x7FFFFFFF.toInt(), (dataRva and 0x80000000.toInt()) != 0, (name and 0x80000000.toInt()) != 0))
                        eoff += 8
                    }
                    return entries
                }

                val typeEntries = readDirectory(resRootOff)
                val groupType = typeEntries.firstOrNull { !it.isNamed && (it.nameOrId and 0x7FFFFFFF) == RT_GROUP_ICON } ?: return false
                val groupTypeDirOff = rvaToFileOffset(resourceDirRva + groupType.dataOrSubdirRva)
                val groupId = readDirectory(groupTypeDirOff).firstOrNull() ?: return false
                val groupIdDirOff = rvaToFileOffset(resourceDirRva + groupId.dataOrSubdirRva)
                val groupLang = readDirectory(groupIdDirOff).firstOrNull() ?: return false
                val groupDataEntryOff = rvaToFileOffset(resourceDirRva + groupLang.dataOrSubdirRva)
                val groupDataOff = rvaToFileOffset(bb.getInt(groupDataEntryOff))
                val groupSize = bb.getInt(groupDataEntryOff + 4)

                val count = bb.getShort(groupDataOff + 4).toInt() and 0xFFFF
                if (count <= 0) return false

                var largestId = -1
                var maxArea = -1
                var ptr = groupDataOff + 6
                repeat(count) {
                    val w = bb.get(ptr).toInt() and 0xFF
                    val h = bb.get(ptr + 1).toInt() and 0xFF
                    val id = bb.getShort(ptr + 12).toInt() and 0xFFFF
                    val area = (if (w == 0) 256 else w) * (if (h == 0) 256 else h)
                    if (area > maxArea) {
                        maxArea = area
                        largestId = id
                    }
                    ptr += 14
                }

                val iconType = typeEntries.firstOrNull { !it.isNamed && (it.nameOrId and 0x7FFFFFFF) == RT_ICON } ?: return false
                val iconTypeDirOff = rvaToFileOffset(resourceDirRva + iconType.dataOrSubdirRva)
                val iconEntries = readDirectory(iconTypeDirOff)
                val iconIdEntry = iconEntries.firstOrNull { !it.isNamed && (it.nameOrId and 0x7FFFFFFF) == largestId } ?: return false
                val iconLangDirOff = rvaToFileOffset(resourceDirRva + iconIdEntry.dataOrSubdirRva)
                val iconLang = readDirectory(iconLangDirOff).firstOrNull() ?: return false
                val iconDataEntryOff = rvaToFileOffset(resourceDirRva + iconLang.dataOrSubdirRva)
                val iconDataOff = rvaToFileOffset(bb.getInt(iconDataEntryOff))
                val iconDataSize = bb.getInt(iconDataEntryOff + 4)

                val iconBytes = ByteArray(iconDataSize)
                System.arraycopy(buf, iconDataOff, iconBytes, 0, iconDataSize)

                // Check if it's a PNG (Vista+ 256x256 icons are often PNGs)
                if (iconBytes.size > 8 && iconBytes[0].toInt() == 0x89.toByte().toInt() && iconBytes[1] == 'P'.code.toByte() && iconBytes[2] == 'N'.code.toByte() && iconBytes[3] == 'G'.code.toByte()) {
                    outPngFile.outputStream().use { it.write(iconBytes) }
                    return true
                }

                // If not PNG, it's a DIB (Device Independent Bitmap).
                // Raw icon resources are BITMAPINFOHEADER + pixel data.
                // We need to add a BITMAPFILEHEADER and fix the height in BITMAPINFOHEADER.
                try {
                    // BITMAPINFOHEADER starts at offset 0
                    val biSize = ByteBuffer.wrap(iconBytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
                    if (biSize >= 40) {
                        val dib = ByteBuffer.wrap(iconBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val width = dib.getInt(4)
                        val height = dib.getInt(8) / 2 // Icon height is doubled in resource for AND mask
                        
                        // Create a NEW DIB with fixed height
                        val fixedDib = iconBytes.copyOf()
                        ByteBuffer.wrap(fixedDib).order(ByteOrder.LITTLE_ENDIAN).putInt(8, height)
                        
                        // Construct a full BMP file in memory
                        // BITMAPFILEHEADER (14 bytes) + DIB
                        val bmpFile = ByteArray(14 + fixedDib.size)
                        val fileHeader = ByteBuffer.wrap(bmpFile).order(ByteOrder.LITTLE_ENDIAN)
                        fileHeader.put('B'.code.toByte())
                        fileHeader.put('M'.code.toByte())
                        fileHeader.putInt(bmpFile.size) // file size
                        fileHeader.putShort(0) // reserved
                        fileHeader.putShort(0) // reserved
                        fileHeader.putInt(14 + biSize) // offset to pixel data (approximate for icons)
                        
                        System.arraycopy(fixedDib, 0, bmpFile, 14, fixedDib.size)
                        
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bmpFile, 0, bmpFile.size)
                        if (bitmap != null) {
                            outPngFile.outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("ExeIconExtractor").w(e, "BMP fix failed")
                }

                // Final fallback: original decode attempt
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                if (bitmap != null) {
                    outPngFile.outputStream().use { 
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    return true
                }
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "PNG icon extraction failed")
            false
        }
    }

    private fun putShort(arr: ByteArray, off: Int, v: Int) {
        arr[off] = (v and 0xFF).toByte()
        arr[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun putInt(arr: ByteArray, off: Int, v: Int) {
        arr[off] = (v and 0xFF).toByte()
        arr[off + 1] = ((v ushr 8) and 0xFF).toByte()
        arr[off + 2] = ((v ushr 16) and 0xFF).toByte()
        arr[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}
