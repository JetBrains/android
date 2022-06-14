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
package com.android.tools.idea.annotations

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * [DumbService] implementation to be used in tests.
 * TODO(b/228294269): Move this to the dedicated testutil module.
 */
class TestDumbService(
  private val project: Project,
) : DumbService() {
  private val smartRunnables = mutableListOf<Runnable>()
  private var modifications = 0L
  private val modificationTracker = ModificationTracker { modifications }

  var dumbMode = true
    set(value) {
      field = value
      modifications++
      if (!value) {
        smartRunnables.forEach { it.run() }
        smartRunnables.clear()
      }
    }
  override fun getModificationTracker(): ModificationTracker = modificationTracker

  override fun isDumb(): Boolean = dumbMode

  override fun runWhenSmart(runnable: Runnable) {
    if (isDumb) {
      smartRunnables.add(runnable)
    } else {
      runnable.run()
    }
  }

  override fun waitForSmartMode() { }

  override fun smartInvokeLater(runnable: Runnable) { }

  override fun smartInvokeLater(runnable: Runnable, modalityState: ModalityState) { }

  override fun queueTask(task: DumbModeTask) { }

  override fun cancelTask(task: DumbModeTask) { }

  override fun cancelAllTasksAndWait() { }

  override fun completeJustSubmittedTasks() { }

  override fun wrapGently(dumbUnawareContent: JComponent, parentDisposable: Disposable): JComponent = JPanel()

  override fun wrapWithSpoiler(dumbAwareContent: JComponent, updateRunnable: Runnable, parentDisposable: Disposable): JComponent = JPanel()

  override fun showDumbModeNotification(message: String) { }

  override fun showDumbModeActionBalloon(balloonText: String, runWhenSmartAndBalloonStillShowing: Runnable) { }

  override fun getProject(): Project = project

  override fun setAlternativeResolveEnabled(enabled: Boolean) {}

  override fun isAlternativeResolveEnabled(): Boolean = false

  override fun suspendIndexingAndRun(activityName: String, activity: Runnable) {}

  override fun runWithWaitForSmartModeDisabled(runnable: Runnable) {}

  override fun unsafeRunWhenSmart(runnable: Runnable) {}
}