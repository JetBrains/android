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

  /**
   *  Reparent fragments 2 and 3 into a new nested navigation
   *  After the reparent:
   *  The action from fragment1 to fragment2 should point to the new navigation
   *  The exit action from fragment4 to fragment2 should also point to the new navigation
   *  The action from fragment2 to fragment3 should remain unchanged
   */
  fun testAddToNewGraphAction() {
    val model = model("nav.xml") {
      navigation {
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

    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!

    val surface = model.surface as NavDesignSurface
    val action = AddToNewGraphAction(surface, listOf(fragment2, fragment3))
    action.actionPerformed(null)

    assertEquals(
        "NlComponent{tag=<navigation>, instance=0}\n" +
            "    NlComponent{tag=<fragment>, instance=1}\n" +
            "        NlComponent{tag=<action>, instance=2}\n" +
            "    NlComponent{tag=<navigation>, instance=3}\n" +
            "        NlComponent{tag=<fragment>, instance=4}\n" +
            "            NlComponent{tag=<action>, instance=5}\n" +
            "    NlComponent{tag=<navigation>, instance=6}\n" +
            "        NlComponent{tag=<fragment>, instance=7}\n" +
            "            NlComponent{tag=<action>, instance=8}\n" +
            "        NlComponent{tag=<fragment>, instance=9}", NlTreeDumper().toTree(model.components)
    )

    val root = surface.currentNavigation
    val fragment1 = model.find("fragment1")!!
    assertEquals(fragment1.parent, root)

    val navigation1 = model.find("navigation1")
    assertEquals(navigation1?.parent, root)

    val newNavigation = model.find("navigation")
    assertEquals(newNavigation?.parent, root)

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
        navigation("navigation1") {
          fragment("fragment4") {
            action("action3", "fragment2")
          }
        }
      }
    }

    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!
    val navigation1 = model.find("navigation1")!!

    val surface = model.surface as NavDesignSurface
    val action = AddToExistingGraphAction(surface, listOf(fragment2, fragment3), "navigation", navigation1)
    action.actionPerformed(null)

    assertEquals(
        "NlComponent{tag=<navigation>, instance=0}\n" +
            "    NlComponent{tag=<fragment>, instance=1}\n" +
            "        NlComponent{tag=<action>, instance=2}\n" +
            "    NlComponent{tag=<navigation>, instance=3}\n" +
            "        NlComponent{tag=<fragment>, instance=4}\n" +
            "            NlComponent{tag=<action>, instance=5}\n" +
            "        NlComponent{tag=<fragment>, instance=6}\n" +
            "            NlComponent{tag=<action>, instance=7}\n" +
            "        NlComponent{tag=<fragment>, instance=8}", NlTreeDumper().toTree(model.components)
    )

    val root = surface.currentNavigation
    val fragment1 = model.find("fragment1")!!
    assertEquals(fragment1.parent, root)

    assertEquals(fragment2.parent, navigation1)
    assertEquals(fragment3.parent, navigation1)

    val fragment4 = model.find("fragment4")!!
    assertEquals(fragment4.parent, navigation1)

    val action1 = model.find("action1")!!
    assertEquals(action1.parent, fragment1)
    assertEquals(action1.actionDestinationId, "navigation1")

    val action2 = model.find("action2")!!
    assertEquals(action2.parent, fragment2)
    assertEquals(action2.actionDestinationId, "fragment3")

    val action3 = model.find("action3")!!
    assertEquals(action3.parent, fragment4)
    assertEquals(action3.actionDestinationId, "fragment2")
  }
}