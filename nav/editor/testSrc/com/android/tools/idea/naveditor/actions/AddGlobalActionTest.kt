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
import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class AddGlobalActionTest {

  @get:Rule
  val navRule = NavEditorRule()

  @Test
  fun testRun() {
    val model = navRule.model("nav.xml") {
      navigation {
        fragment("f1")
        fragment("f2")
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      AddGlobalAction(model.treeReader.find("f2")!!).actionPerformed(TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) model.surface else null })
      val root = model.treeReader.components[0]
      val action = root.children.first { it.isAction }
      assertThat(action.actionDestination).isEqualTo(model.treeReader.find("f2"))
      assertThat(model.surface.selectionModel.selection).containsExactly(action)
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
