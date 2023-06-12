/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertEquals

class LayoutQualifierDropdownMenuTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var context: DataContext
  private lateinit var file: VirtualFile
  private lateinit var surface: NlDesignSurface

  @Before
  fun setUp() {
    surface = Mockito.mock(NlDesignSurface::class.java)

    file = projectRule.fixture.addFileToProject("res/layout/layout1.xml", "").virtualFile
    val manager = ConfigurationManager.getOrCreateInstance(projectRule.module)
    val config = manager.getConfiguration(file)

    whenever(surface.configurations).thenReturn(ImmutableList.of(config))
    context = DataContext { if (DESIGN_SURFACE.`is`(it)) surface else null }
  }

  @Test
  fun checkActionsWithNoOtherVariantFiles() {
    val action = LayoutQualifierDropdownMenu(file)
    action.updateActions(context)
    val expected = """layout1.xml
    ✔ layout1.xml
    ------------------------------------------------------
    Create Landscape Qualifier
    Create Tablet Qualifier
    Add Resource Qualifier
"""
    assertEquals(expected, prettyPrintActions(action))
  }

  @Test
  fun checkActionsWithExistingLandscapeVariationBut() {
    projectRule.fixture.addFileToProject("res/layout-land/layout1.xml", "")
    waitForResourceRepositoryUpdates(projectRule.module)
    val action = LayoutQualifierDropdownMenu(file)
    action.updateActions(context)
    val expected = """layout1.xml
    ✔ layout1.xml
    land/layout1.xml
    ------------------------------------------------------
    Create Tablet Qualifier
    Add Resource Qualifier
"""
    assertEquals(expected, prettyPrintActions(action))
  }

  @Test
  fun checkActionsWithExistingLandscapeAndTabletVariation() {
    projectRule.fixture.addFileToProject("res/layout-land/layout1.xml", "")
    projectRule.fixture.addFileToProject("res/layout-sw600dp/layout1.xml", "")
    waitForResourceRepositoryUpdates(projectRule.module)
    val action = LayoutQualifierDropdownMenu(file)
    action.updateActions(context)
    val expected = """layout1.xml
    ✔ layout1.xml
    land/layout1.xml
    sw600dp/layout1.xml
    ------------------------------------------------------
    Add Resource Qualifier
"""
    assertEquals(expected, prettyPrintActions(action))
  }

  @Test
  fun checkActionTitle() {
    val file2 = projectRule.fixture.addFileToProject("res/layout-land/layout1.xml", "").virtualFile
    val file3 = projectRule.fixture.addFileToProject("res/layout-sw600dp/layout1.xml", "").virtualFile
    waitForResourceRepositoryUpdates(projectRule.module)

    val presentation = Presentation()
    val event = AnActionEvent(null, context, ActionPlaces.UNKNOWN, presentation, ActionManager.getInstance(), 0)

    val action1 = LayoutQualifierDropdownMenu(file)
    action1.update(event)
    assertEquals("layout1.xml", presentation.text)

    val action2 = LayoutQualifierDropdownMenu(file2)
    action2.update(event)
    assertEquals("land/layout1.xml", presentation.text)

    val action3 = LayoutQualifierDropdownMenu(file3)
    action3.update(event)
    assertEquals("sw600dp/layout1.xml", presentation.text)
  }

  @Test
  fun createActionCreatedByVariantFile() {
    val variantFile = projectRule.fixture.addFileToProject("res/layout-land/layout1.xml", "")
    projectRule.fixture.addFileToProject("res/layout-sw600dp/layout1.xml", "")
    waitForResourceRepositoryUpdates(projectRule.module)

    val landConfig =
      ConfigurationManager.getOrCreateInstance(projectRule.module).getConfiguration(variantFile.virtualFile)
    whenever(surface.configurations).thenReturn(ImmutableList.of(landConfig))
    val action = LayoutQualifierDropdownMenu(variantFile.virtualFile)
    action.updateActions(context)
    val expected = """land/layout1.xml
    layout1.xml
    ✔ land/layout1.xml
    sw600dp/layout1.xml
    ------------------------------------------------------
    Add Resource Qualifier
"""
    assertEquals(expected, prettyPrintActions(action))
  }
}
