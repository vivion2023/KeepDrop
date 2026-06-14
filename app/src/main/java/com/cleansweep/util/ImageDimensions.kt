/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.cleansweep.util

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageDimensions {

    fun readFromFile(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var width = options.outWidth
        var height = options.outHeight
        if (width <= 0 || height <= 0) return 0 to 0

        try {
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                width = options.outHeight
                height = options.outWidth
            }
        } catch (_: Exception) {
            // Fall back to raw bounds when EXIF is unavailable or corrupt.
        }

        return width to height
    }
}