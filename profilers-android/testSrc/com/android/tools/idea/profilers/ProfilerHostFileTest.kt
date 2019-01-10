/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilerHostFileTest {
  @Rule
  @JvmField
  val temporaryFolder = TemporaryFolder()

  @Test
  fun testFileName() {
    ProfilerHostFileBuilder("myName").build().let {
      assertThat(it.fileName).isEqualTo("myName")
    }
  }

  @Test
  fun testIsExecutable() {
    ProfilerHostFileBuilder("myName").setExecutable(true).build().let {
      assertThat(it.isExecutable).isTrue()
    }

    ProfilerHostFileBuilder("myName").setExecutable(false).build().let {
      assertThat(it.isExecutable).isFalse()
    }
  }

  @Test
  fun testOnDeviceAbiFileNameFormat() {
    ProfilerHostFileBuilder("myName").build().let {
      assertThat(it.onDeviceAbiFileNameFormat).isNull()
      assertThat(it.isAbiDependent).isFalse()
    }

    ProfilerHostFileBuilder("myName").setOnDeviceAbiFileNameFormat("myFormat_%s").build().let {
      assertThat(it.onDeviceAbiFileNameFormat).isEqualTo("myFormat_%s")
      assertThat(it.isAbiDependent).isTrue()
    }
  }

  @Test
  fun getDirPrefersReleaseDirOverDevDir() {
    val releaseDir = temporaryFolder.newFolder("release")
    temporaryFolder.newFolder("dev")

    val hostFile = ProfilerHostFileBuilder("myfile")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setHomePathSupplier(temporaryFolder.root::getAbsolutePath)
      .build()

    assertThat(hostFile.dir).isEqualTo(releaseDir)
  }

  @Test
  fun getDirIsDevDirIfNoReleaseDir() {
    val devDir = temporaryFolder.newFolder("dev")

    val hostFile = ProfilerHostFileBuilder("myfile")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setHomePathSupplier(temporaryFolder.root::getAbsolutePath)
      .build()

    assertThat(hostFile.dir).isEqualTo(devDir)
  }
}