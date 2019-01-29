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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.AndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.JavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.Library
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.ModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library.LegacyAndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library.LegacyJavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library.LegacyModuleLibrary

// These helpers are used for ?. syntax support

fun ApiVersion.toLegacy() = LegacyApiVersion(this)
fun ClassField.toLegacy() = LegacyClassField(this)
fun TestOptions.toLegacy() = LegacyTestOptions(this)
fun AndroidArtifact.toLegacy(resValues: Map<String, ClassField>) = LegacyAndroidArtifact(this, resValues)
fun JavaArtifact.toLegacy() = LegacyJavaArtifact(this)
fun AndroidSourceSet.toSourceProvider() = LegacySourceProvider(this)

fun TestOptions.toLegacyStub() = LegacyTestOptionsStub(this)
fun AndroidArtifact.toLegacyStub(resValues: Map<String, ClassField>) = LegacyAndroidArtifactStub(this, resValues)
fun JavaArtifact.toLegacyStub() = LegacyJavaArtifactStub(this)

fun BaseArtifact.toSourceProviderContainerForBuildType() = object : SourceProviderContainer {
  override fun getArtifactName(): String = name
  override fun getSourceProvider(): SourceProvider = LegacySourceProvider(mergedSourceProvider.buildTypeSourceSet)
}

fun BaseArtifact.toSourceProviderContainerForDefaultConfig() = object : SourceProviderContainer {
  override fun getArtifactName(): String = name
  override fun getSourceProvider(): SourceProvider = LegacySourceProvider(mergedSourceProvider.defaultSourceSet)
}

fun Library.toLegacy(): Level2Library = when (this) {
  is AndroidLibrary -> LegacyAndroidLibrary(this)
  is JavaLibrary -> LegacyJavaLibrary(this)
  // TODO add NativeLibrary support
  is ModuleDependency -> LegacyModuleLibrary(this)
  else -> throw IllegalStateException("Library has unknown type ${this.javaClass.kotlin.qualifiedName}")
}
