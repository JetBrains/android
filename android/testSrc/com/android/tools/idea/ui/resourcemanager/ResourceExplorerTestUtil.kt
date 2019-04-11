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
package com.android.tools.idea.ui.resourcemanager

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ResourceType
import com.android.tools.idea.gradle.stubs.FileStructure
import com.android.tools.idea.gradle.stubs.android.SourceProviderStub
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.ui.resourcemanager.model.StaticStringMapper
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

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

const val pluginTestFilesDirectoryName = "/plugins-resources"
private const val pngFileName = "png.png"
private const val statelist = "statelist.xml"

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

fun getPNGFile() = File(getPluginsResourcesDirectory(),
                        pngFileName)

fun AndroidProjectRule.getPNGResourceItem(): ResourceItem {
  val fileName = pngFileName
  return getResourceItemFromPath(getPluginsResourcesDirectory(), fileName)
}

fun AndroidProjectRule.getStateList(): ResourceItem {
  getPNGResourceItem() // The state list references the png to call that to import it in the project
  val fileName = statelist
  return getResourceItemFromPath(getPluginsResourcesDirectory(), fileName)
}

fun AndroidProjectRule.getResourceItemFromPath(testFolderPath: String, fileName: String): ResourceItem {
  ApplicationManager.getApplication().invokeAndWait {
    fixture.copyFileToProject("$testFolderPath/$fileName", "res/drawable/$fileName")
  }
  return ResourceRepositoryManager
    .getModuleResources(module)
    ?.getResources(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, fileName.substringBefore(""))!![0]
}

fun createFakeResDirectory(androidFacet: AndroidFacet): File? {
  val fileStructure = FileStructure("/")
  val defaultSourceProvider = SourceProviderStub(fileStructure)
  defaultSourceProvider.addResDirectory("res")
  val first = defaultSourceProvider.resDirectories.first()
  androidFacet.configuration.model = TestAndroidModel(defaultSourceProvider = defaultSourceProvider)
  return first
}