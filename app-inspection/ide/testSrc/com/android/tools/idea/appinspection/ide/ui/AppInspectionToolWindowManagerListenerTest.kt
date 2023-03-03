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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.registerServiceInstance
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInspectionToolWindowManagerListenerTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)
  private val grpcServerRule =
    FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService)
  private val appInspectionServiceRule =
    AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  private class FakeToolWindow(
    project: Project,
    private val toolWindowManager: ToolWindowManager,
    ideServices: AppInspectionIdeServices,
    inspectionView: AppInspectionView
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

    val listener =
      AppInspectionToolWindowManagerListener(project, ideServices, this, inspectionView)

    var shouldBeAvailable = true
    var visible = false

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      shouldBeAvailable = available
    }
    override fun isAvailable() = shouldBeAvailable
    override fun show(runnable: Runnable?) {
      visible = true
      listener.stateChanged(toolWindowManager)
    }
    override fun hide(runnable: Runnable?) {
      visible = false
      listener.stateChanged(toolWindowManager)
    }
    override fun isVisible(): Boolean {
      return visible
    }
  }
  private val ideServices =
    object : AppInspectionIdeServicesAdapter() {
      var notificationText: String? = null

      override fun showNotification(
        content: String,
        title: String,
        severity: AppInspectionIdeServices.Severity,
        hyperlinkClicked: () -> Unit
      ) {
        notificationText = content
      }
    }

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!.around(projectRule)!!
  @Test
  fun testShowBubbleWhenInspectionIsAndIsNotRunning() = runBlocking {
    transportService.setCommandHandler(
      Commands.Command.CommandType.APP_INSPECTION,
      TestAppInspectorCommandHandler(timer)
    )
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    val inspectionView =
      withContext(uiDispatcher) {
        AppInspectionView(
          projectRule.project,
          appInspectionServiceRule.apiServices,
          ideServices,
          appInspectionServiceRule.scope,
          uiDispatcher
        ) {
          it.name == FakeTransportService.FAKE_PROCESS_NAME
        }
      }
    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
    lateinit var toolWindow: ToolWindow
    val toolWindowManager =
      object : ToolWindowHeadlessManagerImpl(projectRule.project) {
        override fun getToolWindow(id: String?): ToolWindow {
          return toolWindow
        }
      }
    toolWindow = FakeToolWindow(projectRule.project, toolWindowManager, ideServices, inspectionView)
    projectRule.project.registerServiceInstance(ToolWindowManager::class.java, toolWindowManager)
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
    assertThat(ideServices.notificationText)
      .isEqualTo(AppInspectionBundle.message("inspection.is.running"))
  }
}
