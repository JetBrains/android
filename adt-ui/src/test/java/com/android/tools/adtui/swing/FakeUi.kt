/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("FakeUiUtil")

package com.android.tools.adtui.swing

import com.android.testutils.waitForCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeMouse.Button.LEFT
import com.android.tools.adtui.swing.FakeMouse.Button.RIGHT
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.TestWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.awt.Component
import java.awt.Container
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.ImageCapabilities
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.ImageObserver
import java.awt.image.VolatileImage
import java.util.concurrent.Future
import javax.swing.JLabel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

/**
 * A utility class to interact with Swing components in unit tests.
 *
 * @param root the top-level component
 * @param screenScale size of a virtual pixel in physical pixels; used for emulating a HiDPI screen
 * @param parentDisposable if provided, FakeUi will use it to clean up
 */
class FakeUi @JvmOverloads constructor(
  val root: Component,
  screenScale: Double = 1.0,
  createFakeWindow: Boolean = false,
  parentDisposable: Disposable? = null,
) {

  @JvmField
  val keyboard: FakeKeyboard = FakeKeyboard()

  @JvmField
  val mouse: FakeMouse = FakeMouse(this, keyboard)

  var screenScale: Double
    get() = screenScaleInternal
    set(value) {
      if (screenScaleInternal != value) {
        screenScaleInternal = value
        ComponentAccessor.setGraphicsConfiguration(getTopLevelComponent(root), FakeGraphicsConfiguration(value))
      }
    }

  private var screenScaleInternal: Double = screenScale

  init {
    if (root.parent == null && createFakeWindow) {
      val rootPane = root as? JRootPane ?: JRootPane().apply {
        glassPane = IdeGlassPaneImpl(this, false)
        isFocusCycleRoot = true
        bounds = root.bounds
        add(root)
      }
      val application = ApplicationManager.getApplication()
      // Use an exact class comparison so that the check fails if the TestWindowManager class stops
      // being final in future and a subclass is introduced.
      if (application != null && WindowManager.getInstance()?.javaClass == TestWindowManager::class.java) {
        // Replace TestWindowManager with a more lenient version.
        application.registerServiceInstance(WindowManager::class.java, FakeUiWindowManager())
      }
      wrapInFakeWindow(rootPane, parentDisposable)
    }

    if (screenScale != 1.0) {
      ComponentAccessor.setGraphicsConfiguration(getTopLevelComponent(root), FakeGraphicsConfiguration(screenScale))
    }
    if (!root.isPreferredSizeSet) {
      root.preferredSize = root.size
    }
    updateToolbars()
  }

  /**
   * Forces a re-layout of all components scoped by this FakeUi instance, for example in response to
   * a parent's bounds changing.
   *
   * Note: The constructor automatically forces a layout pass. You should only need to call this
   * method if you update the UI after constructing the FakeUi.
   */
  fun layout() {
    val layoutRoot = UIUtil.getParentOfType(JRootPane::class.java, root) ?: root
    TreeWalker(layoutRoot).descendantStream().forEach(Component::doLayout)
  }

  /**
   * Forces a re-layout of all components scoped by this FakeUi instance and dispatches all resulting
   * resizing events.
   */
  @Throws(InterruptedException::class)
  fun layoutAndDispatchEvents() {
    layout()
    // Allow resizing events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  /**
   * Renders the root component and returns the image reflecting its appearance.
   */
  fun render(): BufferedImage = render(root)

  /**
   * Renders the given component and returns the image reflecting its appearance.
   */
  fun render(component: Component): BufferedImage {
    val image =
        BufferedImage((component.width * screenScale).toInt(), (component.height * screenScale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.transform = AffineTransform.getScaleInstance(screenScale, screenScale)
    component.printAll(graphics)
    graphics.dispose()
    return image
  }

  /**
   * Dumps the content of the Swing tree to stderr.
   */
  fun dump() {
    dump(root, "")
  }

  private fun dump(component: Component, prefix: String) {
    System.err.println("$prefix${component.javaClass.simpleName}@(${component.x}, ${component.y}) " +
                       "[${component.size.getWidth()}x${component.size.getHeight()}]" +
                       if (isMouseTarget(component)) " {*}" else "" +
                       if (component is JLabel) " text: " + component.text else "")
    if (component is Container) {
      for (i in 0 until component.componentCount) {
        dump(component.getComponent(i), "$prefix  ")
      }
    }
  }

  /**
   * Checks if the component and all its ancestors are visible.
   */
  fun isShowing(component: Component): Boolean {
    var c = component
    while (true) {
      if (!c.isVisible) {
        return false
      }
      if (c == root) {
        break
      }
      c = c.parent
    }
    return true
  }

  fun getPosition(component: Component): Point {
    var comp: Component? = component
    if (component.width == 0 && component.height == 0) {
      layout() // The component has zero size. Force layout to give it a chance to acquire non-zero dimensions.
    }
    var rx = 0
    var ry = 0
    while (comp !== root && comp != null) {
      rx += comp.x
      ry += comp.y
      comp = comp.parent
    }
    return Point(rx, ry)
  }

  fun toRelative(component: Component, x: Int, y: Int): Point {
    val position = getPosition(component)
    return Point(x - position.x, y - position.y)
  }

  /**
   * Simulates pressing and releasing a mouse button over the given component.
   */
  fun clickOn(component: Component, button: FakeMouse.Button = LEFT) {
    clickRelativeTo(component, component.width / 2, component.height / 2, button)
  }

  /**
   * Simulates pressing and releasing the right mouse button over the given component.
   */
  fun rightClickOn(component: Component) {
    clickRelativeTo(component, component.width / 2, component.height / 2, RIGHT)
  }

  /**
   * Simulates pressing and releasing a mouse button over the given component.
   */
  fun clickRelativeTo(component: Component, x: Int, y: Int, button: FakeMouse.Button = LEFT) {
    val location = getPosition(component)
    mouse.click(location.x + x, location.y + y, button)
  }

  /**
   * Returns the first component of the given type satisfying the given predicate by doing breadth-first
   * search starting from the root component, or null if no components satisfy the predicate.
   */
  fun <T: Any> findComponent(type: Class<T>, predicate: (T) -> Boolean = { true }): T? = root.findDescendant(type, predicate)

  inline fun <reified T: Any> findComponent(crossinline predicate: (T) -> Boolean = { true }): T? = root.findDescendant(predicate)

  inline fun <reified T: Any> getComponent(crossinline predicate: (T) -> Boolean = { true }): T = root.getDescendant(predicate)

  /**
   * Returns all components of the given type satisfying the given predicate in the breadth-first
   * order.
   */
  fun <T: Any> findAllComponents(type: Class<T>, predicate: (T) -> Boolean = { true }): List<T> =
    root.findAllDescendants(type, predicate).toList()

  inline fun <reified T: Any> findAllComponents(crossinline predicate: (T) -> Boolean = { true }): List<T> =
    root.findAllDescendants(predicate).toList()

  fun targetMouseEvent(x: Int, y: Int): RelativePoint? = findTarget(root, x, y)

  private fun findTarget(component: Component, x: Int, y: Int): RelativePoint? {
    if (component.contains(x, y)) {
      if (component is Container) {
        for (i in 0 until component.componentCount) {
          val child = component.getComponent(i)
          if (child.isVisible) {
            val target = findTarget(child, x - child.x, y - child.y)
            if (target != null) {
              return target
            }
          }
        }
      }
      if (isMouseTarget(component)) {
        return RelativePoint(component, x, y)
      }
    }
    return null
  }

  private fun isMouseTarget(target: Component): Boolean {
    return target.mouseListeners.isNotEmpty() || target.mouseMotionListeners.isNotEmpty() || target.mouseWheelListeners.isNotEmpty() ||
           target is ActionButton // ActionButton calls enableEvents and overrides processMouseEvent
  }

  /**
   * IJ doesn't always refresh the state of the toolbar buttons. This method forces it to refresh.
   */
  fun updateToolbars() {
    updateToolbars(root)
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      layoutAndDispatchEvents()
    }
    else {
      layout()
    }
  }

  private fun updateToolbars(component: Component) {
    val componentQueue = ArrayDeque<Component>()
    val futures = mutableListOf<Future<*>>()
    componentQueue.add(component)
    while (componentQueue.isNotEmpty()) {
      when (val c = componentQueue.removeFirst()) {
        is ActionToolbar -> futures.add(c.updateActionsAsync())
        is ActionButton -> {
          c.updateUI()
          c.updateIcon()
        }
        is Container -> {
          for (child in c.components) {
            componentQueue.add(child)
          }
        }
      }
    }
    for (future in futures) {
      waitForCondition(2.seconds) { future.isDone }
    }
  }

  class RelativePoint(@JvmField val component: Component, @JvmField val x: Int, @JvmField val y: Int)

  private class FakeGraphicsConfiguration(scale: Double) : GraphicsConfiguration() {

    private val transform: AffineTransform = AffineTransform.getScaleInstance(scale, scale)
    private val device: GraphicsDevice = FakeGraphicsDevice(this)

    override fun getDevice(): GraphicsDevice = device

    override fun createCompatibleVolatileImage(width: Int, height: Int, caps: ImageCapabilities?, transparency: Int): VolatileImage =
      FakeVolatileImage(width, height, caps)

    override fun getColorModel(): ColorModel = ColorModel.getRGBdefault()

    override fun getColorModel(transparency: Int): ColorModel = ColorModel.getRGBdefault()

    override fun getDefaultTransform(): AffineTransform = transform

    override fun getNormalizingTransform(): AffineTransform = transform

    override fun getBounds(): Rectangle = Rectangle()
  }

  private class FakeVolatileImage(
    private val width: Int,
    private val height: Int,
    private val capabilities: ImageCapabilities?,
  ) : VolatileImage() {

    private val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    override fun getWidth(): Int = width

    override fun getWidth(observer: ImageObserver?): Int = width

    override fun getHeight(): Int = height

    override fun getHeight(observer: ImageObserver?): Int = height

    override fun getProperty(name: String, observer: ImageObserver?): Any? = null

    override fun getCapabilities(): ImageCapabilities? = capabilities

    override fun getSnapshot(): BufferedImage = bufferedImage

    override fun createGraphics(): Graphics2D = bufferedImage.createGraphics()

    override fun validate(gc: GraphicsConfiguration): Int = IMAGE_OK

    override fun contentsLost(): Boolean = false
  }

  private class FakeGraphicsDevice constructor(private val defaultConfiguration: GraphicsConfiguration) : GraphicsDevice() {

    override fun getType(): Int = TYPE_RASTER_SCREEN

    override fun getIDstring(): String = "FakeDevice"

    override fun getConfigurations(): Array<GraphicsConfiguration> = emptyArray()

    override fun getDefaultConfiguration(): GraphicsConfiguration = defaultConfiguration
  }
}

private fun wrapInFakeWindow(rootPane: JRootPane, parentDisposable: Disposable?) {
  // A mock is used here because in a headless environment it is not possible to instantiate
  // Window or any of its subclasses due to checks in the Window constructor.
  val mockWindow = mock(Window::class.java)
  whenever(mockWindow.treeLock).thenCallRealMethod()
  whenever(mockWindow.toolkit).thenReturn(fakeToolkit)
  whenever(mockWindow.isShowing).thenReturn(true)
  whenever(mockWindow.isVisible).thenReturn(true)
  whenever(mockWindow.isEnabled).thenReturn(true)
  whenever(mockWindow.isLightweight).thenReturn(true)
  whenever(mockWindow.isFocusableWindow).thenReturn(true)
  whenever(mockWindow.locationOnScreen).thenReturn(Point(0, 0))
  whenever(mockWindow.size).thenReturn(rootPane.size)
  whenever(mockWindow.bounds).thenReturn(Rectangle(0, 0, rootPane.width, rootPane.height))
  whenever(mockWindow.ownedWindows).thenReturn(emptyArray())
  whenever(mockWindow.isFocused).thenReturn(true)
  whenever(mockWindow.getFocusTraversalKeys(anyInt())).thenCallRealMethod()
  ComponentAccessor.setPeer(mockWindow, FakeWindowPeer())
  ComponentAccessor.setParent(rootPane, mockWindow)
  rootPane.addNotify()
  if (parentDisposable != null) {
    Disposer.register(parentDisposable) { runInEdtAndWait { rootPane.removeNotify() } }
  }
}

private fun getTopLevelComponent(component: Component): Component {
  var c = component
  while (c.parent != null && c.parent !is Window) {
    c = c.parent
  }
  return c
}

private val fakeToolkit = FakeUiToolkit()