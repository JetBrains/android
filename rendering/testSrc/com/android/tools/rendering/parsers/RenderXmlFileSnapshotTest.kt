/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.rendering.parsers

import com.android.resources.ResourceFolderType
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RenderXmlFileSnapshotTest {
  @JvmField @Rule val tmpFolder = TemporaryFolder()

  private lateinit var rootDisposable: Disposable
  private lateinit var project: Project

  @Before
  fun before() {
    rootDisposable = Disposer.newDisposable()
    project = MockProject(null, rootDisposable)
  }

  @After
  fun after() {
    Disposer.dispose(rootDisposable)
  }

  @Test
  fun testOnDiskXmlFileSnapshot() {
    val resFolder = tmpFolder.newFolder("res")
    val valuesFolder = File(resFolder, "layout")
    valuesFolder.mkdirs()
    val stringsFile = File(valuesFolder, "layout1.xml")
    stringsFile.writeBytes(
      // language=XML
      """
        <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:orientation="vertical">
          <Button
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Botón"
          />
        </LinearLayout>
      """
        .trimIndent()
        .toByteArray()
    )

    val xmlFile = RenderXmlFileSnapshot(project, stringsFile.absolutePath)

    assertEquals("layout1.xml", xmlFile.name)
    assertEquals("layout1.xml", xmlFile.relativePath)
    assertEquals(project, xmlFile.project)
    assertTrue(xmlFile.isValid)
    assertEquals(ResourceFolderType.LAYOUT, xmlFile.folderType)
    val linearLayoutTag = xmlFile.rootTag
    assertEquals("LinearLayout", linearLayoutTag.name)
    val buttonTag = linearLayoutTag.subTags[0]
    assertEquals("Button", buttonTag.name)
    // Checking that non-ascii characters work
    assertEquals(
      "Botón",
      buttonTag.getAttribute("text", "http://schemas.android.com/apk/res/android")?.value,
    )
  }

  @Test
  fun testInMemoryXmlFileSnapshot() {
    val xmlFile =
      RenderXmlFileSnapshot(
        project,
        "drawable.xml",
        ResourceFolderType.DRAWABLE,
        // language=XML
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
          android:width="24dp"
          android:height="24dp"
          android:viewportHeight="108.0"
          android:viewportWidth="108.0">
          <path
              android:fillColor="#26A69A"
              android:pathData="M0,0h108v108h-108z"
              android:strokeColor="#66FFFFFF"
              android:strokeWidth="0.8" />
          <path
              android:fillColor="#00000000"
              android:pathData="M19,0L19,108"
              android:strokeColor="#33FFFFFF"
              android:strokeWidth="0.8" />
        </vector>
      """
          .trimIndent(),
      )
    assertEquals("drawable.xml", xmlFile.name)
    assertEquals("drawable.xml", xmlFile.relativePath)
    assertEquals(project, xmlFile.project)
    assertTrue(xmlFile.isValid)
    assertEquals(ResourceFolderType.DRAWABLE, xmlFile.folderType)
    val vectorTag = xmlFile.rootTag
    assertEquals("vector", vectorTag.name)
    val pathTag = vectorTag.subTags[0]
    assertEquals("path", pathTag.name)
    // Checking that non-ascii characters work
    assertEquals(
      "M0,0h108v108h-108z",
      pathTag.getAttribute("pathData", "http://schemas.android.com/apk/res/android")?.value,
    )
  }
}
