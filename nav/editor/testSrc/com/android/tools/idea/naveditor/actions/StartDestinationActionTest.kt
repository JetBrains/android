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

import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.isStartDestination
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class StartDestinationActionTest : NavTestCase() {
  fun testStartDestinationAction() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1")
      }
    }

    val component = model.treeReader.find("fragment1")!!
    val action = StartDestinationAction(component)
    action.actionPerformed(Mockito.mock(AnActionEvent::class.java))

    AndroidTestCase.assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
      """.trimIndent(),
      NlTreeDumper().toTree(model.treeReader.components)
    )

    assert(component.isStartDestination)
  }
}