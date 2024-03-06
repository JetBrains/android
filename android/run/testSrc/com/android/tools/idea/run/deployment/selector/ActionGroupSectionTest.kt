/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.idea.run.deployment.Heading
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

class ActionGroupSectionTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun multipleEmptySections() {
    val group =
      createActionGroup(
        ActionGroupSection(null, emptyList()),
        ActionGroupSection(Heading.RUNNING_DEVICES_ID, emptyList()),
        ActionGroupSection(Heading.AVAILABLE_DEVICES_ID, emptyList()),
      )
    assertThat(group.getChildren(null)).isEmpty()
  }

  @Test
  fun emptySectionInMiddle() {
    val group =
      createActionGroup(
        ActionGroupSection(null, listOf(action1)),
        ActionGroupSection(Heading.RUNNING_DEVICES_ID, emptyList()),
        ActionGroupSection(Heading.AVAILABLE_DEVICES_ID, listOf(action2)),
      )
    assertThat(group.getChildren(null).map { it::class })
      .containsExactly(action1::class, Separator::class, Separator::class, action2::class)
      .inOrder()
  }
}

private val action1 =
  object : AnAction("Action 1") {
    override fun actionPerformed(e: AnActionEvent) {}
  }
private val action2 =
  object : AnAction("Action 2") {
    override fun actionPerformed(e: AnActionEvent) {}
  }
