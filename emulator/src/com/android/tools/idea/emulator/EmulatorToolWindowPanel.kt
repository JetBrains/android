/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.emulator.control.ExtendedControlsStatus
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.util.ActionToolbarUtil.makeToolbarNavigable
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.ApkInstaller
import com.android.tools.deployer.InstallStatus
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformNullable
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.actions.findManageSnapshotDialog
import com.android.tools.idea.emulator.actions.showExtendedControls
import com.android.tools.idea.emulator.actions.showManageSnapshotsDialog
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil.getFileListFromAttachedObject
import com.intellij.ide.dnd.FileCopyPasteUtil.isFileListFlavorAvailable
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.intellij.lang.annotations.JdkConstants.AdjustableOrientation
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.awt.Adjustable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.plaf.ScrollBarUI

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
private const val isToolbarHorizontal = true

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
class EmulatorToolWindowPanel(
  private val project: Project,
  val emulator: EmulatorController,
  val uiState: EmulatorUiState
) : BorderLayoutPanel(), DataProvider {

  private val mainToolbar: ActionToolbar
  private var emulatorView: EmulatorView? = null
  private val scrollPane: JScrollPane
  private val layeredPane: JLayeredPane
  private val zoomControlsLayerPane: JPanel
  private var loadingPanel: JBLoadingPanel? = null
  private var contentDisposable: Disposable? = null
  private var floatingToolbar: JComponent? = null
  private var savedEmulatorViewPreferredSize: Dimension? = null
  private var savedScrollPosition: Point? = null

  val id
    get() = emulator.emulatorId

  val title
    get() = emulator.emulatorId.avdName

  val icon
    get() = ICON

  val component: JComponent
    get() = this

  var zoomToolbarVisible = false
    set(value) {
      field = value
      floatingToolbar?.let { it.isVisible = value }
    }

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(EMULATOR_MAIN_TOOLBAR_ID, isToolbarHorizontal)

    zoomControlsLayerPane = JPanel().apply {
      layout = BorderLayout()
      border = JBUI.Borders.empty(UIUtil.getScrollBarWidth())
      isOpaque = false
      isFocusable = true
    }

    scrollPane = MyScrollPane().apply {
      border = null
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
      viewport.background = background
      viewport.addChangeListener {
        val view = viewport.view
        // Remove the explicitly set preferred view size if it does not exceed the viewport size.
        if (view != null && view.isPreferredSizeSet &&
            view.preferredSize.width <= viewport.width && view.preferredSize.height <= viewport.height) {
          view.preferredSize = null
        }
      }
    }

    layeredPane = JLayeredPane().apply {
      layout = LayeredPaneLayoutManager()
      isFocusable = true
      setLayer(zoomControlsLayerPane, JLayeredPane.PALETTE_LAYER)
      setLayer(scrollPane, JLayeredPane.DEFAULT_LAYER)

      add(zoomControlsLayerPane, BorderLayout.CENTER)
      add(scrollPane, BorderLayout.CENTER)
    }

    installDnD()
  }

  fun getPreferredFocusableComponent(): JComponent {
    return emulatorView ?: this
  }

  private fun addToolbar() {
    if (isToolbarHorizontal) {
      mainToolbar.setOrientation(SwingConstants.HORIZONTAL)
      layeredPane.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(mainToolbar.component)
    }
    else {
      mainToolbar.setOrientation(SwingConstants.VERTICAL)
      layeredPane.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(mainToolbar.component)
    }
  }

  private fun installDnD() {
    DnDSupport.createBuilder(this)
      .enableAsNativeTarget()
      .setTargetChecker { event ->
        if (isFileListFlavorAvailable(event)) {
          event.isDropPossible = true
          return@setTargetChecker false
        }
        return@setTargetChecker true
      }
      .setDropHandler(ApkFileDropHandler())
      .install()
  }

  fun setDeviceFrameVisible(visible: Boolean) {
    emulatorView?.deviceFrameVisible = visible
  }

  fun createContent(deviceFrameVisible: Boolean) {
    try {
      val disposable = Disposer.newDisposable()
      contentDisposable = disposable

      val toolbar = EmulatorZoomToolbar.createToolbar(this, disposable)
      toolbar.isVisible = zoomToolbarVisible
      floatingToolbar = toolbar
      zoomControlsLayerPane.add(toolbar, BorderLayout.EAST)

      val emulatorView = EmulatorView(disposable, emulator, deviceFrameVisible)
      emulatorView.background = background
      this.emulatorView = emulatorView
      scrollPane.setViewportView(emulatorView)
      mainToolbar.setTargetComponent(emulatorView)

      addToolbar()

      val loadingPanel = EmulatorLoadingPanel(disposable)
      this.loadingPanel = loadingPanel
      loadingPanel.add(layeredPane, BorderLayout.CENTER)
      addToCenter(loadingPanel)

      loadingPanel.setLoadingText("Connecting to the Emulator")
      loadingPanel.startLoading() // The stopLoading method is called by EmulatorView after the gRPC connection is established.

      // Restore zoom and scroll state.
      val emulatorViewPreferredSize = savedEmulatorViewPreferredSize
      if (emulatorViewPreferredSize != null) {
        emulatorView.preferredSize = emulatorViewPreferredSize
        scrollPane.viewport.viewPosition = savedScrollPosition
      }

      loadingPanel.repaint()

      if (connected) {
        if (uiState.manageSnapshotsDialogShown) {
          showManageSnapshotsDialog(emulatorView, project)
        }
        if (uiState.extendedControlsShown) {
          showExtendedControls(emulator)
        }
      }
    }
    catch (e: Exception) {
      val label = "Unable to create emulator view: $e"
      add(JLabel(label), BorderLayout.CENTER)
    }
  }

  fun destroyContent() {
    val manageSnapshotsDialog = emulatorView?.let { findManageSnapshotDialog(it) }
    uiState.manageSnapshotsDialogShown = manageSnapshotsDialog != null
    manageSnapshotsDialog?.close(DialogWrapper.CLOSE_EXIT_CODE)

    if (StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.get() && connected) {
      emulator.closeExtendedControls(object: EmptyStreamObserver<ExtendedControlsStatus>() {
        override fun onNext(response: ExtendedControlsStatus) {
          EventQueue.invokeLater {
            uiState.extendedControlsShown = response.visibilityChanged
          }
        }
      })
    }

    savedEmulatorViewPreferredSize = emulatorView?.explicitlySetPreferredSize
    savedScrollPosition = scrollPane.viewport.viewPosition

    contentDisposable?.let { Disposer.dispose(it) }
    contentDisposable = null

    zoomControlsLayerPane.removeAll()
    floatingToolbar = null

    emulatorView = null
    mainToolbar.setTargetComponent(null)
    scrollPane.setViewportView(null)

    loadingPanel = null
    removeAll()
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
      else -> null
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
    makeToolbarNavigable(toolbar)
    return toolbar
  }

  private class LayeredPaneLayoutManager : LayoutManager {

    override fun layoutContainer(target: Container) {
      val insets: Insets = target.insets
      val top = insets.top
      val bottom = target.height - insets.bottom
      val left = insets.left
      val right = target.width - insets.right

      for (child in target.components) {
        child.setBounds(left, top, right - left, bottom - top)
      }
    }

    // Request all available space.
    override fun preferredLayoutSize(parent: Container): Dimension =
      if (parent.isPreferredSizeSet) parent.preferredSize else Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)
    override fun addLayoutComponent(name: String?, comp: Component?) {}
    override fun removeLayoutComponent(comp: Component?) {}
  }

  private class MyScrollPane : JBScrollPane(0) {

    override fun createVerticalScrollBar(): JScrollBar {
      return MyScrollBar(Adjustable.VERTICAL)
    }

    override fun createHorizontalScrollBar(): JScrollBar {
      return MyScrollBar(Adjustable.HORIZONTAL)
    }

    init {
      setupCorners()
    }
  }

  private class MyScrollBar(
    @AdjustableOrientation orientation: Int
  ) : JBScrollBar(orientation), IdeGlassPane.TopComponent {

    private var persistentUI: ScrollBarUI? = null

    override fun canBePreprocessed(event: MouseEvent): Boolean {
      return JBScrollPane.canBePreprocessed(event, this)
    }

    override fun setUI(ui: ScrollBarUI) {
      if (persistentUI == null) {
        persistentUI = ui
      }
      super.setUI(persistentUI)
      isOpaque = false
    }

    override fun getUnitIncrement(direction: Int): Int {
      return 5
    }

    override fun getBlockIncrement(direction: Int): Int {
      return 1
    }

    init {
      isOpaque = false
    }
  }

  private inner class ApkFileDropHandler : DnDDropHandler {

    override fun drop(event: DnDEvent) {
      val files = getFileListFromAttachedObject(event.attachedObject)
      val fileNames = files.joinToString(", ") { it.name }
      loadingPanel?.apply {
        setLoadingText("Installing $fileNames")
        startLoading()
      }
      val resultFuture: ListenableFuture<AdbClient.InstallResult?> = findDevice().transformNullable(getAppExecutorService()) { device ->
        if (device == null) return@transformNullable null
        val adbClient = AdbClient(device, LogWrapper(EmulatorToolWindowPanel::class.java))
        val filePaths = files.asSequence().map { it.path }.toList()
        return@transformNullable adbClient.install(filePaths, listOf("-t", "--user", "current", "--full", "--dont-kill"), true)
      }
      resultFuture.addCallback(
          EdtExecutorService.getInstance(),
          success = { installResult ->
            if (installResult?.status == InstallStatus.OK) {
              notifyOfSuccess("$fileNames installed")
            }
            else {
              val message = if (installResult == null) {
                "Unable to find the device to install the app"
              }
              else {
                installResult.reason ?: ApkInstaller.message(installResult)
              }
              notifyOfError(message)
            }
            loadingPanel?.stopLoading()
          },
          failure = { throwable ->
            val message = throwable?.message ?: "Installation failed"
            notifyOfError(message)
            loadingPanel?.stopLoading()
          })
    }

    private fun notifyOfSuccess(message: String) {
      EMULATOR_NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(project)
    }

    private fun notifyOfError(message: String) {
      EMULATOR_NOTIFICATION_GROUP.createNotification(message, NotificationType.WARNING).notify(project)
    }

    private fun findDevice(): ListenableFuture<IDevice?> {
      val serialNumber = "emulator-${emulator.emulatorId.serialPort}"
      val adbFile = AndroidSdkUtils.getAdb(project) ?:
                    return Futures.immediateFailedFuture(RuntimeException("Could not find adb executable"))
      val bridgeFuture: ListenableFuture<AndroidDebugBridge> = AdbService.getInstance().getDebugBridge(adbFile)
      return bridgeFuture.transform(getAppExecutorService()) { debugBridge ->
        debugBridge.devices.find { it.isEmulator && it.serialNumber == serialNumber }
      }
    }
  }
}
