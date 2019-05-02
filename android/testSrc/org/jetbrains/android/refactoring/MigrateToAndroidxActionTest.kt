/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.tools.idea.gradle.project.sync.setup.post.project.GradleKtsBuildFilesWarningStep
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class MigrateToAndroidxActionTest {

  @get:Rule
  var myRule = AndroidProjectRule.inMemory()

  private var myProject: Project? = null
  private var myPresentation: Presentation? = null
  private var myEvent: AnActionEvent? = null

  private var myContext: DataContext? = null

  @Before
  fun mockEvent() {
    myProject = myRule.project
    myPresentation = Presentation()

    myEvent = Mockito.mock(AnActionEvent::class.java)

    Mockito.`when`<Project>(myEvent!!.project).thenReturn(myProject)
    Mockito.`when`(myEvent!!.presentation).thenReturn(myPresentation)
  }

  @Before
  fun mockContext() {
    myContext = Mockito.mock(DataContext::class.java)
    Mockito.`when`<Project>(myContext!!.getData(CommonDataKeys.PROJECT)).thenReturn(myRule.project)
  }

  @Test
  fun `check action is disabled when kts detected`() {
    myProject?.putUserData(GradleKtsBuildFilesWarningStep.HAS_KTS_BUILD_FILES, true)

    val action = MigrateToAndroidxAction()
    val event = myEvent!!
    action.update(event)

    assertTrue("Action should be visible", event.presentation.isVisible)
    assertFalse("Action should be disabled if kts build files detected", event.presentation.isEnabled)
  }

  @Test
  fun `check action is enabled when kts not detected`() {
    myProject?.putUserData(GradleKtsBuildFilesWarningStep.HAS_KTS_BUILD_FILES, false)

    val action = MigrateToAndroidxAction()
    val event = myEvent!!
    action.update(event)

    assertTrue("Action should be visible", event.presentation.isVisible)
    assertTrue("Action should be enabled if no kts build files detected", event.presentation.isEnabled)
  }

  @Test
  fun `check action is enabled when no value from kts detection`() {
    val action = MigrateToAndroidxAction()
    val event = myEvent!!
    action.update(event)

    assertTrue("Action should be visible", event.presentation.isVisible)
    assertTrue("Action should be disabled by default", event.presentation.isEnabled)
  }
}