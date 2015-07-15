/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.ui;

import com.google.common.collect.Lists;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.event.PopupMenuEvent;
import java.awt.AWTEvent;
import java.awt.ItemSelectable;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * ComboBox-like control that displays the different variants for an attribute. The {@code VariantsComboBox} doesn't support editors or
 * custom renderers.
 * <p/>
 * {@link com.intellij.openapi.ui.ComboBox} doesn't correctly support right aligned text and has a very inconsistent UI that makes
 * the elements look different depending on the platform.
 * <p/>
 * {@link javax.swing.JComboBox} only allows the popup to be the same size as the control.
 */
public class VariantsComboBox extends JPanel implements ItemSelectable {
  private static final Border VARIANT_MENU_BORDER = JBUI.Borders.empty(5, 0);
  private static final Border VARIANT_ITEM_BORDER = new JBEmptyBorder(5);
  private static final JBColor VARIANT_MENU_BACKGROUND_COLOR = JBColor.WHITE;

  private JButton myButton = new JButton() {
    @Override
    public void updateUI() {
      setUI((ButtonUI)BasicButtonUI.createUI(this));
    }
  };
  private ComboBoxModel myModel = new DefaultComboBoxModel();
  private ListDataListener myListDataListener = new ListDataListener() {
    @Override
    public void intervalAdded(ListDataEvent e) {
      fireModelUpdated();
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
      fireModelUpdated();
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
      fireModelUpdated();
    }
  };
  private List<Action> myActions = Lists.newArrayList();

  public VariantsComboBox() {
    add(myButton);

    myButton.setBorder(BorderFactory.createEmptyBorder());
    myButton.setIcon(PlatformIcons.COMBOBOX_ARROW_ICON);
    myButton.setHorizontalTextPosition(SwingConstants.LEFT);
    myButton.setHorizontalAlignment(SwingConstants.RIGHT);
    myButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!isVisible()) {
          return;
        }

        if (!isPopupEnabled()) {
          // Only the selected element, do not show popup
          return;
        }

        // When the popup is open, if the user click on the button again, we consume the mouse event so the popup is
        // just closed (and not opened again by the click).
        final AWTEventListener clickListener = new AWTEventListener() {
          @Override
          public void eventDispatched(AWTEvent event) {
            MouseEvent mouseEvent = (MouseEvent)event;

            if (mouseEvent.getID() == MouseEvent.MOUSE_PRESSED && myButton.contains(mouseEvent.getPoint())) {
              mouseEvent.consume();
            }
          }
        };

        JPopupMenu variantsMenu = createPopupMenu();
        variantsMenu.addPopupMenuListener(new PopupMenuListenerAdapter() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            Toolkit.getDefaultToolkit().addAWTEventListener(clickListener, AWTEvent.MOUSE_EVENT_MASK);
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(clickListener);
          }
        });
        variantsMenu.show(myButton, 0, getSize().height);
      }
    });
  }

  public void setModel(@NotNull ComboBoxModel model) {
    myModel.removeListDataListener(myListDataListener);
    myModel = model;
    myModel.addListDataListener(myListDataListener);
    fireModelUpdated();
  }

  @NotNull
  public ComboBoxModel getModel() {
    return myModel;
  }

  @NotNull
  protected JPopupMenu createPopupMenu() {
    JPopupMenu menu = new JBPopupMenu();
    Border existingBorder = menu.getBorder();

    if (existingBorder != null) {
      menu.setBorder(BorderFactory.createCompoundBorder(existingBorder, VARIANT_MENU_BORDER));
    } else {
      menu.setBorder(VARIANT_MENU_BORDER);
    }
    menu.setBackground(VARIANT_MENU_BACKGROUND_COLOR);

    int nElements = myModel.getSize();
    for (int i = 0; i < nElements; i++) {
      final Object element = myModel.getElementAt(i);
      JMenuItem item = new JBMenuItem(element.toString());
      item.setBorder(VARIANT_ITEM_BORDER);
      if (i == 0) {
        // Pre-select the first element
        item.setArmed(true);
      }
      item.setBackground(VARIANT_MENU_BACKGROUND_COLOR);
      item.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Object selectedItem = myModel.getSelectedItem();

          if (selectedItem != null) {
            fireItemSelectionChanged(
              new ItemEvent(VariantsComboBox.this, ItemEvent.ITEM_STATE_CHANGED, selectedItem, ItemEvent.DESELECTED));
          }

          myModel.setSelectedItem(element);
          fireModelUpdated();

          fireItemSelectionChanged(
            new ItemEvent(VariantsComboBox.this, ItemEvent.ITEM_STATE_CHANGED, element, ItemEvent.SELECTED));
        }
      });
      menu.add(item);
    }

    if (!myActions.isEmpty()) {
      if (nElements > 0) {
        menu.addSeparator();
      }
      for (Action action : myActions) {
        JMenuItem newMenuItem = new JBMenuItem(action);
        newMenuItem.setBackground(VARIANT_MENU_BACKGROUND_COLOR);
        newMenuItem.setBorder(VARIANT_ITEM_BORDER);
        menu.add(newMenuItem);
      }
    }

    return menu;
  }

  @Override
  public Object[] getSelectedObjects() {
    Object selectedObject = myModel.getSelectedItem();

    return selectedObject != null ? new Object[]{selectedObject} : ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public void addItemListener(@NotNull ItemListener itemListener) {
    listenerList.add(ItemListener.class, itemListener);
  }

  @Override
  public void removeItemListener(@NotNull ItemListener itemListener) {
    listenerList.remove(ItemListener.class, itemListener);
  }

  @NotNull
  public ItemListener[] getItemListeners() {
    return listenerList.getListeners(ItemListener.class);
  }

  protected void fireModelUpdated() {
    myButton.setText(myModel.getSelectedItem().toString());
    myButton.setIcon(isPopupEnabled() ? PlatformIcons.COMBOBOX_ARROW_ICON : null);
  }

  protected void fireItemSelectionChanged(@NotNull ItemEvent e) {
    for (ItemListener itemListener : getItemListeners()) {
      itemListener.itemStateChanged(e);
    }
  }

  /**
   * Returns whether the popup expand icon (down arrow) should be displayed or not. The icon is displayed if:
   * <ul>
   *   <li>there is more than 1 variant</li>
   *   <li>OR, there are additional actions in the menu</li>
   * </ul>
   */
  protected boolean isPopupEnabled() {
    return myModel.getSize() > 1 || !myActions.isEmpty();
  }

  /**
   * Adds an action that will be displayed at the end of the variants list
   */
  public void addAction(@NotNull Action action) {
    myActions.add(action);
  }
}
