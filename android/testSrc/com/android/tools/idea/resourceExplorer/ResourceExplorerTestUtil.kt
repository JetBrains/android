/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem

/**
 * Return a fake directory on a DummyFileSystem.
 * The application must be set to [com.intellij.mock.MockApplication] to use this.
 *
 * @see com.intellij.openapi.application.ApplicationManager.setApplication
 */
fun getExternalResourceDirectory(vararg files: String): VirtualFile {
  val fileSystem = DummyFileSystem()
  val root = fileSystem.createRoot("design")
  files.forEach {
    fileSystem.createChildFile(Any(), root, it)
  }
  return root
}
