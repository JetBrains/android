package com.android.tools.idea.appinspection.ide

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.ui.AppInspectionToolWindowManagerListener
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.inspector.api.service.TestAppInspectionIdeServices
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.TestAppInspectorCommandHandler
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CompletableDeferred
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

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionViewTest", transportService, transportService)!!
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)
  private val projectRule = AndroidProjectRule.inMemory().initAndroid(false)

  private class FakeToolWindow(
    project: Project,
    private val toolWindowManager: ToolWindowManager,
    private val listener: AppInspectionToolWindowManagerListener
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    var shouldBeAvailable = true
    var visible = true

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

  private val ideServices = TestAppInspectionIdeServices()

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!.around(projectRule)!!

  @Test
  fun testShowBubbleWhenInspectionIsAndIsNotRunning() = runBlocking {
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestAppInspectorCommandHandler(timer))
    val uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    val inspectionView = withContext(uiDispatcher) {
      AppInspectionView(
        projectRule.project, appInspectionServiceRule.apiServices, ideServices,
        appInspectionServiceRule.scope, uiDispatcher
      ) {
        listOf(FakeTransportService.FAKE_PROCESS_NAME)
      }
    }

    Disposer.register(projectRule.fixture.testRootDisposable, inspectionView)
    val listener = AppInspectionToolWindowManagerListener(projectRule.project, inspectionView)

    val balloonShownDeferred = CompletableDeferred<String>()
    lateinit var toolWindow: ToolWindow
    val toolWindowManager = object : ToolWindowHeadlessManagerImpl(projectRule.project) {

      override fun getToolWindow(id: String?): ToolWindow {
        return toolWindow
      }

      override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String) {
        balloonShownDeferred.complete(htmlBody)
      }
    }
    toolWindow = FakeToolWindow(projectRule.project, toolWindowManager, listener)
    projectRule.project.registerServiceInstance(ToolWindowManager::class.java, toolWindowManager)

    // bubble isn't shown when inspection not running
    toolWindow.show()
    toolWindow.hide()
    assertThat(balloonShownDeferred.isActive).isTrue()

    // Attach to a fake process.
    transportService.addDevice(FakeTransportService.FAKE_DEVICE)
    transportService.addProcess(FakeTransportService.FAKE_DEVICE, FakeTransportService.FAKE_PROCESS)

    // Wait for inspection view to load inspectors.
    inspectionView.tabsChangedFlow.first()

    // Check bubble is shown.
    toolWindow.show()
    toolWindow.hide()
    val bubbleText = balloonShownDeferred.await()
    assertThat(bubbleText).isEqualTo(AppInspectionBundle.message("inspection.is.running"))
  }
}