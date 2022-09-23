/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@RunsInEdt
class AgpUpgradeActionTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk().onEdt()

  val project by lazy { projectRule.project }

  @Test
  fun testAgpUpgradeAction() {
    projectRule.fixture.addFileToProject("build.gradle", """
      buildscript {
        dependencies {
          classpath 'com.android.tools.build:gradle:4.2.0'
        }
      }
    """.trimIndent())
    projectRule.fixture.addFileToProject("settings.gradle", "include ':app'")
    projectRule.fixture.addFileToProject("app/build.gradle", """
      plugins {
        id 'com.android.application'
      }
    """.trimIndent())
    val event = mock(AnActionEvent::class.java)
    val dataContext = mock(DataContext::class.java)
    whenever(event.project).thenReturn(project)
    whenever(event.dataContext).thenReturn(dataContext)
    AgpUpgradeAction().actionPerformed(event)
    fun ready(): Boolean {
      return ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")?.contentManager?.contents?.isNotEmpty() ?: false
    }
    // TODO(b/247414701): this 10s timeout is arbitrary and seems excessive, but we get sporadic failures with a timeout of 5.  If 10
    //  appears to cure the problem, great; if not, then this test needs reworking (there would probably be no need for it if the
    //  ContentManagerImpl for the Upgrade Assissant were already registered and initialized).
    PlatformTestUtil.waitWithEventsDispatching("Upgrade Assistant content not provided", ::ready, 10)
    assertThat(ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!.contentManager.contents).hasLength(1)
  }
}