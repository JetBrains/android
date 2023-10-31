/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TemporaryDirectory
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor

class BenchmarkLinkListenerTest {

  private val projectRule = AndroidProjectRule.inMemory()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val rules = RuleChain.outerRule(projectRule).around(temporaryDirectoryRule)
  private val mockEditorService = mock<FileEditorManager>()
  private val fileCapture = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
  private lateinit var componentStack: ComponentStack;
  @Before
  fun setup() {
    componentStack = ComponentStack(projectRule.project)
    componentStack.registerServiceInstance(FileEditorManager::class.java, mockEditorService)
    whenever(mockEditorService.openEditor(fileCapture.capture(), any())).thenReturn(ArrayList<FileEditor>())
  }

  @After
  fun tearDown() {
    componentStack.restore()
  }

  @Test
  fun listenerOpensProvider() {
    val listener = BenchmarkLinkListener(projectRule.project)
    val traceFile = FileUtil.createTempFile("traceFile",".trace")
    traceFile.deleteOnExit()
    listener.hyperlinkClicked("file://${traceFile.name}")
    assertThat(fileCapture.value.file.name).isEqualTo(traceFile.name)
  }
}