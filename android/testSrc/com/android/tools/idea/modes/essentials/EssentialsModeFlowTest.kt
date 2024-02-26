/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.modes.essentials

import com.android.tools.idea.testing.disposable
import com.intellij.testFramework.ProjectRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EssentialsModeFlowTest {
  @get:Rule val projectRule = ProjectRule()

  private val project
    get() = projectRule.project

  @After
  fun tearDown() {
    EssentialsMode.setEnabled(false, project)
  }

  @Test
  fun flowIsInitialized() {
    EssentialsMode.setEnabled(false, project)
    assertEquals(false, essentialsModeFlow(project, projectRule.disposable).value)

    EssentialsMode.setEnabled(true, project)
    assertEquals(true, essentialsModeFlow(project, projectRule.disposable).value)
  }

  @Test
  fun valueIsUpdatedWhenEssentialsModeIsUpdated() {
    val flow = essentialsModeFlow(project, projectRule.disposable)
    assertEquals(false, flow.value)

    EssentialsMode.setEnabled(true, project)
    assertEquals(true, flow.value)

    EssentialsMode.setEnabled(false, project)
    assertEquals(false, flow.value)
  }
}
