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
package com.android.tools.idea.ndk

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

data class ModuleVariantAbi(val moduleName: String, val variantName: String, val abiName: String)

enum class NativeLanguageKind {
  C,
  CPP
}

/** Compiler settings for a native source file. */
interface NativeCompilerSetting {
  /** Which module/variant/abi this setting belongs to. */
  val moduleVariantAbi: ModuleVariantAbi
  val languageKind: NativeLanguageKind
  val compilerExe: File
  val compilerWorkingDir: File
  val compilerFlags: List<String>
  val source: VirtualFile

  /** All include paths. May not be needed for now. */
  val includePaths: List<File>
}

/** Provider of native configurations. */
interface NativeWorkspaceProvider {
  companion object {
    private val EP_NAME = ExtensionPointName.create<NativeWorkspaceProvider>("com.android.tools.idea.ndk.nativeWorkspaceProvider")

    fun getIncludePaths(project: Project, filter: (ModuleVariantAbi) -> Boolean): Stream<File> =
      EP_NAME.extensions().asSequence().flatMap {
        it.getIncludePaths(project, filter).asSequence()
      }.asStream()

    fun getCompilerSettings(project: Project, filter: (ModuleVariantAbi) -> Boolean): Stream<NativeCompilerSetting> =
      EP_NAME.extensions().asSequence().flatMap {
        it.getCompilerSettings(project, filter).asSequence()
      }.asStream()
  }

  /** Gets union of all include paths for all module/variant/abi that matches the given filter. */
  fun getIncludePaths(project: Project, filter: (ModuleVariantAbi) -> Boolean): Stream<File>

  /** Gets all compiler settings for all module/variant/abi that matches the given filter. */
  fun getCompilerSettings(project: Project, filter: (ModuleVariantAbi) -> Boolean): Stream<NativeCompilerSetting>
}

