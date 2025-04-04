/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.testFramework.HeavyPlatformTestCase
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations


class RefreshLinkedCppProjectsActionTest : HeavyPlatformTestCase() {

  @Mock
  var mySyncState: GradleSyncState? = null

  @Mock
  private val myEvent: AnActionEvent? = null

  private var myPresentation: Presentation? = null
  private var myAction: SyncProjectAction? = null

  override fun setUp() {
    super.setUp();
    MockitoAnnotations.initMocks(this)

    myPresentation = Presentation()
    Mockito.`when`(myEvent!!.presentation).thenReturn(myPresentation)
  }

  fun testTitleTextAndMnemonic() {
    val action = createAction(true)
    Truth.assertThat(action.templateText).isEqualTo("Refresh Linked C++ Projects")
    // There's no mnemonic for this action.
    Truth.assertThat(action.templatePresentation.mnemonic).isEqualTo(0)
  }

  fun testDoUpdateWithSyncInProgressWithoutCpp() {
    myAction = createAction(false)

    IdeComponents(project, getTestRootDisposable()).replaceProjectService(GradleSyncState::class.java, mySyncState!!)
    Mockito.`when`(mySyncState!!.isSyncInProgress).thenReturn(true)

    myAction!!.doUpdate(myEvent!!, project)

    assertFalse(myPresentation!!.isEnabled)
  }

  fun testDoUpdateWithSyncInProgressWithCpp() {
    myAction = createAction(true)

    IdeComponents(project, getTestRootDisposable()).replaceProjectService(GradleSyncState::class.java, mySyncState!!)
    Mockito.`when`(mySyncState!!.isSyncInProgress).thenReturn(true)

    myAction!!.doUpdate(myEvent!!, project)

    assertFalse(myPresentation!!.isEnabled)
  }

  fun testDoUpdateWithSyncNotInProgressWithoutCpp() {
    myAction = createAction(false)

    IdeComponents(project, getTestRootDisposable()).replaceProjectService(GradleSyncState::class.java, mySyncState!!)
    Mockito.`when`(mySyncState!!.isSyncInProgress).thenReturn(false)

    myAction!!.doUpdate(myEvent!!, project)

    // If there is no C++, then the action is disabled.
    assertFalse(myPresentation!!.isEnabled)
  }

  fun testDoUpdateWithSyncNotInProgressWithCpp() {
    myAction = createAction(true)

    IdeComponents(project, getTestRootDisposable()).replaceProjectService(GradleSyncState::class.java, mySyncState!!)
    Mockito.`when`(mySyncState!!.isSyncInProgress).thenReturn(false)

    myAction!!.doUpdate(myEvent!!, project)

    assertTrue(myPresentation!!.isEnabled)
  }

  private fun createAction(projectHasCpp: Boolean): RefreshLinkedCppProjectsAction {
    return object : RefreshLinkedCppProjectsAction() {

      override fun containsExternalCppProjects(project: Project): Boolean {
        return projectHasCpp
      }
    }
  }
}