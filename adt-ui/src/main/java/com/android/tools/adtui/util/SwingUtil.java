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

import com.google.common.math.IntMath;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import javax.swing.plaf.basic.ComboPopup;
import org.jetbrains.annotations.NotNull;

public final class SwingUtil {

  private static final String DOWN = "selectNext";
  private static final String UP = "selectPrevious";

  public static void doNotSelectSeparators(@NotNull JComboBox combo) {
    doNotSelectItems(combo, (e) -> e instanceof JSeparator);
  }

  public static void doNotSelectItems(@NotNull JComboBox combo, @NotNull Function<Object, Boolean> skipItem) {
    ActionMap actions = combo.getActionMap();
    actions.put(DOWN, new Actions(DOWN, combo, skipItem));
    actions.put(UP, new Actions(UP, combo, skipItem));
  }

  private static class Actions extends AbstractAction {

    private JComboBox comboBox;
    private Function<Object, Boolean> mySkipItem;

    public Actions(String name, JComboBox comboBox, Function<Object, Boolean> skipItem) {
      super(name);
      this.comboBox = comboBox;
      this.mySkipItem = skipItem;
    }

    /**
     * @see javax.swing.plaf.basic.BasicComboBoxUI.Actions#actionPerformed(ActionEvent)
     */
    @Override
    public void actionPerformed(ActionEvent e) {
      String key = (String)getValue(Action.NAME);
      if (DOWN.equals(key)) {
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
      else if (UP.equals(key)) {
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
      int startIndex = current;
      do {
        current = IntMath.mod(next ? current + 1 : current - 1, comboBox.getItemCount());
      }
      while (startIndex != current && mySkipItem.apply(comboBox.getItemAt(current)));
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

  /**
   * @return a {@link MouseEvent} similar to the given {@param event} except that its {@link MouseEvent#getID()} will be the {@param id}.
   */
  @NotNull
  public static MouseEvent convertMouseEventID(@NotNull MouseEvent event, int id) {
    return new MouseEvent((Component)event.getSource(),
                          id, event.getWhen(), event.getModifiers(), event.getX(), event.getY(),
                          event.getClickCount(), event.isPopupTrigger(),
                          event.getButton());
  }

  /**
   * @return a new {@link MouseEvent} similar to the given event except that its point will be the given point.
   */
  @NotNull
  public static MouseEvent convertMouseEventPoint(@NotNull MouseEvent event, @NotNull Point newPoint) {
    return new MouseEvent(
      event.getComponent(),
      event.getID(),
      event.getWhen(),
      event.getModifiers(),
      newPoint.x,
      newPoint.y,
      event.getClickCount(),
      event.isPopupTrigger());
  }
}
