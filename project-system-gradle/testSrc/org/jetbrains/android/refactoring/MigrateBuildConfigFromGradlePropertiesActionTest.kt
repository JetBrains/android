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
package org.jetbrains.android.refactoring

import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class MigrateBuildConfigFromGradlePropertiesActionTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  private val project get() = projectRule.project
  private val fixture get() = projectRule.fixture

  @Test
  fun `check action is disabled in non-gradle projects`() {
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(DefaultProjectSystem(project))
    val file = fixture.addFileToProject("BUILD", "load(\"//tools/base/bazel:bazel.bzl\", \"iml_module\")")

    ApplicationManager.getApplication().invokeAndWait { fixture.openFileInEditor(file.virtualFile) }
    val action = MigrateBuildConfigFromGradlePropertiesAction()
    val event = TestActionEvent.createTestEvent(action, DataManager.getInstance().getDataContext(fixture.editor.component))
    ApplicationManager.getApplication().runReadAction {
      action.update(event)
    }

    Assert.assertFalse("Action should not be visible", event.presentation.isVisible)
    Assert.assertFalse("Action should not be enabled", event.presentation.isEnabled)
  }
}