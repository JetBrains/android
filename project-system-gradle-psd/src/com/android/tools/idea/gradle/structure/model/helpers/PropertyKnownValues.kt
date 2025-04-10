/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:Suppress("UNUSED_PARAMETER")

package com.android.tools.idea.gradle.structure.model.helpers

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.idea.concurrency.readOnPooledThread
import com.android.tools.idea.concurrency.transformNullable
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.FORCE
import com.android.tools.idea.gradle.project.upgrade.computeGradlePluginUpgradeState
import com.android.tools.idea.gradle.repositories.search.SearchRequest
import com.android.tools.idea.gradle.repositories.search.SearchResult
import com.android.tools.idea.gradle.repositories.search.SingleModuleSearchQuery
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.getHolderModuleByGradlePath
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.KnownValues
import com.android.tools.idea.gradle.structure.model.meta.ListProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.android.tools.idea.gradle.structure.model.meta.withFileSelectionRoot
import com.android.tools.idea.gradle.util.GradleVersionsRepository
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.FilenameIndex
import java.io.File

fun booleanValues(model: Any?): ListenableFuture<List<ValueDescriptor<Boolean>>> =
  immediateFuture(listOf(ValueDescriptor(value = false), ValueDescriptor(value = true)))

fun installedSdksAsStrings(model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().androidSdks)

fun installedSdksAsInts(model: Any?): ListenableFuture<List<ValueDescriptor<Int>>> =
  immediateFuture(installedEnvironments().androidSdks
                    .mapNotNull {
                      it.value.getText { toString() }.toIntOrNull()?.let { intValue ->
                        ValueDescriptor(intValue, it.description)
                      }
                    })

fun installedBuildTools(model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().buildTools)

fun ndkVersionValues(model: PsAndroidModule?): ListenableFuture<List<ValueDescriptor<String>>> {
  val defaultNdkVersion = model?.resolvedNativeModel?.defaultNdkVersion?.let { ValueDescriptor(it) }
  return immediateFuture((listOfNotNull(defaultNdkVersion) + installedEnvironments().ndks).distinct())
}

fun installedCompiledApis(model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().compiledApis)

fun languageLevels(model: PsAndroidModule): ListenableFuture<List<ValueDescriptor<LanguageLevel>>> {
  val languageLevels = mutableListOf(
    ValueDescriptor(value = LanguageLevel.JDK_1_6, description = "Java 6"),
    ValueDescriptor(value = LanguageLevel.JDK_1_7, description = "Java 7"),
    ValueDescriptor(value = LanguageLevel.JDK_1_8, description = "Java 8"),
    ValueDescriptor(value = LanguageLevel.JDK_11, description = "Java 11")
  )
  model.compileSdkVersion.maybeValue?.toIntOrNull()?.let { compileSdkVersion ->
    // API 34 supports Java 17 https://developer.android.com/build/jdks
    if(compileSdkVersion >= VersionCodes.UPSIDE_DOWN_CAKE) {
      languageLevels.add(ValueDescriptor(value = LanguageLevel.JDK_17, description = "Java 17"))
    }
  }
  return immediateFuture(languageLevels)
}

fun signingConfigs(module: PsAndroidModule): ListenableFuture<List<ValueDescriptor<Unit>>> = immediateFuture(module.signingConfigs.map {
  ValueDescriptor(ParsedValue.Set.Parsed(null, DslText.Reference("signingConfigs.${it.name}")))
})

fun proGuardFileValuesCore(module: PsAndroidModule): List<ValueDescriptor<File>> =
  module.parent.ideProject.getHolderModuleByGradlePath(module.gradlePath)?.let { ideModule ->
    FilenameIndex.getAllFilesByExt(
      ideModule.project,
      "pro",
      ideModule.moduleContentScope)
      .mapNotNull {
        module.resolvedModel?.rootDirPath?.let { rootPath ->
          ValueDescriptor(ParsedValue.Set.Parsed(File(it.path).relativeTo(rootPath), DslText.Literal))
        }
      } +
    FilenameIndex.getAllFilesByExt(
      ideModule.project,
      "txt",
      ideModule.moduleContentScope)
      .filter { it.name.startsWith("proguard", ignoreCase = true) }
      .mapNotNull {
        module.resolvedModel?.rootDirPath?.let { rootPath ->
          ValueDescriptor(ParsedValue.Set.Parsed(File(it.path).relativeTo(rootPath), DslText.Literal))
        }
      }
  }.orEmpty() +
  ValueDescriptor(ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("getDefaultProguardFile('proguard-android.txt')")))

fun proGuardFileValues(module: PsAndroidModule): ListenableFuture<List<ValueDescriptor<File>>> =
  readOnPooledThread { proGuardFileValuesCore(module) }

fun buildTypeMatchingFallbackValuesCore(project: PsProject): List<ValueDescriptor<String>> =
  project
    .modules.asSequence()
    .flatMap { (it as? PsAndroidModule)?.buildTypes?.asSequence()?.map { it.name } ?: emptySequence() }
    .distinct()
    .sorted()
    .map { ValueDescriptor(it) }
    .toList()

fun productFlavorMatchingFallbackValuesCore(project: PsProject, dimension: String?): List<ValueDescriptor<String>> =
  project
    .modules.asSequence()
    .flatMap {
      (it as? PsAndroidModule)
        ?.productFlavors?.asSequence()
        ?.filter { dimension == null || it.configuredDimension.maybeValue == dimension }
        ?.map { it.name }
      ?: emptySequence()
    }
    .distinct()
    .sorted()
    .map { ValueDescriptor(it) }
    .toList()

fun buildTypeMatchingFallbackValues(project: PsProject): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(buildTypeMatchingFallbackValuesCore(project))

fun productFlavorMatchingFallbackValues(project: PsProject, dimension: String?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(productFlavorMatchingFallbackValuesCore(project, dimension))

private const val MAX_ARTIFACTS_TO_REQUEST = 50  // Note: we do not expect more than one result per repository.
fun dependencyVersionValues(model: PsDeclaredLibraryDependency): ListenableFuture<List<ValueDescriptor<String>>> =
  model.spec.group?.let { group ->
    val name = model.spec.name
    val currentVersion = model.spec.version
    val filter: (Version) -> Boolean = when (group) {
      "com.google.guava" -> when {
        currentVersion == null -> { { true } }
        currentVersion.endsWith("-jre") -> { { v -> v.toString().endsWith("-jre") } }
        currentVersion.endsWith("-android") -> { { v -> v.toString().endsWith("-android") } }
        else -> { { true } }
      }
      else -> { { true } }
    }
    Futures.transform(
      model.parent.parent.repositorySearchFactory
        .create(model.parent.getArtifactRepositories())
        .search(SearchRequest(SingleModuleSearchQuery(group, name), MAX_ARTIFACTS_TO_REQUEST, 0)),
      { it!!.toVersionValueDescriptors(filter) },
      directExecutor())
  } ?: immediateFuture(listOf())

fun androidGradlePluginVersionValues(model: PsProject): ListenableFuture<List<ValueDescriptor<String>>> =
  Futures.transform(
    model.repositorySearchFactory
      .create(model.getPluginArtifactRepositories())
      .search(SearchRequest(SingleModuleSearchQuery("com.android.tools.build", "gradle"), MAX_ARTIFACTS_TO_REQUEST, 0)),
    { sr ->
      val searchResult = sr!!
      // TODO(b/242691473): going through toString() here is not pretty, but the type information is buried quite deep.
      val versions = searchResult.artifacts.flatMap { it.versions }.distinct().mapNotNull { AgpVersion.tryParse(it.toString()) }.toSet()
      val latestKnown = AgpVersions.latestKnown
      // return only results that will not lead to forced upgrades
      searchResult.toVersionValueDescriptors {
        // TODO(b/242691473): again, not pretty
        val agpVersion = AgpVersion.tryParse(it.toString()) ?: return@toVersionValueDescriptors false
        computeGradlePluginUpgradeState(agpVersion, latestKnown, versions).importance != FORCE
      }
    },
    directExecutor())

fun versionValues(): ListenableFuture<KnownValues<String>> =
  GradleVersionsRepository.getKnownVersionsFuture().transformNullable(directExecutor()) { versions: List<String>? ->
    object : KnownValues<String> {
      override val literals: List<ValueDescriptor<String>> =
        versions?.sortedByDescending { Version.parse(it) }?.map { ValueDescriptor(it) } ?: emptyList()
      override fun isSuitableVariable(variable: Annotated<ParsedValue.Set.Parsed<String>>): Boolean = false
    }
  }

@VisibleForTesting
fun SearchResult.toVersionValueDescriptors(filter: (Version) -> Boolean = { true }): List<ValueDescriptor<String>> =
  artifacts
    .flatMap { it.versions }
    .distinct()
    .filter(filter)
    .sortedDescending()
    .map { version -> ValueDescriptor(version.toString()) }

fun <T : PsChildModel> ListProperty<T, File>.withProFileSelector(module: T.() -> PsAndroidModule) =
  withFileSelectionRoot(
    masks = listOf("*.pro", "*.txt"),
    browseRoot = { module().parent.ideProject.basePath?.let { File(it) } },
    resolveRoot = { module().rootDir })
