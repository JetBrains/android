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
package com.android.tools.adtui.common

import java.awt.Component
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JPanel
import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicPanelUI

/*
 * Base Panels that use the [primaryPanelBackground] and [secondaryPanelBackground]
 * and apply the same background to their children
 */

/**
 * Panel that will have a primary role in the UI.
 *
 * For example, the central panel in the Layout Editor
 */
open class AdtPrimaryPanel(layout: LayoutManager? = FlowLayout()) : JPanel(layout) {

  override fun addImpl(comp: Component?, constraints: Any?, index: Int) {
    super.addImpl(comp, constraints, index)
    comp?.background = background
  }

  override fun updateUI() {
    setUI(AdtPrimaryPanelUI)
  }
}

/**
 * Panel that will have a secondary role in the UI.
 *
 * For example, the component tree in the Layout Editor
 */
open class AdtSecondaryPanel(layout: LayoutManager? = FlowLayout()) : JPanel(layout) {

  override fun addImpl(comp: Component?, constraints: Any?, index: Int) {
    super.addImpl(comp, constraints, index)
    comp?.background = background
  }

  override fun updateUI() {
    setUI(AdtSecondaryPanelUI)
  }
}

/**
 * Flag to know if [initAdtDefaults] has been called
 */
private var defaultInstalled = false

/**
 * Add defaults color used by the AdtPanel to the UIManager
 */
private fun initAdtDefaults() {
  if (!defaultInstalled) {
    UIManager.put("AdtPanel.primary.background", primaryPanelBackground)
    UIManager.put("AdtPanel.secondary.background", secondaryPanelBackground)
    defaultInstalled = true
  }
}

/**
 * UI for [AdtPrimaryPanel]
 */
private object AdtPrimaryPanelUI : BasicPanelUI() {

  init {
    initAdtDefaults()
  }

  override fun installDefaults(p: JPanel?) {
    LookAndFeel.installColorsAndFont(p,
        "AdtPanel.primary.background",
        "Panel.foreground",
        "Panel.font")
    LookAndFeel.installBorder(p, "Panel.border")
    LookAndFeel.installProperty(p, "opaque", true)
  }
}

/**
 *  UI for [AdtSecondaryPanel]
 */
private object AdtSecondaryPanelUI : BasicPanelUI() {

  init {
    initAdtDefaults()
  }

  override fun installDefaults(p: JPanel?) {
    LookAndFeel.installColorsAndFont(p,
        "AdtPanel.secondary.background",
        "Panel.foreground",
        "Panel.font")
    LookAndFeel.installBorder(p, "Panel.border")
    LookAndFeel.installProperty(p, "opaque", true)
  }
}
