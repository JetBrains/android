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
package com.android.tools.idea.profilers.profilingconfig

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.event.FakeEventService
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.JBSplitter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent

class CpuProfilingConfigurationsDialogTest {

  private lateinit var configurations: CpuProfilingConfigurationsDialog.ProfilingConfigurable
  private lateinit var project: Project
  private lateinit var model: CpuProfilerConfigModel
  private lateinit var featureTracker: FeatureTracker
  private lateinit var myStage: CpuProfilerStage
  private var deviceLevel = 0

  private val myTimer = FakeTimer()
  private val myIdeServices = FakeIdeProfilerServices()
  private val myTransportService = FakeTransportService(myTimer)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuProfilingConfigDialogTestChannel", myTransportService, FakeEventService())

  @get:Rule
  val myEdtRule = EdtRule()

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    project = MockProjectEx(disposableRule.disposable)
    myStage = CpuProfilerStage(profilers)
    model =  CpuProfilerConfigModel(profilers, myStage)
    model.profilingConfiguration = FakeIdeProfilerServices.ATRACE_CONFIG
    featureTracker = FakeFeatureTracker()
    configurations = CpuProfilingConfigurationsDialog.ProfilingConfigurable(project, model, deviceLevel, featureTracker)
  }

  @After
  fun tearDown() {
    // Need to clear any override we use inside tests here.
    StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
  }

  @Test
  fun configDialogIconWhenTaskBasedUxEnabled() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(true)
    var profilingComponent : JComponent = configurations.createComponent()!!
    assertThat(profilingComponent).isNotNull()
    val splitter: JBSplitter = TreeWalker(profilingComponent).descendants().filterIsInstance<JBSplitter>().first()
    val firstComponent = splitter.firstComponent
    val actionPanel: CommonActionsPanel = TreeWalker(firstComponent).descendants().filterIsInstance<CommonActionsPanel>().first()
    // Add, remove, up and down buttons should be null
    val addButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.ADD)
    assertThat(addButtonAction).isNull()
    val downButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.DOWN)
    assertThat(downButtonAction).isNull()
    val upButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.UP)
    assertThat(upButtonAction).isNull()
    val removeButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.REMOVE)
    assertThat(removeButtonAction).isNull()
  }

  @Test
  fun configDialogIconWhenTaskBasedUxDisabled() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(false)
    var profilingComponent : JComponent = configurations.createComponent()!!
    assertThat(profilingComponent).isNotNull()
    val splitter: JBSplitter = TreeWalker(profilingComponent).descendants().filterIsInstance<JBSplitter>().first()
    val firstComponent = splitter.firstComponent
    val actionPanel: CommonActionsPanel = TreeWalker(firstComponent).descendants().filterIsInstance<CommonActionsPanel>().first()
    // Add, remove, up and down buttons should not be null
    val addButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.ADD)
    assertThat(addButtonAction).isNotNull()
    val downButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.DOWN)
    assertThat(downButtonAction).isNotNull()
    val upButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.UP)
    assertThat(upButtonAction).isNotNull()
    val removeButtonAction = actionPanel.getAnAction(CommonActionsPanel.Buttons.REMOVE)
    assertThat(removeButtonAction).isNotNull()
  }
}