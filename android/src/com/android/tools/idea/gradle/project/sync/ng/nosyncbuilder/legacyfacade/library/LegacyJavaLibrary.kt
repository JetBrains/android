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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.JavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2Library
import java.io.File

open class LegacyJavaLibrary(private val javaLibrary: JavaLibrary) : Level2Library {
  override fun getType(): Int = Level2Library.LIBRARY_JAVA
  override fun getArtifactAddress(): String = javaLibrary.artifactAddress!!
  override fun getArtifact(): File = javaLibrary.artifact

  override fun getBuildId(): String? = throw unsupportedMethodForJavaLibrary("getBuildId")
  override fun getProjectPath(): String? = throw unsupportedMethodForJavaLibrary("getProjectPath")
  override fun getVariant(): String? = throw unsupportedMethodForJavaLibrary("getVariant")
  override fun getFolder(): File = throw unsupportedMethodForJavaLibrary("getFolder")
  override fun getManifest(): String = throw unsupportedMethodForJavaLibrary("getManifest")
  override fun getJarFile(): String = throw unsupportedMethodForJavaLibrary("getJarFile")
  override fun getResFolder(): String = throw unsupportedMethodForJavaLibrary("getResFolder")
  override fun getResStaticLibrary(): File? = throw unsupportedMethodForJavaLibrary("getResStaticLibrary")
  override fun getAssetsFolder(): String = throw unsupportedMethodForJavaLibrary("getAssetsFolder")
  override fun getLocalJars(): Collection<String> = throw unsupportedMethodForJavaLibrary("getLocalJars")
  override fun getJniFolder(): String = throw unsupportedMethodForJavaLibrary("getJniFolder")
  override fun getAidlFolder(): String = throw unsupportedMethodForJavaLibrary("getAidlFolder")
  override fun getRenderscriptFolder(): String = throw unsupportedMethodForJavaLibrary("getRenderscriptFolder")
  override fun getProguardRules(): String = throw unsupportedMethodForJavaLibrary("getProguardRules")
  override fun getLintJar(): String = throw unsupportedMethodForJavaLibrary("getLintJar")
  override fun getExternalAnnotations(): String = throw unsupportedMethodForJavaLibrary("getExternalAnnotations")
  override fun getPublicResources(): String = throw unsupportedMethodForJavaLibrary("getPublicResources")
  override fun getSymbolFile(): String = throw unsupportedMethodForJavaLibrary("getSymbolFile")
}

private fun unsupportedMethodForJavaLibrary(methodName: String): UnsupportedOperationException =
  UnsupportedOperationException("$methodName() cannot be called when getType() returns LIBRARY_JAVA")
