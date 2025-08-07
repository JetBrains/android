/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.validation.android

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.InternedModels
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.testFramework.ProjectRule
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.charset.Charset
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [EncodingValidationStrategy].
 */
class EncodingValidationStrategyTest {
  @Mock
  private val myEncodings: EncodingProjectManager? = null
  private var myStrategy: EncodingValidationStrategy? = null

  @get:Rule
  val projectRule = ProjectRule()
  val project by lazy { projectRule.project }

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    whenever(myEncodings!!.defaultCharset).thenReturn(Charset.forName("ISO-8859-1"))
    myStrategy = EncodingValidationStrategy(project, myEncodings)
  }

  @Test
  fun testValidate() {
    val modelEncoding = "UTF-8"
    val androidModel = mock(GradleAndroidModel::class.java)
    whenever(androidModel.agpVersion).thenReturn(AgpVersion.parse("1.2.0"))
    val ideAndroidProject = AndroidProjectBuilder()
      .build()
      .invoke(
        "projectName",
        ":app",
        File("/"),
        File("/app"),
        "1.2.0",
        InternedModels(null)
      )
      .androidProject
      .let { androidProject ->
        androidProject.copy(
          javaCompileOptions = androidProject.javaCompileOptions?.copy(encoding = modelEncoding)
        )
      }
    whenever(androidModel.androidProject).thenAnswer { invocation: InvocationOnMock? -> ideAndroidProject }
    myStrategy!!.validate(mock(Module::class.java), androidModel)
    assertThat(myStrategy!!.mismatchingEncoding).isEqualTo(modelEncoding)
  }

  @Test
  fun testFixAndReportFoundIssues() {
    val syncMessages = GradleSyncMessages.getInstance(project)
    val mismatchingEncoding = "UTF-8"
    myStrategy!!.mismatchingEncoding = mismatchingEncoding
    myStrategy!!.fixAndReportFoundIssues()
    val message = syncMessages.reportedMessages.firstOrNull()
    assertThat(message).isNotNull()
    val text = message!!.text
    Truth.assertThat(text.split('\n')).hasSize(2)
    Truth.assertThat(text).startsWith("The project encoding (ISO-8859-1) has been reset")
    verify(myEncodings, times(1))?.let { it.defaultCharsetName = mismatchingEncoding }
  }

  @Test
  fun testFixAndReportFoundIssuesWithNoMismatch() {
    val syncMessages = GradleSyncMessages.getInstance(project)
    myStrategy!!.mismatchingEncoding = null
    myStrategy!!.fixAndReportFoundIssues()
    val message = syncMessages.reportedMessages.firstOrNull()
    assertThat(message).isNull()
    verify(myEncodings, never())?.let { it.defaultCharsetName = ArgumentMatchers.anyString() }
  }
}