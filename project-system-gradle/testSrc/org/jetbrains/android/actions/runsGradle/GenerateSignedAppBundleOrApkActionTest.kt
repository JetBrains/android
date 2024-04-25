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
package org.jetbrains.android.actions.runsGradle

import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.KOTLIN_LIB
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import org.jetbrains.android.actions.GenerateSignedAppBundleOrApkAction
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptWorkspaceFileIndexContributor

/**
 * Tests for [GenerateSignedAppBundleOrApkAction]
 */
class GenerateSignedAppBundleOrApkActionTest: AndroidGradleTestCase() {
  fun testDefaultProjectSystemActionDisabled() {
    loadSimpleApplication()
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(DefaultProjectSystem(project))
    val action = GenerateSignedAppBundleOrApkAction()
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()
  }

  fun testSimpleProjectActionEnabled() {
    loadSimpleApplication()
    val action = GenerateSignedAppBundleOrApkAction()
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.isVisible).isTrue()
  }

  fun testLibraryOnlyProjectActionDisabled() {
    val disposable = testRootDisposable
    val ep = WorkspaceFileIndexImpl.EP_NAME
    val filteredExtensions = ep.extensionList.filter { it !is KotlinScriptWorkspaceFileIndexContributor }
    ExtensionTestUtil.maskExtensions(ep, filteredExtensions, disposable)
    loadProject(KOTLIN_LIB)
    val action = GenerateSignedAppBundleOrApkAction()
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.isVisible).isFalse()
  }
}