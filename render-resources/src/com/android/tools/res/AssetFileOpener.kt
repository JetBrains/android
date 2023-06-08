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

import java.io.InputStream

/**
 * Abstract out the specific logic of receiving the [InputStream] for the resource file located at path. In some case (e.g. inside Studio)
 * it might not be as simple as reading the file from disk since file on disk might be out-of-date and the updated file version is kept in
 * e.g. memory.
 */
interface AssetFileOpener {
  fun openAssetFile(path: String): InputStream?

  fun openNonAssetFile(path: String): InputStream?
}