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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.project.sync.hyperlink.EnableAndroidXHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.io.File

class AndroidXUsedReporterTest {
  private val expectedUrl = "https://developer.android.com/jetpack/androidx/migrate"
  @Test
  fun `expected quick fixes with properties file`() {
    val reporter = AndroidXUsedReporter()
    val mockedPropertiesFile = mock(File::class.java)
    val expectedPath = "/path/to/gradle.properties"
    Mockito.`when`(mockedPropertiesFile.exists()).thenReturn(true)
    Mockito.`when`(mockedPropertiesFile.path).thenReturn(expectedPath)
    val fixes = reporter.createQuickFixes(mockedPropertiesFile)
    assertThat(fixes).hasSize(3)
    assertThat(fixes[0]).isInstanceOf(EnableAndroidXHyperlink::class.java)
    assertThat(fixes[1]).isInstanceOf(OpenFileHyperlink::class.java)
    assertThat((fixes[1] as OpenFileHyperlink).filePath).isEqualTo(expectedPath)
    assertThat(fixes[2]).isInstanceOf(OpenUrlHyperlink::class.java)
    assertThat((fixes[2] as OpenUrlHyperlink).url).isEqualTo(expectedUrl)
  }

  @Test
  fun `expected quick fixes without properties file`() {
    val reporter = AndroidXUsedReporter()
    val mockedPropertiesFile = mock(File::class.java)
    Mockito.`when`(mockedPropertiesFile.exists()).thenReturn(false)
    val fixes = reporter.createQuickFixes(mockedPropertiesFile)
    assertThat(fixes).hasSize(2)
    assertThat(fixes[0]).isInstanceOf(EnableAndroidXHyperlink::class.java)
    assertThat(fixes[1]).isInstanceOf(OpenUrlHyperlink::class.java)
    assertThat((fixes[1] as OpenUrlHyperlink).url).isEqualTo(expectedUrl)
  }
}