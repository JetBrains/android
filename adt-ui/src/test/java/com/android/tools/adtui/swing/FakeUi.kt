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

import com.android.tools.adtui.ImageUtils.createDipImage
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.PlatformTestUtil
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.util.ArrayDeque
import java.util.Enumeration
import java.util.function.Predicate
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource

/**
 * A utility class to interact with Swing components in unit tests.
 *
 * @param root the top-level component component
 * @param screenScale size of a virtual pixel in physical pixels; used for emulating a HiDPI screen
 */
class FakeUi @JvmOverloads constructor(val root: Component, val screenScale: Double = 1.0) {

  @JvmField
  val keyboard: FakeKeyboard = FakeKeyboard()
  @JvmField
  val mouse: FakeMouse = FakeMouse(this, keyboard)

  init {
    if (screenScale != 1.0 && root.parent == null) {
      // Applying graphics configuration involves re-parenting, so don't do it for a component that already has a parent.
      applyGraphicsConfiguration(FakeGraphicsConfiguration(screenScale), root)
    }
    root.preferredSize = root.size
    layout()
  }

  /**
   * Forces a re-layout of all components scoped by this FakeUi instance, for example in response to
   * a parent's bounds changing.
   *
   * Note: The constructor automatically forces a layout pass. You should only need to call this
   * method if you update the UI after constructing the FakeUi.
   */
  fun layout() {
    TreeWalker(root).descendantStream().forEach { obj: Component -> obj.doLayout() }
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
  fun render(): BufferedImage {
    return render(root)
  }

  /**
   * Renders the given component and returns the image reflecting its appearance.
   */
  fun render(component: Component): BufferedImage {
    val image =
        createDipImage((component.width * screenScale).toInt(), (component.height * screenScale).toInt(), BufferedImage.TYPE_INT_ARGB)
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
                       if (isMouseTarget(component)) " {*}" else "")
    if (component is Container) {
      for (i in 0 until component.componentCount) {
        dump(component.getComponent(i), "$prefix  ")
      }
    }
  }

  fun getPosition(component: Component): Point {
    var comp: Component? = component
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
   * Simulates pressing and releasing the left mouse button over the given component.
   */
  @Throws(InterruptedException::class)
  fun clickOn(component: Component) {
    val location = getPosition(component)
    mouse.click(location.x + component.width / 2, location.y + component.height / 2)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  /**
   * Returns the first component of the given type satisfying the given predicate by doing breadth-first
   * search starting from the root component, or null if no components satisfy the predicate.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> findComponent(type: Class<T>, predicate: (T) -> Boolean = { true }): T? {
    if (type.isInstance(root) && predicate(root as T)) {
      return root
    }
    if (root is Container) {
      val queue = ArrayDeque<Container>()
      queue.add(root)
      while (queue.isNotEmpty()) {
        val container = queue.remove()
        for (child in container.components) {
          if (type.isInstance(child) && predicate(child as T)) {
            return child
          }
          if (child is Container) {
            queue.add(child)
          }
        }
      }
    }
    return null
  }

  inline fun <reified T: Component> findComponent(crossinline predicate: (T) -> Boolean = { true }) : T? {
    return findComponent(T::class.java) { predicate(it) }
  }

  fun <T> findComponent(type: Class<T>, predicate: Predicate<T>): T? {
    return findComponent(type) { predicate.test(it) }
  }

  inline fun <reified T: Component> getComponent(crossinline predicate: (T) -> Boolean = { true }) : T {
    return findComponent(T::class.java) { predicate(it) } ?: throw AssertionError()
  }

  fun <T> getComponent(type: Class<T>, predicate: Predicate<T>): T {
    return findComponent(type) { predicate.test(it) } ?: throw AssertionError()
  }

  /**
   * Returns all components of the given type satisfying the given predicate in the breadth-first
   * order.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> findAllComponents(type: Class<T>, predicate: (T) -> Boolean = { true }): List<T> {
    val result = mutableListOf<T>()
    if (type.isInstance(root) && predicate(root as T)) {
      result.add(root)
    }
    if (root is Container) {
      val queue = ArrayDeque<Container>()
      queue.add(root)
      while (queue.isNotEmpty()) {
        val container = queue.remove()
        for (child in container.components) {
          if (type.isInstance(child) && predicate(child as T)) {
            result.add(child)
          }
          if (child is Container) {
            queue.add(child)
          }
        }
      }
    }
    return result
  }

  inline fun <reified T: Component> findAllComponents(crossinline predicate: (T) -> Boolean = { true }) : List<T> {
    return findAllComponents(T::class.java) { predicate(it) }
  }

  fun targetMouseEvent(x: Int, y: Int): RelativePoint? {
    return findTarget(root, x, y)
  }

  private fun findTarget(component: Component, x: Int, y: Int): RelativePoint? {
    if (component.contains(x, y)) {
      if (component is Container) {
        for (i in 0 until component.componentCount) {
          val child = component.getComponent(i)
          val target = findTarget(child, x - child.x, y - child.y)
          if (target != null) {
            return target
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
  }

  private fun applyGraphicsConfiguration(config: GraphicsConfiguration, component: Component) {
    // Work around package-private visibility of the Component.setGraphicsConfiguration method.
    val container: Container = object : Container() {
      override fun getGraphicsConfiguration(): GraphicsConfiguration {
        return config
      }
    }
    container.add(component)
  }

  private fun updateToolbars(component: Component) {
    if (component is ActionButton) {
      component.updateUI()
      component.updateIcon()
    }
    if (component is ActionToolbar) {
      val toolbar = component as ActionToolbar
      toolbar.updateActionsImmediately()
    }
    if (component is Container) {
      for (child in component.components) {
        updateToolbars(child)
      }
    }
  }

  class RelativePoint(@JvmField val component: Component, @JvmField val x: Int, @JvmField val y: Int)

  private class FakeGraphicsConfiguration(scale: Double) : GraphicsConfiguration() {

    private val transform: AffineTransform = AffineTransform.getScaleInstance(scale, scale)
    private val device: GraphicsDevice = FakeGraphicsDevice(this)

    override fun getDevice(): GraphicsDevice {
      return device
    }

    override fun getColorModel(): ColorModel {
      return ColorModel.getRGBdefault()
    }

    override fun getColorModel(transparency: Int): ColorModel {
      return ColorModel.getRGBdefault()
    }

    override fun getDefaultTransform(): AffineTransform {
      return transform
    }

    override fun getNormalizingTransform(): AffineTransform {
      return transform
    }

    override fun getBounds(): Rectangle {
      return Rectangle()
    }
  }

  private class FakeGraphicsDevice constructor(private val defaultConfiguration: GraphicsConfiguration) : GraphicsDevice() {

    override fun getType(): Int {
      return TYPE_RASTER_SCREEN
    }

    override fun getIDstring(): String {
      return "FakeDevice"
    }

    override fun getConfigurations(): Array<GraphicsConfiguration> {
      return emptyArray()
    }

    override fun getDefaultConfiguration(): GraphicsConfiguration {
      return defaultConfiguration
    }
  }
}

/**
 * Sets all default fonts to Droid Sans that is included in the bundled JDK. This makes fonts the same across all platforms.
 *
 * To improve error detection it may be helpful to scale the font used up (to improve matches across platforms and detect text changes)
 * or down (to decrease the importance of text in generated images).
 */
fun setPortableUiFont(scale: Float = 1.0f) {
  val keys: Enumeration<*> = UIManager.getLookAndFeelDefaults().keys()
  val default = ImageDiffTestUtil.getDefaultFont()
  while (keys.hasMoreElements()) {
    val key = keys.nextElement()
    val value = UIManager.get(key)
    if (value is FontUIResource) {
      val font = default.deriveFont(value.style).deriveFont(value.size.toFloat() * scale)
      UIManager.put(key, FontUIResource(font))
    }
  }
}