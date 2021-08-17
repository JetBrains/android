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

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class BenchmarkLinkListenerTest {

  private val projectRule = AndroidProjectRule.inMemory()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val rules = RuleChain.outerRule(projectRule).around(temporaryDirectoryRule)

  private val mockEditorService = mock<FileEditorProviderManager>()
  private val fileCapture = ArgumentCaptor.forClass(VirtualFile::class.java)
  private val testProvider = mock<FileEditorProvider>()
  @Before
  fun setup() {
    projectRule.replaceService(FileEditorProviderManager::class.java, mockEditorService)
    `when`(mockEditorService.getProviders(eq(projectRule.project), fileCapture.capture())).thenReturn(arrayOf(testProvider))
  }

  @Test
  fun listenerOpensProvider() {
    val listener = BenchmarkLinkListener(projectRule.project)
    val traceFile = temporaryDirectoryRule.createVirtualFile("traceFile.trace")
    listener.hyperlinkClicked("file://${traceFile.name}")
    verify(testProvider, times(1)).createEditor(projectRule.project, fileCapture.value)
    assertThat(fileCapture.value.name).isEqualTo(traceFile.name)
  }
}