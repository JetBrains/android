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
package com.android.tools.idea.assistant.view

import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.StatefulButtonNotifier
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.view.StatefulButton.ActionButton
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Paint
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import org.jetbrains.annotations.TestOnly

/**
 * A wrapper presentation on [ActionButton] that allows for the button to maintain state. In
 * practice this means that either a button is displayed or a message indicating why the action was
 * not available is displayed. A common example is adding dependencies. If the dependency has
 * already been added, displaying a success message instead of an "add" button is appropriate.
 */
class StatefulButton(
  action: ActionData,
  listener: ActionListener,
  stateManager: AssistActionStateManager?,
  project: Project
) : JPanel(GridBagLayout()) {
  @JvmField @VisibleForTesting val myButton: ActionButton
  private val mySuccessMessage: String?
  private val myStateManager: AssistActionStateManager?
  val actionData: ActionData
  val project: Project
  private val myMessageBusConnections: MutableCollection<MessageBusConnection> = ArrayList()

  @JvmField @VisibleForTesting var myMessage: StatefulButtonMessage? = null

  @get:TestOnly var isLoaded = false

  /**
   * Creates a button that changes UI based on state.
   *
   * @param action model parsed from xml
   * @param listener listens for click and handles action
   * @param stateManager a button can be associated with a manager that listens for updates and
   * changes button UI. If null, button is always in default state (same as NOT_APPLICABLE)
   * @param project
   */
  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false
    actionData = action
    myStateManager = stateManager
    this.project = project

    // TODO: Don't cache this, restructure messaging to be more centralized with state-dependent
    // templates. For example, allow the bundle
    // to express the "partial" state with a message "{0} of {1} modules have Foo added", "complete"
    // state with "All modules with Foo added"
    // etc.
    mySuccessMessage = action.successMessage
    val c = GridBagConstraints()
    c.gridx = 0
    c.gridy = 0
    c.weightx = 1.0
    c.anchor = GridBagConstraints.NORTHWEST
    c.insets = JBUI.insets(7, 0, 10, 5)
    val buttonPanel = JPanel(FlowLayout())
    add(buttonPanel, c)
    myButton = ActionButton(action, listener, this)
    buttonPanel.add(myButton)
    if (myStateManager != null) {
      myButton.isEnabled = false
      val loadingIcon = AsyncProcessIcon("Loading")
      buttonPanel.add(loadingIcon)
      val app = ApplicationManager.getApplication()
      app.executeOnPooledThread {
        myStateManager.init(project, action)
        app.invokeLater {
          myButton.isVisible = false
          myMessage = myStateManager.getStateDisplay(project, action, mySuccessMessage)
          if (myMessage != null) {
            c.gridy++
            c.fill = GridBagConstraints.HORIZONTAL
            add(myMessage, c)
            // Initialize to hidden until state management is completed.
            myMessage!!.isVisible = false
          }
          updateButtonState()
          loadingIcon.isVisible = false
          isLoaded = true
        }
      }
    } else {
      // Initialize the button state. This includes making the proper element visible.
      updateButtonState()
      isLoaded = true
    }
  }

  override fun addNotify() {
    assert(SwingUtilities.isEventDispatchThread())
    updateButtonState()
    if (myStateManager != null) {
      // Listen for notifications that the state has been updated.
      val connection = project.messageBus.connect()
      myMessageBusConnections.add(connection)
      connection.subscribe(
        StatefulButtonNotifier.BUTTON_STATE_TOPIC,
        StatefulButtonNotifier { updateButtonState() }
      )
    }
    super.addNotify()
  }

  override fun removeNotify() {
    assert(SwingUtilities.isEventDispatchThread())
    myMessageBusConnections.forEach { it.disconnect() }
    myMessageBusConnections.clear()
    super.removeNotify()
  }

  /**
   * Updates the state of the button display based on the associated `AssistActionStateManager` if
   * present. This should be called whenever there may have been a state change.
   *
   * TODO: Determine how to update the state on card view change at minimum.
   */
  fun updateButtonState() {
    UIUtil.invokeLaterIfNeeded {

      // There may be cases where the action is not stateful such as triggering a debug event which
      // can occur any number of times.
      if (myStateManager == null) {
        myButton.isEnabled = true
        return@invokeLaterIfNeeded
      }
      val state = myStateManager.getState(project, actionData)
      revalidate()
      repaint()
      if (myMessage != null) {
        updateUIForState(state)
      }
    }
  }

  private fun updateUIForState(state: AssistActionState) {
    myButton.isVisible = state.isButtonVisible
    myButton.isEnabled = state.isButtonEnabled
    if (myMessage != null) {
      myMessage!!.isVisible = state.isMessageVisible
    }
    if (state.isMessageVisible && myStateManager != null) {
      remove(myMessage)
      myMessage = myStateManager.getStateDisplay(project, actionData, mySuccessMessage)
      if (myMessage == null) {
        return
      }
      val c = GridBagConstraints()
      c.gridx = 0
      c.gridy = 1
      c.weightx = 1.0
      c.anchor = GridBagConstraints.NORTHWEST
      c.fill = GridBagConstraints.HORIZONTAL
      c.insets = JBUI.insets(7, 0, 10, 5)
      add(myMessage, c)
    }
  }

  /**
   * Generic button used for handling arbitrary actions. No display properties should be overridden
   * here as this class purely addresses logical handling and is not opinionated about display.
   * Action buttons may have a variety of visual styles which will either be added inline where used
   * or by subclassing this class.
   */
  class ActionButton(action: ActionData, listener: ActionListener, wrapper: StatefulButton) :
    JButton(action.label) {
    val key: String?
    private val myButtonWrapper: StatefulButton

    /**
     * @param action POJO containing the action configuration.
     * @param listener The common listener used across all action buttons.
     */
    init {
      key = action.key
      myButtonWrapper = wrapper
      addActionListener(listener)
      isOpaque = false
      if (action.isHighlighted) {
        highlight()
      }
    }

    fun updateState() {
      myButtonWrapper.updateButtonState()
    }

    val actionData: ActionData
      get() = myButtonWrapper.actionData
    val project: Project
      get() = myButtonWrapper.project

    /**
     * Set this button's background, border, and font styles to look the same as a default dialog
     * button
     */
    private fun highlight() {
      if (getUI() is DarculaButtonUI) {
        // Background color and font
        setUI(HighlightedDarculaButtonUI())

        // Border color
        border =
          object : DarculaButtonPainter() {
            override fun getBorderPaint(button: Component): Paint {
              return JBColor.namedColor(
                "Button.default.focusedBorderColor",
                JBColor.namedColor("Button.darcula.defaultFocusedOutlineColor", 0x87afda)
              )
            }
          }

        // Text color
        var foreground = UIManager.getColor("Button.default.foreground")
        if (foreground == null) {
          foreground = UIManager.getColor("Button.darcula.selectedButtonForeground")
        }
        foreground?.let { setForeground(it) }
      }
    }
  }

  private class HighlightedDarculaButtonUI : DarculaButtonUI() {
    override fun getButtonColorStart(): Color {
      return defaultButtonColorStart
    }

    override fun getButtonColorEnd(): Color {
      return defaultButtonColorEnd
    }

    override fun setupDefaultButton(button: JComponent, graphics: Graphics) {
      val font = button.font
      if (!SystemInfo.isMac && font is FontUIResource) {
        graphics.font = font.deriveFont(Font.BOLD)
      }
    }
  }
}
