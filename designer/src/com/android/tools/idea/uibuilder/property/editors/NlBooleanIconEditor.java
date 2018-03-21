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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.tools.idea.common.property.NlProperty;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.ToggleActionButton;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.actionSystem.ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE;

/**
 * Editor for editing a boolean property displays as an icon with selection on/off.
 */
public class NlBooleanIconEditor {
  private static final String INSPECTOR_PLACE = "Inspector Place";

  private final BooleanAction myAction;
  private final ActionButton myButton;
  private final Presentation myPresentation;

  public NlBooleanIconEditor(@NotNull Icon icon, @NotNull String description) {
    this(icon, description, SdkConstants.VALUE_TRUE, SdkConstants.VALUE_FALSE);
  }

  public NlBooleanIconEditor(@NotNull Icon icon, @NotNull String description, @NotNull String singleValue) {
    this(icon, description, singleValue, null);
  }

  public NlBooleanIconEditor(@NotNull Icon icon, @NotNull String description, @NotNull String trueValue, @Nullable String falseValue) {
    myAction = new BooleanAction(icon, description, trueValue, falseValue);
    myPresentation = myAction.getTemplatePresentation().clone();
    myButton = new ActionButton(myAction, myPresentation, INSPECTOR_PLACE, NAVBAR_MINIMUM_BUTTON_SIZE);
    myButton.setFocusable(true);
  }

  public Component getComponent() {
    return myButton;
  }

  public void setProperty(@NotNull NlProperty property) {
    myAction.setProperty(property);
    updateDescription(property);

    // This will update the selected state of the ActionButton:
    AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(myButton), INSPECTOR_PLACE, myPresentation,
                                            ActionManager.getInstance(), 0);
    ActionUtil.performDumbAwareUpdate(myAction, event, false);
    myButton.updateIcon();  }

  private void updateDescription(@NotNull NlProperty property) {
    AttributeDefinition definition = property.getDefinition();
    if (definition != null) {
      String description = definition.getValueDoc(myAction.myTrueValue);
      if (description != null) {
        // TODO: Add a getter of the presentation instance in ActionButton. Then we can get rid of myPresentation.
        myPresentation.setDescription(description);
        myPresentation.setText(description);
      }
    }
  }

  private static class BooleanAction extends ToggleActionButton {
    private final String myTrueValue;
    private final String myFalseValue;
    private NlProperty myProperty;

    public BooleanAction(@NotNull Icon icon, @Nullable String description, @NotNull String trueValue, @Nullable String falseValue) {
      super(description, icon);
      getTemplatePresentation().setIcon(icon);
      myTrueValue = trueValue;
      myFalseValue = falseValue;
    }

    public void setProperty(@NotNull NlProperty property) {
      myProperty = property;
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return myProperty != null && myTrueValue.equals(myProperty.getResolvedValue());
    }

    @Override
    public void setSelected(AnActionEvent event, boolean selected) {
      myProperty.setValue(selected ? myTrueValue : myFalseValue);
    }
  }
}
