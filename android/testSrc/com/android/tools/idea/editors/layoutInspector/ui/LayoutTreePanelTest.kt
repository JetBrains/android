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
package com.android.tools.idea.editors.layoutInspector.ui

import com.android.tools.idea.editors.layoutInspector.LayoutFileData
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import java.nio.file.Paths

/**
 * Test Sub-view feature in layout inspector
 */
class LayoutTreePanelTest : AndroidTestCase() {

  private lateinit var myContext: LayoutInspectorContext
  private lateinit var myPanel: LayoutTreePanel
  private lateinit var myTestData: LayoutFileData

  override fun setUp() {
    super.setUp()
    val testFile = Paths.get(AndroidTestBase.getTestDataPath(), "editors/layoutInspector/LayoutCapture.li").toFile()
    val layoutFile = LocalFileSystem.getInstance().findFileByIoFile(testFile)
    myTestData = LayoutFileData(layoutFile!!)

    myContext = LayoutInspectorContext(myTestData, project)
    myPanel = LayoutTreePanel()
    myPanel.setSize(800, 800)
    myPanel.setToolContext(myContext)
  }

  //check back panel is not visible by default
  @Test
  fun testDefaultBackPanel() {
    assertFalse(myPanel.backPanel.isVisible)
  }

  //check back panel is visible after diving into a sub view
  // then back to invisible after it is removed.
  @Test
  fun testBackPanelVisible() {
    val subViewRoot = myContext.root!!.children[0]
    myContext.subviewList.add(subViewRoot)

    assertTrue(myPanel.backPanel.isVisible)
    assertEquals(myContext.subviewList.last(), subViewRoot)

    myContext.subviewList.remove(subViewRoot)
    assertFalse(myPanel.backPanel.isVisible)
  }
}