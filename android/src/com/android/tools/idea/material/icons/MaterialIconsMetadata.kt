/*
 * Copyright (C) 2019 The Android Open Source Project
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

/**
 * Metadata for the Material design icons, based on the metadata file obtained from http://fonts.google.com/metadata/icons.
 */
class MaterialIconsMetadata(
  val host: String,
  val urlPattern: String,
  val families: Array<String>,
  val icons: Array<MaterialMetadataIcon>
)

/**
 * Metadata of each icon within [MaterialIconsMetadata.icons].
 */
class MaterialMetadataIcon (
  val name: String,
  val version: Int,
  val unsupportedFamilies: Array<String>,
  val categories: Array<String>,
  val tags: Array<String>
)