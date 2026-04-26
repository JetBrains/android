/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.streaming.xr.AbstractXrInputController
import com.android.tools.idea.streaming.xr.AbstractXrInputController.Companion.UNKNOWN_PASSTHROUGH_COEFFICIENT
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.beans.PropertyChangeListener
import javax.swing.JCheckBox
import javax.swing.JSlider
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Action to show a slider to control XR dimming/passthrough. */
internal class StreamingXrPassthroughAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    val xrController = getXrInputController(event)
    presentation.isVisible = xrController?.isPassthroughSupported == true && xrController.dimmingLevels.isNotEmpty()
    presentation.isEnabled = presentation.isVisible && xrController?.passthroughCoefficient != UNKNOWN_PASSTHROUGH_COEFFICIENT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val xrController = getXrInputController(event) ?: return
    XrPassthroughPopup(xrController).show(event.inputEvent?.component)
  }
}

private class XrPassthroughPopup(val xrController: AbstractXrInputController) {

  private val dimmingLevels = xrController.dimmingLevels
  private val coroutineScope = xrController.createCoroutineScope()

  fun show(anchor: Component?) {
    var passthroughCheckBox: Cell<JBCheckBox>? = null
    var dimmingSlider: Cell<JSlider>? = null

    val panel =
      panel {
          row("Passthrough:") {
            passthroughCheckBox =
              checkBox("").accessibleName("Passthrough").selected(xrController.passthroughEnabled).onChanged {
                setPassthroughAndDimming(it, dimmingSlider!!.component)
              }
          }
          row("Dimming:") {
            val smallFont = JBFont.label().lessOn(4f)
            dimmingSlider =
              slider(0, dimmingLevels.size - 1, 0, 1)
                .accessibleName("Dimming")
                .labelTable(dimmingLevels.indices.associateWith { JBLabel("${dimmingLevels[it].toPercent()}%").apply { font = smallFont } })
                .applyToComponent {
                  snapToTicks = true
                  value = xrController.dimmingLevelIndex
                  // JSlider is rendered with some internal margins that make it appear misaligned compared to other widgets.
                  // Adding the left empty border makes the UI DSL layout mechanics shift the slider to the left making it
                  // appear aligned with the checkbox.
                  border = JBUI.Borders.emptyLeft(16)
                }
                .onChanged {
                  if (!it.valueIsAdjusting) {
                    setPassthroughAndDimming(passthroughCheckBox!!.component, it)
                  }
                }
          }
        }
        .apply {
          isFocusCycleRoot = true
          isFocusTraversalPolicyProvider = true
          focusTraversalPolicy = LayoutFocusTraversalPolicy()
          border = JBUI.Borders.empty(14)
        }

    checkNotNull(passthroughCheckBox)
    checkNotNull(dimmingSlider)

    val popup =
      JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, passthroughCheckBox.component)
        .setTitle("Environment Visibility")
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnClickOutside(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelKeyEnabled(true)
        .createPopup()
    Disposer.register(xrController, popup)

    val propertyChangeListener = PropertyChangeListener {
      when (it.propertyName) {
        AbstractXrInputController.PASSTHROUGH_COEFFICIENT_PROPERTY -> {
          passthroughCheckBox.applyToComponent { isSelected = xrController.passthroughEnabled }
        }
        AbstractXrInputController.DIMMING_COEFFICIENT_PROPERTY -> {
          dimmingSlider.applyToComponent { value = xrController.dimmingLevelIndex }
        }
      }
    }

    xrController.addPropertyChangeListener(propertyChangeListener, popup)

    when (anchor) {
      is ActionButtonComponent -> popup.showUnderneathOf(anchor)
      null -> popup.showInFocusCenter()
      else -> popup.showInCenterOf(anchor)
    }
  }

  private fun setPassthroughAndDimming(passthroughCheckBox: JCheckBox, dimmingSlider: JSlider) {
    coroutineScope.launch {
      xrController.setPassthroughAndDimming(
        passthroughCoefficient = if (passthroughCheckBox.isSelected) 1f else 0f,
        dimmingCoefficient = dimmingLevels[dimmingSlider.value],
      )
    }
  }
}

/** Returns the index of the element closest to the given value. */
private fun FloatArray.indexOfClosest(value: Float): Int {
  require(isNotEmpty())
  var closestDistance = Float.MAX_VALUE
  var closestIndex = -1
  for ((index, element) in this.withIndex()) {
    val distance = abs(value - element)
    if (distance < closestDistance) {
      closestDistance = distance
      closestIndex = index
    }
    if (distance == 0f) {
      break
    }
  }
  return closestIndex
}

private val AbstractXrInputController.passthroughEnabled: Boolean
  get() = passthroughCoefficient >= 0.5f

private val AbstractXrInputController.dimmingLevelIndex: Int
  get() = dimmingLevels.indexOfClosest(dimmingCoefficient)

private fun Float.toPercent(): Int = (this * 100).roundToInt()
