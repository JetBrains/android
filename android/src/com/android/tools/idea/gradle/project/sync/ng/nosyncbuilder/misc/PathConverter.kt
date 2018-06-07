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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.FileProto
import java.io.File

class PathConverter(moduleDirFile: File, sdkDirFile: File, libraryDirFile: File, outDirFile: File) {
  enum class DirType {
    MODULE,
    SDK,
    LIBRARY,
    OUT
  }

  private val knownDirs = mapOf(
    DirType.MODULE to moduleDirFile.toPath(),
    DirType.SDK to sdkDirFile.toPath(),
    DirType.LIBRARY to libraryDirFile.toPath(), // Gradle cached libraries
    DirType.OUT to outDirFile.toPath() // output directory where artifacts are stored
  )

  private fun toRelativePath(fileFile: File, dirType: DirType): String {
    val file = fileFile.toPath()
    val dir = knownDirs[dirType]!!
    if (!file.startsWith(dir)) {
      throw Exception("File ${fileFile} is not in the $dirType directory ${dir}") // TODO: custom exception?
    }
    return dir.relativize(file).toString()
  }

  private fun toAbsolutePath(relative: String, dirType: DirType): File = File(knownDirs[dirType]!!.toFile(), relative)

  fun fileFromProto(proto: FileProto.File) = toAbsolutePath(proto.relativePath, DirType.valueOf(proto.relativeTo.name))

  fun fileToProto(file: File, dirType: DirType = DirType.MODULE) = FileProto.File.newBuilder()
    .setRelativePath(toRelativePath(file, dirType))
    .setRelativeTo(FileProto.File.RelativeTo.valueOf(dirType.name))
    .build()!!
}
