/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.SdkConstants
import com.android.emulator.snapshot.SnapshotOuterClass
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.UsageLogReporter
import com.android.tools.utp.plugins.host.icebox.proto.IceboxOutputProto
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidTestRetentionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import javax.swing.ImageIcon

private const val RESOURCE_BASE = "com/android/tools/idea/testartifacts/instrumented/testsuite/snapshots/"
private const val SCREENSHOT_PNG = "screenshot.png"
private const val SNAPSHOT_TAR = "fakeSnapshotWithScreenshot.tar"
private const val SNAPSHOT_TAR_GZ = "fakeSnapshotWithScreenshot.tar.gz"
private const val SNAPSHOT_WITH_PB_TAR = "fakeSnapshotWithPb.tar.gz"
private const val SNAPSHOT_PB = "snapshot.pb"

class RetentionViewTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryFolderRule = TemporaryFolder()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(disposableRule)
    .around(temporaryFolderRule)

  private lateinit var retentionView: RetentionView
  private lateinit var androidSdkHandler: AndroidSdkHandler
  private val sdkRoot = createInMemoryFileSystemAndFolder("sdk")

  private val mockRuntime: Runtime = mock()

  private val mockProcess: Process = mock()

  private val mockLogReporter: UsageLogReporter = mock()

  @Before
  fun setUp() {
    val p = FakeLocalPackage(SdkConstants.FD_EMULATOR, sdkRoot.resolve("emulator"))
    p.location.resolve(SdkConstants.FN_EMULATOR).recordExistingFile()
    val packages = RepositoryPackages(listOf(p), listOf())
    val mgr: RepoManager = FakeRepoManager(sdkRoot, packages)
    androidSdkHandler = AndroidSdkHandler(sdkRoot, sdkRoot, mgr)
    whenever(mockRuntime.exec(any<Array<String>>())).thenReturn(mockProcess)
    whenever(mockRuntime.exec(any<String>())).thenReturn(mockProcess)
    retentionView = RetentionView(androidSdkHandler, FakeProgressIndicator(), mockRuntime, mockLogReporter,
                                  MoreExecutors.directExecutor())
  }

  @Test
  fun loadRetentionInfo() {
    val packageName = "my.app.name"
    val iceboxInfo = temporaryFolderRule.newFile()
    FileOutputStream(iceboxInfo).use {
      IceboxOutputProto.IceboxOutput.newBuilder().setAppPackage(packageName).build().writeTo(it)
    }
    retentionView.setRetentionInfoFile(iceboxInfo)
    assertThat(packageName).isEqualTo(retentionView.appName)
  }

  @Test
  fun loadTarScreenshot() {
    assertThat(retentionView.image).isNull()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      // Fake the panel size so that it will update the image
      retentionView.rootPanel.resize(200, 200)
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.image).isNotNull()
      assertThat(retentionView.myImageLabel.icon).isNotNull()
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
    }
  }

  @Test
  fun loadTarScreenshotCached() {
    assertThat(retentionView.image).isNull()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      retentionView.setSnapshotFile(null)
      assertThat(retentionView.image).isNull()
      // Fake the panel size so that it will update the image
      retentionView.rootPanel.resize(200, 200)
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.image).isNotNull()
      assertThat(retentionView.myImageLabel.icon).isNotNull()
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
    }
  }

  @Test
  fun loadTarGzScreenshot() {
    assertThat(retentionView.image).isNull()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR_GZ)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.image).isNotNull()
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
    }
  }

  @Test
  fun loadBadSnapshotPb() {
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR_GZ)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Snapshot protobuf not found")).isTrue()
    }
    val eventCaptor = argumentCaptor<AndroidStudioEvent.Builder>()
    verify(mockLogReporter).report(eventCaptor.capture(),
                                any())

    val event = eventCaptor.firstValue
    assertThat(event.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(AndroidStudioEvent.EventKind.ANDROID_TEST_RETENTION_EVENT)
    assertThat(event.androidTestRetentionEvent.snapshotCompatibility.result)
      .isEqualTo(AndroidTestRetentionEvent.SnapshotCompatibility.Result.SNAPSHOT_PROTO_FILE_NOT_FOUND)
  }

  @Test
  fun loadBadSnapshotPbInterrupted() {
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR_GZ)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    val application = ApplicationManager.getApplication()
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    val toolTipText = "default"
    var uiUpdateChecks = 0
    retentionView.myRetentionDebugButton.isEnabled = false
    retentionView.myRetentionDebugButton.toolTipText = toolTipText
    retentionView.scanSnapshotFileContent(snapshotFile) {
      if (application.isDispatchThread) {
        uiUpdateChecks += 1
        uiUpdateChecks > 1
      } else {
        false
      }
    }
    application.invokeAndWait {
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).contains("Validating snapshot")
    }
  }

  @Test
  fun loadSnapshotWithPb() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isTrue()
    }
    val eventCaptor = argumentCaptor<AndroidStudioEvent.Builder>()
    verify(mockLogReporter).report(eventCaptor.capture(),
                                   any())
    val event = eventCaptor.firstValue
    assertThat(event.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(AndroidStudioEvent.EventKind.ANDROID_TEST_RETENTION_EVENT)
    assertThat(event.androidTestRetentionEvent.snapshotCompatibility.result)
      .isEqualTo(AndroidTestRetentionEvent.SnapshotCompatibility.Result.LOADABLE)
  }

  @Test
  fun loadSnapshotFolderWithPb() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFolder = temporaryFolderRule.newFolder()
    val snapshotFile = snapshotFolder.resolve(SNAPSHOT_PB)

    FileOutputStream(snapshotFile).use {
      SnapshotOuterClass.Snapshot.newBuilder().build().writeTo(it)
    }
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFolder)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isTrue()
    }
    val eventCaptor = argumentCaptor<AndroidStudioEvent.Builder>()
    verify(mockLogReporter).report(eventCaptor.capture(),
                                   any())
    val event = eventCaptor.firstValue
    assertThat(event.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(AndroidStudioEvent.EventKind.ANDROID_TEST_RETENTION_EVENT)
    assertThat(event.androidTestRetentionEvent.snapshotCompatibility.result)
      .isEqualTo(AndroidTestRetentionEvent.SnapshotCompatibility.Result.LOADABLE)
  }

  @Test
  fun snapshotNotLoadable() {
    val reason = "a good reason"
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream(
      "Not loadable\n$reason".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).contains(reason)
    }
    val eventCaptor = argumentCaptor<AndroidStudioEvent.Builder>()
    verify(mockLogReporter).report(eventCaptor.capture(),
                                   any())
    val event = eventCaptor.firstValue
    assertThat(event.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(AndroidStudioEvent.EventKind.ANDROID_TEST_RETENTION_EVENT)
    assertThat(event.androidTestRetentionEvent.snapshotCompatibility.result)
      .isEqualTo(AndroidTestRetentionEvent.SnapshotCompatibility.Result.EMULATOR_LOADABLE_CHECK_FAILURE)
  }


  @Test
  fun snapshotNotLoadableCached() {
    val reason = "a good reason"
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream(
      "Not loadable\n$reason".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      retentionView.setSnapshotFile(null)
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).contains(reason)
      verify(mockRuntime, times(1)).exec(any<String>())
    }
    verify(mockLogReporter, times(1)).report(any(), any())
  }

  @Test
  fun loadSameSnapshotWithPb() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isTrue()
    }
    verify(mockRuntime, times(1)).exec(any<String>())
  }

  @Test
  fun unloadSnapshot() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      retentionView.setSnapshotFile(null)
      assertThat(retentionView.image).isNull()
    }
  }

  @Test
  fun loadSnapshotWithPbNotLoadable() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Not loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Snapshot not loadable")).isTrue()
    }
    val eventCaptor = argumentCaptor<AndroidStudioEvent.Builder>()
    verify(mockLogReporter).report(eventCaptor.capture(),
                                   any())
    val event = eventCaptor.firstValue
    assertThat(event.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
    assertThat(event.kind).isEqualTo(AndroidStudioEvent.EventKind.ANDROID_TEST_RETENTION_EVENT)
    assertThat(event.androidTestRetentionEvent.snapshotCompatibility.result)
      .isEqualTo(AndroidTestRetentionEvent.SnapshotCompatibility.Result.EMULATOR_LOADABLE_CHECK_FAILURE)
  }

  @Test
  fun loadNullScreenshot() {
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(null)
      assertThat(retentionView.image).isNull()
    }
  }

  @Test
  fun imageUpdate() {
    ApplicationManager.getApplication().invokeAndWait {
      // Fake the panel size so that it will update the image
      retentionView.rootPanel.resize(200, 200)
      val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SCREENSHOT_PNG)
      retentionView.updateSnapshotImage(ImageIcon(url).image, 100, 100) { false }
      assertThat(retentionView.myImageLabel.icon).isNotNull()
    }
  }

  @Test
  fun interruptImageUpdate() {
    ApplicationManager.getApplication().invokeAndWait {
      // Fake the panel size so that it will update the image
      retentionView.rootPanel.resize(200, 200)
      val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SCREENSHOT_PNG)
      retentionView.updateSnapshotImage(ImageIcon(url).image, 100, 100) { true }
      assertThat(retentionView.myImageLabel.icon).isNull()
    }
  }

  @Test
  fun scanSnapshotWithUiUpdate() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    val toolTipText = "default"
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.myRetentionDebugButton.isEnabled = false
      retentionView.myRetentionDebugButton.toolTipText = toolTipText
      retentionView.scanSnapshotFileContent(snapshotFile) {
        false
      }
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isTrue()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).isNotEqualTo(toolTipText)
    }
  }

  // Very fine-grain tests for multithreading behavior when setting snapshot files.
  // The problem here is that sometimes people start validating a new retention snapshot before a previous validation
  // is done, which raises a conflict in updating the UI.
  // Thus in the UI code it checks multiple times if it has become stale. If so, interrupt the UI updates.
  @Test
  fun interruptUiUpdate0() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    val toolTipText = "default"
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.myRetentionDebugButton.isEnabled = false
      retentionView.myRetentionDebugButton.toolTipText = toolTipText
      retentionView.scanSnapshotFileContent(snapshotFile) {
        true
      }
      assertThat(retentionView.image).isNull()
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).isEqualTo(toolTipText)
    }
  }

  @Test
  fun interruptUiUpdate1() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    val toolTipText = "default"
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      var uiUpdateChecks = 0
      retentionView.myRetentionDebugButton.isEnabled = false
      retentionView.myRetentionDebugButton.toolTipText = toolTipText
      retentionView.scanSnapshotFileContent(snapshotFile) {
        uiUpdateChecks += 1
        uiUpdateChecks > 1
      }
      assertThat(retentionView.image).isNull()
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).isEqualTo(toolTipText)
    }
  }

  @Test
  fun interruptUiUpdate2() {
    whenever(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    val toolTipText = "default"
    assertThat(url).isNotNull()
    FileUtils.copyURLToFile(url, snapshotFile)
    ApplicationManager.getApplication().invokeAndWait {
      for (i in 2..5) {
        var uiUpdateChecks = 0
        retentionView.myRetentionDebugButton.isEnabled = false
        retentionView.myRetentionDebugButton.toolTipText = toolTipText
        retentionView.scanSnapshotFileContent(snapshotFile) {
          uiUpdateChecks += 1
          uiUpdateChecks > i
        }
        assertThat(retentionView.image).isNull()
        assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
        assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Validating snapshot file")).isTrue()
      }
    }
  }

  @Test
  fun interruptUiUpdate3() {
    ApplicationManager.getApplication().invokeAndWait {
      val toolTipText = "default"
      var uiUpdateChecks = 0
      retentionView.myRetentionDebugButton.isEnabled = false
      retentionView.myRetentionDebugButton.toolTipText = toolTipText
      retentionView.scanSnapshotFileContent(null) {
        uiUpdateChecks += 1
        uiUpdateChecks > 1
      }
      assertThat(retentionView.image).isNull()
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText).isEqualTo(toolTipText)
    }
  }

  @Test
  fun checkBackgroundColor() {
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.myInfoText.isOpaque).isFalse()
    }
  }
}
