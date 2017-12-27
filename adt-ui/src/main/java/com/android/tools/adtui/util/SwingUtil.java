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
package com.android.tools.adtui.util;

import com.intellij.util.ui.UIUtil;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.event.ActionEvent;

public class SwingUtil {

  private static final String DOWN = "selectNext";
  private static final String UP = "selectPrevious";

  public static void doNotSelectSeparators(JComboBox combo) {
    ActionMap actions = combo.getActionMap();
    actions.put(DOWN, new Actions(DOWN, combo));
    actions.put(UP, new Actions(UP, combo));
  }

  private static class Actions extends AbstractAction {

    private JComboBox comboBox;

    public Actions(String name, JComboBox comboBox) {
      super(name);
      this.comboBox = comboBox;
    }

    /**
     * @see javax.swing.plaf.basic.BasicComboBoxUI.Actions#actionPerformed(ActionEvent)
     */
    @Override
    public void actionPerformed(ActionEvent e) {
      String key = (String)getValue(Action.NAME);
      if (key == DOWN) {
        if (comboBox.isShowing()) {
          if (comboBox.isPopupVisible()) {
            if (comboBox.getUI() != null) {
              selectNextPossibleValue();
            }
          }
          else {
            comboBox.showPopup();
          }
        }
      }
      else if (key == UP) {
        if (comboBox.getUI() != null) {
          if (comboBox.isPopupVisible()) {
            selectPreviousPossibleValue();
          }
          else if (UIManager.getBoolean("ComboBox.showPopupOnNavigation")) {
            comboBox.showPopup();
          }
        }
      }
    }

    private JList getPopupList() {
      ComboPopup popup = UIUtil.getComboBoxPopup(comboBox);
      assert popup != null;
      return popup.getList();
    }

    private boolean isTableCellEditor() {
      return Boolean.TRUE.equals(comboBox.getClientProperty("JComboBox.isTableCellEditor"));
    }

    private int newIndex(int current, boolean next) {
      do {
        current = next ? current + 1 : current - 1;
      }
      while (current >= 0 && current < comboBox.getItemCount() && comboBox.getItemAt(current) instanceof JSeparator);
      return current;
    }

    /**
     * Selects the next item in the list.  It won't change the selection if the
     * currently selected item is already the last item.
     *
     * @see javax.swing.plaf.basic.BasicComboBoxUI#selectNextPossibleValue()
     */
    protected void selectNextPossibleValue() {
      boolean isTableCellEditor = isTableCellEditor();
      JList listBox = getPopupList();
      int si = comboBox.isPopupVisible() ? listBox.getSelectedIndex() : comboBox.getSelectedIndex();
      si = newIndex(si, true);
      if (si <= comboBox.getModel().getSize() - 1) {
        listBox.setSelectedIndex(si);
        listBox.ensureIndexIsVisible(si);
        if (!isTableCellEditor) {
          if (!(UIManager.getBoolean("ComboBox.noActionOnKeyNavigation") && comboBox.isPopupVisible())) {
            comboBox.setSelectedIndex(si);
          }
        }
        comboBox.repaint();
      }
    }

    /**
     * Selects the previous item in the list.  It won't change the selection if the
     * currently selected item is already the first item.
     *
     * @see javax.swing.plaf.basic.BasicComboBoxUI#selectPreviousPossibleValue()
     */
    protected void selectPreviousPossibleValue() {
      boolean isTableCellEditor = isTableCellEditor();
      JList listBox = getPopupList();
      int si = comboBox.isPopupVisible() ? listBox.getSelectedIndex() : comboBox.getSelectedIndex();
      si = newIndex(si, false);
      if (si >= 0) {
        listBox.setSelectedIndex(si);
        listBox.ensureIndexIsVisible(si);
        if (!isTableCellEditor) {
          if (!(UIManager.getBoolean("ComboBox.noActionOnKeyNavigation") && comboBox.isPopupVisible())) {
            comboBox.setSelectedIndex(si);
          }
        }
        comboBox.repaint();
      }
    }
  }
}
