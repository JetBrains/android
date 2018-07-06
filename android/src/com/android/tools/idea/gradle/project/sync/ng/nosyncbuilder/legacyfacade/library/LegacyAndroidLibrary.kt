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

import com.android.SdkConstants.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.AndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2Library
import com.android.utils.FileUtils
import java.io.File

open class LegacyAndroidLibrary(private val androidLibrary: AndroidLibrary) : Level2Library {
  override fun getType(): Int = Level2Library.LIBRARY_ANDROID
  override fun getArtifactAddress(): String = androidLibrary.artifactAddress!!
  override fun getArtifact(): File = androidLibrary.artifact
  override fun getFolder(): File = androidLibrary.bundleFolder

  override fun getManifest(): String = FN_ANDROID_MANIFEST_XML
  override fun getJarFile(): String = FileUtils.join(FD_JARS, FN_CLASSES_JAR)
  override fun getResFolder(): String = FD_RESOURCES
  override fun getResStaticLibrary(): File? = null // TODO(qumeric): properly support it
  override fun getAssetsFolder(): String = FD_ASSETS
  override fun getLocalJars(): Collection<String> = androidLibrary.localJars.map(File::toString)
  override fun getJniFolder(): String = FD_JNI
  override fun getAidlFolder(): String = FD_AIDL
  override fun getRenderscriptFolder(): String = FD_RENDERSCRIPT
  override fun getProguardRules(): String = FN_PROGUARD_TXT
  override fun getLintJar(): String = FileUtils.join(FD_JARS, FN_LINT_JAR)
  override fun getExternalAnnotations(): String = FN_ANNOTATIONS_ZIP
  override fun getPublicResources(): String = FN_PUBLIC_TXT
  override fun getSymbolFile(): String = FN_RESOURCE_TEXT

  override fun getBuildId(): String? = throw unsupportedMethodForAndroidLibrary("getBuildId")
  override fun getProjectPath(): String? = throw unsupportedMethodForAndroidLibrary("getProjectPath")
  override fun getVariant(): String? = throw unsupportedMethodForAndroidLibrary("getVariant")
}

private fun unsupportedMethodForAndroidLibrary(methodName: String): UnsupportedOperationException =
  UnsupportedOperationException("$methodName() cannot be called when getType() returns LIBRARY_ANDROID")
