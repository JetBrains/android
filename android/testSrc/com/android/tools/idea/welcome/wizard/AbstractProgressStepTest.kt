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
package com.android.tools.idea.welcome.wizard

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JProgressBar
import org.junit.Rule
import org.junit.Test

class AbstractProgressStepTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun isIndeterminate() {
    val wizardModel =
      object : WizardModel() {
        override fun handleFinished() {}
      }
    val step =
      object : AbstractProgressStep<WizardModel>(wizardModel, "Step") {
        override fun execute() {}
      }
    Disposer.register(wizardModel, step)

    val progressIndicator = step.getProgressIndicator()
    progressIndicator.start()

    assertThat(progressIndicator.isIndeterminate).isFalse()

    progressIndicator.isIndeterminate = true

    assertThat(progressIndicator.isIndeterminate).isTrue()

    Disposer.dispose(wizardModel)
  }

  @RunsInEdt
  @Test
  fun printFromBackgroundThread() {
    val wizardModel =
      object : WizardModel() {
        override fun handleFinished() {}
      }
    val step =
      object : AbstractProgressStep<WizardModel>(wizardModel, "Step") {
        override fun execute() {}
      }
    Disposer.register(wizardModel, step)

    val latch = CountDownLatch(1)
    var error: Throwable? = null

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        step.print("Installing SDK package...", ConsoleViewContentType.NORMAL_OUTPUT)
        step.print("Downloading: 50%\n", ConsoleViewContentType.NORMAL_OUTPUT)
      } catch (t: Throwable) {
        error = t
      } finally {
        latch.countDown()
      }
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(error).isNull()

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Disposer.dispose(wizardModel)
  }

  @RunsInEdt
  @Test
  fun progressIndicatorUpdatesFromBackgroundThread() {
    val wizardModel =
      object : WizardModel() {
        override fun handleFinished() {}
      }
    val step =
      object : AbstractProgressStep<WizardModel>(wizardModel, "Step") {
        override fun execute() {}
      }
    Disposer.register(wizardModel, step)

    val progressIndicator = step.getProgressIndicator()
    progressIndicator.start()

    val latch = CountDownLatch(1)
    var error: Throwable? = null

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        progressIndicator.text = "Downloading components"
        progressIndicator.text2 = "File 1 of 3"
        progressIndicator.fraction = 0.5
        progressIndicator.isIndeterminate = false
      } catch (t: Throwable) {
        error = t
      } finally {
        latch.countDown()
      }
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(error).isNull()

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val fakeUi = FakeUi(step.component)
    val labels = fakeUi.findAllComponents<JLabel>()
    val progressBar = fakeUi.findComponent<JProgressBar>()!!

    val labelTexts = labels.mapNotNull { it.text }
    assertThat(labelTexts).contains("Downloading components")
    assertThat(labelTexts).contains("File 1 of 3")
    assertThat(progressBar.value).isEqualTo(500)
    assertThat(progressBar.isIndeterminate).isFalse()

    // Now test stop()
    val stopLatch = CountDownLatch(1)
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        progressIndicator.stop()
      } finally {
        stopLatch.countDown()
      }
    }
    assertThat(stopLatch.await(5, TimeUnit.SECONDS)).isTrue()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(progressBar.isVisible).isFalse()

    Disposer.dispose(wizardModel)
  }

  @RunsInEdt
  @Test
  fun cancellationAndRunningState() {
    val wizardModel =
      object : WizardModel() {
        override fun handleFinished() {}
      }
    val step =
      object : AbstractProgressStep<WizardModel>(wizardModel, "Step") {
        override fun execute() {}
      }
    Disposer.register(wizardModel, step)

    val progressIndicator = step.getProgressIndicator()
    assertThat(step.isRunning()).isFalse()
    assertThat(step.isCanceled()).isFalse()

    progressIndicator.start()
    assertThat(step.isRunning()).isTrue()

    progressIndicator.cancel()
    assertThat(step.isCanceled()).isTrue()

    Disposer.dispose(wizardModel)
  }
}
