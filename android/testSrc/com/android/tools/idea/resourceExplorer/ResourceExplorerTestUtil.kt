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

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.resources.Density
import com.android.resources.NightMode
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.vfs.VfsUtil
import com.android.tools.idea.resourceExplorer.model.StaticStringMapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import org.jetbrains.android.AndroidTestBase

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

val pluginTestFilesDirectoryName = "/plugins-resources"

fun getTestDataDirectory() = AndroidTestBase.getTestDataPath() + "/resourceExplorer"

fun getPluginsResourcesDirectory(): String {
  return getTestDataDirectory() + pluginTestFilesDirectoryName
}

val densityMapper = StaticStringMapper(
  mapOf(
    "@2x" to DensityQualifier(Density.XHIGH),
    "@3x" to DensityQualifier(Density.XXHIGH),
    "@4x" to DensityQualifier(Density.XXXHIGH)
  ), DensityQualifier(Density.MEDIUM)
)

val nightModeMapper = StaticStringMapper(
  mapOf("_dark" to NightModeQualifier(NightMode.NIGHT))
)

fun pathToVirtualFile(path: String) = BrowserUtil.getURL(path)!!.let(VfsUtil::findFileByURL)!!