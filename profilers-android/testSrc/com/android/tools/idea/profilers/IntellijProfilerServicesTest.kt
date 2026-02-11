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

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.RegisteredDependencyId
import com.android.tools.idea.projectsystem.RegisteredDependencyQueryId
import com.android.tools.idea.projectsystem.RegisteringModuleSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidRunConfigurationModule
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState
import com.android.tools.nativeSymbolizer.SymbolFilesLocator
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.mock.MockProjectEx
import com.intellij.mock.MockPsiManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.EmptyModuleManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IntellijProfilerServicesTest {

  private lateinit var project: Project
  private lateinit var intellijProfilerServices: IntellijProfilerServices

  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val disposableRule = DisposableRule()

  private val spiesToDispose = mutableListOf<IntellijProfilerServices>()

  @After
  fun tearDownAfterTest() {
    Disposer.dispose(intellijProfilerServices)
    for (spy in spiesToDispose) {
      Disposer.dispose(spy)
    }
  }

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
      StudioFlags.PROFILER_LEAKCANARY.clearOverride()
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
  fun featureFlagConfigLeakCanaryEnabled() {
    StudioFlags.PROFILER_LEAKCANARY.override(true)
    assertTrue(IntellijProfilerServices.FeatureConfigProd().isLeakCanaryEnabled)
  }

  @Test
  fun featureFlagConfigLeakCanaryDisabled() {
    StudioFlags.PROFILER_LEAKCANARY.override(false)
    assertFalse(IntellijProfilerServices.FeatureConfigProd().isLeakCanaryEnabled)
  }

  @Test
  fun featureFlagSystemTraceInEditorDisabled() {
    StudioFlags.PROFILER_SYSTEM_TRACE_IN_EDITOR.override(false)
    assertFalse(IntellijProfilerServices.FeatureConfigProd().isSystemTraceInEditorEnabled)
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
    val resultNew = intellijProfilerServices.getTaskCpuProfilerConfigs(9)
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
    } finally {
      Disposer.dispose(intellijProfilerServicesNow)
    }
  }

  @Test
  fun testIsTaskSupportedOnStartup() {
    // The following tasks are supported on startup.
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.NATIVE_ALLOCATIONS)).isTrue()
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.CALLSTACK_SAMPLE)).isTrue()
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)).isTrue()
    // The following tasks are NOT supported on startup.
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.HEAP_DUMP)).isFalse()
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)).isFalse()
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.LIVE_VIEW)).isFalse()
    assertThat(intellijProfilerServices.isTaskSupportedOnStartup(ProfilerTaskType.UNSPECIFIED)).isFalse()
  }

  @Test
  fun testAddDependencyDoesNothingIfDependencyAlreadyExists() {
    val artifact = GoogleMavenArtifactId.LEAKCANARY
    val mocks = setupDependencyMocks()
    doReturn(false).whenever(mocks.services).showConfirmationDialog(any(), any(), any())
    whenever(mocks.androidModuleSystem.hasResolvedDependency(any())).thenReturn(true)

    mocks.services.addDependency(artifact, DependencyType.DEBUG_IMPLEMENTATION)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    verify(mocks.services, never()).showConfirmationDialog(any(), any(), any())
    verify(mocks.registeringModuleSystem, never()).registerDependency(any<GoogleMavenArtifactId>(), any())
  }

  @Test
  fun testAddDependencyShowsDialogIfDependencyIsMissing() {
    val artifact = GoogleMavenArtifactId.LEAKCANARY
    val mocks = setupDependencyMocks()
    whenever(mocks.androidModuleSystem.hasResolvedDependency(any())).thenReturn(false)
    doReturn(false).whenever(mocks.services).showConfirmationDialog(any(), any(), any())

    mocks.services.addDependency(artifact, DependencyType.DEBUG_IMPLEMENTATION)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    verify(mocks.services).showConfirmationDialog(any(), eq(artifact), eq(DependencyType.DEBUG_IMPLEMENTATION))
    verify(mocks.registeringModuleSystem, never()).registerDependency(any<GoogleMavenArtifactId>(), any())
  }

  @Test
  fun testAddDependencyAddsDependencyAndSyncsIfDialogConfirmed() {
    val artifact = GoogleMavenArtifactId.LEAKCANARY
    val mocks = setupDependencyMocks()
    whenever(mocks.androidModuleSystem.hasResolvedDependency(any())).thenReturn(false)
    doReturn(true).whenever(mocks.services).showConfirmationDialog(any(), any(), any())
    whenever(mocks.syncManager.requestSyncProject(any())).thenReturn(mock())

    mocks.services.addDependency(artifact, DependencyType.IMPLEMENTATION)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    verify(mocks.services).showConfirmationDialog(any(), eq(artifact), eq(DependencyType.IMPLEMENTATION))
    verify(mocks.registeringModuleSystem).registerDependency(eq(artifact), eq(DependencyType.IMPLEMENTATION))
    verify(mocks.syncManager).requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
  }

  @Test
  fun testAddDependencyReturnsFalseIfModuleIsNull() {
    val artifact = GoogleMavenArtifactId.LEAKCANARY
    val mocks = setupDependencyMocks()
    whenever(mocks.configurationModule.module).thenReturn(null)

    val future = mocks.services.addDependency(artifact, DependencyType.IMPLEMENTATION)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    assertThat(future.get()).isFalse()
    verify(mocks.services, never()).showConfirmationDialog(any(), any(), any())
  }

  @Test
  fun testAddDependencyReturnsFalseIfRegisteringModuleSystemIsNull() {
    val artifact = GoogleMavenArtifactId.LEAKCANARY
    val mocks = setupDependencyMocks()
    whenever(mocks.androidModuleSystem.getRegisteringModuleSystem()).thenReturn(null)

    val future = mocks.services.addDependency(artifact, DependencyType.IMPLEMENTATION)
    ApplicationManager.getApplication().invokeAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    assertThat(future.get()).isFalse()
    verify(mocks.services, never()).showConfirmationDialog(any(), any(), any())
  }

  private data class DependencyMocks(
    val services: IntellijProfilerServices,
    val androidModuleSystem: AndroidModuleSystem,
    val registeringModuleSystem: RegisteringModuleSystem<RegisteredDependencyQueryId, RegisteredDependencyId>,
    val syncManager: ProjectSystemSyncManager,
    val runManager: RunManager,
    val configurationModule: AndroidRunConfigurationModule,
  )

  private fun setupDependencyMocks(): DependencyMocks {
    val projectSystemService = mock<ProjectSystemService>()
    val androidProjectSystem = mock<AndroidProjectSystem>()
    val androidModuleSystem = mock<AndroidModuleSystem>()
    val registeringModuleSystem = mock<RegisteringModuleSystem<RegisteredDependencyQueryId, RegisteredDependencyId>>()
    val syncManager = mock<ProjectSystemSyncManager>()
    val runManager = mock<RunManager>()
    val configurationSettings = mock<RunnerAndConfigurationSettings>()
    val androidConfiguration = mock<AndroidRunConfigurationBase>()
    val configurationModule = mock<AndroidRunConfigurationModule>()
    val module = mock<Module>()

    // Register services on the project so static helpers like ProjectSystemUtil can find them
    (project as MockProjectEx).registerService(ProjectSystemService::class.java, projectSystemService)
    (project as MockProjectEx).registerService(RunManager::class.java, runManager)

    whenever(projectSystemService.projectSystem).thenReturn(androidProjectSystem)
    whenever(androidProjectSystem.getModuleSystem(any())).thenReturn(androidModuleSystem)
    whenever(androidProjectSystem.getSyncManager()).thenReturn(syncManager)
    whenever(androidModuleSystem.getRegisteringModuleSystem()).thenReturn(registeringModuleSystem)

    whenever(runManager.selectedConfiguration).thenReturn(configurationSettings)
    whenever(configurationSettings.configuration).thenReturn(androidConfiguration)
    whenever(androidConfiguration.configurationModule).thenReturn(configurationModule)
    whenever(configurationModule.module).thenReturn(module)
    whenever(module.name).thenReturn("app")
    whenever(module.project).thenReturn(project)

    // Re-create services as a spy for this test
    val actualInstance = IntellijProfilerServices(project, mock<SymbolFilesLocator>())
    Disposer.register(disposableRule.disposable, actualInstance)
    val servicesSpy = spy(actualInstance)
    spiesToDispose.add(actualInstance)

    return DependencyMocks(servicesSpy, androidModuleSystem, registeringModuleSystem, syncManager, runManager, configurationModule)
  }

  private fun mockProjectAttributes(project: Project) {
    val moduleManager = EmptyModuleManager(project)
    val psiManger = MockPsiManager(project)
    val cpuProfilerStateSpy = Mockito.spy(CpuProfilerConfigsState())
    whenever(project.getService(CpuProfilerConfigsState::class.java)).thenReturn(cpuProfilerStateSpy)
    whenever(project.getService(ModuleManager::class.java)).thenReturn(moduleManager)
    whenever(project.getService(PsiManager::class.java)).thenReturn(psiManger)
  }
}
