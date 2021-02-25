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
package com.android.tools.idea.imports

import com.google.gson.stream.JsonReader
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.kotlin.name.FqName
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Lookup from class names to maven.google.com artifacts by reading indices from [GMavenIndexRepository].
 *
 * Here, it covers all the latest stable versions of libraries which are explicitly marked as `Yes` to include in
 * go/studio-auto-import-packages.
 */
// TODO: rename to MavenClassRegistry.
class MavenClassRegistryFromRepository(private val indexRepository: GMavenIndexRepository) : MavenClassRegistryBase() {
  val lookup: Map<String, List<Library>> = generateLookup()

  /**
   * Given a class name, returns the likely collection of [MavenClassRegistryBase.Library] objects for the maven.google.com
   * artifacts containing that class.
   *
   * Here, the passed in [className] can be either short class name or fully qualified class name.
   *
   * This implementation only returns results of index data from [GMavenIndexRepository].
   */
  override fun findLibraryData(className: String, useAndroidX: Boolean): Collection<Library> {
    // We only support projects that set android.useAndroidX=true.
    if (!useAndroidX) return emptyList()

    val index = className.lastIndexOf('.')
    val shortName = className.substring(index + 1)
    val packageName = if (index == -1) "" else className.substring(0, index)

    val foundArtifacts = lookup[shortName] ?: return emptyList()

    if (packageName.isEmpty()) return foundArtifacts

    return foundArtifacts.filter { it.packageName == packageName }
  }

  private fun generateLookup(): Map<String, List<Library>> {
    val data = indexRepository.loadIndexFromDisk()

    return try {
      data.use { readIndicesFromJsonFile(it) }
    }
    catch (e: Exception) {
      logger<MavenClassRegistryFromRepository>().warn("Problem reading GMaven index file: ${e.message}")
      emptyMap()
    }
  }

  @Throws(IOException::class)
  private fun readIndicesFromJsonFile(inputStream: InputStream): Map<String, List<Library>> {
    return JsonReader(InputStreamReader(inputStream)).use {
      it.beginObject()
      if (it.nextName() != "Index") throw MalformedIndexException("\"Index\" key is missing($it).")

      val map = readIndexArray(it)
      it.endObject()
      map
    }
  }

  @Throws(IOException::class)
  private fun readIndexArray(reader: JsonReader): Map<String, List<Library>> {
    val map = mutableMapOf<String, List<Library>>()

    reader.beginArray()
    while (reader.hasNext()) {
      readGMavenIndex(reader).toMavenClassRegistryMap()
        .asSequence()
        .fold(map) { acc, entry ->
          // Merge the content of this entry into the accumulated map. If the class name(key of this entry) exists in
          // this accumulated map, corresponding library(value of this entry) is appended to the existing list in this
          // accumulated map. Else a new entry is added into this accumulated map.
          acc.merge(entry.key, listOf(entry.value)) { oldValue, value ->
            oldValue + value
          }
          acc
        }
    }
    reader.endArray()
    return map
  }

  @Throws(IOException::class)
  private fun readGMavenIndex(reader: JsonReader): GMavenArtifactIndex {
    reader.beginObject()
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var fqcns: Collection<FqName>? = null
    while (reader.hasNext()) {
      when (reader.nextName()) {
        INDEX_KEY.GROUP_ID.key -> {
          groupId = reader.nextString()
        }
        INDEX_KEY.ARTIFACT_ID.key -> {
          artifactId = reader.nextString()
        }
        INDEX_KEY.VERSION.key -> {
          version = reader.nextString()
        }
        INDEX_KEY.FQCNS.key -> {
          fqcns = readFqcns(reader)
        }
        else -> {
          reader.skipValue()
        }
      }
    }

    val gMavenIndex = GMavenArtifactIndex(
      groupId = groupId ?: throw MalformedIndexException("Group ID is missing($reader)."),
      artifactId = artifactId ?: throw MalformedIndexException("Artifact ID is missing($reader)."),
      version = version ?: throw MalformedIndexException("Version is missing($reader)."),
      fqcns = fqcns ?: throw MalformedIndexException("Fully qualified class names are missing($reader).")
    )
    reader.endObject()
    return gMavenIndex
  }

  @Throws(IOException::class)
  private fun readFqcns(reader: JsonReader): Collection<FqName> {
    val fqcns = mutableListOf<FqName>()
    reader.beginArray()
    while (reader.hasNext()) {
      fqcns.add(FqName(reader.nextString()))
    }
    reader.endArray()
    return fqcns
  }

  enum class INDEX_KEY(val key: String) {
    GROUP_ID("groupId"),
    ARTIFACT_ID("artifactId"),
    VERSION("version"),
    FQCNS("fqcns")
  }
}

/**
 * An index of a specific [version] of GMaven Artifact.
 */
data class GMavenArtifactIndex(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val fqcns: Collection<FqName>
) {
  /**
   * Converts to a map from class names to corresponding [MavenClassRegistryBase.Library]s.
   */
  fun toMavenClassRegistryMap(): Map<String, MavenClassRegistryBase.Library> {
    return fqcns.asSequence()
      .map { fqName ->
        val library = MavenClassRegistryBase.Library(
          artifact = "$groupId:$artifactId",
          packageName = fqName.parent().asString(),
        )
        fqName.shortName().asString() to library
      }
      .toMap()
  }
}

/**
 * Exception thrown when parsing malformed GMaven index file.
 */
private class MalformedIndexException(message: String) : RuntimeException(message)