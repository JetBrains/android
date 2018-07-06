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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.ModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2Library
import java.io.File

open class LegacyModuleLibrary(private val moduleDependency: ModuleDependency) : Level2Library {
  override fun getType(): Int = Level2Library.LIBRARY_MODULE
  override fun getArtifactAddress(): String = moduleDependency.artifactAddress!!
  override fun getBuildId(): String? = moduleDependency.buildId
  override fun getProjectPath(): String? = moduleDependency.projectPath
  override fun getVariant(): String? = moduleDependency.variant

  override fun getArtifact(): File = throw unsupportedMethodForModuleLibrary("getArtifact")
  override fun getFolder(): File = throw unsupportedMethodForModuleLibrary("getFolder")
  override fun getManifest(): String = throw unsupportedMethodForModuleLibrary("getManifest")
  override fun getJarFile(): String = throw unsupportedMethodForModuleLibrary("getJarFile")
  override fun getResFolder(): String = throw unsupportedMethodForModuleLibrary("getResFolder")
  override fun getResStaticLibrary(): File? = throw unsupportedMethodForModuleLibrary("getResStaticLibrary")
  override fun getAssetsFolder(): String = throw unsupportedMethodForModuleLibrary("getAssetsFolder")
  override fun getLocalJars(): Collection<String> = throw unsupportedMethodForModuleLibrary("getLocalJars")
  override fun getJniFolder(): String = throw unsupportedMethodForModuleLibrary("getJniFolder")
  override fun getAidlFolder(): String = throw unsupportedMethodForModuleLibrary("getAidlFolder")
  override fun getRenderscriptFolder(): String = throw unsupportedMethodForModuleLibrary("getRenderscriptFolder")
  override fun getProguardRules(): String = throw unsupportedMethodForModuleLibrary("getProguardRules")
  override fun getLintJar(): String = throw unsupportedMethodForModuleLibrary("getLintJar")
  override fun getExternalAnnotations(): String = throw unsupportedMethodForModuleLibrary("getExternalAnnotations")
  override fun getPublicResources(): String = throw unsupportedMethodForModuleLibrary("getPublicResources")
  override fun getSymbolFile(): String = throw unsupportedMethodForModuleLibrary("getSymbolFile")
}

private fun unsupportedMethodForModuleLibrary(methodName: String): UnsupportedOperationException =
  UnsupportedOperationException("$methodName() cannot be called when getType() returns LIBRARY_MODULE")
