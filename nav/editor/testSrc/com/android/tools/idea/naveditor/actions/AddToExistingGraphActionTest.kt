/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.ChangeType
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNavEditor
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.TestActionEvent
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class AddToExistingGraphActionTest : NavTestCase() {
  /**
   *  Reparent fragments 2 and 3 into an existing navigation
   *  After the reparent:
   *  The action from fragment1 to fragment2 should point to the existing navigation
   *  The exit action from fragment4 to fragment2 should remain unchanged
   *  The action from fragment2 to fragment3 should remain unchanged
   */
  fun testAddToExistingGraphAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("action1", "fragment2")
        }
        fragment("fragment2") {
          action("action2", "fragment3")
        }
        fragment("fragment3")
        navigation("navigation1", startDestination = "fragment4") {
          fragment("fragment4") {
            action("action3", "fragment2")
          }
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf())
    val root = model.treeReader.components[0]
    val navigation1 = model.treeReader.find("navigation1")!!
    val action = AddToExistingGraphAction("navigation", navigation1)
    val event = TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) surface else null }
    TestNavUsageTracker.create(model).use { tracker ->
      action.actionPerformed(event)

      verifyNoMoreInteractions(tracker)
      assertSameElements(navigation1.children.map { it.id }, "fragment4")
      assertSameElements(root.children.map { it.id }, "fragment1", "fragment2", "fragment3", "navigation1")

      val fragment2 = model.treeReader.find("fragment2")!!
      val fragment3 = model.treeReader.find("fragment3")!!

      surface.selectionModel.setSelection(listOf(fragment2, fragment3))
      action.actionPerformed(event)

      assertSameElements(navigation1.children.map { it.id }, "fragment4", "fragment2", "fragment3")
      assertSameElements(root.children.map { it.id }, "fragment1", "navigation1")

      assertEquals(navigation1.startDestinationId, "fragment4")

      val action1 = model.treeReader.find("action1")!!
      assertEquals(action1.parent?.id, "fragment1")
      assertEquals(action1.actionDestinationId, "navigation1")

      val action2 = model.treeReader.find("action2")!!
      assertEquals(action2.parent, fragment2)
      assertEquals(action2.actionDestinationId, "fragment3")

      val action3 = model.treeReader.find("action3")!!
      assertEquals(action3.parent?.id, "fragment4")
      assertEquals(action3.actionDestinationId, "fragment2")

      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.MOVE_TO_GRAPH)
                                 .setSource(NavEditorEvent.Source.CONTEXT_MENU)
                                 .build())
    }
  }

  fun testUndo() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
        fragment("f3")
        navigation("subnav") {
          fragment("f4")
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    surface.scene?.getSceneComponent("f1")?.setPosition(100, 200)
    surface.scene?.getSceneComponent("f2")?.setPosition(400, 500)
    surface.selectionModel.setSelection(listOf(model.treeReader.find("f1")!!, model.treeReader.find("f2")!!))
    surface.getSceneManager(model)?.save(listOf(surface.scene?.getSceneComponent("f1")!!, surface.scene?.getSceneComponent("f2")!!))

    val action = AddToExistingGraphAction("existing", model.treeReader.find("subnav")!!)
    action.actionPerformed(TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) surface else null })
    UndoManager.getInstance(project).undo(TestNavEditor(model.virtualFile, project))
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(ChangeType.EDIT)

    assertEquals(100, surface.scene?.getSceneComponent("f1")?.drawX)
    assertEquals(200, surface.scene?.getSceneComponent("f1")?.drawY)
    assertEquals(400, surface.scene?.getSceneComponent("f2")?.drawX)
    assertEquals(500, surface.scene?.getSceneComponent("f2")?.drawY)

    assertEquals(model.treeReader.components[0], model.treeReader.find("f1")?.parent)
    assertEquals(model.treeReader.components[0], model.treeReader.find("f2")?.parent)
    assertEquals(model.treeReader.components[0], model.treeReader.find("f3")?.parent)
    assertEquals(model.treeReader.find("subnav")!!, model.treeReader.find("f4")?.parent)
  }
}