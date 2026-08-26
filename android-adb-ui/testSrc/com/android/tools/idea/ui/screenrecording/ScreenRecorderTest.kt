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
package com.android.tools.idea.ui.screenrecording

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.ui.save.PostSaveAction
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TemporaryDirectory
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import javax.swing.JButton
import javax.swing.JLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [ScreenRecorder]. */
@RunsInEdt
class ScreenRecorderTest {

  private val projectRule = ProjectRule()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule val ruleChain = RuleChain(projectRule, EdtRule(), PortableUiFontRule(), HeadlessDialogRule(), temporaryDirectoryRule)

  private val project: Project
    get() = projectRule.project

  private val testRootDisposable
    get() = projectRule.disposable

  private val coroutineScope by lazy { testRootDisposable.createCoroutineScope(Dispatchers.EDT) }

  private val settings by lazy { DeviceScreenRecordingSettings.getInstance() }

  private var displayedErrorMessage: String? = null

  @Before
  fun setUp() {
    settings.loadState(DeviceScreenRecordingSettings())
    settings.saveConfig.postSaveAction = PostSaveAction.NONE
    settings.saveConfig.saveLocation = temporaryDirectoryRule.newPath().toString()

    TestDialogManager.setTestDialog(
      { message ->
        displayedErrorMessage = message
        0
      },
      testRootDisposable,
    )
  }

  @After
  fun tearDown() {
    dispatchAllEventsInIdeEventQueue()
    findModelessDialog<ScreenRecorderDialog>()?.close(CLOSE_EXIT_CODE)
    settings.loadState(DeviceScreenRecordingSettings())
  }

  @Test
  fun recordScreen_invalidDuration_throwsIllegalArgumentException() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { recorder.recordScreen(0) }
    }

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { recorder.recordScreen(-5) }
    }
  }

  @Test
  fun recordScreen_success() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    val dialog = findModelessDialog<ScreenRecorderDialog>()
    assertThat(dialog).isNotNull()
    val ui = FakeUi(dialog!!.rootPane)
    assertThat(ui.getComponent<JLabel> { it.text == "Record Screen - Pixel 6" }).isNotNull()

    provider.stopRecording()
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(findModelessDialog<ScreenRecorderDialog>()).isNull()
    assertThat(provider.pulledRecordingPath).isNotNull()
    assertThat(provider.pulledRecordingPath?.parent).isEqualTo(Path.of(settings.saveConfig.saveLocation))
    assertThat(provider.pulledRecordingPath?.fileName.toString()).startsWith("Screen_recording_")
    assertThat(provider.pulledRecordingPath?.fileName.toString()).endsWith(".mp4")
    assertThat(settings.recordingCount).isEqualTo(1)
    assertThat(displayedErrorMessage).isNull()
  }

  @Test
  fun recordScreen_dialogStopButtonClicked() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    val dialog = findModelessDialog<ScreenRecorderDialog>()
    assertThat(dialog).isNotNull()

    val ui = FakeUi(dialog!!.rootPane)
    val stopButton = ui.getComponent<JButton> { it.text == "Stop Recording" }
    ui.clickOn(stopButton)
    dispatchAllEventsInIdeEventQueue()

    assertThat(provider.stopRecordingCalled).isTrue()
    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(provider.pulledRecordingPath).isNotNull()
    assertThat(settings.recordingCount).isEqualTo(1)
  }

  @Test
  fun recordScreen_dialogCancelled_cancelsRecordingAndRethrows() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    val dialog = findModelessDialog<ScreenRecorderDialog>()
    assertThat(dialog).isNotNull()

    dialog!!.close(CANCEL_EXIT_CODE)
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCancelled).isTrue()
    assertThat(provider.cancelRecordingCalled).isTrue()
    assertThat(provider.pulledRecordingPath).isNull()
    assertThat(settings.recordingCount).isEqualTo(0)
  }

  @Test
  fun recordScreen_coroutineCancelled_cancelsRecordingAndClosesDialog() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    assertThat(findModelessDialog<ScreenRecorderDialog>()).isNotNull()

    recordingJob.cancel()
    dispatchAllEventsInIdeEventQueue()

    assertThat(provider.cancelRecordingCalled).isTrue()
    assertThat(findModelessDialog<ScreenRecorderDialog>()).isNull()
    assertThat(provider.pulledRecordingPath).isNull()
    assertThat(settings.recordingCount).isEqualTo(0)
  }

  @Test
  fun recordScreen_recordingFailsWithCause_showsErrorDialog() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    provider.failRecording(RuntimeException("Device disconnected"))
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(findModelessDialog<ScreenRecorderDialog>()).isNull()
    assertThat(provider.pulledRecordingPath).isNull()
    assertThat(settings.recordingCount).isEqualTo(0)
    assertThat(displayedErrorMessage).isEqualTo("Failed to record the screen - Device disconnected")
  }

  @Test
  fun recordScreen_recordingFailsWithoutCause_showsGenericErrorDialog() {
    val provider = FakeRecordingProvider()
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    provider.failRecording(RuntimeException())
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(displayedErrorMessage).isEqualTo("Failed to record the screen")
  }

  @Test
  fun recordScreen_recordingDoesNotExist_showsErrorDialog() {
    val provider = FakeRecordingProvider()
    provider.recordingExists = false
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    provider.stopRecording()
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(provider.pulledRecordingPath).isNull()
    assertThat(settings.recordingCount).isEqualTo(0)
    assertThat(displayedErrorMessage).isEqualTo("Failed to record the screen")
  }

  @Test
  fun recordScreen_pullRecordingFails_showsErrorDialog() {
    val provider = FakeRecordingProvider()
    provider.pullException = IOException("Disk is full")
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    provider.stopRecording()
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(settings.recordingCount).isEqualTo(0)
    assertThat(displayedErrorMessage).startsWith("Failed to save screen recording to ")
  }

  @Test
  fun recordScreen_pullRecordingCancelled_rethrowsCancellation() {
    val provider = FakeRecordingProvider()
    provider.pullException = CancellationException("Pull cancelled")
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    provider.stopRecording()
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCancelled).isTrue()
    assertThat(settings.recordingCount).isEqualTo(0)
  }

  @Test
  fun recordScreen_usesConfiguredSaveLocationAndTemplate() {
    val tempDir = temporaryDirectoryRule.newPath("custom_dir")
    settings.saveConfig.saveLocation = tempDir.toString()
    settings.saveConfig.filenameTemplate = "my_custom_recording_<#>"
    settings.recordingCount = 5

    val provider = FakeRecordingProvider(fileExtension = "webm")
    val recorder = ScreenRecorder(project, provider, "Pixel 6")

    val recordingJob = coroutineScope.async { recorder.recordScreen(300) }
    dispatchAllEventsInIdeEventQueue()

    provider.stopRecording()
    dispatchAllEventsInIdeEventQueue()

    assertThat(recordingJob.isCompleted).isTrue()
    assertThat(provider.pulledRecordingPath).isEqualTo(tempDir.resolve("my_custom_recording_6.webm"))
    assertThat(settings.recordingCount).isEqualTo(6)
  }

  private class FakeRecordingProvider(override val fileExtension: String = "mp4") : RecordingProvider {
    private val recordingDeferred = CompletableDeferred<Unit>()
    var recordingExists = true
    var pulledRecordingPath: Path? = null
    var stopRecordingCalled = false
    var cancelRecordingCalled = false
    var pullException: Throwable? = null

    override suspend fun startRecording(): Deferred<Unit> = recordingDeferred

    override fun stopRecording() {
      stopRecordingCalled = true
      recordingDeferred.complete(Unit)
    }

    override fun cancelRecording() {
      cancelRecordingCalled = true
      recordingDeferred.cancel()
    }

    fun failRecording(throwable: Throwable) {
      recordingDeferred.completeExceptionally(throwable)
    }

    override suspend fun doesRecordingExist(): Boolean = recordingExists

    override suspend fun pullRecording(target: Path) {
      pullException?.let { throw it }
      pulledRecordingPath = target
    }

    override fun dispose() {}
  }
}
