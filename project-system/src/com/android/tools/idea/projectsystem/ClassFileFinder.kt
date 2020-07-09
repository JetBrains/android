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
@file:JvmName("ClassFileFinderUtil")

package com.android.tools.idea.projectsystem

import com.android.SdkConstants
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

/**
 * A [ClassFileFinder] searches build output to find the class file corresponding to a
 * fully-qualified class name.
 *
 * Because the build system is responsible for generating these class files, implementations
 * of [ClassFileFinder] are build system-specific. To retrieve class files in a build
 * system-agnostic way, callers should go through the [AndroidModuleSystem] abstraction.
 */
interface ClassFileFinder {
  /**
   * @return the [VirtualFile] corresponding to the class file for the given
   * fully-qualified class name, or null if the class file can't be found.
   */
  fun findClassFile(fqcn: String): VirtualFile?
}

/**
 * Given a fully-qualified class name, searches the directory contents of the build system
 * [outputRoot] for the corresponding class file.
 *
 * @return the class file where the class referenced by [fqcn] is defined, or null if the
 *         [outputRoot] doesn't exist or doesn't contain such a class file.
 */
fun findClassFileInOutputRoot(outputRoot: VirtualFile, fqcn: String): VirtualFile? {
  if (!outputRoot.exists()) return null

  val pathSegments = fqcn.split(".").toTypedArray()
  pathSegments[pathSegments.size - 1] += SdkConstants.DOT_CLASS
  val outputBase = (JarFileSystem.getInstance().getJarRootForLocalFile(outputRoot) ?: outputRoot)

  val classFile = VfsUtil.findRelativeFile(outputBase, *pathSegments)
                  ?: VfsUtil.findFile(Paths.get(outputBase.path, *pathSegments), true)

  return if (classFile != null && classFile.exists()) classFile else null
}