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
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.StaticStringMapper
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.WaitFor
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestBase
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertTrue

/** Return a fake directory on a DummyFileSystem. */
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

  val resourceRepository = StudioResourceRepositoryManager.getModuleResources(module)
                           ?: throw Exception("No StudioResourceRepositoryManager for module=$module")
  resourceRepository.invalidateResourceDirs()

  return resourceRepository
    .getResources(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, fileName.substringBefore("."))
    .firstOrNull() ?: throw Exception("Unable to obtain resource res/drawable/$fileName ${resourceRepository.getResources(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE)}")
}

private const val WAIT_TIMEOUT = 3000

internal inline fun <reified T : JComponent> waitAndAssert(container: JPanel, crossinline condition: (list: T?) -> Boolean) {
  val waitForComponentCondition = object : WaitFor(WAIT_TIMEOUT) {
    public override fun condition(): Boolean {
      invokeAndWaitIfNeeded {
        UIUtil.dispatchAllInvocationEvents()
      }
      return@condition condition(UIUtil.findComponentOfType(container, T::class.java))
    }
  }
  assertTrue(waitForComponentCondition.isConditionRealized)
}

internal fun simulateMouseClick(component: JComponent, point: Point, clickCount: Int) {
  runInEdtAndWait {
    // A click is done through a mouse pressed & released event, followed by the actual mouse clicked event.
    component.dispatchEvent(MouseEvent(
      component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 0, false))
    component.dispatchEvent(MouseEvent(
      component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 0, false))
    component.dispatchEvent(MouseEvent(
      component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, clickCount, false))
  }
}