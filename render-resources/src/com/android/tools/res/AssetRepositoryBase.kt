/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.res

import com.android.ide.common.rendering.api.AssetRepository
import java.io.ByteArrayInputStream
import java.io.InputStream

/** [AssetRepository] implementing logic common to Studio and out-of-Studio cases. */
class AssetRepositoryBase(private val assetFileOpener: AssetFileOpener) : AssetRepository() {
  override fun isSupported(): Boolean = true

  /** Checks if the given path points to a file resource. */
  override fun isFileResource(path: String): Boolean = isResourceFile(path)

  /**
   * It takes an absolute path that does not point to an asset and opens the file. Currently, the access
   * is restricted to files under the resources directories and the downloadable font cache directory.
   *
   * @param cookie ignored
   * @param url the path pointing to a file on disk, or to a ZIP file entry. In the latter case the path
   *     has the following format: "apk:<i>path_to_zip_file</i>!/<i>path_to_zip_entry</i>
   * @param mode ignored
   */
  override fun openNonAsset(cookie: Int, path: String, mode: Int): InputStream? {
    if (path.startsWith("apk:") || path.startsWith("jar:")) {
      return ByteArrayInputStream(FileResourceReader.readBytes(path))
    }

    return assetFileOpener.openNonAssetFile(path)
  }

  override fun openAsset(path: String, mode: Int): InputStream? = assetFileOpener.openAssetFile(path)
}