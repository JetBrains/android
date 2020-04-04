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
package com.android.tools.idea.layoutinspector.common

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.getTypedArgument
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import javax.swing.JPopupMenu

class ViewContextMenuFactoryTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private val mockActionManager: ActionManager = mock()
  private val mockPopupMenu: ActionPopupMenu = mock()
  private val source: JComponent = mock()
  private val popupMenuComponent: JPopupMenu = mock()
  private var createdGroup: ActionGroup? = null
  private val inspectorModel: InspectorModel = model {
    view(ROOT, viewId = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "rootId")) {
      view(VIEW1)
      view(VIEW2, qualifiedName = "viewName") {
        view(VIEW3, textValue = "myText")
      }
    }
  }

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(ActionManager::class.java, mockActionManager, disposableRule.disposable)
    `when`(mockActionManager.createActionPopupMenu(any(), any())).thenAnswer { invocation ->
      createdGroup = invocation.getTypedArgument(1)
      mockPopupMenu
    }
    `when`(mockPopupMenu.component).thenReturn(popupMenuComponent)
  }

  @Test
  fun testEmptyModel() {
    showViewContextMenu(listOf(), model { }, source, 0, 0)
    assertThat(createdGroup).isNull()
  }

  @Test
  fun testNoViews() {
    inspectorModel.root.flatten().filter { it.drawId == VIEW1 || it.drawId == VIEW3 }.forEach { it.visible = false }
    showViewContextMenu(listOf(), inspectorModel, source, 123, 456)
    val createdAction = createdGroup?.getChildren(null)?.single()
    assertThat(createdAction?.templateText).isEqualTo("Show All")
    createdAction?.actionPerformed(mock())
    assertThat(inspectorModel.root.flatten().all { it.visible }).isTrue()

    verify(popupMenuComponent).show(source, 123, 456)
  }

  @Test
  fun testOneView() {
    showViewContextMenu(listOf(inspectorModel.root.flatten().first { it.drawId == VIEW2 }), inspectorModel, source, 0, 0)
    assertThat(createdGroup?.getChildren(null)?.map { it.templateText })
      .containsExactly("Hide Subtree", "Show Only Subtree", "Show Only Parents", "Show All").inOrder()

    val hideSubtree = createdGroup?.getChildren(null)?.get(0)!!
    hideSubtree.actionPerformed(mock())

    assertThat(inspectorModel.root.flatten().filter { it.visible }.map { it.drawId }).containsExactly(ROOT, VIEW1, -1L)

    inspectorModel.root.flatten().forEach { it.visible = true }
    val showOnlySubtree = createdGroup?.getChildren(null)?.get(1)!!
    showOnlySubtree.actionPerformed(mock())

    assertThat(inspectorModel.root.flatten().filter { it.visible }.map { it.drawId }).containsExactly(VIEW2, VIEW3)

    inspectorModel.root.flatten().forEach { it.visible = true }
    val showOnlyParents = createdGroup?.getChildren(null)?.get(2)!!
    showOnlyParents.actionPerformed(mock())

    assertThat(inspectorModel.root.flatten().filter { it.visible }.map { it.drawId }).containsExactly(ROOT, VIEW2, -1L)
  }

  @Test
  fun testMultipleViews() {
    showViewContextMenu(inspectorModel.root.flatten().filter { it.drawId in listOf(ROOT, VIEW2, VIEW3) },
                        inspectorModel, source, 0, 0)
    assertThat(createdGroup?.getChildren(null)?.map { it.templateText })
      .containsExactly("Select View", "Hide Subtree", "Show Only Subtree", "Show Only Parents", "Show All").inOrder()

    val selectView = createdGroup?.getChildren(null)?.get(0)!!
    val views = (selectView as DropDownAction).getChildren(null)
    assertThat(views.map { it.templateText }).containsExactly("rootId", "viewName", "myText").inOrder()

    views[0].actionPerformed(mock())
    assertThat(inspectorModel.selection).isEqualTo(inspectorModel.root.flatten().first { it.drawId == ROOT })
    views[1].actionPerformed(mock())
    assertThat(inspectorModel.selection).isEqualTo(inspectorModel.root.flatten().first { it.drawId == VIEW2 })
    views[2].actionPerformed(mock())
    assertThat(inspectorModel.selection).isEqualTo(inspectorModel.root.flatten().first { it.drawId == VIEW3 })
  }
}