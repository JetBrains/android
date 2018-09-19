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
package com.android.tools.idea.naveditor.editor

import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.actions.*
import com.android.tools.idea.naveditor.model.*
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Tests for actions used by the nav editor
 */
class NavActionTest : NavTestCase() {
  fun testAddGlobalAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val action = AddGlobalAction(model.surface, component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
            NlComponent{tag=<action>, instance=2}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components))

    val globalAction = model.find("action_global_fragment1")!!

    assertNotNull(globalAction.parent)
    assertNull(globalAction.parent?.id)
    assertEquals(globalAction.actionDestinationId, "fragment1")

    assertEquals(listOf(globalAction), model.surface.selectionModel.selection)
  }

  fun testReturnToSourceAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val action = ReturnToSourceAction(model.surface, component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )
    val returnToSourceAction = model.find("action_fragment1_pop")!!

    assertEquals(component.id, returnToSourceAction.popUpTo)
    assertTrue(returnToSourceAction.inclusive)
    assertEquals(listOf(returnToSourceAction), model.surface.selectionModel.selection)

    FileDocumentManager.getInstance().saveAllDocuments()
    val result = String(model.virtualFile.contentsToByteArray())
    Truth.assertThat(result.replace("\n *".toRegex(), "\n")).contains(
      """
        <action
        android:id="@+id/action_fragment1_pop"
        app:popUpTo="@id/fragment1"
        app:popUpToInclusive="true" />
      """.trimIndent())
  }

  fun testStartDestinationAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!
    val action = StartDestinationAction(component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    assert(component.isStartDestination)
  }

  fun testToSelfAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val action = ToSelfAction(model.surface, component)
    action.actionPerformed(null)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
                NlComponent{tag=<action>, instance=2}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    val selfAction = model.find("action_fragment1_self")!!
    assertTrue(selfAction.isSelfAction)
    assertEquals(listOf(selfAction), model.surface.selectionModel.selection)

    FileDocumentManager.getInstance().saveAllDocuments()
    val result = String(model.virtualFile.contentsToByteArray())
    Truth.assertThat(result.replace("\n *".toRegex(), "\n")).contains(
      """
        <action
        android:id="@+id/action_fragment1_self"
        app:destination="@id/fragment1" />
      """.trimIndent())
  }

  fun testSelectNextAction() {
    val model = model("nav.xml") {
      navigation {
        action("action1")
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!

    val surface = model.surface as NavDesignSurface
    val action = SelectNextAction(surface)

    action.actionPerformed(null)
    assertEquals(listOf(fragment1), model.surface.selectionModel.selection)

    action.actionPerformed(null)
    assertEquals(listOf(fragment2), model.surface.selectionModel.selection)

    action.actionPerformed(null)
    assertEquals(listOf(fragment3), model.surface.selectionModel.selection)

    action.actionPerformed(null)
    assertEquals(listOf(fragment1), model.surface.selectionModel.selection)
  }


  fun testSelectPreviousAction() {
    val model = model("nav.xml") {
      navigation {
        action("action1")
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!

    val surface = model.surface as NavDesignSurface
    val action = SelectPreviousAction(surface)

    action.actionPerformed(null)
    assertEquals(listOf(fragment3), model.surface.selectionModel.selection)

    action.actionPerformed(null)
    assertEquals(listOf(fragment2), model.surface.selectionModel.selection)

    action.actionPerformed(null)
    assertEquals(listOf(fragment1), model.surface.selectionModel.selection)

    action.actionPerformed(null)
    assertEquals(listOf(fragment3), model.surface.selectionModel.selection)
  }

  fun testSelectAllAction() {
    val model = model("nav.xml") {
      navigation {
        action("action1")
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!

    val surface = model.surface as NavDesignSurface
    val action = SelectAllAction(surface)

    action.actionPerformed(null)
    assertEquals(listOf(fragment1, fragment2, fragment3), model.surface.selectionModel.selection)
  }
}