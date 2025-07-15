/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.importer.wfs.honeyface

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.lang.reflect.Type
import java.net.URI
import java.nio.file.Path
import java.util.Objects.nonNull

private val LOG = Logger.getInstance(HoneyFaceParser::class.java)

/**
 * Parses `honeyface.json` files using [Gson]. These files are extracted from `.wfs` (
 * [WatchFaceStudio](https://developer.samsung.com/watch-face-studio/overview.html)) files.
 */
internal class HoneyFaceParser(
  private val gson: Gson =
    GsonBuilder()
      .registerTypeAdapter(
        Path::class.java,
        object : JsonDeserializer<Path> {
          override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
          ) = Path.of(URI.create(json.asString).path)
        },
      )
      .setPrettyPrinting()
      .create()
) {

  fun parse(file: File): HoneyFace? =
    try {
      gson.fromJson(file.readText(), HoneyFace::class.java)?.takeIf { it.isValid() }
    } catch (e: Exception) {
      LOG.warn("Failed to parse honeyface.json file", e)
      null
    }

  private fun HoneyFace.isValid(): Boolean {
    // When parsed from GSON, these fields can be null if not present, despite being marked as
    // non-null in the constructor
    return nonNull(settings) &&
      nonNull(background) &&
      nonNull(scene) &&
      nonNull(stringResource) &&
      nonNull(themeColors) &&
      nonNull(buildData) &&
      nonNull(styleGroup)
  }
}
