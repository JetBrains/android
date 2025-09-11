/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

import com.android.ide.common.gradle.Component
import com.android.tools.idea.gradle.model.impl.FileImpl
import com.android.tools.idea.gradle.model.impl.toImpl
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
  override val manifest: FileImpl,
  override val compileJarFiles: List<FileImpl>,
  override val runtimeJarFiles: List<FileImpl>,
  override val resFolder: FileImpl,
  override val resStaticLibrary: FileImpl?,
  override val assetsFolder: FileImpl,
  override val jniFolder: FileImpl,
  override val aidlFolder: FileImpl,
  override val renderscriptFolder: FileImpl,
  override val proguardRules: FileImpl,
  override val lintJar: FileImpl?,
  override val srcJars: List<FileImpl>,
  override val docJar: FileImpl?,
  override val externalAnnotations: FileImpl,
  override val publicResources: FileImpl,
  override val artifact: FileImpl?,
  override val symbolFile: FileImpl
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
    manifest.path.translate(folder),
    compileJarFiles.map { it.path.translate(folder) },
    runtimeJarFiles.map { it.path.translate(folder) },
    resFolder.path.translate(folder),
    resStaticLibrary?.path?.translate(folder),
    assetsFolder.path.translate(folder),
    jniFolder.path.translate(folder),
    aidlFolder.path.translate(folder),
    renderscriptFolder.path.translate(folder),
    proguardRules.path.translate(folder),
    lintJar?.path?.translate(folder),
    srcJars.map { it.path.translate(folder) },
    docJar?.path?.translate(folder),
    externalAnnotations.path.translate(folder),
    publicResources.path.translate(folder),
    artifact?.path?.translate(folder),
    symbolFile.path.translate(folder)
  )

  // Used for serialization by the IDE.
  @VisibleForTesting
  constructor() : this(
    artifactAddress = "",
    component = null,
    name = "",
    folder = FileImpl(""),
    manifest = FileImpl(""),
    compileJarFiles = mutableListOf(),
    runtimeJarFiles = mutableListOf(),
    resFolder = FileImpl(""),
    resStaticLibrary = null,
    assetsFolder = FileImpl(""),
    jniFolder = FileImpl(""),
    aidlFolder = FileImpl(""),
    renderscriptFolder = FileImpl(""),
    proguardRules = FileImpl(""),
    lintJar = FileImpl(""),
    srcJars = mutableListOf(),
    docJar = FileImpl(""),
    externalAnnotations = FileImpl(""),
    publicResources = FileImpl(""),
    artifact = FileImpl(""),
    symbolFile = FileImpl("")
  )

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
      fun File.makeRelativeAndTranslate(): FileImpl = this.relativeToOrSelf(folder).path.translate(folder, deduplicate)
      fun String.makeRelativeAndTranslate(): FileImpl = File(this).makeRelativeAndTranslate()

      return IdeAndroidLibraryImpl(
        artifactAddress = artifactAddress,
        component = component,
        name = name,
        folder = folder.toImpl(),
        manifest = manifest.makeRelativeAndTranslate(),
        compileJarFiles = compileJarFiles.map { it.makeRelativeAndTranslate() },
        runtimeJarFiles = runtimeJarFiles.map { it.makeRelativeAndTranslate() },
        resFolder = resFolder.makeRelativeAndTranslate(),
        resStaticLibrary = resStaticLibrary?.makeRelativeAndTranslate(),
        assetsFolder = assetsFolder.makeRelativeAndTranslate(),
        jniFolder = jniFolder.makeRelativeAndTranslate(),
        aidlFolder = aidlFolder.makeRelativeAndTranslate(),
        renderscriptFolder = renderscriptFolder.makeRelativeAndTranslate(),
        proguardRules = proguardRules.makeRelativeAndTranslate(),
        lintJar = lintJar?.makeRelativeAndTranslate(),
        srcJars = srcJars.map { it.makeRelativeAndTranslate() },
        docJar = docJar?.makeRelativeAndTranslate(),
        externalAnnotations = externalAnnotations.makeRelativeAndTranslate(),
        publicResources = publicResources.makeRelativeAndTranslate(),
        artifact = artifact?.makeRelativeAndTranslate(),
        symbolFile = symbolFile.makeRelativeAndTranslate()
      )
    }
  }
}

private fun String.translate(folder: File, deduplicate: String.() -> String = { this }): FileImpl =
  folder.resolve(this).normalize().path.deduplicate().let(::FileImpl)
