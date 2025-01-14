/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.action

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.editors.strings.model.StringResourceRepository
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class RemoveKeysActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val table: StringResourceTable = mock()
  private val model: StringResourceTableModel = mock()
  private val repository: StringResourceRepository = mock()
  private val removeKeysAction = RemoveKeysAction()
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
        .build()
    event = TestActionEvent.createTestEvent(dataContext)

    whenever(stringResourceEditor.panel).thenReturn(panel)
    whenever(panel.table).thenReturn(table)
    whenever(table.model).thenReturn(model)
    whenever(model.getKey(anyInt())).thenAnswer {
      StringResourceKey(it.getArgument<Int>(0).toString())
    }
    whenever(model.repository).thenReturn(repository)
    whenever(repository.getItems(any())).thenAnswer {
      resourceItems[it.getArgument<StringResourceKey>(0).name.toInt()]
    }
  }

  @Test
  fun doUpdate_disabled_tooFew() {
    // Do not select any rows.
    whenever(table.hasSelectedCell()).thenReturn(false)
    removeKeysAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun doUpdate_enabled() {
    whenever(table.hasSelectedCell()).thenReturn(true)
    removeKeysAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionPerformed_nothingSelected() {
    whenever(table.selectedModelRowIndex).thenReturn(-1)

    removeKeysAction.actionPerformed(event)
  }

  @Test
  fun actionPerformed() {
    val index = 3
    whenever(table.selectedModelRowIndex).thenReturn(index)

    removeKeysAction.actionPerformed(event)

    verify(panel).deleteSelectedKeys()
  }

  companion object {
    /**
     * Creates a bunch of resource items to use in the test.
     *
     * These look like "resource 1a", "resource 1b", ... "resource 2a", "resource 2b", ... ...
     */
    private val resourceItems: List<List<ResourceItem>> =
      IntRange(0, 10).map { index ->
        CharRange('a', 'e').map { char -> createFakeStringResourceItem("resource $index$char") }
      }

    private fun createFakeStringResourceItem(resourceName: String): ResourceItem {
      return object : ResourceItem {
        override fun getName(): String = resourceName

        override fun getType(): ResourceType = ResourceType.STRING

        override fun getNamespace(): ResourceNamespace = ResourceNamespace.RES_AUTO

        override fun getLibraryName(): String? = null

        override fun getResourceValue(): ResourceValue? = null

        override fun getSource(): PathString? = null

        override fun isFileBased(): Boolean = false

        override fun getConfiguration(): FolderConfiguration = FolderConfiguration()
      }
    }
  }
}
