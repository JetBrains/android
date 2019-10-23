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

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.actionSystem.AnActionEvent
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class AddGlobalActionTest : NavTestCase() {
  fun testRun() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      AddGlobalAction(model.surface, model.find("f2")!!).actionPerformed(mock(AnActionEvent::class.java))
      val root = model.components[0]
      val action = root.children.first { it.isAction }
      assertEquals(model.find("f2")!!, action.actionDestination)
      assertSameElements(model.surface.selectionModel.selection, action)
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
                                 .setActionInfo(NavActionInfo.newBuilder()
                                                  .setCountFromSource(1)
                                                  .setCountSame(1)
                                                  .setCountToDestination(1)
                                                  .setType(NavActionInfo.ActionType.GLOBAL))
                                 .setSource(NavEditorEvent.Source.CONTEXT_MENU).build())
    }
  }
}
