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
@file:JvmName("AndroidModelAdapterUtil")
package com.android.tools.idea.model

import com.android.builder.model.AaptOptions
import com.android.builder.model.SourceProvider
import com.android.ide.common.gradle.model.toAaptOptionsNamespacing
import com.android.ide.common.gradle.model.toSourceProvider
import com.android.ide.common.util.PathMap
import com.android.ide.common.util.PathString
import com.android.projectmodel.*
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import toPathTreeMap
import java.io.File

/**
 * Function that tests whether the class specified by fqcn (Fully Qualified Class Name) is out of
 * date and needs to be rebuilt.
 */
typealias OutOfDateTester = (module: Module, fqcn: String, classFile: VirtualFile) -> Boolean

/**
 * Converts a selection of variants from an [com.android.projectmodel.AndroidModel] into an [AndroidModel].
 * Note that the [AndroidModel] interface was only intended to represent a single [AndroidProject],
 * but the [com.android.projectmodel.AndroidModel] type may contain multiple [AndroidProject] instances.
 *
 * In the event that there is exactly one [Variant] selected from the [AndroidProject], the conversion
 * will be exact. If there is more than one selected [Variant], this adapter will use heuristics to
 * make the multiple projects appear to be a single model (it may aggregate the projects,
 * use data from the first project, etc.) but in such situations the conversion will be imperfect.
 *
 * The fix for such imperfect conversions is to remove the code that assumes a single [AndroidProject]
 * per [Module] and obtain information directly from the [AndroidProject] rather than from this adapter.
 * Such fixes can't be made in the adapter itself due to the contradictory API contracts.
 *
 * Any number of variants can be selected simultaneously. For gradle projects, the normal case is for a
 * single variant to be selected at a time and for the input model to contain exactly one project. For
 * Blaze projects, the normal case is for the model to contain multiple projects and for the selection
 * to contain all of them. To simplify both use cases, this class has two convenience
 * constructors, one which selects everything and the other selects a specific variant.
 *
 * This class is invariant. In order to change the model or selected variant, use the Kotlin [copy]
 * method to make a copy with new attributes.
 *
 * This class was implemented as part of a plan to eventually delete the [AndroidModel] interface.
 * For this reason, the main business logic has been written in out-of-class static utility methods that
 * accept model types, and the overloads just redirect to those utility methods. This is intended to
 * make future deletion of those methods easy via inline refactoring. When adding logic to this class,
 * please consider putting all additions and bugfixes in the utility methods rather than the overloads.
 */
data class AndroidModelAdapter(
  val input: AndroidModelSubset,
  private val rootDir: File,
  private val classJars: ClassJarProvider,
  private val outOfDateTester: OutOfDateTester
) : AndroidModel {
  /**
   * Constructs an [AndroidModelAdapter] with everything selected.
   */
  constructor (input: com.android.projectmodel.AndroidModel, rootDir: File, classJars: ClassJarProvider, outOfDateTester: OutOfDateTester) : this(
    selectAllVariants(input), rootDir, classJars, outOfDateTester)

  /**
   * Constructs an [AndroidModelAdapter] with a single named variant selected.
   */
  constructor (input: com.android.projectmodel.AndroidModel, selectedVariant:String, rootDir: File, classJars: ClassJarProvider, outOfDateTester: OutOfDateTester) : this(
    selectVariant(input, selectedVariant), rootDir, classJars, outOfDateTester)

  /**
   * [PathMap] of all generated paths. This is basically used as a set: the keys and values are equal.
   */
  private val generatedPaths = input.model.generatedPaths()

  /**
   * Return a source provider containing the sources from all [AndroidProject] instances in the model
   * that apply to all variants.
   */
  @Deprecated("")
  override fun getDefaultSourceProvider(): SourceProvider
    = defaultConfigsFor(input.model).mergedSourceSet().toSourceProvider(matchAllArtifacts().simpleName)

  @Deprecated("")
  override fun getActiveSourceProviders(): MutableList<SourceProvider>
    = input.selectedConfigs(ARTIFACT_NAME_MAIN).toSourceProviders()

  @Deprecated("")
  override fun getTestSourceProviders(): MutableList<SourceProvider>
    = input.selectedConfigs(ARTIFACT_NAME_UNIT_TEST, ARTIFACT_NAME_ANDROID_TEST).toSourceProviders()

  @Deprecated("")
  override fun getAllSourceProviders(): MutableList<SourceProvider>
    = input.allConfigs(ARTIFACT_NAME_MAIN).toSourceProviders()

  override fun getApplicationId(): String = guessApplicationIdFor(input)

  override fun getAllApplicationIds(): MutableSet<String> = getAllApplicationIds(input.model).toMutableSet()

  override fun overridesManifestPackage(): Boolean
    = input.selectedConfigs(ARTIFACT_NAME_MAIN).any { it.config.manifestValues.applicationId != null }

  override fun isDebuggable(): Boolean
    = input.selectedArtifacts().any { it.artifact.resolved.manifestValues.debuggable == true }

  override fun getMinSdkVersion(): AndroidVersion? = getMinSdkVersion(input)

  override fun getRuntimeMinSdkVersion(): AndroidVersion?
    = input.firstMainArtifact()?.artifact?.resolved?.manifestValues?.compileSdkVersion

  override fun getTargetSdkVersion(): AndroidVersion?
    = input.firstMainArtifact()?.artifact?.resolved?.manifestValues?.targetSdkVersion

  override fun getVersionCode(): Int?
    = input.firstMainArtifact()?.artifact?.resolved?.manifestValues?.versionCode

  override fun getRootDirPath(): File = rootDir

  @Deprecated("")
  override fun getRootDir(): VirtualFile {
    val rootDirPath = rootDirPath
    return LocalFileSystem.getInstance().findFileByIoFile(rootDirPath)!!
  }

  override fun isGenerated(file: VirtualFile): Boolean
    = generatedPaths.containsPrefixOf(file.toPathString())

  override fun getResValues(): MutableMap<String, DynamicResourceValue>
    = (input.firstMainArtifact()?.artifact?.resolved?.resValues ?: emptyMap()).toMutableMap()

  override fun getDataBindingMode(): DataBindingMode = dataBindingMode(input)

  override fun getNamespacing(): AaptOptions.Namespacing
    = input.selectedVariants().firstOrNull()?.project?.namespacing?.toAaptOptionsNamespacing() ?: AaptOptions.Namespacing.DISABLED

  override fun getClassJarProvider(): ClassJarProvider = classJars

  override fun isClassFileOutOfDate(module: Module, fqcn: String, classFile: VirtualFile): Boolean
    = outOfDateTester(module, fqcn, classFile)

  /**
   * Converts a list of [ConfigAssociation] to a list of [SourceProvider]. Omits [Config] instances that have no sources.
   */
  private fun Iterable<ConfigAssociation>.toSourceProviders(): MutableList<SourceProvider>
    = filterNot { it.config.sources.isEmpty() }.map { it.toSourceProvider() }.toMutableList()
}

val LIB_ANDROIDX_DATA_BINDING = GoogleMavenArtifactId.ANDROIDX_DATA_BINDING_LIB.getCoordinate("+")
val LIB_DATA_BINDING = GoogleMavenArtifactId.DATA_BINDING_LIB.getCoordinate("+")

/**
 * Guesses the [DataBindingMode] for the selected variants within the given model, based on the project's dependencies.
 */
fun dataBindingMode(model: AndroidModelSubset): DataBindingMode {
  return when {
    model.dependsOn(LIB_ANDROIDX_DATA_BINDING) -> DataBindingMode.ANDROIDX
    model.dependsOn(LIB_DATA_BINDING) -> DataBindingMode.SUPPORT
    else -> DataBindingMode.NONE
  }
}

/**
 * Returns all generated paths for the given model. The keys and values are equal in the resulting map.
 */
fun com.android.projectmodel.AndroidModel.generatedPaths(): PathMap<PathString>
  = this.projects.flatMap { it.generatedPaths }.associateBy { it }.toPathTreeMap()

/**
 * Returns the min SDK version for the selected variants within the given model.
 */
fun getMinSdkVersion(input: AndroidModelSubset) : AndroidVersion? {
  return input.firstMainArtifact()?.let { artifactContext ->
    artifactContext.artifact.resolved.manifestValues.compileSdkVersion?.let { minSdkVersion ->
      // If this version has a codename, try to find the most specific override without a codename
      if (minSdkVersion.codename != null) {
        artifactContext.project.configTable
          .configsIntersecting(artifactContext.variant.configPath)
          .reversed().mapNotNull {
            it.manifestValues.compileSdkVersion
          }.firstOrNull { it.codename != null }
      }
      else minSdkVersion
    }
  }
}

fun getAllApplicationIds(model: com.android.projectmodel.AndroidModel): Set<String>
  = model.projects.flatMap { it.variants }.flatMap { it.artifacts.mapNotNull {it.resolved.manifestValues.applicationId } }.toSet()

/**
 * Attempts to guess a representative application ID for the given model. If the model contains multiple application IDs, this returns the
 * first one. If it contains no application IDs, it returns the empty string.
 *
 * Note that there are very few situations in which it is valid to use a single application ID for an entire model. Callers should consider
 * querying the model directly and using the correct application ID for each artifact.
 */
fun guessApplicationIdFor(model: AndroidModelSubset): String {
  return model.firstMainArtifact()?.artifact?.resolved?.manifestValues?.applicationId ?: ""
}

/**
 * Returns the set of [Config] for the given model which apply globally.
 */
fun defaultConfigsFor(model:com.android.projectmodel.AndroidModel): List<Config> {
  return model.projects.flatMap { it.configTable.filter { it.path.matchesEverything }.configs }
}
