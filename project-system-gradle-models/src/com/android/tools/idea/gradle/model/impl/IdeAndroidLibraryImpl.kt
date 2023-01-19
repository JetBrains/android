/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeUnresolvedAndroidLibrary
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.Serializable

/**
 * The implementation of IdeLibrary for Android libraries.
 **/
data class IdeAndroidLibraryImpl(
  override val artifactAddress: String,
  override val name: String,
  override val folder: File,
  private val _manifest: String,
  private val _compileJarFiles: List<String>,
  private val _runtimeJarFiles: List<String>,
  private val _resFolder: String,
  private val _resStaticLibrary: String?,
  private val _assetsFolder: String,
  private val _jniFolder: String,
  private val _aidlFolder: String,
  private val _renderscriptFolder: String,
  private val _proguardRules: String,
  private val _lintJar: String?,
  private val _externalAnnotations: String,
  private val _publicResources: String,
  private val _artifact: String?,
  private val _symbolFile: String
) : IdeUnresolvedAndroidLibrary, Serializable {

  // Used for serialization by the IDE.
  @VisibleForTesting
  constructor() : this(
    artifactAddress = "",
    name = "",
    folder = File(""),
    _manifest = "",
    _compileJarFiles = mutableListOf(),
    _runtimeJarFiles = mutableListOf(),
    _resFolder = "",
    _resStaticLibrary = null,
    _assetsFolder = "",
    _jniFolder = "",
    _aidlFolder = "",
    _renderscriptFolder = "",
    _proguardRules = "",
    _lintJar = "",
    _externalAnnotations = "",
    _publicResources = "",
    _artifact = "",
    _symbolFile = ""
  )

  private fun String.translate(): File = folder.resolve(this).normalize().path.let(::File)

  override val manifest: File get() = _manifest.translate()
  override val compileJarFiles: List<File> get() = _compileJarFiles.map { it.translate() }
  override val runtimeJarFiles: List<File> get() = _runtimeJarFiles.map { it.translate() }
  override val resFolder: File get() = _resFolder.translate()
  override val resStaticLibrary: File? get() = _resStaticLibrary?.translate()
  override val assetsFolder: File get() = _assetsFolder.translate()
  override val jniFolder: File get() = _jniFolder.translate()
  override val aidlFolder: File get() = _aidlFolder.translate()
  override val renderscriptFolder: File get() = _renderscriptFolder.translate()
  override val proguardRules: File get() = _proguardRules.translate()
  override val lintJar: File? get() = _lintJar?.translate()
  override val externalAnnotations: File get() = _externalAnnotations.translate()
  override val publicResources: File get() = _publicResources.translate()
  override val artifact: File? get() = _artifact?.translate()
  override val symbolFile: File get() = _symbolFile.translate()

  companion object {
    fun create(
      artifactAddress: String,
      name: String,
      folder: File,
      manifest: String,
      compileJarFiles: List<String>,
      runtimeJarFiles: List<String>,
      resFolder: String,
      resStaticLibrary: File?,
      assetsFolder: String,
      jniFolder: String,
      aidlFolder: String,
      renderscriptFolder: String,
      proguardRules: String,
      lintJar: String?,
      externalAnnotations: String,
      publicResources: String,
      artifact: File?,
      symbolFile: String,
      deduplicate: String.() -> String
    ): IdeAndroidLibraryImpl {
      fun String.makeRelative(): String = File(this).relativeToOrSelf(folder).path.deduplicate()
      fun File.makeRelative(): String = this.relativeToOrSelf(folder).path.deduplicate()

      return IdeAndroidLibraryImpl(
        artifactAddress = artifactAddress,
        name = name,
        folder = folder,
        _manifest = manifest.makeRelative(),
        _compileJarFiles = compileJarFiles.map { it.makeRelative() },
        _runtimeJarFiles = runtimeJarFiles.map { it.makeRelative() },
        _resFolder = resFolder.makeRelative(),
        _resStaticLibrary = resStaticLibrary?.makeRelative(),
        _assetsFolder = assetsFolder.makeRelative(),
        _jniFolder = jniFolder.makeRelative(),
        _aidlFolder = aidlFolder.makeRelative(),
        _renderscriptFolder = renderscriptFolder.makeRelative(),
        _proguardRules = proguardRules.makeRelative(),
        _lintJar = lintJar?.makeRelative(),
        _externalAnnotations = externalAnnotations.makeRelative(),
        _publicResources = publicResources.makeRelative(),
        _artifact = artifact?.makeRelative(),
        _symbolFile = symbolFile.makeRelative()
      )
    }
  }
}
