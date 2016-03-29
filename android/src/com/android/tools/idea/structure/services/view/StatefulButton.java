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
package com.android.tools.idea.structure.services.view;

import com.android.tools.idea.structure.services.AssistActionStateManager;
import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceMap;
import com.android.tools.idea.structure.services.datamodel.ActionData;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A wrapper presentation on {@link ActionButton} that allows for the button to maintain state. In practice this means that either a button
 * is displayed or a message indicating why the action was not available is displayed. A common example is adding dependencies. If the
 * dependency has already been added, displaying a success message instead of an "add" button is appropriate.
 *
 * TODO: Determine how we want to listen for project and module changes and refresh display/replace the serviceMap
 */
public class StatefulButton extends JPanel {

  private ActionButton myButton;
  private StatefulButtonMessage myMessage;
  private AssistActionStateManager myStateManager;
  private DeveloperService myDeveloperService;

  public StatefulButton(@NotNull ActionData action, @NotNull ActionListener listener, @NotNull DeveloperServiceMap serviceMap) {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);
    BorderLayout layout = (BorderLayout)getLayout();
    layout.setVgap(0);
    layout.setHgap(0);

    String actionArgument = action.getActionArgument();
    myDeveloperService = serviceMap.get(actionArgument);

    myButton = new ActionButton(action, listener, this);
    add(myButton, BorderLayout.NORTH);
    // Initialize to hidden until state management is completed.
    myButton.setVisible(false);

    for (AssistActionStateManager stateManager : AssistActionStateManager.EP_NAME.getExtensions()) {
      if (stateManager.getId().equals(action.getKey())) {
        myStateManager = stateManager;
        break;
      }
    }
    if (myStateManager != null) {
      myStateManager.init(myDeveloperService);
      myMessage = myStateManager.getStateDisplay(myDeveloperService, action.getSuccessMessage());
      add(myMessage, BorderLayout.SOUTH);
      // Initialize to hidden until state management is completed.
      myMessage.setVisible(false);
    }

    // Initialize the button state. This includes making the proper element visible.
    updateButtonState();
  }

  /**
   * Updates the state of the button display based on the associated {@code AssistActionStateManager} if present. This should be called whenever
   * there may have been a state change.
   *
   * TODO: Determine how to update the state on card view change at minimum.
   */
  public void updateButtonState() {
    // There may be cases where the action is not stateful such as triggering a debug event which can occur any number of times.
    if (myStateManager == null) {
      myButton.setVisible(true);
      return;
    }
    boolean activate = myStateManager.isCompletable(myDeveloperService);

    if (myMessage != null) {
      myButton.setVisible(activate);
      myMessage.setVisible(!activate);
      return;
    }

    // If there's no message display, just disable/enable the button.
    myButton.setEnabled(activate);
  }

  /**
   * Generic button used for handling arbitrary actions. No display properties should be overridden here as this class purely addresses
   * logical handling and is not opinionated about display. Action buttons may have a variety of visual styles which will either be added
   * inline where used or by subclassing this class.
   */
  public class ActionButton extends JButton {
    private String myKey;
    private String myActionArgument;
    private StatefulButton myButtonWrapper;

    /**
     * @param action   POJO containing the action configuration.
     * @param listener The common listener used across all action buttons.
     */
    public ActionButton(@NotNull ActionData action,
                        @NotNull ActionListener listener,
                        @NotNull StatefulButton wrapper) {
      super(action.getLabel());

      myKey = action.getKey();
      myActionArgument = action.getActionArgument();
      myButtonWrapper = wrapper;
      addActionListener(listener);
      setOpaque(false);
    }

    public String getKey() {
      return myKey;
    }

    public String getActionArgument() {
      return myActionArgument;
    }

    public void updateState() {
      myButtonWrapper.updateButtonState();
    }
  }

}
