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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class AppInspectionToolWindowManagerListenerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)
  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService)
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  private val ideServices =
    object : AppInspectionIdeServicesAdapter() {
      var notificationText: String? = null

      override fun showNotification(content: String, title: String, severity: AppInspectionIdeServices.Severity, action: AnAction?) {
        notificationText = content
      }
    }

  @get:Rule val ruleChain = RuleChain(projectRule, disposableRule, grpcServerRule, appInspectionServiceRule)

  @Test
  fun testShowBubbleWhenInspectionIsAndIsNotRunning() = runBlocking {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestAppInspectorCommandHandler(timer))
    val uiDispatcher = Dispatchers.EDT as CoroutineDispatcher
    val inspectionView =
      withContext(uiDispatcher) {
        AppInspectionView(
          projectRule.project,
          appInspectionServiceRule.apiServices,
          ideServices,
          appInspectionServiceRule.scope,
          uiDispatcher,
        ) {
          it.name == FakeTransportService.FAKE_PROCESS_NAME
        }
      }
    Disposer.register(disposableRule.disposable, inspectionView)

    val toolWindow = createFakeToolWindow(projectRule.project, disposableRule.disposable, "App Inspection")
    val listener = AppInspectionToolWindowManagerListener(projectRule.project, ideServices, toolWindow, inspectionView)
    projectRule.project.messageBus.connect(disposableRule.disposable).subscribe(ToolWindowManagerListener.TOPIC, listener)

    // bubble isn't shown when inspection not running
    toolWindow.show()
    toolWindow.hide()
    assertThat(ideServices.notificationText).isNull()
    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)
    // Wait for inspection view to load inspectors.
    inspectionView.tabsChangedFlow.first()
    // Check bubble is shown.
    toolWindow.show()
    toolWindow.hide()
    assertThat(ideServices.notificationText).isEqualTo(AppInspectionBundle.message("inspection.is.running"))
  }
}
