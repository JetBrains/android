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
package com.android.tools.adtui.stdui.menu;

import com.android.tools.adtui.model.stdui.CommonAction;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class CommonMenuItem extends JMenuItem implements PropertyChangeListener {

  /**
   * Swing uses {@link JMenu#isSelected()} for highlight states, so here we use an extra boolean to indicate if the action is selected.
   */
  private boolean myActionSelected;

  public CommonMenuItem(CommonAction action) {
    super(action);
    myActionSelected = action.isSelected();
    action.addPropertyChangeListener(this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    switch (event.getPropertyName()) {
      case CommonAction.SELECTED_CHANGED:
        assert event.getNewValue() instanceof Boolean;
        setSelected((Boolean)event.getNewValue());
        myActionSelected = (Boolean)event.getNewValue();
        repaint();
        break;
      default:
        break;
    }
  }

  public boolean isActionSelected() {
    return myActionSelected;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setUI(new CommonMenuItemUI());
  }
}
