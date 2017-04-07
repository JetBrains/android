/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.assistant.view;

import com.android.tools.idea.assistant.AssistActionStateManager;
import com.android.tools.idea.assistant.StatefulButtonNotifier;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter;
import com.intellij.ide.ui.laf.intellij.MacIntelliJButtonBorder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A wrapper presentation on {@link ActionButton} that allows for the button to maintain state. In practice this means that either a button
 * is displayed or a message indicating why the action was not available is displayed. A common example is adding dependencies. If the
 * dependency has already been added, displaying a success message instead of an "add" button is appropriate.
 */
public class StatefulButton extends JPanel {

  private ActionButton myButton;
  private final String mySuccessMessage;
  private StatefulButtonMessage myMessage;
  private AssistActionStateManager myStateManager;
  private final ActionData myAction;
  private final Project myProject;

  @SuppressWarnings("deprecation")
  public StatefulButton(@NotNull ActionData action, @NotNull ActionListener listener, @NotNull Project project) {
    super(new VerticalLayout(5, SwingConstants.LEFT));
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    myAction = action;
    myProject = project;

    // TODO: Don't cache this, restructure messaging to be more centralized with state-dependent templates. For example, allow the bundle
    // to express the "partial" state with a message "{0} of {1} modules have Foo added", "complete" state with "All modules with Foo added"
    // etc.
    mySuccessMessage = action.getSuccessMessage();

    myButton = new ActionButton(action, listener, this) {
      @Override
      public void setUI(ButtonUI ui) {
        // Custom ButtonUI needed to avoid white-on-white that appears with Mac rendering.
        // Overriding setUI ensures a platform theme change doesn't undo the ButtonUI override.

        // Hack: Hardcoding a custom ButtonUI since the platform specific customization for Mac, MacIntelliJButtonUI, doesn't always draw
        // with get*Color* colors. Using a custom implementation that always draws using those colors.
        super.setUI(StatefulButtonUI.createUI(myButton));
        // TODO: Ask IntelliJ whether MacIntelliJButtonUI.paint should use get*Color*() to draw buttons when MacIntelliJButtonBorder present

        // Super hack: Below is an unfortunate side effect of the hardcoding above. The Mac and Darcula button border implementation classes
        // use dramatically different inset sizes, but the custom ButtonUI implementation is a fork of DarculaButtonUI which assumes a
        // larger inset. The code below injects a padded border with the Darcula insets when the Mac border is present.
        JButton defaultButton = new JButton();
        Border defaultButtonBorder = defaultButton.getBorder();
        if (defaultButtonBorder instanceof MacIntelliJButtonBorder) {
          defaultButtonBorder = new DarculaButtonPainter();
        }
        Insets insets = defaultButtonBorder.getBorderInsets(defaultButton);
        setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
      }
    };

    add(myButton);
    // Initialize to hidden until state management is completed.
    myButton.setVisible(false);

    for (AssistActionStateManager stateManager : AssistActionStateManager.EP_NAME.getExtensions()) {
      if (stateManager.getId().equals(action.getKey())) {
        myStateManager = stateManager;
        break;
      }
    }
    if (myStateManager != null) {
      myStateManager.init(project, action);
      myMessage = myStateManager.getStateDisplay(project, action, mySuccessMessage);
      add(myMessage);
      // Initialize to hidden until state management is completed.
      myMessage.setVisible(false);

      // Listen for notifications that the state has been updated.
      for (Module module : GradleProjectInfo.getInstance(project).getAndroidModules()) {
        MessageBusConnection connection = module.getMessageBus().connect(module);
        connection.subscribe(StatefulButtonNotifier.BUTTON_STATE_TOPIC, this::updateButtonState);
      }
    }

    // Initialize the button state. This includes making the proper element visible.
    updateButtonState();
  }

  @NotNull
  public ActionData getActionData() {
    return myAction;
  }

  /**
   * Updates the state of the button display based on the associated {@code AssistActionStateManager} if present. This should be called
   * whenever there may have been a state change.
   *
   * TODO: Determine how to update the state on card view change at minimum.
   */
  public void updateButtonState() {
    // Ensure we're on the AWT event dispatch thread
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::updateButtonState);
      return;
    }
    // There may be cases where the action is not stateful such as triggering a debug event which can occur any number of times.
    if (myStateManager == null) {
      myButton.setVisible(true);
      return;
    }

    AssistActionStateManager.ActionState state = myStateManager.getState(myProject, myAction);
    // HACK ALERT: Getting state may have the side effect of updating the underlying state display, re-fetch.
    // TODO: Refactor button related code and state management such that state can express arbitrary completion details (such as N of M
    // modules being complete) and allow the button message to be refreshed on state change.
    remove(myMessage);
    myMessage = myStateManager.getStateDisplay(myProject, myAction, mySuccessMessage);
    add(myMessage);
    revalidate();
    repaint();

    if (myMessage != null) {
      switch (state) {
        case ERROR:
        case COMPLETE:
          myButton.setVisible(false);
          myMessage.setVisible(true);
          break;
        // TODO(b/29617676): Show button disabled in a working state.
        case IN_PROGRESS:
        case INCOMPLETE:
          myButton.setVisible(true);
          myMessage.setVisible(false);
          break;
        default:
          myButton.setVisible(true);
          myMessage.setVisible(true);
      }
      return;
    }

    // If there's no message display, just disable/enable the button. Show in error state since there's no message to explain the issue.
    myButton.setEnabled(!state.equals(AssistActionStateManager.ActionState.COMPLETE));
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * Generic button used for handling arbitrary actions. No display properties should be overridden here as this class purely addresses
   * logical handling and is not opinionated about display. Action buttons may have a variety of visual styles which will either be added
   * inline where used or by subclassing this class.
   */
  public static class ActionButton extends JButton {
    private final String myKey;
    private final StatefulButton myButtonWrapper;

    /**
     * @param action   POJO containing the action configuration.
     * @param listener The common listener used across all action buttons.
     */
    public ActionButton(@NotNull ActionData action,
                        @NotNull ActionListener listener,
                        @NotNull StatefulButton wrapper) {
      super(action.getLabel());

      myKey = action.getKey();
      myButtonWrapper = wrapper;
      addActionListener(listener);
      setOpaque(false);
    }

    @NotNull
    public String getKey() {
      return myKey;
    }

    public void updateState() {
      myButtonWrapper.updateButtonState();
    }

    @NotNull
    public ActionData getActionData() {
      return myButtonWrapper.getActionData();
    }

    @NotNull
    public Project getProject() {
      return myButtonWrapper.getProject();
    }
  }
}
