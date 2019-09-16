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

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.gradle.structure.configurables.ui.readOnPooledThread
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchResult
import com.google.common.base.Function
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.ModuleManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.concurrency.SameThreadExecutor
import java.io.File

fun booleanValues(model: Any?): ListenableFuture<List<ValueDescriptor<Boolean>>> =
  immediateFuture(listOf(ValueDescriptor(value = false), ValueDescriptor(value = true)))

fun installedSdksAsStrings(model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().androidSdks.map { ValueDescriptor(it.value.getText { toString() }, it.description) })

fun installedSdksAsInts(model: Any?): ListenableFuture<List<ValueDescriptor<Int>>> =
  immediateFuture(installedEnvironments().androidSdks)

fun installedBuildTools(model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().buildTools)

fun installedCompiledApis(model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().compiledApis)

fun languageLevels(model: Any?): ListenableFuture<List<ValueDescriptor<LanguageLevel>>> = immediateFuture(listOf(
  ValueDescriptor(value = LanguageLevel.JDK_1_6, description = "Java 6"),
  ValueDescriptor(value = LanguageLevel.JDK_1_7, description = "Java 7"),
  ValueDescriptor(value = LanguageLevel.JDK_1_8, description = "Java 8")
))

fun signingConfigs(module: PsAndroidModule): ListenableFuture<List<ValueDescriptor<Unit>>> = immediateFuture(module.signingConfigs.map {
  ValueDescriptor(ParsedValue.Set.Parsed(Unit, DslText.Reference("signingConfigs.${it.name}")))
})

fun proGuardFileValuesCore(module: PsAndroidModule): List<ValueDescriptor<File>> =
  ModuleManager.getInstance(module.parent.ideProject).findModuleByName(module.name)?.let { ideModule ->
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
        ?.filter { dimension == null || it.dimension.maybeValue == dimension }
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
  Futures.transform(model.parent.parent.repositorySearchFactory
                      .create(model.parent.getArtifactRepositories())
                      .search(SearchRequest(model.spec.name, model.spec.group, MAX_ARTIFACTS_TO_REQUEST, 0)), Function {
    it!!.toVersionValueDescriptors()
  }, SameThreadExecutor.INSTANCE)

@VisibleForTesting
fun SearchResult.toVersionValueDescriptors(): List<ValueDescriptor<String>> =
  artifacts
    .flatMap { it.versions }
    .distinct()
    .sortedDescending()
    .map { version -> ValueDescriptor(version.toString()) }
