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

import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.uibuilder.editor.LayoutQualifierDropdownMenu
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.DataContext
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

  @Before
  fun setUp() {
    val surface = Mockito.mock(NlDesignSurface::class.java)

    val file = projectRule.fixture.addFileToProject("res/layout/layout1.xml", "")
    val manager = ConfigurationManager.getOrCreateInstance(projectRule.module)
    val config = manager.getConfiguration(file.virtualFile)

    Mockito.`when`(surface.configurations).thenReturn(ImmutableList.of(config))
    context = DataContext { if (DESIGN_SURFACE.`is`(it)) surface else null }
  }

  @Test
  fun checkActionsWithNoOtherVariantFiles() {
    val action = LayoutQualifierDropdownMenu("")
    action.updateActions(context)
    val expected = """
    layout1.xml
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
    val action = LayoutQualifierDropdownMenu("")
    action.updateActions(context)
    val expected = """
    layout1.xml
    layout-land/layout1.xml
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
    val action = LayoutQualifierDropdownMenu("")
    action.updateActions(context)
    val expected = """
    layout1.xml
    layout-land/layout1.xml
    layout-sw600dp/layout1.xml
    ------------------------------------------------------
    Add Resource Qualifier
"""
    assertEquals(expected, prettyPrintActions(action))
  }
}
