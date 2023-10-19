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
import java.io.File

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
   * @return the [ClassContent] corresponding to the class file for the given
   * fully-qualified class name, or null if the class file can't be found.
   */
  fun findClassFile(fqcn: String): ClassContent?
}

/** Content of a class. It can be loaded e.g. from a file or a jar or from memory. */
sealed interface ClassContent {
  val content: ByteArray

  /** If [content] is still up-to-date. It can become out of date when e.g. file or jar changes. */
  fun isUpToDate(): Boolean

  companion object {
    /** Loads a .class file from a [file]. */
    @JvmStatic
    fun loadFromFile(file: File): ClassContent {
      return FileClassContent(file)
    }

    /** Creates a [ClassContent] based on the [jarFile] and [content] of a jar entry (.class file). */
    @JvmStatic
    fun fromJarEntryContent(jarFile: File, content: ByteArray): ClassContent {
      return JarEntryClassContent(jarFile, content)
    }
  }
}

private class FileClassContent(private val file: File) : ClassContent {
  override val content: ByteArray = file.readBytes()
  private val timestamp: Long = file.lastModified()
  private val size: Long = file.length()

  override fun isUpToDate(): Boolean {
    return timestamp == file.lastModified() && size == file.length()
  }
}

private class JarEntryClassContent(private val jarFile: File, override val content: ByteArray) : ClassContent {

  private val jarSize: Long = jarFile.length()
  private val jarTimestamp: Long = jarFile.lastModified()

  override fun isUpToDate(): Boolean {
    return jarTimestamp == jarFile.lastModified() && jarSize == jarFile.length()
  }
}

fun getPathFromFqcn(fqcn: String): String {
  return fqcn.replace(".", "/") + SdkConstants.DOT_CLASS
}