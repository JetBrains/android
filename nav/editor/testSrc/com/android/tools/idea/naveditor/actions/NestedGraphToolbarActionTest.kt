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

import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.actionSystem.AnActionEvent
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class NestedGraphToolbarActionTest : NavTestCase() {

  /**
   *  Reparent fragments 2 and 3 into a new nested navigation
   *  After the reparent:
   *  The action from fragment1 to fragment2 should point to the new navigation
   *  The exit action from fragment4 to fragment2 should also point to the new navigation
   *  The action from fragment2 to fragment3 should remain unchanged
   */
  fun testNestedGraphToolbarAction() {
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
    val action = NestedGraphToolbarAction(surface)

    TestNavUsageTracker.create(model).use { tracker ->
      action.actionPerformed(Mockito.mock(AnActionEvent::class.java))

      val navigation1 = model.find("navigation1")!!
      val root = model.components[0]

      assertSameElements(navigation1.children.map { it.id }, "fragment4")
      assertSameElements(root.children.map { it.id }, "fragment1", "fragment2", "fragment3", "navigation1")

      verifyNoMoreInteractions(tracker)

      val fragment1 = model.find("fragment1")!!
      val fragment2 = model.find("fragment2")!!
      val fragment3 = model.find("fragment3")!!
      val fragment4 = model.find("fragment4")!!
      surface.selectionModel.setSelection(listOf(fragment2, fragment3))
      action.actionPerformed(Mockito.mock(AnActionEvent::class.java))

      val newNavigation = model.find("navigation")!!

      assertSameElements(newNavigation.children.map { it.id }, "fragment2", "fragment3")
      assertSameElements(root.children.map { it.id }, "fragment1", "navigation1", "navigation")
      assertSameElements(navigation1.children.map { it.id }, "fragment4")

      assertEquals(newNavigation.startDestinationId, "fragment2")

      val action1 = model.find("action1")!!
      assertEquals(action1.parent, fragment1)
      assertEquals(action1.actionDestinationId, "navigation")

      val action2 = model.find("action2")!!
      assertEquals(action2.parent, fragment2)
      assertEquals(action2.actionDestinationId, "fragment3")

      val action3 = model.find("action3")!!
      assertEquals(action3.parent, fragment4)
      assertEquals(action3.actionDestinationId, "navigation")

      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.CREATE_NESTED_GRAPH)
                                 .setSource(NavEditorEvent.Source.TOOLBAR)
                                 .build())
    }
  }
}