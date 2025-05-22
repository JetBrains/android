/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport

import com.android.test.testutils.TestUtils.getWorkspaceRoot
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File

class DeployableFileTest {
  @get:Rule
  val timeout = Timeout.seconds(60)

  @Rule
  @JvmField
  val temporaryFolder = TemporaryFolder()

  @Test
  fun testFileName() {
    DeployableFile.Builder("myName").build().let {
      assertThat(it.fileName).isEqualTo("myName")
    }
  }

  @Test
  fun testIsExecutable() {
    DeployableFile.Builder("myName").setExecutable(true).build().let {
      assertThat(it.isExecutable).isTrue()
    }

    DeployableFile.Builder("myName").setExecutable(false).build().let {
      assertThat(it.isExecutable).isFalse()
    }
  }

  @Test
  fun testOnDeviceAbiFileNameFormat() {
    DeployableFile.Builder("myName").build().let {
      assertThat(it.onDeviceAbiFileNameFormat).isNull()
      assertThat(it.isAbiDependent).isFalse()
    }

    DeployableFile.Builder("myName").setOnDeviceAbiFileNameFormat("myFormat_%s").build().let {
      assertThat(it.onDeviceAbiFileNameFormat).isEqualTo("myFormat_%s")
      assertThat(it.isAbiDependent).isTrue()
    }
  }

  @Test
  fun getDirIsReleaseDir() {
    val releaseDir = temporaryFolder.newFolder("release")
    temporaryFolder.newFolder("dev")

    val hostFile = DeployableFile.Builder("myfile")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setIsRunningFromSources(false)
      .setHomePath(temporaryFolder.root.absolutePath)
      .build()

    assertThat(hostFile.dir).isEqualTo(releaseDir)
  }

  @Test
  fun getDirIsDevDir() {
    val devDir = temporaryFolder.newFolder("dev")

    val hostFile = DeployableFile.Builder("myfile")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setIsRunningFromSources(true)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    assertThat(hostFile.dir).isEqualTo(devDir)
  }

  @Test
  fun getDirAbsolutePath() {
    // To make this test work on both windows and linux machines, the path can't be hardcoded to one format. Therefore, we use workspace
    // root's path because it is formatted correctly.
    val targetDir = getWorkspaceRoot().toString()

    // Test running from sources (dev mode) - calling getDir() on the deployable file should return the dev directory.
    val hostFile1 = DeployableFile.Builder("myfile")
      .setReleaseDir(targetDir)
      .setDevDir(targetDir)
      .setIsRunningFromSources(true)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    assertThat(hostFile1.dir).isEqualTo(File(targetDir))

    // Test release mode - calling getDir() on the deployable file should return the release directory.
    val hostFile2 = DeployableFile.Builder("myfile")
      .setReleaseDir(targetDir)
      .setDevDir(targetDir)
      .setIsRunningFromSources(false)
      .setSourcesRoot(temporaryFolder.root.absolutePath)
      .build()

    assertThat(hostFile2.dir).isEqualTo(File(targetDir))
  }
}