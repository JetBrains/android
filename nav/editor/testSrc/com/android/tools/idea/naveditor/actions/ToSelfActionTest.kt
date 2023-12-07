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
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.effectiveDestination
import com.android.tools.idea.naveditor.model.isAction
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.testFramework.TestActionEvent
import org.mockito.Mockito

class ToSelfActionTest : NavTestCase() {
  fun testRun() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("f1")
        fragment("f2")
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      val f2 = model.find("f2")!!
      ToSelfAction(f2).actionPerformed(TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) model.surface else null })
      val action = f2.children.first { it.isAction }
      assertEquals(f2, action.effectiveDestination)
      assertSameElements(model.surface.selectionModel.selection, action)
      Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                         .setType(NavEditorEvent.NavEditorEventType.CREATE_ACTION)
                                         .setActionInfo(NavActionInfo.newBuilder()
                                                          .setCountFromSource(1)
                                                          .setCountToDestination(1)
                                                          .setCountSame(1)
                                                          .setType(NavActionInfo.ActionType.SELF))
                                         .setSource(NavEditorEvent.Source.CONTEXT_MENU).build())
    }
  }

}