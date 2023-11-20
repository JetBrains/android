/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.testutils.MockitoKt
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState
import com.android.tools.nativeSymbolizer.SymbolFilesLocator
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockProjectEx
import com.intellij.mock.MockPsiManager
import com.intellij.openapi.module.EmptyModuleManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntellijProfilerServicesTest {

  private lateinit var project: Project
  private lateinit var intellijProfilerServices: IntellijProfilerServices

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @Before
  fun before() {
    project = Mockito.spy(MockProjectEx(disposableRule.disposable))
    mockProjectAttributes(project)
    intellijProfilerServices = IntellijProfilerServices(project, Mockito.mock(SymbolFilesLocator::class.java))
    Disposer.register(disposableRule.disposable, intellijProfilerServices)
  }

  @After
  fun after() {
    Disposer.dispose(intellijProfilerServices)
  }

  companion object {
    @JvmStatic
    @AfterClass
    fun tearDown() {
      StudioFlags.PROFILER_TRACEBOX.clearOverride()
      StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
    }
  }

  @Test
  fun featureFlagConfigTraceBoxEnabled() {
    StudioFlags.PROFILER_TRACEBOX.override(true)
    assertTrue(IntellijProfilerServices.FeatureConfigProd().isTraceboxEnabled)
  }

  @Test
  fun featureFlagConfigTraceBoxDisabled() {
    StudioFlags.PROFILER_TRACEBOX.override(false)
    assertFalse(IntellijProfilerServices.FeatureConfigProd().isTraceboxEnabled)
  }

  @Test
  fun testGetTaskCpuProfilerConfigs() {
    val result = intellijProfilerServices.getTaskCpuProfilerConfigs(8)
    assertThat(result.size).isEqualTo(5)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
    assertThat(result[3].name).isEqualTo("Native Allocations")
    assertThat(result[4].name).isEqualTo("System Trace")
  }

  @Test
  fun testGetTaskCpuProfilerConfigsWhenProjectStateChanged() {
    val result = intellijProfilerServices.getTaskCpuProfilerConfigs(9)
    assertThat(result.size).isEqualTo(5)
    assertThat(result[0].name).isEqualTo("Callstack Sample")
    assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
    assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
    assertThat(result[3].name).isEqualTo("Native Allocations")
    assertThat(result[4].name).isEqualTo("System Trace")

    val configsToSave: ArrayList<CpuProfilerConfig> = ArrayList()
    configsToSave.add(CpuProfilerConfig("HelloTest1", CpuProfilerConfig.Technology.INSTRUMENTED_JAVA))
    configsToSave.add(CpuProfilerConfig("HelloTest2", CpuProfilerConfig.Technology.SAMPLED_NATIVE))
    // Update configs
    CpuProfilerConfigsState.getInstance(project).taskConfigs = configsToSave

    // Updated config should be reflected
    val resultNew = intellijProfilerServices.getTaskCpuProfilerConfigs(9);
    assertThat(resultNew.size).isEqualTo(2)
    assertThat(resultNew[0].name).isEqualTo("HelloTest1")
    assertThat(resultNew[1].name).isEqualTo("HelloTest2")
  }

  @Test
  fun testGetNativeMemorySamplingRateForCurrentConfigForTaskBased() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(true)
    project = Mockito.spy(MockProjectEx(disposableRule.disposable))
    mockProjectAttributes(project)
    val intellijProfilerServicesNow = IntellijProfilerServices(project, Mockito.mock(SymbolFilesLocator::class.java))
    Disposer.register(disposableRule.disposable, intellijProfilerServicesNow)
    try {
      val result = intellijProfilerServicesNow.getTaskCpuProfilerConfigs(9)
      assertThat(result.size).isEqualTo(5)
      assertThat(result[0].name).isEqualTo("Callstack Sample")
      assertThat(result[1].name).isEqualTo("Java/Kotlin Method Trace")
      assertThat(result[2].name).isEqualTo("Java/Kotlin Method Sample (legacy)")
      assertThat(result[3].name).isEqualTo("Native Allocations")
      assertThat(result[4].name).isEqualTo("System Trace")

      // 2048 is default samplingRateBytes value for native memory
      assertEquals(intellijProfilerServicesNow.nativeAllocationsMemorySamplingRate, 2048)
    }
    finally {
      Disposer.dispose(intellijProfilerServicesNow)
    }
  }

  private fun mockProjectAttributes(project: Project) {
    val moduleManager = EmptyModuleManager(project)
    val psiManger = MockPsiManager(project)
    val cpuProfilerStateSpy = Mockito.spy(CpuProfilerConfigsState())
    MockitoKt.whenever(project.getService(CpuProfilerConfigsState::class.java)).thenReturn(cpuProfilerStateSpy)
    MockitoKt.whenever(project.getService(ModuleManager::class.java)).thenReturn(moduleManager)
    MockitoKt.whenever(project.getService(PsiManager::class.java)).thenReturn(psiManger)
  }
}