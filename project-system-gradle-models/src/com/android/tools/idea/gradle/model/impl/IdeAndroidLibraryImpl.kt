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

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Version
import com.android.tools.idea.gradle.model.IdeUnresolvedAndroidLibrary
import com.google.common.annotations.VisibleForTesting
import java.io.File

import java.io.Serializable

/**
 * The implementation of IdeLibrary for Android libraries.
 **/
data class IdeAndroidLibraryImpl(
  override val artifactAddress: String,
  override val component: Component?,
  override val name: String,
  override val folder: FileImpl,
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
  private val _srcJars: List<String>,
  private val _docJar: String?,
  private val _externalAnnotations: String,
  private val _publicResources: String,
  private val _artifact: String?,
  private val _symbolFile: String
) : IdeUnresolvedAndroidLibrary, Serializable {
  constructor(
    artifactAddress: String,
    component: Component?,
    name: String,
    folder: File,
    manifest: File,
    compileJarFiles: List<File>,
    runtimeJarFiles: List<File>,
    resFolder: File,
    resStaticLibrary: File?,
    assetsFolder: File,
    jniFolder: File,
    aidlFolder: File,
    renderscriptFolder: File,
    proguardRules: File,
    lintJar: File?,
    srcJars: List<File>,
    docJar: File?,
    externalAnnotations: File,
    publicResources: File,
    artifact: File?,
    symbolFile: File
  ) : this(
    artifactAddress,
    component,
    name,
    folder.toImpl(),
    manifest.path,
    compileJarFiles.map { it.path },
    runtimeJarFiles.map { it.path },
    resFolder.path,
    resStaticLibrary?.path,
    assetsFolder.path,
    jniFolder.path,
    aidlFolder.path,
    renderscriptFolder.path,
    proguardRules.path,
    lintJar?.path,
    srcJars.map { it.path },
    docJar?.path,
    externalAnnotations.path,
    publicResources.path,
    artifact?.path,
    symbolFile.path
  )

  // Used for serialization by the IDE.
  @VisibleForTesting
  constructor() : this(
    artifactAddress = "",
    component = null,
    name = "",
    folder = FileImpl(""),
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
    _srcJars = mutableListOf(),
    _docJar = "",
    _externalAnnotations = "",
    _publicResources = "",
    _artifact = "",
    _symbolFile = ""
  )

  private fun String.translate(): FileImpl = folder.resolve(this).normalize().path.let(::FileImpl)

  override val manifest: FileImpl get() = _manifest.translate()
  override val compileJarFiles: List<FileImpl> get() = _compileJarFiles.map { it.translate() }
  override val runtimeJarFiles: List<FileImpl> get() = _runtimeJarFiles.map { it.translate() }
  override val resFolder: FileImpl get() = _resFolder.translate()
  override val resStaticLibrary: FileImpl? get() = _resStaticLibrary?.translate()
  override val assetsFolder: FileImpl get() = _assetsFolder.translate()
  override val jniFolder: FileImpl get() = _jniFolder.translate()
  override val aidlFolder: FileImpl get() = _aidlFolder.translate()
  override val renderscriptFolder: FileImpl get() = _renderscriptFolder.translate()
  override val proguardRules: FileImpl get() = _proguardRules.translate()
  override val lintJar: FileImpl? get() = _lintJar?.translate()
  override val srcJars: List<FileImpl> get() = _srcJars.map { it.translate() }
  override val docJar: FileImpl? get() = _docJar?.translate()
  override val externalAnnotations: FileImpl get() = _externalAnnotations.translate()
  override val publicResources: FileImpl get() = _publicResources.translate()
  override val artifact: FileImpl? get() = _artifact?.translate()
  override val symbolFile: FileImpl get() = _symbolFile.translate()

  companion object {
    fun create(
      artifactAddress: String,
      component: Component?,
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
      srcJars: List<String>,
      docJar: String?,
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
        component = component,
        name = name,
        folder = folder.toImpl(),
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
        _srcJars = srcJars.map { it.makeRelative()},
        _docJar = docJar?.makeRelative(),
        _externalAnnotations = externalAnnotations.makeRelative(),
        _publicResources = publicResources.makeRelative(),
        _artifact = artifact?.makeRelative(),
        _symbolFile = symbolFile.makeRelative()
      )
    }
  }
}
