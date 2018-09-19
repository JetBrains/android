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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNlEditor
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager

class AddToNewGraphActionTest : NavTestCase() {
  /**
   *  Reparent fragments 2 and 3 into a new nested navigation
   *  After the reparent:
   *  The action from fragment1 to fragment2 should point to the new navigation
   *  The exit action from fragment4 to fragment2 should also point to the new navigation
   *  The action from fragment2 to fragment3 should remain unchanged
   */
  fun testAddToNewGraphAction() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1") {
          action("action1", "fragment2")
        }
        fragment("fragment2") {
          action("action2", "fragment3")
        }
        fragment("fragment3")
        navigation("navigation1") {
          fragment("fragment4") {
            action("action3", "fragment2")
          }
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf())
    val action = AddToNewGraphAction(surface)

    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
            NlComponent{tag=<fragment>, instance=3}
                NlComponent{tag=<action>, instance=4}
            NlComponent{tag=<fragment>, instance=5}
            NlComponent{tag=<navigation>, instance=6}
                NlComponent{tag=<fragment>, instance=7}
                    NlComponent{tag=<action>, instance=8}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!
    surface.selectionModel.setSelection(listOf(fragment2, fragment3))
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
            NlComponent{tag=<navigation>, instance=3}
                NlComponent{tag=<fragment>, instance=4}
                    NlComponent{tag=<action>, instance=5}
            NlComponent{tag=<navigation>, instance=6}
                NlComponent{tag=<fragment>, instance=7}
                    NlComponent{tag=<action>, instance=8}
                NlComponent{tag=<fragment>, instance=9}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    val root = surface.currentNavigation
    val fragment1 = model.find("fragment1")!!
    assertEquals(fragment1.parent, root)

    val navigation1 = model.find("navigation1")
    assertEquals(navigation1?.parent, root)

    val newNavigation = model.find("navigation")
    assertEquals(newNavigation?.parent, root)
    assertEquals(newNavigation?.startDestinationId, "fragment2")

    assertEquals(fragment2.parent, newNavigation)
    assertEquals(fragment3.parent, newNavigation)

    val fragment4 = model.find("fragment4")!!
    assertEquals(fragment4.parent, navigation1)

    val action1 = model.find("action1")!!
    assertEquals(action1.parent, fragment1)
    assertEquals(action1.actionDestinationId, "navigation")

    val action2 = model.find("action2")!!
    assertEquals(action2.parent, fragment2)
    assertEquals(action2.actionDestinationId, "fragment3")

    val action3 = model.find("action3")!!
    assertEquals(action3.parent, fragment4)
    assertEquals(action3.actionDestinationId, "navigation")
  }

  fun testUndo() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
        fragment("f3")
      }
    }

    val surface = model.surface as NavDesignSurface
    surface.scene?.getSceneComponent("f1")?.setPosition(100, 200)
    surface.scene?.getSceneComponent("f2")?.setPosition(400, 500)
    surface.selectionModel.setSelection(listOf(model.find("f1"), model.find("f2")))
    surface.sceneManager?.save(listOf(surface.scene?.getSceneComponent("f1"), surface.scene?.getSceneComponent("f2")))

    val action = AddToNewGraphAction(surface)
    action.actionPerformed(null)
    UndoManager.getInstance(project).undo(TestNlEditor(model.virtualFile, project))
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertEquals(100, surface.scene?.getSceneComponent("f1")?.drawX)
    assertEquals(200, surface.scene?.getSceneComponent("f1")?.drawY)
    assertEquals(400, surface.scene?.getSceneComponent("f2")?.drawX)
    assertEquals(500, surface.scene?.getSceneComponent("f2")?.drawY)
  }
}