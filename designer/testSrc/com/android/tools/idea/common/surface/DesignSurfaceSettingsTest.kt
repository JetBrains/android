/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.BackedTestFile
import com.intellij.openapi.application.runWriteAction
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase

@Suppress("UnstableApiUsage")
class DesignSurfaceSettingsTest : AndroidTestCase() {

  private lateinit var defaultFilePathToZoomLevelMap: MutableMap<String, Double>

  override fun setUp() {
    super.setUp()
    val settings = SurfaceState()
    defaultFilePathToZoomLevelMap = settings.filePathToZoomLevelMap
  }

  override fun tearDown() {
    val settings = SurfaceState()
    try {
      settings.filePathToZoomLevelMap = defaultFilePathToZoomLevelMap
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  fun testShareGlobalState() {
    val state1 = DesignSurfaceSettings.getInstance(project).surfaceState
    val state2 = DesignSurfaceSettings.getInstance(project).surfaceState

    // The references should be same.
    assertEquals(state1, state2)
  }

  fun testSaveLoadSettings() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    val filePathToZoomLevelMap = mutableMapOf<String, Double>()
    filePathToZoomLevelMap["path1"] = 0.1
    filePathToZoomLevelMap["path2"] = 0.2

    surfaceState.filePathToZoomLevelMap = filePathToZoomLevelMap

    // Check the values are same after getting another instance.
    val anotherSurfaceState = DesignSurfaceSettings.getInstance(project).surfaceState
    assertEquals(filePathToZoomLevelMap, anotherSurfaceState.filePathToZoomLevelMap)
  }

  fun testSaveLoadSettingsSameFileSameSurfaces() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    val filePathToZoomLevelMap = mutableMapOf<String, Double>()
    filePathToZoomLevelMap["path1:23"] = 0.1
    filePathToZoomLevelMap["path1:32"] = 0.2
    filePathToZoomLevelMap["path1:23"] = 0.4
    filePathToZoomLevelMap["path1:32"] = 0.6

    surfaceState.filePathToZoomLevelMap = filePathToZoomLevelMap
    val anotherSurfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    // Check the stored value is still on the same surface
    assertEquals(2, anotherSurfaceState.filePathToZoomLevelMap.size)
    TestCase.assertEquals(0.4, anotherSurfaceState.filePathToZoomLevelMap["path1:23"])
    TestCase.assertEquals(0.6, anotherSurfaceState.filePathToZoomLevelMap["path1:32"])

    // Check the values are same after getting another instance.
    assertEquals(filePathToZoomLevelMap, anotherSurfaceState.filePathToZoomLevelMap)
  }

  fun testSaveLoadSettingsWhenSameFileAndDifferentSurfaces() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    val filePathToZoomLevelMap = mutableMapOf<String, Double>()
    filePathToZoomLevelMap["path1:12"] = 0.1
    filePathToZoomLevelMap["path1:12"] = 0.2
    filePathToZoomLevelMap["path1:12"] = 0.4
    filePathToZoomLevelMap["path1:12"] = 0.2

    surfaceState.filePathToZoomLevelMap = filePathToZoomLevelMap

    val anotherSurfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    // Check the stored value is still on the same surface
    assertEquals(1, anotherSurfaceState.filePathToZoomLevelMap.size)
    TestCase.assertEquals(0.2, anotherSurfaceState.filePathToZoomLevelMap.values.last())

    // Check the values are same after getting another instance.
    assertEquals(filePathToZoomLevelMap, anotherSurfaceState.filePathToZoomLevelMap)
  }

  fun testSavingPathForBackedFile() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    val originalFile = myFixture.addFileToProject("path/to/origin/file", "").virtualFile
    val backedFile1 = BackedTestFile("path/to/backed/file1", originalFile)
    val backedFile2 = BackedTestFile("path/to/backed/file2", originalFile)

    val zoomController = createZoomController()
    zoomController.setScale(0.1)
    surfaceState.saveFileScale(myFixture.project, originalFile, zoomController)
    assertEquals(0.1, surfaceState.loadFileScale(myFixture.project, backedFile1, zoomController))
    assertEquals(0.1, surfaceState.loadFileScale(myFixture.project, backedFile2, zoomController))

    zoomController.setScale(3.0)
    surfaceState.saveFileScale(myFixture.project, backedFile1, zoomController)
    assertEquals(3.0, surfaceState.loadFileScale(myFixture.project, originalFile, zoomController))
    assertEquals(3.0, surfaceState.loadFileScale(myFixture.project, backedFile2, zoomController))

    zoomController.setScale(0.5)
    surfaceState.saveFileScale(myFixture.project, backedFile2, zoomController)
    assertEquals(0.5, surfaceState.loadFileScale(myFixture.project, originalFile, zoomController))
    assertEquals(0.5, surfaceState.loadFileScale(myFixture.project, backedFile1, zoomController))
  }

  fun testSavingPathForBackedFileWithZoomKeys() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState

    val originalFile = myFixture.addFileToProject("path/to/origin/file", "").virtualFile
    val backedFile1 = BackedTestFile("path/to/backed/file1", originalFile)
    val backedFile2 = BackedTestFile("path/to/backed/file2", originalFile)

    val zoomController = createZoomController()

    zoomController.setScale(0.1)
    surfaceState.saveFileScale(myFixture.project, originalFile, zoomController)
    assertEquals(0.1, surfaceState.loadFileScale(myFixture.project, backedFile1, zoomController))
    assertEquals(0.1, surfaceState.loadFileScale(myFixture.project, backedFile2, zoomController))

    zoomController.setScale(3.0)
    surfaceState.saveFileScale(myFixture.project, backedFile1, zoomController)
    assertEquals(3.0, surfaceState.loadFileScale(myFixture.project, originalFile, zoomController))
    assertEquals(3.0, surfaceState.loadFileScale(myFixture.project, backedFile2, zoomController))

    zoomController.storeId = "STORE_ID"
    zoomController.setScale(0.5)
    surfaceState.saveFileScale(myFixture.project, backedFile2, zoomController)
    assertEquals(0.5, surfaceState.loadFileScale(myFixture.project, originalFile, zoomController))
    assertEquals(0.5, surfaceState.loadFileScale(myFixture.project, backedFile1, zoomController))
  }

  fun testSaveOrganizationState() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState
    val file = myFixture.addFileToProject("path/to/file", "").virtualFile
    surfaceState.saveOrganizationGroupState(file, "method", true)
    val savedState = surfaceState.getOrganizationGroupState(file)
    assertEquals(mapOf("method" to true), savedState)
  }

  fun testUpdateOrganizationState() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState
    val file = myFixture.addFileToProject("path/to/file", "").virtualFile
    surfaceState.saveOrganizationGroupState(file, "method", true)
    val savedState = surfaceState.getOrganizationGroupState(file)
    assertEquals(mapOf("method" to true), savedState)
    surfaceState.saveOrganizationGroupState(file, "method", false)
    val updatedState = surfaceState.getOrganizationGroupState(file)
    assertEquals(mapOf("method" to false), updatedState)
  }

  fun testOrganizationStateForNewFile() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState
    val file = myFixture.addFileToProject("path/to/file", "").virtualFile
    val savedState = surfaceState.getOrganizationGroupState(file)
    assertEquals(emptyMap<String, Boolean>(), savedState)
  }

  fun testRevalidateOrganizationGroups() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState
    val file = myFixture.addFileToProject("path/to/file", "").virtualFile
    surfaceState.saveOrganizationGroupState(file, "method1", true)
    surfaceState.saveOrganizationGroupState(file, "method2", true)
    surfaceState.saveOrganizationGroupState(file, "method3", false)
    surfaceState.saveOrganizationGroupState(file, "method4", false)
    surfaceState.saveOrganizationGroupState(file, "method5", false)
    surfaceState.revalidateOrganizationGroups(
      file,
      setOf("method1", "method2", "method3", "method4", "method5"),
    )
    assertEquals(
      mapOf(
        "method1" to true,
        "method2" to true,
        "method3" to false,
        "method4" to false,
        "method5" to false,
      ),
      surfaceState.getOrganizationGroupState(file),
    )
    surfaceState.revalidateOrganizationGroups(file, setOf("method4", "method5"))
    assertEquals(
      mapOf("method4" to false, "method5" to false),
      surfaceState.getOrganizationGroupState(file),
    )
  }

  fun testRemoveFileFromProject() {
    val surfaceState = DesignSurfaceSettings.getInstance(project).surfaceState
    val file = myFixture.addFileToProject("path/to/file", "").virtualFile
    surfaceState.saveOrganizationGroupState(file, "method", true)
    assertTrue(surfaceState.organizationGroups.isNotEmpty())
    runWriteAction { file.delete(this) }
    surfaceState.revalidateOrganizationGroups()
    assertTrue(surfaceState.organizationGroups.isEmpty())
  }

  companion object {
    private fun createZoomController() =
      object : ZoomController {
        private var currentScale = 1.0
        override val scale: Double
          get() = currentScale

        override val screenScalingFactor: Double
          get() = 1.0

        override var storeId: String? = null

        override val minScale: Double
          get() = 0.1

        override val maxScale: Double
          get() = 10.0

        override fun setScale(scale: Double, x: Int, y: Int): Boolean {
          currentScale = scale
          return true
        }

        override fun zoomToFit(): Boolean = true

        override fun getFitScale(): Double = 1.0

        override fun zoom(type: ZoomType) = true

        override fun canZoomIn(): Boolean = true

        override fun canZoomOut(): Boolean = true

        override fun canZoomToFit(): Boolean = true

        override fun canZoomToActual(): Boolean = true
      }
  }
}
