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
@file:JvmName("HeadlessDialogTester")
package com.android.tools.adtui.swing

import com.android.annotations.concurrency.GuardedBy
import com.google.common.util.concurrent.ListenableFutureTask
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.ui.popup.StackingPopupDispatcher
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.Consumer
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Window
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseMotionListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.UIManager

/**
 * Enables showing of modal dialogs in a headless test environment.
 */
fun enableHeadlessDialogs(disposable: Disposable) {
  getApplication().replaceService(DialogWrapperPeerFactory::class.java, HeadlessDialogWrapperPeerFactory(), disposable)
}

/**
 * Executes a function that opens a modal dialog and then a function that interacts with it.
 * The function returns when the dialog is closed.
 *
 * @param dialogCreator user code that opens a modal dialog
 * @param dialogInteractor user code for interacting with the dialog
 */
fun createDialogAndInteractWithIt(dialogCreator: () -> Unit, dialogInteractor: (DialogWrapper) -> Unit) {
  val modalDepth = modalDialogStack.size + 1
  val dialogClosed = CountDownLatch(1)

  val futureTask = ListenableFutureTask.create {
    modalityChangeLock.lock()
    try {
      while (true) {
        if (modalDialogStack.size == modalDepth) {
          val dialog = modalDialogStack.last()
          invokeLater(ModalityState.any()) {
            try {
              dialogInteractor(dialog)
            }
            finally {
              if (dialog.isShowing) {
                dialog.close(CANCEL_EXIT_CODE)
              }
              dialogClosed.countDown()
            }
          }
          break
        }
        modalityChangeCondition.await()
      }
    }
    finally {
      modalityChangeLock.unlock()
    }
  }
  getApplication().executeOnPooledThread(futureTask)

  try {
    dialogCreator()

    while (dialogClosed.count > 0) {
      UIUtil.dispatchAllInvocationEvents()
      dialogClosed.await(10, TimeUnit.MILLISECONDS)
    }
  }
  finally {
    futureTask.cancel(true)
  }
}

/**
 * Executes a [Runnable] that opens a modal dialog and then a [Consumer] that interacts with it.
 * The function returns when the dialog is closed. This version of the method is intended to be called from Java.
 *
 * @param dialogCreator user code that opens a modal dialog
 * @param dialogInteractor user code for interacting with the dialog
 */
fun createDialogAndInteractWithIt(dialogCreator: Runnable, dialogInteractor: Consumer<DialogWrapper>) {
  createDialogAndInteractWithIt(dialogCreator::run, dialogInteractor::consume)
}

private val modalityChangeLock = ReentrantLock()
@GuardedBy("modalityChangeLock")
private val modalityChangeCondition = modalityChangeLock.newCondition()
@GuardedBy("modalityChangeLock")
private val modalDialogStack = mutableListOf<DialogWrapper>()

private val LOG
  get() = Logger.getInstance(DialogWrapper::class.java)

/**
 * Implementation of [DialogWrapperPeerFactory] for headless tests involving modal dialogs.
 */
class HeadlessDialogWrapperPeerFactory : DialogWrapperPeerFactory() {

  override fun createPeer(wrapper: DialogWrapper, project: Project?, canBeParent: Boolean): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, project, canBeParent)
  }

  override fun createPeer(wrapper: DialogWrapper,
                          project: Project?,
                          canBeParent: Boolean,
                          ideModalityType: IdeModalityType): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, project, canBeParent, ideModalityType)
  }

  override fun createPeer(wrapper: DialogWrapper, canBeParent: Boolean): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, canBeParent)
  }

  override fun createPeer(wrapper: DialogWrapper, parent: Component, canBeParent: Boolean): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, parent, canBeParent)
  }

  override fun createPeer(wrapper: DialogWrapper, canBeParent: Boolean, ideModalityType: IdeModalityType): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, null as Window?, canBeParent, ideModalityType)
  }

  override fun createPeer(wrapper: DialogWrapper,
                          owner: Window,
                          canBeParent: Boolean,
                          ideModalityType: IdeModalityType): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, owner, canBeParent, ideModalityType)
  }
}

/**
 * Semi-realistic implementation of [DialogWrapperPeer] for headless tests involving modal dialogs.
 *
 * Derived from DialogWrapperPeerImpl and undoubtedly contains a significant amount of redundant
 * code. This redundant code is preserved in hope that it may be used in future to implement more
 * behaviors mimicking real dialogs.
 */
private class HeadlessDialogWrapperPeer : DialogWrapperPeer {
  private val wrapper: DialogWrapper
  private val canBeParent: Boolean
  private val disposeActions = arrayListOf<Runnable>()
  private var project: Project? = null
  private val rootPane = createRootPane()
  private var modal = true
  private var size = Dimension(0, 0)
  private var location = Point()
  private var title: String? = null
  private var visible = false
  private var nestedEventLoopLatch: CountDownLatch? = null

  /**
   * Creates modal [DialogWrapper]. The currently active window will be the dialog's parent.
   *
   * @param proj parent window for the dialog will be calculated based on focused window for
   *     the specified project. This parameter can be null. In this case parent window will
   *     be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows.
   *     This parameter is used by the window manager.
   */
  constructor(wrapper: DialogWrapper, proj: Project?, canBeParent: Boolean, ideModalityType: IdeModalityType = IdeModalityType.IDE) {
    project = proj
    val headless = isHeadlessEnv
    this.wrapper = wrapper
    val windowManager = windowManager
    var window: Window? = null
    if (windowManager != null) {
      if (project == null) {
        project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContext)
      }
      window = windowManager.suggestParentWindow(project)
      if (window == null) {
        val focusedWindow = windowManager.mostRecentFocusedWindow
        if (focusedWindow is IdeFrameImpl) {
          window = focusedWindow
        }
      }
      if (window == null) {
        for (frameHelper in windowManager.projectFrameHelpers) {
          if (frameHelper.frame!!.isActive) {
            break
          }
        }
      }
    }
    this.canBeParent = headless || canBeParent
  }

  constructor(wrapper: DialogWrapper, canBeParent: Boolean) : this(wrapper, null as Project?, canBeParent)

  constructor(wrapper: DialogWrapper, parent: Component, canBeParent: Boolean) {
    val headless = isHeadlessEnv
    this.wrapper = wrapper
    this.canBeParent = headless || canBeParent
  }

  constructor(wrapper: DialogWrapper, owner: Window?, canBeParent: Boolean, ideModalityType: IdeModalityType) {
    val headless = isHeadlessEnv
    this.wrapper = wrapper
    this.canBeParent = headless || canBeParent
  }

  override fun isHeadless(): Boolean {
    return true
  }

  override fun getCurrentModalEntities(): Array<Any> {
    return LaterInvocator.getCurrentModalEntities()
  }

  override fun setUndecorated(undecorated: Boolean) {}

  override fun addMouseListener(listener: MouseListener) {}

  override fun addMouseListener(listener: MouseMotionListener) {}

  override fun addKeyListener(listener: KeyListener) {}

  override fun toFront() {}

  override fun toBack() {}

  override fun dispose() {
    check(EventQueue.isDispatchThread())
    nestedEventLoopLatch?.countDown()
    for (runnable in disposeActions) {
      runnable.run()
    }
    disposeActions.clear()
    val disposer = Runnable { project = null }
    UIUtil.invokeLaterIfNeeded(disposer)
  }

  override fun getContentPane(): Container? {
    return rootPane.contentPane
  }

  override fun validate() {}

  override fun repaint() {}

  override fun getOwner(): Window? {
    return null
  }

  override fun getWindow(): Window? {
    return null
  }

  override fun getRootPane(): JRootPane {
    return rootPane
  }

  override fun getSize(): Dimension {
    return size
  }

  override fun setSize(width: Int, height: Int) {
    size = Dimension(width, height)
  }

  override fun getTitle(): String {
    return title!!
  }

  override fun setTitle(title: String) {
    this.title = title
  }

  override fun pack() {}

  override fun setAppIcons() {}

  override fun setModal(modal: Boolean) {
    this.modal = modal
  }

  override fun isModal(): Boolean {
    return modal
  }

  override fun isVisible(): Boolean {
    return visible
  }

  override fun isShowing(): Boolean {
    return visible
  }

  override fun getPreferredSize(): Dimension {
    return rootPane.preferredSize
  }

  override fun isResizable() = false

  override fun setResizable(resizable: Boolean) {}

  override fun getLocation(): Point {
    return location
  }

  override fun setLocation(p: Point) {
    location = p
  }

  override fun setLocation(x: Int, y: Int) {
    setLocation(Point(x, y))
  }

  override fun show(): ActionCallback {
    check(EventQueue.isDispatchThread())
    val result = ActionCallback()
    val anCancelAction = AnCancelAction()
    val rootPane = getRootPane()
    UIUtil.decorateWindowHeader(rootPane)
    val window = window
    if (window is JDialog && !window.isUndecorated) {
      UIUtil.setCustomTitleBar(window, rootPane) { runnable: Runnable ->
        Disposer.register(wrapper.disposable, Disposable { runnable.run() })
      }
    }
    // FIXME-ank4: CustomFrameDialogContent is internal class. What is this `updateLayout` for?
    //val contentPane = contentPane
    //if (contentPane is CustomFrameDialogContent) {
    //  contentPane.updateLayout()
    //}
    rootPane.size = rootPane.preferredSize
    anCancelAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, rootPane)
    disposeActions.add(Runnable { anCancelAction.unregisterCustomShortcutSet(rootPane) })
    val commandProcessor = if (getApplication() != null) CommandProcessor.getInstance() as CommandProcessorEx else null
    val appStarted = commandProcessor != null

    val changeModalityState = appStarted && isModal && !wrapper.isModalProgress // ProgressWindow starts a modality state itself.

    if (changeModalityState) {
      commandProcessor!!.enterModal()
      LaterInvocator.enterModal(wrapper)
    }

    if (appStarted) {
      hidePopupsIfNeeded()
    }

    try {
      visible = true
      nestedEventLoop()
    }
    finally {
      if (changeModalityState) {
        commandProcessor!!.leaveModal()
        LaterInvocator.leaveModal(wrapper)
      }
      result.createSetDoneRunnable().run()
    }
    return result
  }

  override fun setContentPane(content: JComponent) {
    rootPane.contentPane = content
  }

  override fun centerInParent() {}

  private fun createRootPane(): JRootPane {
    val pane = DialogRootPane()
    pane.isOpaque = true
    return pane
  }

  private fun nestedEventLoop() {
    val latch = CountDownLatch(1)
    nestedEventLoopLatch = latch

    modalityChangeLock.lock()
    modalDialogStack.add(wrapper)
    modalityChangeCondition.signalAll()
    modalityChangeLock.unlock()

    while (latch.count > 0) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      latch.await(10, TimeUnit.MILLISECONDS)
    }

    modalityChangeLock.lock()
    modalDialogStack.removeAt(modalDialogStack.size - 1)
    modalityChangeCondition.signalAll()
    modalityChangeLock.unlock()

    nestedEventLoopLatch = null
  }

  private fun hidePopupsIfNeeded() {
    if (SystemInfo.isMac) {
      StackingPopupDispatcher.getInstance().hidePersistentPopups()
      disposeActions.add(Runnable { StackingPopupDispatcher.getInstance().restorePersistentPopups() })
    }
  }

  private val isHeadlessEnv: Boolean
    get() {
      val app = getApplication()
      return if (app == null) GraphicsEnvironment.isHeadless() else app.isUnitTestMode || app.isHeadlessEnvironment
    }

  private val windowManager: WindowManagerEx?
    get() {
      var windowManager: WindowManagerEx? = null
      val application = getApplication()
      if (application != null) {
        windowManager = WindowManagerEx.getInstanceEx()
      }
      return windowManager
    }

  private inner class AnCancelAction : AnAction(), DumbAware {
    override fun update(event: AnActionEvent) {
      val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
      event.presentation.isEnabled = false
      if (focusOwner is JComponent && SpeedSearchBase.hasActiveSpeedSearch(focusOwner)) {
        return
      }
      if (StackingPopupDispatcher.getInstance().isPopupFocused) {
        return
      }
      val tree = ComponentUtil.getParentOfType(JTree::class.java as Class<out JTree?>, focusOwner)
      val table = ComponentUtil.getParentOfType(JTable::class.java as Class<out JTable?>, focusOwner)
      if (tree != null || table != null) {
        if (hasNoEditingTreesOrTablesUpward(focusOwner)) {
          event.presentation.isEnabled = true
        }
      }
    }

    private fun hasNoEditingTreesOrTablesUpward(component: Component): Boolean {
      var comp: Component? = component
      while (comp != null) {
        if (isEditingTreeOrTable(comp)) return false
        comp = comp.parent
      }
      return true
    }

    private fun isEditingTreeOrTable(comp: Component): Boolean {
      if (comp is JTree) {
        return comp.isEditing
      }
      else if (comp is JTable) {
        return comp.isEditing
      }
      return false
    }

    override fun actionPerformed(e: AnActionEvent) {
      wrapper.doCancelAction(e.inputEvent)
    }
  }

  private inner class DialogRootPane : JRootPane(), DataProvider {
    private val myGlassPaneIsSet: Boolean
    private var myLastMinimumSize: Dimension? = null
    override fun createLayeredPane(): JLayeredPane {
      val p: JLayeredPane = JBLayeredPane()
      p.name = this.name + ".layeredPane"
      return p
    }

    override fun validate() {
      super.validate()
      if (wrapper.isAutoAdjustable) {
        val window = wrapper.window
        if (window != null) {
          val size = minimumSize
          if (size != myLastMinimumSize) {
            // Update window minimum size only if root pane minimum size is changed.
            if (size == null) {
              myLastMinimumSize = null
            }
            else {
              myLastMinimumSize = Dimension(size)
              JBInsets.addTo(size, window.insets)
              val screen = ScreenUtil.getScreenRectangle(window)
              if (size.width > screen.width || size.height > screen.height) {
                val application = getApplication()
                if (application != null && application.isInternal) {
                  val sb = StringBuilder("dialog minimum size is bigger than screen: ")
                  sb.append(size.width).append("x").append(size.height)
                  IJSwingUtilities.appendComponentClassNames(sb, this)
                  LOG.warn(sb.toString())
                }
                if (size.width > screen.width) size.width = screen.width
                if (size.height > screen.height) size.height = screen.height
              }
            }
            window.minimumSize = size
          }
        }
      }
    }

    override fun setGlassPane(glass: Component) {
      if (myGlassPaneIsSet) {
        LOG.warn("Setting of glass pane for DialogWrapper is prohibited", Exception())
        return
      }
      super.setGlassPane(glass)
    }

    override fun setContentPane(contentPane: Container) {
      super.setContentPane(contentPane)
      contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})
    }

    override fun getData(@NonNls dataId: String): Any? {
      return if (PlatformDataKeys.UI_DISPOSABLE.`is`(dataId)) wrapper.disposable else null
    }

    init {
      setGlassPane(IdeGlassPaneImpl(this))
      myGlassPaneIsSet = true
      putClientProperty("DIALOG_ROOT_PANE", true)
      border = UIManager.getBorder("Window.border")
    }
  }
}
