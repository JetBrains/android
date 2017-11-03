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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.assistant.AssistActionState;
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
import org.jetbrains.annotations.Nullable;

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

  @VisibleForTesting
  @NotNull
  final ActionButton myButton;
  @Nullable private final String mySuccessMessage;
  @Nullable private final AssistActionStateManager myStateManager;
  @NotNull private final ActionData myAction;
  @NotNull private final Project myProject;
  @VisibleForTesting
  @Nullable
  StatefulButtonMessage myMessage;

  /**
   * Creates a button that changes UI based on state.
   *
   * @param action       model parsed from xml
   * @param listener     listens for click and handles action
   * @param stateManager a button can be associated with a manager that listens for updates and changes button UI. If null, button is
   *                     always in default state (same as NOT_APPLICABLE)
   * @param project
   */
  @SuppressWarnings("deprecation")
  public StatefulButton(@NotNull ActionData action,
                        @NotNull ActionListener listener,
                        @Nullable AssistActionStateManager stateManager,
                        @NotNull Project project) {
    super(new VerticalLayout(5, SwingConstants.LEFT));
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    myAction = action;
    myStateManager = stateManager;
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

    AssistActionState state = myStateManager.getState(myProject, myAction);
    revalidate();
    repaint();


    if (myMessage != null) {
      updateUIForState(state);
      return;
    }
  }

  private void updateUIForState(AssistActionState state) {
    myButton.setVisible(state.isButtonVisible());
    myButton.setEnabled(state.isButtonEnabled());
    myMessage.setVisible(state.isMessageVisible());
    if (state.isMessageVisible()) {
      remove(myMessage);
      myMessage = myStateManager.getStateDisplay(myProject, myAction, mySuccessMessage);
      if (myMessage == null) {
        return;
      }
      add(myMessage);
    }
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
