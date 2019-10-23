/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.resources.aar

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.utils.HashCodes

/**
 * A ([AbstractAarResourceRepository], [FolderConfiguration]) pair. Instances of [AbstractAarResourceItem] contain
 * a reference to an `AarConfiguration` instead of two separate references to [AbstractAarResourceRepository] and
 * [FolderConfiguration]. This indirection saves memory because the number of `AarConfiguration` instances is tiny
 * fraction of the number of [AbstractAarResourceItem] instances.
 */
internal data class AarConfiguration(val repository: AbstractAarResourceRepository, val folderConfiguration: FolderConfiguration) {
  /**
   * Overridden to not distinguish between repositories loaded from the same file or folder.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AarConfiguration

    if (repository.origin != other.repository.origin) return false
    if (folderConfiguration != other.folderConfiguration) return false

    return true
  }

  /**
   * Overridden to not distinguish between repositories loaded from the same file or folder.
   */
  override fun hashCode(): Int {
    return HashCodes.mix(repository.origin.hashCode(), folderConfiguration.hashCode());
  }
}
