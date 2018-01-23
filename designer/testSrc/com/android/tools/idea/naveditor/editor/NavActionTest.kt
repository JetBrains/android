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
import com.android.tools.idea.naveditor.actions.AddGlobalAction
import com.android.tools.idea.naveditor.actions.ReturnToSourceAction
import com.android.tools.idea.naveditor.actions.StartDestinationAction
import com.android.tools.idea.naveditor.actions.ToSelfAction
import com.android.tools.idea.naveditor.model.*

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
        "NlComponent{tag=<navigation>, instance=0}\n" +
            "    NlComponent{tag=<fragment>, instance=1}\n" +
            "    NlComponent{tag=<action>, instance=2}", NlTreeDumper().toTree(model.components)
    )
    val globalAction = model.find("action")!!

    assertNotNull(globalAction.parent)
    assertNull(globalAction.parent?.id)
    assertEquals(globalAction.actionDestinationId, "fragment1")
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
        "NlComponent{tag=<navigation>, instance=0}\n" +
            "    NlComponent{tag=<fragment>, instance=1}\n" +
            "        NlComponent{tag=<action>, instance=2}", NlTreeDumper().toTree(model.components)
    )
    val returnToSourceAction = model.find("action")!!

    assertEquals(component.id, returnToSourceAction.popUpTo)
    assertTrue(returnToSourceAction.inclusive)
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
        "NlComponent{tag=<navigation>, instance=0}\n" +
            "    NlComponent{tag=<fragment>, instance=1}", NlTreeDumper().toTree(model.components)
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
        "NlComponent{tag=<navigation>, instance=0}\n" +
            "    NlComponent{tag=<fragment>, instance=1}\n" +
            "        NlComponent{tag=<action>, instance=2}", NlTreeDumper().toTree(model.components)
    )

    val selfAction = model.find("action")!!

    assertTrue(selfAction.isSelfAction)
  }
}