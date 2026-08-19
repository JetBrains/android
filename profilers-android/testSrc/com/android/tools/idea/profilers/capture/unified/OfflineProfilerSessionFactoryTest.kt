/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.profilers.IntellijProfilerServices
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.ProfilerEmptyStateView
import com.android.tools.profilers.cpu.CpuCaptureStageView
import com.android.tools.profilers.memory.MemoryCaptureStageView
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockProjectEx
import com.intellij.mock.MockPsiManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.EmptyModuleManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.awt.BorderLayout
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JLayeredPane
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [OfflineProfilerSessionFactory], verifying fallback states and empty session initializations. */
class OfflineProfilerSessionFactoryTest {

  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var project: Project
  private lateinit var virtualFile: VirtualFile
  private lateinit var parentComponent: JPanel
  private lateinit var componentsProvider: (IntellijProfilerServices) -> IdeProfilerComponents
  private lateinit var mockIdeProfilerComponents: IdeProfilerComponents

  @Before
  fun setUp() {
    project = Mockito.spy(MockProjectEx(disposableRule.disposable))
    whenever(project.basePath).thenReturn("/mock/path")
    val mockProject = project as MockProjectEx
    mockProject.registerService(ModuleManager::class.java, EmptyModuleManager(project))
    mockProject.registerService(PsiManager::class.java, MockPsiManager(project))
    mockProject.registerService(CpuProfilerConfigsState::class.java, CpuProfilerConfigsState())

    virtualFile = mock()
    parentComponent = JPanel()
    mockIdeProfilerComponents = mock()
    val mockLoadingPanel = mock<LoadingPanel>()
    whenever(mockLoadingPanel.component).thenReturn(JPanel())
    whenever(mockIdeProfilerComponents.createLoadingPanel(any())).thenReturn(mockLoadingPanel)
    componentsProvider = { mockIdeProfilerComponents }
  }

  @Test
  fun testBuildSessionWithNonExistentFile() {
    whenever(virtualFile.path).thenReturn("/non/existent/trace/file.trace")
    whenever(virtualFile.name).thenReturn("file.trace")
    whenever(virtualFile.nameWithoutExtension).thenReturn("file")

    val session = buildSessionAndWait()

    assertThat(session).isNotNull()
    assertThat(session!!.profilers).isNotNull()
    assertThat(session.profilersView).isNotNull()
    assertThat(session.stageView).isNull()

    ApplicationManager.getApplication().invokeAndWait {
      assertThat(parentComponent.components).isNotEmpty()
      assertThat(parentComponent.components[0]).isInstanceOf(ProfilerEmptyStateView::class.java)
    }
  }

  @Test
  fun testBuildSessionWithEmptyFile() {
    val emptyFile = File.createTempFile("empty", ".trace")
    emptyFile.deleteOnExit()

    whenever(virtualFile.path).thenReturn(emptyFile.absolutePath)
    whenever(virtualFile.name).thenReturn(emptyFile.name)
    whenever(virtualFile.nameWithoutExtension).thenReturn(emptyFile.nameWithoutExtension)

    val session = buildSessionAndWait()

    assertThat(session).isNotNull()
    assertThat(session!!.profilers).isNotNull()
    assertThat(session.profilersView).isNotNull()
    assertThat(session.stageView).isNull()

    ApplicationManager.getApplication().invokeAndWait {
      assertThat(parentComponent.components).isNotEmpty()
      assertThat(parentComponent.components[0]).isInstanceOf(ProfilerEmptyStateView::class.java)
    }
  }

  @Test
  fun testBuildSessionWithValidCpuTrace() {
    val validFile = File.createTempFile("valid", ".trace")
    validFile.writeText("trace content")
    validFile.deleteOnExit()

    whenever(virtualFile.path).thenReturn(validFile.absolutePath)
    whenever(virtualFile.name).thenReturn(validFile.name)
    whenever(virtualFile.nameWithoutExtension).thenReturn(validFile.nameWithoutExtension)

    val session = buildSessionAndWait()

    assertThat(session).isNotNull()
    assertThat(session!!.stageView).isInstanceOf(CpuCaptureStageView::class.java)
    assertThat(session.profilersView.component).isInstanceOf(TooltipLayeredPane::class.java)
    val layeredPane = session.profilersView.component as JLayeredPane
    assertThat(layeredPane.getLayer(session.profilersView.stageComponent)).isEqualTo(JLayeredPane.DEFAULT_LAYER)
    val stageLayout = session.profilersView.stageComponent.layout as BorderLayout
    assertThat(stageLayout.getConstraints(session.stageView!!.component)).isEqualTo(BorderLayout.CENTER)
  }

  @Test
  fun testBuildSessionWithValidMemoryTrace() {
    val validFile = File.createTempFile("valid", ".hprof")
    validFile.writeText("hprof content")
    validFile.deleteOnExit()

    whenever(virtualFile.path).thenReturn(validFile.absolutePath)
    whenever(virtualFile.name).thenReturn(validFile.name)
    whenever(virtualFile.extension).thenReturn("hprof")
    whenever(virtualFile.nameWithoutExtension).thenReturn(validFile.nameWithoutExtension)

    val session = buildSessionAndWait()

    assertThat(session).isNotNull()
    assertThat(session!!.stageView).isInstanceOf(MemoryCaptureStageView::class.java)
    assertThat(session.profilersView.component).isInstanceOf(TooltipLayeredPane::class.java)
    val layeredPane = session.profilersView.component as JLayeredPane
    assertThat(layeredPane.getLayer(session.profilersView.stageComponent)).isEqualTo(JLayeredPane.DEFAULT_LAYER)
    val stageLayout = session.profilersView.stageComponent.layout as BorderLayout
    assertThat(stageLayout.getConstraints(session.stageView!!.component)).isEqualTo(BorderLayout.CENTER)
  }

  private fun buildSessionAndWait(): OfflineProfilerSession? {
    var session: OfflineProfilerSession? = null
    val latch = CountDownLatch(1)
    OfflineProfilerSessionFactory.buildSessionAsync(project, virtualFile, parentComponent, disposableRule.disposable, componentsProvider) {
      s ->
      session = s
      latch.countDown()
    }
    latch.await(5, TimeUnit.SECONDS)
    return session
  }
}
