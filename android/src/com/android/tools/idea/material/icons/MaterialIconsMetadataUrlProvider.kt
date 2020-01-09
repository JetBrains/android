/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.material.icons

import com.android.tools.idea.material.icons.MaterialIconsUtils.MATERIAL_ICONS_PATH
import com.android.tools.idea.material.icons.MaterialIconsUtils.METADATA_FILE_NAME
import java.net.URL

/**
 * Provides a [URL] that is used to get the metadata file and then parse it.
 */
interface MaterialIconsMetadataUrlProvider {
  fun getMetadataUrl(): URL?
}

/**
 * The default implementation of [MaterialIconsMetadataUrlProvider], returns the [URL] for the bundled metadata file in Android Studio.
 */
internal class BundledMetadataUrlProvider : MaterialIconsMetadataUrlProvider {
  override fun getMetadataUrl(): URL? =
    javaClass.classLoader.getResource(MATERIAL_ICONS_PATH + METADATA_FILE_NAME)
}