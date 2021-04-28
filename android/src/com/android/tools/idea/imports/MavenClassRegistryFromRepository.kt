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
  val lookup: LookupData = generateLookup()

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

    val foundArtifacts = lookup.fqcnMap[shortName] ?: return emptyList()

    if (packageName.isEmpty()) return foundArtifacts

    return foundArtifacts.filter { it.packageName == packageName }
  }

  override fun findKtxLibrary(artifact: String): String? {
    return lookup.ktxMap[artifact]
  }

  private fun generateLookup(): LookupData {
    val data = indexRepository.loadIndexFromDisk()

    return try {
      data.use { readIndicesFromJsonFile(it) }
    }
    catch (e: Exception) {
      logger<MavenClassRegistryFromRepository>().warn("Problem reading GMaven index file: ${e.message}")
      LookupData.EMPTY
    }
  }

  @Throws(IOException::class)
  private fun readIndicesFromJsonFile(inputStream: InputStream): LookupData {
    return JsonReader(InputStreamReader(inputStream)).use { reader ->
      var map: LookupData? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "Index" -> map = readIndexArray(reader)
          else -> reader.skipValue()
        }
      }

      reader.endObject()
      map ?: LookupData.EMPTY
    }
  }

  @Throws(IOException::class)
  private fun readIndexArray(reader: JsonReader): LookupData {
    val fqcnMap = mutableMapOf<String, List<Library>>()
    val ktxMap = mutableMapOf<String, String>()

    reader.beginArray()
    while (reader.hasNext()) {
      val indexData = readGMavenIndex(reader)

      // Update "fqcn to artifacts" map.
      indexData
        .toMavenClassRegistryMap()
        .asSequence()
        .fold(fqcnMap) { acc, entry ->
          // Merge the content of this entry into the accumulated map. If the class name(key of this entry) exists in
          // this accumulated map, corresponding library(value of this entry) is appended to the existing list in this
          // accumulated map. Else a new entry is added into this accumulated map.
          acc.merge(entry.key, listOf(entry.value)) { oldValue, value ->
            oldValue + value
          }
          acc
        }

      // Update "artifact to the associated KTX artifact" map.
      val entry = indexData.toKtxMapEntry() ?: continue
      ktxMap[entry.targetLibrary] = entry.ktxLibrary

    }
    reader.endArray()
    return LookupData(fqcnMap, ktxMap)
  }

  @Throws(IOException::class)
  private fun readGMavenIndex(reader: JsonReader): GMavenArtifactIndex {
    reader.beginObject()
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var ktxTargets: Collection<String>? = null
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
        INDEX_KEY.KTX_TARGETS.key -> {
          ktxTargets = readKtxTargets(reader)
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
      ktxTargets = ktxTargets ?: throw MalformedIndexException("Ktx targets are missing($reader)."),
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

  @Throws(IOException::class)
  private fun readKtxTargets(reader: JsonReader): Collection<String> {
    val decoratedLibraries = mutableListOf<String>()
    reader.beginArray()
    while (reader.hasNext()) {
      decoratedLibraries.add(reader.nextString())
    }
    reader.endArray()
    return decoratedLibraries
  }

  enum class INDEX_KEY(val key: String) {
    GROUP_ID("groupId"),
    ARTIFACT_ID("artifactId"),
    VERSION("version"),
    KTX_TARGETS("ktxTargets"),
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
  val ktxTargets: Collection<String>,
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
          version = version
        )
        fqName.shortName().asString() to library
      }
      .toMap()
  }

  /**
   * Converts to a map entry from the KTX library to its decorated library.
   *
   * E.g. "androidx.activity:activity-ktx -> androidx.activity:activity".
   */
  fun toKtxMapEntry(): KtxMapEntry? {
    if (ktxTargets.isEmpty()) return null

    // It's implicit that there's up to one target artifact that's associated to the given KTX artifact.
    return KtxMapEntry(
      ktxLibrary = "$groupId:$artifactId",
      targetLibrary = ktxTargets.first()
    )
  }
}

/**
 * An entry of a map from the KTX library to its decorated library.
 */
data class KtxMapEntry(val ktxLibrary: String, val targetLibrary: String)

/**
 * Lookup data extracted from a index file.
 */
data class LookupData(
  /**
   * A map from fully qualified class names to the corresponding [MavenClassRegistryBase.Library]s
   */
  val fqcnMap: Map<String, List<MavenClassRegistryBase.Library>>,
  /**
   * A map from non-KTX libraries to the associated KTX libraries.
   */
  val ktxMap: Map<String, String>
) {
  companion object {
    @JvmStatic
    val EMPTY = LookupData(emptyMap(), emptyMap())
  }
}

/**
 * Exception thrown when parsing malformed GMaven index file.
 */
private class MalformedIndexException(message: String) : RuntimeException(message)