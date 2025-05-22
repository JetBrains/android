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

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.replaceService
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.net.URI
import java.io.File

class BenchmarkLinkListenerTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val rules = checkNotNull(RuleChain.outerRule(projectRule).around(temporaryDirectoryRule))
  private val mockEditorService = mock<FileEditorManager>()
  private val mockBrowserService = mock<BrowserLauncher>()
  private val fileCapture = ArgumentCaptor.forClass(FileEditorNavigatable::class.java)
  private lateinit var componentStack: ComponentStack

  @Before
  fun setup() {
    componentStack = ComponentStack(projectRule.project)
    componentStack.registerServiceInstance(FileEditorManager::class.java, mockEditorService)
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, mockBrowserService, projectRule.testRootDisposable)
    whenever(mockEditorService.openEditor(any(), anyBoolean())).thenCallRealMethod()
    whenever(mockEditorService.openFileEditor(fileCapture.capture(), any())).thenReturn(ArrayList<FileEditor>())
  }

  @After
  fun tearDown() {
    componentStack.restore()
  }

  @Test
  fun listenerOpensV2FileLink() {
    val listener = BenchmarkLinkListener(projectRule.project)
    val traceFile = FileUtil.createTempFile("traceFile", ".trace")
    traceFile.deleteOnExit()
    ApplicationManager.getApplication().invokeAndWait {
      listener.hyperlinkClicked("file://${traceFile.name}")
    }
    assertThat(fileCapture.value.file.name).isEqualTo(traceFile.name)
  }

  @Test
  fun listenerOpensV3FileLink() {
    val listener = BenchmarkLinkListener(projectRule.project)
    val traceFile = FileUtil.createTempFile("traceFile", ".trace")
    traceFile.deleteOnExit()
    listener.hyperlinkClicked("uri://${traceFile.name}?param1=value1&param2=value2")
    assertThat(fileCapture.value.file.name).isEqualTo(traceFile.name)
  }

  @Test
  fun listenerOpensV2FileLinkInPerfettoWeb() {
    val fakePerfettoLoader = FakePerfettoLoader()
    assertThat(fakePerfettoLoader.callCount).isEqualTo(0)
    val listener =
      BenchmarkLinkListener(projectRule.project, isPerfettoWebLoaderEnabled = true, openTraceInPerfettoWebLoader = fakePerfettoLoader::load)
    val traceFile = FileUtil.createTempFile("traceFile", ".perfetto-trace")
    traceFile.deleteOnExit()
    listener.hyperlinkClicked("file://${traceFile.name}")
    with(fakePerfettoLoader) {
      assertThat(callCount).isEqualTo(1)
      assertThat(capturedFile.name).isEqualTo(traceFile.name)
      assertThat(capturedQuery).isEqualTo(null)
    }
  }

  @Test
  fun listenerOpensV3FileLinkInPerfettoWeb() {
    val fakePerfettoLoader = FakePerfettoLoader()
    assertThat(fakePerfettoLoader.callCount).isEqualTo(0)
    val listener =
      BenchmarkLinkListener(projectRule.project, isPerfettoWebLoaderEnabled = true, openTraceInPerfettoWebLoader = fakePerfettoLoader::load)
    val traceFile = FileUtil.createTempFile("traceFile", ".perfetto-trace")
    traceFile.deleteOnExit()
    val query = "param1=value1&param%202=value%202"
    listener.hyperlinkClicked("uri://${traceFile.name}?$query")
    with(fakePerfettoLoader) {
      assertThat(callCount).isEqualTo(1)
      assertThat(capturedFile.name).isEqualTo(traceFile.name)
      assertThat(capturedQuery).isEqualTo(query)
    }
  }

  @Test
  fun listenerOpensHttpUrl() {
    listenerOpensWebLink("http://foo.bar.baz")
  }

  @Test
  fun listenerOpensHttpsUrl() {
    listenerOpensWebLink("https://foo.bar.baz")
  }

  private fun listenerOpensWebLink(url: String) {
    val listener = BenchmarkLinkListener(projectRule.project)
    listener.hyperlinkClicked(url)
    verify(mockBrowserService).browse(URI.create(url))
  }

  @Test
  fun listenerIgnoresUnrecognizedLinks() {
    val listener = BenchmarkLinkListener(projectRule.project)
    listener.hyperlinkClicked("qwerty")
    listener.hyperlinkClicked("asdf")
    listener.hyperlinkClicked("ftp://abc.def")
    listener.hyperlinkClicked("vnc://192.168.1.100")
    verifyNoInteractions(mockBrowserService)
    verifyNoInteractions(mockEditorService)
  }
}

private class FakePerfettoLoader {
  lateinit var capturedFile: File
    private set
  var capturedQuery: String? = null
    private set
  var callCount = 0
    private set

  fun load(file: File, query: String?) {
    callCount++
    this.capturedFile = file
    this.capturedQuery = query
  }
}