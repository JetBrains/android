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
package com.android.tools.idea.res

import com.android.SdkConstants.FD_SAMPLE_DATA
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.toPathString
import com.android.tools.idea.util.toVirtualFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MainContentRootSampleDataDirectoryProviderTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().initAndroid(true)
  private lateinit var provider: MainContentRootSampleDataDirectoryProvider
  private lateinit var mainContentRoot: VirtualFile

  @Before
  fun setUp() {
    provider = MainContentRootSampleDataDirectoryProvider(projectRule.module)
    mainContentRoot = projectRule.fixture.findFileInTempDir("")
  }

  @Test
  fun getSampleDataDirectory() {
    val expectedPath = mainContentRoot.toPathString().resolve(FD_SAMPLE_DATA)
    assertThat(provider.getSampleDataDirectory()).isEqualTo(expectedPath)
  }

  @Test
  fun getOrCreateSampleDataDirectory() {
    val sampleDataDir =
      WriteAction.computeAndWait(
        ThrowableComputable<VirtualFile?, Throwable> {
          provider.getOrCreateSampleDataDirectory().toVirtualFile()
        }
      )

    assertThat(sampleDataDir).isNotNull()
    assertThat(sampleDataDir!!.parent).isEqualTo(mainContentRoot)
    assertThat(sampleDataDir.exists()).isTrue()
  }
}
