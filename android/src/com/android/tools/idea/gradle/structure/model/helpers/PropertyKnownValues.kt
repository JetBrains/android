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

import com.android.tools.idea.gradle.structure.configurables.ui.readOnPooledThread
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.ModuleManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.FilenameIndex
import java.io.File

fun booleanValues(context: Any?, model: Any?): ListenableFuture<List<ValueDescriptor<Boolean>>> =
  immediateFuture(listOf(ValueDescriptor(value = false), ValueDescriptor(value = true)))

fun installedSdksAsStrings(context: Any?, model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().androidSdks.map { ValueDescriptor(it.value.getText { toString() }, it.description) })

fun installedSdksAsInts(context: Any?, model: Any?): ListenableFuture<List<ValueDescriptor<Int>>> =
  immediateFuture(installedEnvironments().androidSdks)

fun installedBuildTools(context: Any?, model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().buildTools)

fun installedCompiledApis(context: Any?, model: Any?): ListenableFuture<List<ValueDescriptor<String>>> =
  immediateFuture(installedEnvironments().compiledApis)

fun languageLevels(context: Any?, model: Any?): ListenableFuture<List<ValueDescriptor<LanguageLevel>>> = immediateFuture(listOf(
  ValueDescriptor(value = LanguageLevel.JDK_1_6, description = "Java 6"),
  ValueDescriptor(value = LanguageLevel.JDK_1_7, description = "Java 7"),
  ValueDescriptor(value = LanguageLevel.JDK_1_8, description = "Java 8")
))

fun signingConfigs(module: PsAndroidModule): ListenableFuture<List<ValueDescriptor<Unit>>> = immediateFuture(module.signingConfigs.map {
  ValueDescriptor(ParsedValue.Set.Parsed(Unit, DslText.Reference("signingConfigs.${it.name}")))
})

fun proGuardFileValuesCore(module: PsAndroidModule): List<ValueDescriptor<File>> =
  ModuleManager.getInstance(module.parent.resolvedModel).findModuleByName(module.name)?.let { ideModule ->
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

