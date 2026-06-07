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

package com.cleansweep.domain.deletepool

object DeletePoolStatus {
    const val IN_POOL = "IN_POOL"
    const val DELETING = "DELETING"
    const val DELETED = "DELETED"
    const val FAILED = "FAILED"
    const val NEEDS_PERMISSION = "NEEDS_PERMISSION"
}

object DeletePoolLocatorType {
    const val FILE_PATH = "FILE_PATH"
    const val MEDIASTORE_URI = "MEDIASTORE_URI"
    const val SAF_URI = "SAF_URI"
}

object DeletePoolResultCode {
    const val DELETED = "DELETED"
    const val ALREADY_GONE = "ALREADY_GONE"
    const val FILE_CHANGED = "FILE_CHANGED"
    const val PERMISSION_REQUIRED = "PERMISSION_REQUIRED"
    const val FAILED = "FAILED"
}
