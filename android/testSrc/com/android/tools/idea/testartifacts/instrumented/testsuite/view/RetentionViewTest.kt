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

import com.android.tools.idea.concurrency.AndroidExecutors
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.concurrency.BoundedTaskExecutor
import org.apache.commons.io.IOUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val RESOURCE_BASE = "com/android/tools/idea/testartifacts/instrumented/testsuite/snapshots/"
private const val SNAPSHOT_TAR = "fakeSnapshotWithScreenshot.tar"
private const val SNAPSHOT_TAR_GZ = "fakeSnapshotWithScreenshot.tar.gz"

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

  private val retentionView = RetentionView()

  @Test
  fun loadTarScreenshot() {
    assertThat(retentionView.image).isNull()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR)
    assertThat(url).isNotNull()
    with (FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.image).isNotNull()
  }

  @Test
  fun loadTarGzScreenshot() {
    assertThat(retentionView.image).isNull()
    val url = RetentionViewTest::class.java.classLoader.getResource(RESOURCE_BASE + SNAPSHOT_TAR_GZ)
    // RetentionView needs a real file so that it can parse the file name extension for compression format.
    val snapshotFile = temporaryFolderRule.newFile(SNAPSHOT_TAR_GZ)
    assertThat(url).isNotNull()
    with (FileOutputStream(snapshotFile)) {
      IOUtils.copy(url.openStream(), this)
    }
    retentionView.setSnapshotFile(snapshotFile)
    (AndroidExecutors.getInstance().ioThreadExecutor as BoundedTaskExecutor).waitAllTasksExecuted(5, TimeUnit.SECONDS)
    assertThat(retentionView.image).isNotNull()
  }

  @Test
  fun loadNullScreenshot() {
    retentionView.setSnapshotFile(null)
    assertThat(retentionView.image).isNull()
  }
}