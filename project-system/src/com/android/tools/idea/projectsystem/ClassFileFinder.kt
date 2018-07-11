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

import com.intellij.openapi.vfs.VirtualFile

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
