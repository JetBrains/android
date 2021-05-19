/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.android.tools.property.testing

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.ExpirableRunnable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import sun.awt.AWTAccessor
import java.awt.Component
import java.awt.Container
import java.awt.DefaultKeyboardFocusManager
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.FocusEvent
import java.awt.peer.ComponentPeer
import java.lang.reflect.Constructor
import javax.swing.JComponent

/**
 * Rule that provides mock peers for Swing components and keeps track of focus.
 *
 * Use [setRootPeer] to initialize a Swing component tree. This rule will create
 * an invisible [Frame] and add the top component to the frame. All components
 * will be supplied with a mock peer which will enable a large portion of the
 * normal focus logic to kick in while running the test.
 *
 * Note that components created dynamically would also have to setup with a peer.
 * Do that by calling [setRootPeer] on the top component again.
 */
class SwingFocusRule(private var appRule: ApplicationRule? = null) : ExternalResource() {
  private var afterCleanUp = false
  private var focusManager: MyKeyboardFocusManager? = null
  private var oldFocusManager : KeyboardFocusManager? = null
  private val answer = Answer { invocation ->
    val componentToGainFocus = invocation.getArgument<Component>(0)
    val temporary = invocation.getArgument<Boolean>(1)
    val cause = invocation.getArgument<FocusEvent.Cause>(4)
    val event = FocusEvent(componentToGainFocus, 0, temporary, focusOwner, cause)
    val oldFocusOwner = focusOwner
    focusOwner?.focusListeners?.forEach { it.focusLost(event) }
    focusOwner = componentToGainFocus
    focusOwner?.focusListeners?.forEach { it.focusGained(event) }
    focusManager?.focusChanged(oldFocusOwner, focusOwner)
    true
  }

  private var _window: Frame? = null
  private val window: Frame
    get() {
      if (afterCleanUp) {
        error("Frame has been cleared")
      }
      var value = _window
      if (value == null) {
        value = FakeFrame()
        setSinglePeer(value)
        _window = value
      }
      return value
    }

  var focusOwner: Component? = null
    private set

  private var ideFocusManager: IdeFocusManager? = null

  /**
   * Make [component] the top component for the test.
   *
   * This component will be added to the [Frame] that is created by this rule,
   * and a peer is assigned to this component and all its sub components.
   */
  fun setRootPeer(component: Component) {
    if (component.parent == null) {
      window.add(component)
    }
    setPeer(component)
  }

  /**
   * Assign a peer to the specified [component] and all its sub components.
   *
   * Use this method if additional components are created after [setRootPeer]
   * is called.
   */
  fun setPeer(component: Component) {
    setSinglePeer(component)
    (component as? Container)?.components?.forEach { setPeer(it) }
  }

  private fun setSinglePeer(component: Component) {
    val accessor = AWTAccessor.getComponentAccessor()
    if (accessor.getPeer<ComponentPeer>(component) == null) {
      val peer = Mockito.mock(ComponentPeer::class.java)
      Mockito.`when`(peer.requestFocus(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(),
                                       ArgumentMatchers.anyLong(), ArgumentMatchers.any())).then(answer)
      accessor.setPeer(component, peer)
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return when {
      description.getAnnotation(RunWithTestFocusManager::class.java) == null -> base
      SystemInfo.isLinux && System.getenv("DISPLAY") == null -> skipIfLinux(description)
      else -> super.apply(base, description)
    }
  }

  override fun before() {
    overrideGraphicsEnvironment(false)
    _window = FakeFrame()
    focusManager = MyKeyboardFocusManager()
    ideFocusManager = MyIdeFocusManager(focusManager!!)
    appRule!!.testApplication.registerService(IdeFocusManager::class.java, ideFocusManager!!)
    oldFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
  }

  /**
   * Set all references to null after the test is run to minimize the memory used to run the tests.
   * Note: The memory allocated here will stay around while other tests are running.
   */
  override fun after() {
    afterCleanUp = true
    _window = null
    focusOwner = null
    focusManager = null
    ideFocusManager = null
    appRule = null
    KeyboardFocusManager.setCurrentKeyboardFocusManager(oldFocusManager)
    overrideGraphicsEnvironment(null)
    oldFocusManager = null
  }

  private fun skipIfLinux(description: Description): Statement = object : Statement() {
    override fun evaluate() {
      println("Test \"${description.displayName}\" does not run on Linux when DISPLAY is not set (HeadlessException)")
    }
  }

  private fun overrideGraphicsEnvironment(headless: Boolean?) {
    val ge = GraphicsEnvironment::class.java
    val field = ge.getDeclaredField("headless")
    field.isAccessible = true
    field.set(null, headless)
  }

  /**
   * Implementation of a [KeyboardFocusManager].
   *
   * This implementation allows us to test components that wants to
   * know what the current focus owner is. We can also emulate focus
   * the property changes that some components act on.
   *
   * The component: [com.intellij.ui.table.JBTable]
   * is an example of a component that processes focus property events.
   */
  private class MyKeyboardFocusManager : DefaultKeyboardFocusManager() {
    private var focusOwner: Component? = null

    override fun getFocusOwner(): Component? {
      return focusOwner
    }

    fun focusChanged(oldFocusOwner: Component?, newFocusOwner: Component?) {
      focusOwner = newFocusOwner
      firePropertyChange("focusOwner", oldFocusOwner, newFocusOwner)
      firePropertyChange("permanentFocusOwner", oldFocusOwner, newFocusOwner)
    }
  }

  /**
   * Implementation of [IdeFocusManager].
   *
   * Some components in uses an [IdeFocusManager] instead of [KeyboardFocusManager]
   * to find out the current focus owner.
   */
  private inner class MyIdeFocusManager(private val focusManager: MyKeyboardFocusManager) : IdeFocusManager() {

    override fun getFocusOwner(): Component? {
      return focusManager.focusOwner
    }

    override fun getFocusTargetFor(comp: JComponent): JComponent? = null

    override fun doWhenFocusSettlesDown(runnable: Runnable) = runnable.run()

    override fun doWhenFocusSettlesDown(runnable: Runnable, modality: ModalityState) = runnable.run()

    override fun doWhenFocusSettlesDown(runnable: ExpirableRunnable) = runnable.run()

    override fun getFocusedDescendantFor(comp: Component): Component? = null

    override fun requestDefaultFocus(forced: Boolean): ActionCallback = ActionCallback.DONE

    override fun isFocusTransferEnabled(): Boolean = true

    override fun runOnOwnContext(context: DataContext, runnable: Runnable) = runnable.run()

    override fun getLastFocusedFor(frame: Window?): Component? = null

    override fun getLastFocusedFrame(): IdeFrame? = null

    override fun getLastFocusedIdeWindow(): Window? = null

    override fun toFront(c: JComponent?) {}

    override fun requestFocus(c: Component, forced: Boolean): ActionCallback = ActionCallback.DONE

    override fun dispose() {}
  }

  /**
   * A Frame that is never shown.
   *
   * A [Frame] used in headless unit tests, that will never
   * be visible. Override methods such that Swing thinks it
   * is visible without displaying a window on the screen.
   */
  private class FakeFrame : Frame() {
    override fun isShowing(): Boolean {
      return true
    }

    override fun isVisible(): Boolean {
      return true
    }

    override fun setVisible(b: Boolean) {
    }
  }
}

/**
 * Use this annotation to mark the test methods where the
 * functionality in this rule should apply.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RunWithTestFocusManager
