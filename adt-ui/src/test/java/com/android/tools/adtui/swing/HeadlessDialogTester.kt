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
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.DialogWrapperDialog
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.ui.popup.StackingPopupDispatcher
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.ui.ComponentUtil
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.ToolbarUtil
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.Consumer
import com.intellij.util.ExceptionUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.InvocationEvent
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseMotionListener
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.UIManager
import kotlin.concurrent.withLock

/**
 * Enables showing of dialogs in a headless test environment.
 * Don't call this function directly, prefer [HeadlessDialogRule].
 */
fun enableHeadlessDialogs(disposable: Disposable) {
  Disposer.register(disposable) {
    modelessDialogs.forEach { Disposer.dispose(it.disposable) }
  }
  getApplication().replaceService(DialogWrapperPeerFactory::class.java, HeadlessDialogWrapperPeerFactory(), disposable)
}

/**
 * Looks for a currently shown modeless dialog.
 */
fun findModelessDialog(predicate: Predicate<DialogWrapper>): DialogWrapper? = modelessDialogs.find { predicate.test(it) }

/**
 * Calls the [dialogCreator] function that opens a modal dialog and then the [dialogInteractor]
 * function that interacts with it. This function returns when the dialog is closed.
 *
 * @param dialogCreator user code that opens a modal dialog
 * @param dialogInteractor user code for interacting with the dialog
 */
fun createModalDialogAndInteractWithIt(dialogCreator: Runnable, dialogInteractor: Consumer<DialogWrapper>) {
  createModalDialogAndInteractWithIt(modalDialogStack.size + 1, dialogCreator, dialogInteractor::consume)
}

private fun createModalDialogAndInteractWithIt(modalDepth: Int, dialogCreator: Runnable, dialogInteractor: Consumer<DialogWrapper>) {
  val dialogClosed = CountDownLatch(1)

  val futureTask = ListenableFutureTask.create {
    modalityChangeLock.lock()
    try {
      while (true) {
        if (modalDialogStack.size == modalDepth) {
          val dialog = modalDialogStack.last()
          EventQueue.invokeLater {
            try {
              dialogInteractor.consume(dialog)
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
    dialogCreator.run()

    while (dialogClosed.count > 0) {
      if (dispatchNextInvocationEventIfAny() == null) {
        dialogClosed.await(10, TimeUnit.MILLISECONDS)
      }
    }
  }
  finally {
    futureTask.cancel(true)
  }

  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
}

private fun dispatchNextInvocationEventIfAny(): AWTEvent? {
  // Code similar to EdtInvocationManager.dispatchAllInvocationEvents.
  val eventQueue = Toolkit.getDefaultToolkit().systemEventQueue
  while (eventQueue.peekEvent() != null) {
    try {
      val event = eventQueue.nextEvent
      if (event is InvocationEvent) {
        dispatchEventMethod.invoke(eventQueue, event)
        return event
      }
    }
    catch (e: InvocationTargetException) {
      ExceptionUtil.rethrowAllAsUnchecked(e.cause)
    }
    catch (e: Exception) {
      ExceptionUtil.rethrow(e)
    }
  }
  return null
}

private val modalityChangeLock = ReentrantLock()
@GuardedBy("modalityChangeLock")
private val modalityChangeCondition = modalityChangeLock.newCondition()
@GuardedBy("modalityChangeLock")
private val modalDialogStack = mutableListOf<DialogWrapper>()

private val modelessDialogs = ContainerUtil.createConcurrentList<DialogWrapper>()

private val dispatchEventMethod = ReflectionUtil.getDeclaredMethod(EventQueue::class.java, "dispatchEvent", AWTEvent::class.java)!!

/**
 * Implementation of [DialogWrapperPeerFactory] for headless tests involving dialogs.
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
    return HeadlessDialogWrapperPeer(wrapper, null, canBeParent)
  }

  override fun createPeer(wrapper: DialogWrapper, parent: Component, canBeParent: Boolean): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, null, canBeParent)
  }

  override fun createPeer(wrapper: DialogWrapper, canBeParent: Boolean, ideModalityType: IdeModalityType): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, null, canBeParent, ideModalityType)
  }

  override fun createPeer(wrapper: DialogWrapper,
                          owner: Window,
                          canBeParent: Boolean,
                          ideModalityType: IdeModalityType): DialogWrapperPeer {
    return HeadlessDialogWrapperPeer(wrapper, null, canBeParent, ideModalityType)
  }
}

/**
 * Semi-realistic implementation of [DialogWrapperPeer] for headless tests involving dialogs.
 *
 * Derived from DialogWrapperPeerImpl and undoubtedly contains a significant amount of redundant
 * code. This redundant code is preserved in hope that it may be used in future to implement more
 * behaviors mimicking real dialogs.
 */
@Suppress("UnstableApiUsage")
private class HeadlessDialogWrapperPeer(
  private val wrapper: DialogWrapper,
  private var project: Project?,
  canBeParent: Boolean,
  ideModalityType: IdeModalityType = IdeModalityType.IDE
) : DialogWrapperPeer() {
  private val canBeParent: Boolean
  private val disposeActions = arrayListOf<Runnable>()
  private val rootPane = createRootPane()
  private var modal = true
  private var size = Dimension(0, 0)
  private var location = Point()
  private var title: String? = null
  private var visible = false
  private var nestedEventLoopLatch: CountDownLatch? = null

  init {
    modal = ideModalityType != IdeModalityType.MODELESS
    val headless = isHeadlessEnv
    this.canBeParent = headless || canBeParent
  }

  override fun isHeadless(): Boolean {
    return true
  }

  override fun setOnDeactivationAction(disposable: Disposable, onDialogDeactivated: Runnable) {}

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
    if (!modal) {
      modelessDialogs.remove(wrapper)
    }
    visible = false
    for (runnable in disposeActions) {
      runnable.run()
    }
    disposeActions.clear()
    UIUtil.invokeLaterIfNeeded { project = null }
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

  override fun show(): CompletableFuture<*> {
    check(EventQueue.isDispatchThread())
    val result = CompletableFuture<Any?>()
    val anCancelAction = AnCancelAction()
    val rootPane = getRootPane()
    UIUtil.decorateWindowHeader(rootPane)
    val window = window
    if (window is JDialog && !window.isUndecorated) {
      ToolbarUtil.setTransparentTitleBar(window, rootPane) { runnable: Runnable ->
        Disposer.register(wrapper.disposable, runnable::run)
      }
    }

    val dialog = MyDialog()
    dialog.add(rootPane)

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
      if (!isModal) {
        modelessDialogs.add(wrapper)
      }
      if (changeModalityState) {
        nestedEventLoop()
      }
    }
    finally {
      if (changeModalityState) {
        commandProcessor!!.leaveModal()
        LaterInvocator.leaveModal(wrapper)
      }
      result.complete(null)
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
    check(nestedEventLoopLatch == null)
    val latch = CountDownLatch(1)
    nestedEventLoopLatch = latch

    modalityChangeLock.withLock {
      modalDialogStack.add(wrapper)
      modalityChangeCondition.signalAll()
    }

    try {
      while (latch.count > 0) {
        if (PlatformTestUtil.dispatchNextEventIfAny() == null) {
          latch.await(10, TimeUnit.MILLISECONDS)
        }
      }
    } finally {
      modalityChangeLock.withLock {
        modalDialogStack.removeAt(modalDialogStack.size - 1)
        modalityChangeCondition.signalAll()
      }

      nestedEventLoopLatch = null
    }
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

  private inner class AnCancelAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

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

    override fun createLayeredPane(): JLayeredPane {
      return JBLayeredPane().apply { name = "$name.layeredPane" }
    }

    override fun setContentPane(contentPane: Container) {
      super.setContentPane(contentPane)
      contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})
    }

    override fun getData(@NonNls dataId: String): Any? {
      return if (PlatformDataKeys.UI_DISPOSABLE.`is`(dataId)) wrapper.disposable else null
    }

    init {
      putClientProperty("DIALOG_ROOT_PANE", true)
      border = UIManager.getBorder("Window.border")
    }
  }

  private inner class MyDialog : JPanel(), DialogWrapperDialog {

    override fun getDialogWrapper(): DialogWrapper {
      return wrapper
    }
  }
}
