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
package com.android.tools.idea.material.icons.metadata

import com.android.utils.HashCodes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.isDirectory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * Metadata for the Material design icons, based on the metadata file obtained from http://fonts.google.com/metadata/icons.
 */
data class MaterialIconsMetadata(
  val host: String,
  @SerializedName("asset_url_pattern")
  val urlPattern: String,
  val families: Array<String>,
  val icons: Array<MaterialMetadataIcon>
) {
  companion object {
    val EMPTY = MaterialIconsMetadata(
      host = "fonts.gstatic.com",
      urlPattern = "/s/i/{family}/{icon}/v{version}/{asset}",
      families = emptyArray(),
      icons = emptyArray()
    )

    /**
     * Parses the file resolved by the [url] into [MaterialIconsMetadata].
     *
     * Returns an empty instance if the parsing fails. See [EMPTY].
     */
    fun parse(url: URL, logger: Logger): MaterialIconsMetadata {
      var metadata: MaterialIconsMetadata = EMPTY

      try {
        metadata = BufferedReader(InputStreamReader(url.openStream(), Charsets.UTF_8)).use { reader ->
          getGson().fromJson(reader, MaterialIconsMetadata::class.java)
        }
      }
      catch (e: Exception) {
        when (e) {
          is IOException,
          is JsonIOException,
          is JsonSyntaxException -> logger.warn("Error reading url", e)
          else -> throw e
        }
      }
      return metadata
    }

    /**
     * Writes the metadata as a serialized Json text.
     */
    fun writeAsJson(metadata: MaterialIconsMetadata, target: Path, logger: Logger) {
      if (target.isDirectory()) {
        logger.error("Given path is a directory")
        return
      }
      try {
        Files.newBufferedWriter(target, Charsets.UTF_8).use { writer ->
          getGson().toJson(metadata, writer)
        }
      }
      catch (e: Exception) {
        when (e) {
          is IOException,
          is JsonIOException -> logger.warn("Error saving metadata file", e)
          else -> throw e
        }
      }
    }

    private fun getGson(): Gson = GsonBuilder()
      .registerTypeAdapter(MaterialIconsMetadata::class.java, MetadataDeserializer())
      .registerTypeAdapter(MaterialMetadataIcon::class.java, IconDeserializer())
      .generateNonExecutableJson()
      .create()
  }

  /**
   * Override equals to compare arrays by content.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MaterialIconsMetadata

    if (host != other.host) return false
    if (urlPattern != other.urlPattern) return false
    if (!families.contentEquals(other.families)) return false
    if (!icons.contentEquals(other.icons)) return false

    return true
  }

  /**
   * Override hashcode to compare arrays by content.
   */
  override fun hashCode(): Int {
    return HashCodes.mix(host.hashCode(), urlPattern.hashCode(), families.contentHashCode(), icons.contentHashCode())
  }
}

/**
 * Metadata of each icon within [MaterialIconsMetadata.icons].
 */
data class MaterialMetadataIcon(
  val name: String,
  val version: Int,
  @SerializedName("unsupported_families")
  val unsupportedFamilies: Array<String>,
  val categories: Array<String>,
  val tags: Array<String>
) {

  /**
   * Override equals to compare arrays by content.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MaterialMetadataIcon

    if (name != other.name) return false
    if (version != other.version) return false
    if (!unsupportedFamilies.contentEquals(other.unsupportedFamilies)) return false
    if (!categories.contentEquals(other.categories)) return false
    if (!tags.contentEquals(other.tags)) return false

    return true
  }

  /**
   * Override hashcode to compare arrays by content.
   */
  override fun hashCode(): Int {
    return HashCodes.mix(
      name.hashCode(), version, unsupportedFamilies.contentHashCode(), categories.contentHashCode(), tags.contentHashCode())
  }
}

/**
 * [JsonDeserializer] for [MaterialIconsMetadata].
 */
private class MetadataDeserializer : JsonDeserializer<MaterialIconsMetadata?> {
  private val hostKey = "host"
  private val urlPatternKey = "asset_url_pattern"
  private val familiesKey = "families"
  private val iconsKey = "icons"

  override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MaterialIconsMetadata? {
    if (json == null || typeOfT == null || context == null) return null
    val jsonObject = json.asJsonObject
    val host = jsonObject[hostKey].asString
    val urlPattern = jsonObject[urlPatternKey].asString
    val families = context.deserialize<Array<String>>(jsonObject[familiesKey], Array<String>::class.java)
    val icons = context.deserialize<Array<MaterialMetadataIcon>>(jsonObject[iconsKey], Array<MaterialMetadataIcon>::class.java)
    return MaterialIconsMetadata(host, urlPattern, families, icons)
  }
}

/**
 * [JsonDeserializer] for [MaterialMetadataIcon].
 */
private class IconDeserializer : JsonDeserializer<MaterialMetadataIcon?> {
  private val nameKey = "name"
  private val versionKey = "version"
  private val unsupportedFamiliesKey = "unsupported_families"
  private val categoriesKey = "categories"
  private val tagsKey = "tags"

  override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MaterialMetadataIcon? {
    if (json == null || typeOfT == null || context == null) return null
    val jsonObject = json.asJsonObject
    val name = jsonObject[nameKey].asString
    val version = jsonObject[versionKey].asInt
    val unsupportedFamilies = context.deserialize<Array<String>>(jsonObject[unsupportedFamiliesKey], Array<String>::class.java)
    val categories = context.deserialize<Array<String>>(jsonObject[categoriesKey], Array<String>::class.java)
    val tags = context.deserialize<Array<String>>(jsonObject[tagsKey], Array<String>::class.java)

    return MaterialMetadataIcon(name, version, unsupportedFamilies, categories, tags)
  }
}