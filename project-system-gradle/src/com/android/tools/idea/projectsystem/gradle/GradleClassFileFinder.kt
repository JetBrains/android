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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.project.ModuleBasedClassFileFinder
import com.android.tools.idea.projectsystem.findClassFileInOutputRoot
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.Objects

class GradleClassFileFinder @JvmOverloads constructor(module: Module, private val includeAndroidTests: Boolean = false) :
  ModuleBasedClassFileFinder(module) {
  override fun findClassFileInModule(module: Module, className: String): VirtualFile? {
    return GradleClassFinderUtil.getModuleCompileOutputs(module, includeAndroidTests)
      .map { file: File? ->
        VfsUtil.findFileByIoFile(
          file!!, true
        )
      }
      .filter { obj: VirtualFile? -> Objects.nonNull(obj) }
      .map { vFile: VirtualFile? ->
        findClassFileInOutputRoot(
          vFile!!, className
        )
      }
      .filter { obj: VirtualFile? -> Objects.nonNull(obj) }
      .findFirst()
      .orElse(null)
  }
}
