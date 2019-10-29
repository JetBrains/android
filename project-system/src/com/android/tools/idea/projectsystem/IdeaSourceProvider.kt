/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.vfs.VirtualFile

/**
 * Like [SourceProvider], but for IntelliJ, which means it provides
 * [VirtualFile] and IDEA's url references rather than [File] references.
 *
 * Note: VirtualFile versions may return fewer items or a null manifest where the url versions return a non-empty url(s) if the file
 * referred to does not exist in the VFS.
 *
 * @see VirtualFile.getUrl
 */
interface IdeaSourceProvider {

  val name: String

  val manifestFileUrl: String
  val manifestDirectory: VirtualFile?
  val manifestFile: VirtualFile?

  val javaDirectoryUrls: Collection<String>
  val javaDirectories: Collection<VirtualFile>

  val resourcesDirectoryUrls: Collection<String>
  val resourcesDirectories: Collection<VirtualFile>

  val aidlDirectoryUrls: Collection<String>
  val aidlDirectories: Collection<VirtualFile>

  val renderscriptDirectoryUrls: Collection<String>
  val renderscriptDirectories: Collection<VirtualFile>

  val jniDirectoryUrls: Collection<String>
  val jniDirectories: Collection<VirtualFile>

  val jniLibsDirectoryUrls: Collection<String>
  val jniLibsDirectories: Collection<VirtualFile>

  val resDirectoryUrls: Collection<String>
  val resDirectories: Collection<VirtualFile>

  val assetsDirectoryUrls: Collection<String>
  val assetsDirectories: Collection<VirtualFile>

  val shadersDirectoryUrls: Collection<String>
  val shadersDirectories: Collection<VirtualFile>
}
