// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.RefactoringProcessorInstantiator
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

@RunsInEdt
class UpgradeAssistantWindowModelThreadingTest {
  @get:Rule val projectRule = ProjectRule()

  @get:Rule val edtRule = EdtRule()

  private val currentVersion = AgpVersion.parse("8.2.2")
  private val newVersion = AgpVersion.parse("8.13.0")

  @Before
  fun registerServices() {
    val project = projectRule.project
    project.registerOrReplaceServiceInstance(GradleSyncState::class.java, mock(), project)
    val instantiator =
      mock<RefactoringProcessorInstantiator> {
        on { createProcessor(any(), any(), any()) } doAnswer
          {
            ReadAction.nonBlocking<AgpUpgradeRefactoringProcessor> { mock() }.executeSynchronously()
          }
      }
    project.registerOrReplaceServiceInstance(RefactoringProcessorInstantiator::class.java, instantiator, project)
  }

  @Test
  fun testNewVersionCommitOnEdtWithoutWriteIntentLock() {
    val model =
      UpgradeAssistantWindowModel(
        projectRule.project,
        { currentVersion },
        latestKnownVersion = newVersion,
        newProjectVersion = newVersion,
        knownVersionsRequester = { emptySet() },
      )
    model.versionComboTextChanged()

    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
      model.newVersionCommit(newVersion.toString())
    }

    assertThat(model.processor).isNotNull()
  }
}
