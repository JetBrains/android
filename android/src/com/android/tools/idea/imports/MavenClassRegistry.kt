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

import com.android.tools.idea.imports.MavenClassRegistry.LibraryImportData
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.DependencyType.ANNOTATION_PROCESSOR
import com.android.tools.idea.projectsystem.DependencyType.DEBUG_IMPLEMENTATION
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.google.gson.stream.JsonReader
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.name.FqName

private val KOTLIN_ARTIFACT_PLATFORM_SUFFIXES =
  Regex("-(android|desktop|jvmstubs|linuxx64stubs)$", RegexOption.IGNORE_CASE)

/**
 * Registry contains [lookup] extracted by reading indices from [GMavenIndexRepository].
 *
 * Here, it covers all the latest stable versions of libraries which are explicitly marked as `Yes`
 * to include in go/studio-auto-import-packages.
 */
class MavenClassRegistry private constructor(@VisibleForTesting internal val lookup: LookupData) {

  /**
   * Given an unresolved name, returns the likely collection of [LibraryImportData] objects for the
   * maven.google.com artifacts containing a class or function matching the [name] and
   * [receiverType].
   *
   * @param name simple or fully-qualified name typed by the user. May correspond to a class name
   *   (any files) or a top-level Kotlin function name (Kotlin files only).
   * @param receiverType the fully-qualified name of the receiver type, if any, or `null` for no
   *   receiver.
   */
  fun findLibraryData(
    name: String,
    receiverType: String?,
    useAndroidX: Boolean,
    completionFileType: FileType?,
    module: Module,
  ): Collection<LibraryImportData> =
    findLibraryDataInternal(name, receiverType, false, useAndroidX, completionFileType, module)

  /**
   * Given an unresolved name, returns the likely collection of [LibraryImportData] objects for the
   * maven.google.com artifacts containing a class or function matching the [name].
   *
   * @param name simple or fully-qualified name typed by the user. May correspond to a class name
   *   (any files) or a top-level Kotlin function name, including extension functions (Kotlin files
   *   only).
   */
  fun findLibraryDataAnyReceiver(
    name: String,
    useAndroidX: Boolean,
    completionFileType: FileType?,
    module: Module,
  ): Collection<LibraryImportData> =
    findLibraryDataInternal(name, null, true, useAndroidX, completionFileType, module)

  private fun findLibraryDataInternal(
    name: String,
    receiverType: String?,
    anyReceiver: Boolean,
    useAndroidX: Boolean,
    completionFileType: FileType?,
    module: Module,
  ): Collection<LibraryImportData> {
    // We only support projects that set android.useAndroidX=true.
    if (!useAndroidX) return emptyList()

    val shortName = name.substringAfterLast('.', missingDelimiterValue = name)
    val packageName = name.substringBeforeLast('.', missingDelimiterValue = "")

    val shouldMap =
      module.project
        .getProjectSystem()
        .getTokenOrNull(AndroidMavenImportToken.EP_NAME)
        ?.shouldMapKmpArtifacts(module) ?: false

    val foundArtifacts =
      buildList {
          if (anyReceiver || receiverType == null)
            lookup.classNameMap[shortName]?.let { addAll(it) }
          // Only suggest top-level Kotlin functions when completing in a Kotlin file.
          if (completionFileType == KotlinFileType.INSTANCE) {
            if (anyReceiver) {
              lookup.topLevelFunctionsMapAllReceivers[shortName]?.let { addAll(it) }
            } else {
              val functionSpecifier = FunctionSpecifier(shortName, receiverType?.let(::FqName))
              lookup.topLevelFunctionsMap[functionSpecifier]?.let { addAll(it) }
            }
          }
        }
        .mapNotNull { data ->
          if (shouldMap && data.artifact in lookup.kmpArtifactMap) {
            // If the artifact is in the map, then we either replace it or drop it.
            val baseArtifact = lookup.kmpArtifactMap[data.artifact]
            baseArtifact?.let { data.copy(artifact = it) }
          } else {
            // For artifacts not in the map, just use the existing data.
            data
          }
        }
        .distinct()

    if (packageName.isEmpty()) return foundArtifacts

    return foundArtifacts.filter { it.importedItemPackageName == packageName }
  }

  /**
   * For the given runtime artifact, if Kotlin is the adopted language, the corresponding ktx
   * library is provided.
   */
  fun findKtxLibrary(artifact: String): String? {
    return lookup.ktxMap[artifact]
  }

  /** Returns a collection of [Coordinate]. */
  fun getCoordinates(): Collection<Coordinate> {
    return lookup.coordinateList
  }

  /**
   * Returns a value indicating whether a given [packageName] is included in any of the libraries
   * indexed by this registry.
   */
  fun isPackageIndexed(packageName: String): Boolean {
    return packageName in allIndexedPackages
  }

  private val allIndexedPackages by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      (lookup.classNameMap.values + lookup.topLevelFunctionsMap.values)
        .flatten()
        .mapNotNull { it.importedItemPackageName.takeIf(String::isNotEmpty) }
        .toSet()
    }

  /**
   * Given a simple class name (eg, not a fully-qualified name), returns a collection of
   * [LibraryImportData] objects that contains a class with that name.
   */
  fun findImportDataByClassName(simpleClassName: String) = lookup.classNameMap[simpleClassName]

  /**
   * Given a [FunctionSpecifier], returns a collection of [LibraryImportData] objects that contains
   * a matching top-level function.
   */
  fun findImportDataByFunctionSpecifier(functionSpecifier: FunctionSpecifier) =
    lookup.topLevelFunctionsMap[functionSpecifier]

  private enum class IndexKey(val key: String) {
    GROUP_ID("groupId"),
    ARTIFACT_ID("artifactId"),
    VERSION("version"),
    KTX_TARGETS("ktxTargets"),
    FQCNS("fqcns"),
    TOP_LEVEL_FUNCTIONS("ktlfns"),
  }

  companion object {
    @TestOnly
    fun createFrom(getIndexFileStream: () -> InputStream): MavenClassRegistry {
      val lookup = generateLookup(getIndexFileStream())
      return MavenClassRegistry(lookup)
    }

    /**
     * For the given artifact, if it also requires extra artifacts for proper functionality, provide
     * it.
     *
     * This is to handle those special cases. For example, for an unresolved symbol "@Preview",
     * "androidx.compose.ui:ui-tooling-preview" is one of the suggested artifacts to import based on
     * the extracted contents from the GMaven index file. However, this is not enough
     * -"androidx.compose.ui:ui-tooling" should be added on instead. So we just provide both in the
     * end.
     */
    fun findExtraArtifacts(artifact: String): Map<String, DependencyType> {
      return when (artifact) {
        "androidx.room:room-runtime",
        "android.arch.persistence.room:runtime" ->
          mapOf("android.arch.persistence.room:compiler" to ANNOTATION_PROCESSOR)
        "androidx.remotecallback:remotecallback" ->
          mapOf("androidx.remotecallback:remotecallback-processor" to ANNOTATION_PROCESSOR)
        "androidx.compose.ui:ui-tooling-preview" ->
          mapOf("androidx.compose.ui:ui-tooling" to DEBUG_IMPLEMENTATION)
        else -> emptyMap()
      }
    }

    private fun generateLookup(data: InputStream): LookupData {
      return try {
        data.use { readIndicesFromJsonFile(it) }
      } catch (e: Exception) {
        logger<MavenClassRegistry>().warn("Problem reading GMaven index file: ${e.message}")
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
      val classNames: MutableList<Pair<String, LibraryImportData>> = mutableListOf()
      val topLevelFunctions: MutableList<Pair<FunctionSpecifier, LibraryImportData>> =
        mutableListOf()
      val ktxMap: MutableMap<String, String> = mutableMapOf()
      val coordinateList: MutableList<Coordinate> = mutableListOf()
      val allIndexData: MutableList<GMavenArtifactIndex> = mutableListOf()

      reader.beginArray()
      while (reader.hasNext()) {
        val indexData = readGMavenIndex(reader)
        allIndexData.add(indexData)

        // Get class names and their associated libraries.
        classNames.addAll(indexData.getClassSimpleNamesWithLibraries())

        // Get top-level function names and their associated libraries.
        topLevelFunctions.addAll(indexData.getTopLevelFunctionSpecifiersWithLibraries())

        // Update "artifact to the associated KTX artifact" map.
        indexData.toKtxMapEntry()?.let { ktxMap[it.targetLibrary] = it.ktxLibrary }

        // Update maven artifact coordinate list.
        coordinateList.add(Coordinate(indexData.groupId, indexData.artifactId, indexData.version))
      }
      reader.endArray()

      val classNameMap = classNames.groupBy({ it.first }, { it.second })
      val topLevelFunctionsMap = topLevelFunctions.groupBy({ it.first }, { it.second })

      val kmpArtifactMap = allIndexData.toKmpArtifactMap()
      return LookupData(classNameMap, topLevelFunctionsMap, ktxMap, kmpArtifactMap, coordinateList)
    }

    private fun List<GMavenArtifactIndex>.toKmpArtifactMap(): Map<String, String?> {
      val artifactToVersionMap = this.associate { "${it.groupId}:${it.artifactId}" to it.version }

      val candidateArtifacts =
        artifactToVersionMap.keys.filter { KOTLIN_ARTIFACT_PLATFORM_SUFFIXES.containsMatchIn(it) }

      return buildMap {
        for (candidateArtifact in candidateArtifacts) {
          val candidateVersion = requireNotNull(artifactToVersionMap[candidateArtifact])
          val baseArtifact = candidateArtifact.substringBeforeLast('-')

          // Only do a replacement if the base artifact exists in some form.
          if (baseArtifact in artifactToVersionMap) {
            if (candidateVersion == artifactToVersionMap[baseArtifact]) {
              // When the versions match, we want to do a replacement.
              put(candidateArtifact, baseArtifact)
            } else {
              // When the versions don't match, we want to drop the platform-specific
              // recommendation.
              put(candidateArtifact, null)
            }
          }
        }
      }
    }

    @Throws(IOException::class)
    private fun readGMavenIndex(reader: JsonReader): GMavenArtifactIndex {
      reader.beginObject()
      var groupId: String? = null
      var artifactId: String? = null
      var version: String? = null
      var ktxTargets: Collection<String>? = null
      var fqcns: Collection<FqName>? = null
      // Top-level functions aren't in the index when empty in order to save bytes. Missing is not
      // consider malformed, so allow empty list.
      var topLevelFunctions: Collection<KotlinTopLevelFunction> = emptyList()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          IndexKey.GROUP_ID.key -> {
            groupId = reader.nextString()
          }
          IndexKey.ARTIFACT_ID.key -> {
            artifactId = reader.nextString()
          }
          IndexKey.VERSION.key -> {
            version = reader.nextString()
          }
          IndexKey.KTX_TARGETS.key -> {
            ktxTargets = readKtxTargets(reader)
          }
          IndexKey.FQCNS.key -> {
            fqcns = readFqcns(reader)
          }
          IndexKey.TOP_LEVEL_FUNCTIONS.key -> {
            topLevelFunctions = readTopLevelFunctions(reader)
          }
          else -> {
            reader.skipValue()
          }
        }
      }

      val gMavenIndex =
        GMavenArtifactIndex(
          groupId = groupId ?: throw MalformedIndexException("Group ID is missing($reader)."),
          artifactId =
            artifactId ?: throw MalformedIndexException("Artifact ID is missing($reader)."),
          version = version ?: throw MalformedIndexException("Version is missing($reader)."),
          ktxTargets =
            ktxTargets ?: throw MalformedIndexException("Ktx targets are missing($reader)."),
          fqcns =
            fqcns
              ?: throw MalformedIndexException("Fully qualified class names are missing($reader)."),
          topLevelFunctions = topLevelFunctions,
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
    private fun readTopLevelFunctions(reader: JsonReader): Collection<KotlinTopLevelFunction> {
      return buildList {
        reader.beginArray()
        while (reader.hasNext()) {
          reader.beginObject()
          var fqName: String? = null
          var xFqName: String? = null
          var receiverFqName: String? = null
          while (reader.hasNext()) {
            when (reader.nextName()) {
              "fqn" -> fqName = reader.nextString()
              "xfqn" -> xFqName = reader.nextString()
              "rcvr" -> receiverFqName = reader.nextString()
              else -> reader.skipValue()
            }
          }
          reader.endObject()

          when {
            fqName != null -> add(KotlinTopLevelFunction.fromJvmQualifiedName(fqName))
            xFqName != null && receiverFqName != null ->
              add(KotlinTopLevelFunction.fromJvmQualifiedName(xFqName, receiverFqName))
          }
        }
        reader.endArray()
      }
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
  }

  /**
   * Library data for importing a specific item (class or function) with its GMaven artifact.
   *
   * @property artifact maven coordinate: groupId:artifactId, please note version is not included
   *   here.
   * @property importedItemFqName fully-qualified name of the item to import. Can be a class or
   *   function name.
   * @property importedItemPackageName package name of the item to import.
   * @property version the version of the [artifact].
   */
  data class LibraryImportData(
    val artifact: String,
    val importedItemFqName: String,
    val importedItemPackageName: String,
    val version: String? = null,
  )

  /** Coordinate for Google Maven artifact. */
  data class Coordinate(val groupId: String, val artifactId: String, val version: String)

  /** Lookup data extracted from an index file. */
  data class LookupData(
    /** A map from simple class names to the corresponding [LibraryImportData] objects. */
    val classNameMap: Map<String, List<LibraryImportData>>,
    /** A map from function specifiers to the corresponding [LibraryImportData] objects. */
    val topLevelFunctionsMap: Map<FunctionSpecifier, List<LibraryImportData>>,
    /** A map from non-KTX libraries to the associated KTX libraries. */
    val ktxMap: Map<String, String>,
    /**
     * A map from platform-specific artifacts to general artifacts, eg
     * "androidx.compose.ui:ui-android" to "androidx.compose.ui:ui". If the value is null, then the
     * artifact shouldn't be used for KMP purposes.
     */
    val kmpArtifactMap: Map<String, String?>,
    /** A list of Google Maven [Coordinate]. */
    val coordinateList: List<Coordinate>,
  ) {
    /**
     * A map from simple names (irrespective of receiver) to corresponding [LibraryImportData]
     * objects.
     */
    val topLevelFunctionsMapAllReceivers: Map<String, List<LibraryImportData>> =
      topLevelFunctionsMap.entries.groupBy({ it.key.simpleName }, { it.value }).mapValues {
        it.value.flatten().distinct()
      }

    companion object {
      @JvmStatic val EMPTY = LookupData(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList())
    }
  }
}

/** An index of a specific [version] of GMaven Artifact. */
private data class GMavenArtifactIndex(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val ktxTargets: Collection<String>,
  val fqcns: Collection<FqName>,
  val topLevelFunctions: Collection<KotlinTopLevelFunction>,
) {

  /** Gets a list of simple class names and their corresponding [LibraryImportData]s. */
  fun getClassSimpleNamesWithLibraries(): List<Pair<String, LibraryImportData>> {
    return fqcns.map { fqName ->
      fqName.shortName().asString() to
        LibraryImportData(
          artifact = "$groupId:$artifactId",
          importedItemFqName = fqName.asString(),
          importedItemPackageName = fqName.parent().asString(),
          version = version,
        )
    }
  }

  /**
   * Gets a list of top-level function simple names and their corresponding [LibraryImportData]s.
   */
  fun getTopLevelFunctionSpecifiersWithLibraries():
    List<Pair<FunctionSpecifier, LibraryImportData>> {
    return topLevelFunctions.map { topLevelFunction ->
      topLevelFunction.toSpecifier() to
        LibraryImportData(
          artifact = "$groupId:$artifactId",
          importedItemFqName = topLevelFunction.kotlinFqName.asString(),
          importedItemPackageName = topLevelFunction.packageName,
          version = version,
        )
    }
  }

  /**
   * Converts to a map entry from the KTX library to its decorated library.
   *
   * E.g. "androidx.activity:activity-ktx -> androidx.activity:activity".
   */
  fun toKtxMapEntry(): KtxMapEntry? {
    if (ktxTargets.isEmpty()) return null

    // It's implicit that there's up to one target artifact that's associated to the given KTX
    // artifact.
    return KtxMapEntry(ktxLibrary = "$groupId:$artifactId", targetLibrary = ktxTargets.first())
  }
}

/** An entry of a map from the KTX library to its decorated library. */
private data class KtxMapEntry(val ktxLibrary: String, val targetLibrary: String)

/** A top-level Kotlin function. */
@TestOnly
internal data class KotlinTopLevelFunction(
  /** Unqualified function name. */
  val simpleName: String,
  /** Package name of the function. */
  val packageName: String,
  /**
   * Fully-qualified name of the function in Kotlin. This does not contain the synthetic class (e.g.
   * "FileFacadeKt") that contains the function in the JVM. That makes this name appropriate to use
   * when calling from Kotlin, but not from Java.
   */
  val kotlinFqName: FqName,
  /** Fully-qualified name of the function's receiver in Kotlin. */
  val receiverFqName: FqName?,
) {

  fun toSpecifier() = FunctionSpecifier(simpleName, receiverFqName)

  companion object {
    fun fromJvmQualifiedName(
      fqName: String,
      receiverFqName: String? = null,
    ): KotlinTopLevelFunction {
      require(fqName.contains('.')) {
        "fqName does not have file facade class containing the function: '$fqName'"
      }

      val functionSimpleName = fqName.substringAfterLast('.')
      val classFullName = fqName.substringBeforeLast('.')
      val packageName = classFullName.substringBeforeLast('.', "")
      val packagePrefix = if (packageName.isEmpty()) "" else "$packageName."

      return KotlinTopLevelFunction(
        simpleName = functionSimpleName,
        packageName = packageName,
        kotlinFqName = FqName("$packagePrefix$functionSimpleName"),
        receiverFqName = receiverFqName?.let(::FqName),
      )
    }
  }
}

data class FunctionSpecifier(val simpleName: String, val receiverFqName: FqName?)

/** Exception thrown when parsing malformed GMaven index file. */
private class MalformedIndexException(message: String) : RuntimeException(message)
