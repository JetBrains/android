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
package com.android.tools.adtui.categorytable

import com.android.tools.adtui.common.ColoredIconGenerator
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBLabel
import java.awt.Color
import javax.swing.Icon
import javax.swing.JButton
import kotlin.reflect.KProperty

/**
 * A [TableComponent] that has an icon whose presentation it changes based on the
 * [TablePresentation].
 *
 * This enables a common interface and largely shared implementation for IconLabel and IconButton,
 * despite their need to inherit from different base classes.
 */
interface IconTableComponent : TableComponent {
  var baseIcon: Icon?
  var iconColor: Color?
  fun setIcon(icon: Icon?)
}

fun IconTableComponent.updateIconColor(presentation: TablePresentation) {
  iconColor =
    when {
      presentation.rowSelected && !ExperimentalUI.isNewUI() -> presentation.foreground
      else -> null
    }
}

/** A property delegate that calls [IconTableComponent.updateIcon] when its value changes. */
class IconTableComponentProperty<T>(initialValue: T) {
  private var value = initialValue

  operator fun getValue(component: IconTableComponent, property: KProperty<*>) = value

  operator fun setValue(component: IconTableComponent, property: KProperty<*>, value: T) {
    this.value = value
    component.updateIcon()
  }
}

internal fun IconTableComponent.updateIcon() {
  setIcon(baseIcon?.applyColor(iconColor))
}

internal fun Icon.applyColor(color: Color?): Icon =
  when {
    this is ColorableIcon -> applyColor(color)
    color != null -> ColoredIconGenerator.generateColoredIcon(this, color)
    else -> this
  }

/** A JBLabel that displays an Icon which changes color when it's in a selected row. */
class IconLabel(initialBaseIcon: Icon?) : JBLabel(initialBaseIcon), IconTableComponent {
  override var baseIcon by IconTableComponentProperty(initialBaseIcon)
  override var iconColor: Color? by IconTableComponentProperty(null)

  override fun addNotify() {
    super.addNotify()
    updateIcon()
  }

  override fun updateTablePresentation(
    manager: TablePresentationManager,
    presentation: TablePresentation
  ) {
    manager.defaultApplyPresentation(this, presentation)
    updateIconColor(presentation)
  }
}

/** A JButton that displays an Icon which changes color when it's in a selected row. */
open class IconButton(initialBaseIcon: Icon?) : JButton(), IconTableComponent {
  override var baseIcon by IconTableComponentProperty(initialBaseIcon)
  override var iconColor: Color? by IconTableComponentProperty(null)
  private var rowSelected: Boolean = false
    set(value) {
      field = value
      updateBorder()
    }

  init {
    addFocusListener { updateBorder() }
  }

  override fun addNotify() {
    super.addNotify()
    updateIcon()
  }

  override fun updateTablePresentation(
    manager: TablePresentationManager,
    presentation: TablePresentation
  ) {
    manager.defaultApplyPresentation(this, presentation)
    updateIconColor(presentation)
    rowSelected = presentation.rowSelected
  }

  fun updateBorder() {
    border = tableCellBorder(rowSelected, isFocusOwner)
  }

  override fun updateUI() {
    super.updateUI()
    // This is called when the component is created and when the theme is changed. If we wish
    // to override any properties set by the superclass, we must do so here, so that they persist
    // after theme changes.
    border = null
    isContentAreaFilled = false
    // This method gets called from the superclass constructor before the class is fully initialized
    // and the following will crash; only update on subsequent calls to updateUI.
    if (parent != null) {
      updateIcon()
    }
  }
}

/** An Icon that can change color. */
interface ColorableIcon : Icon {
  fun applyColor(color: Color?): Icon
}

/**
 * The default IntelliJ progress spinner animated icon, made colorable.
 *
 * Inheriting from it is necessary to access its underlying Frames.
 */
class ColorableAnimatedSpinnerIcon : AnimatedIcon.Default(), ColorableIcon {
  override fun applyColor(color: Color?): Icon =
    when (color) {
      null -> this
      else -> AnimatedIcon(*frames.map { it.applyColor(color) }.toTypedArray())
    }

  private fun Frame.applyColor(color: Color): Frame {
    val frame = this
    return object : Frame {
      override fun getIcon() = ColoredIconGenerator.generateColoredIcon(frame.icon, color)
      override fun getDelay() = frame.delay
    }
  }
}
