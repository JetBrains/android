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
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.concurrency.AndroidExecutors
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.util.concurrency.BoundedTaskExecutor
import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.MockitoAnnotations
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

private const val RESOURCE_BASE = "com/android/tools/idea/testartifacts/instrumented/testsuite/snapshots/"
private const val SNAPSHOT_TAR = "fakeSnapshotWithScreenshot.tar"
private const val SNAPSHOT_TAR_GZ = "fakeSnapshotWithScreenshot.tar.gz"
private const val SNAPSHOT_WITH_PB_TAR = "fakeSnapshotWithPb.tar.gz"

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
  private val sdkPath = File("/sdk")

  @Mock
  private lateinit var mockRuntime: Runtime

  @Mock
  private lateinit var mockProcess: Process
  private val mockFileOp = MockFileOp()

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    val p = FakeLocalPackage(SdkConstants.FD_EMULATOR, mockFileOp)
    mockFileOp.recordExistingFile(p.location.resolve(SdkConstants.FN_EMULATOR))
    val packages = RepositoryPackages(listOf(p), listOf())
    val mgr: RepoManager = FakeRepoManager(mockFileOp.toPath(sdkPath), packages)
    androidSdkHandler = AndroidSdkHandler(sdkPath, sdkPath, mockFileOp, mgr)
    `when`(mockRuntime.exec(any(Array<String>::class.java))).thenReturn(mockProcess)
    `when`(mockRuntime.exec(anyString())).thenReturn(mockProcess)
    retentionView = RetentionView(androidSdkHandler, FakeProgressIndicator(), mockRuntime)
  }

  @Test
  fun loadTarScreenshot() {
    assertThat(retentionView.image).isNull()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR)
    assertThat(url).isNotNull()
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
    }
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.image).isNotNull()
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
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
    }
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    ApplicationManager.getApplication().invokeAndWait {
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
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
    }
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Snapshot protobuf broken")).isTrue()
    }
  }

  @Test
  fun loadSnapshotWithPb() {
    `when`(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
    }
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5000, TimeUnit.SECONDS)
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isTrue()
    }
  }

  @Test
  fun loadSnapshotWithPbNotLoadable() {
    `when`(mockProcess.inputStream).thenReturn(ByteArrayInputStream("Not loadable".toByteArray(Charset.defaultCharset())))
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(snapshotFile)
    }
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
      assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Snapshot not loadable")).isTrue()
    }
  }

  @Test
  fun loadNullScreenshot() {
    ApplicationManager.getApplication().invokeAndWait {
      retentionView.setSnapshotFile(null)
    }
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.image).isNull()
    }
  }

  @Test
  fun checkBackgroundColor() {
    ApplicationManager.getApplication().invokeAndWait {
      assertThat(retentionView.myInfoText.isOpaque).isFalse()
    }
  }
}
