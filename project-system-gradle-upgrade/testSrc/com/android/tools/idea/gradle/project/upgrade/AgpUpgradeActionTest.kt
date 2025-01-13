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

import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.After
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AgpUpgradeActionTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk().onEdt()

  val project by lazy { projectRule.project }

  @After
  fun tearDown() {
    JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
  }

  @Test
  fun testAgpUpgradeActionDisabledForDefaultProjectSystem() {
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(DefaultProjectSystem(project))
    val action = AgpUpgradeAction()
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun testAgpUpgradeActionEnabledForGradleProjectSystem() {
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(GradleProjectSystem(project))
    val action = AgpUpgradeAction()
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun testAgpUpgradeActionDisabledForUntrustedProject() {
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(GradleProjectSystem(project))
    project.setTrusted(false)
    val action = AgpUpgradeAction()
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

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
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
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