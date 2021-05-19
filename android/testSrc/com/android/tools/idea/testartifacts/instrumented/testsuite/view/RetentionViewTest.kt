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
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoManager
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.concurrency.AndroidExecutors
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.concurrency.BoundedTaskExecutor
import org.apache.commons.io.IOUtils
import org.ini4j.Ini
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val RESOURCE_BASE = "com/android/tools/idea/testartifacts/instrumented/testsuite/snapshots/"
private const val SNAPSHOT_TAR = "fakeSnapshotWithScreenshot.tar"
private const val SNAPSHOT_TAR_GZ = "fakeSnapshotWithScreenshot.tar.gz"
private const val SNAPSHOT_WITH_PB_TAR = "fakeSnapshotWithPb.tar.gz"
private const val RESOURCE_SYSTEM_IMAGE_BUILD_PROP = "systemImageBuild.prop"
private const val SDK_SYSTEM_IMAGE_BUILD_PROP = "build.prop"
private const val FAKE_EMULATOR_REVISION = "26.0.0"
private const val INI_GLOBAL_SECTION_NAME = "global"

@RunWith(JUnit4::class)
@RunsInEdt
class RetentionViewTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryFolderRule = TemporaryFolder()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(temporaryFolderRule)

  private lateinit var retentionView: RetentionView
  private lateinit var androidSdkHandler: AndroidSdkHandler
  private lateinit var sdkPath: File

  @Before
  fun setUp() {
    sdkPath = temporaryFolderRule.newFolder()

    val p = FakeLocalPackage(SdkConstants.FD_EMULATOR)
    p.setRevision(Revision.parseRevision(FAKE_EMULATOR_REVISION))
    val packages = RepositoryPackages()
    packages.setLocalPkgInfos(ImmutableList.of<LocalPackage>(p))
    val mgr: RepoManager = FakeRepoManager(null, packages)
    androidSdkHandler = AndroidSdkHandler(sdkPath, null, MockFileOp(), mgr)
    retentionView = RetentionView(androidSdkHandler, FakeProgressIndicator())
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
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.image).isNotNull()
    assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
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
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.image).isNotNull()
    assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
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
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
    assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Snapshot protobuf broken")).isTrue()
  }

  fun setupSystemFolder() {
    val systemImageFolder = sdkPath.resolve("system-images")
      .resolve("android-29")
      .resolve("google_apis_playstore")
      .resolve("x86_64")
    assertThat(systemImageFolder.mkdirs()).isTrue()
    val buildPropUrl = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + RESOURCE_SYSTEM_IMAGE_BUILD_PROP)
    assertThat(buildPropUrl).isNotNull()
    val buildPropFile = systemImageFolder.resolve(SDK_SYSTEM_IMAGE_BUILD_PROP)
    assertThat(buildPropFile.createNewFile()).isTrue()
    with(FileOutputStream(buildPropFile)) {
      IOUtils.copy(buildPropUrl.openStream(), this)
    }
  }

  @Test
  fun loadSnapshotWithPb() {
    setupSystemFolder()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.myRetentionDebugButton.isEnabled).isTrue()
  }

  @Test
  fun loadSnapshotPbWithoutSystem() {
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_WITH_PB_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    with(FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.myRetentionDebugButton.isEnabled).isFalse()
    assertThat(retentionView.myRetentionDebugButton.toolTipText.contains("Failed to find system image build property")).isTrue()
  }

  @Test
  fun loadNullScreenshot() {
    retentionView.setSnapshotFile(null)
    assertThat(retentionView.image).isNull()
  }

  @Test
  fun checkSystemImageVersion() {
    setupSystemFolder()
    val hardwareIni =  Ini().also {
      it.add(INI_GLOBAL_SECTION_NAME)
      it[INI_GLOBAL_SECTION_NAME]?.add("disk.systemPartition.initPath",
                                       "/Android/Sdk/system-images/android-29/google_apis_playstore/x86_64//system.img")
      it[INI_GLOBAL_SECTION_NAME]?.add("android.sdk.root", "/Android/Sdk")
    }
    // The system image version must match the one from ${RESOURCE_BASE + RESOURCE_SYSTEM_IMAGE_BUILD_PROP}
    assertThat(isSystemImageCompatible(hardwareIni, "QSR1.190920.001 dev-keys", androidSdkHandler).compatible).isTrue()
  }

  @Test
  fun checkSystemImageVersionEmptyIni() {
    val hardwareIni = Ini()
    isSystemImageCompatible(hardwareIni, "QSR1.190920.001 dev-keys", androidSdkHandler).also {
      assertThat(it.compatible).isFalse()
      assertThat(it.reason).isNotNull()
    }
  }

  @Test
  fun checkBadSystemImageVersion() {
    setupSystemFolder()
    val hardwareIni =  Ini().also {
      it.add(INI_GLOBAL_SECTION_NAME)
      it[INI_GLOBAL_SECTION_NAME]?.add("disk.systemPartition.initPath",
                                       "/Android/Sdk/system-images/android-29/google_apis_playstore/x86_64//system.img")
      it[INI_GLOBAL_SECTION_NAME]?.add("android.sdk.root", "/Android/Sdk")
    }
    isSystemImageCompatible(hardwareIni, "invalid", androidSdkHandler).also {
      assertThat(it.compatible).isFalse()
      assertThat(it.reason!!.contains("System image version mismatch")).isTrue()
    }
  }

  @Test
  fun checkBackgroundColor() {
    assertThat(retentionView.myInfoText.isOpaque).isFalse()
  }
}
