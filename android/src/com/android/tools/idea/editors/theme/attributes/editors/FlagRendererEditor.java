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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.Html;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;

/**
 * Renderer and Editor for attributes that take flags as values.
 * When editing, opens a dialog with checkboxes for all the possible flags to choose from.
 */
public class FlagRendererEditor extends TypedCellRendererEditor<EditedStyleItem, String> {
  private final Box myBox = new Box(BoxLayout.LINE_AXIS);
  /** Renderer component, with isShowing overridden because of the use of a {@link CellRendererPane} */
  private final JLabel myLabel = new JLabel() {
    @Override
    public boolean isShowing() {
      return true;
    }
  };
  private final EditorTextField myTextField = new EditorTextField();
  private EditedStyleItem myItem = null;

  public FlagRendererEditor() {
    myBox.add(myTextField);
    myBox.add(Box.createHorizontalGlue());
    JButton editButton = new JButton();
    myBox.add(editButton);
    myTextField.setAlignmentX(Component.LEFT_ALIGNMENT);
    myTextField.setOneLineMode(true);
    editButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    editButton.setText("...");
    int buttonWidth = editButton.getFontMetrics(editButton.getFont()).stringWidth("...") + 10;
    editButton.setPreferredSize(new Dimension(buttonWidth, editButton.getHeight()));
    myLabel.setOpaque(true); // Allows for colored background

    editButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final FlagDialog dialog = new FlagDialog();

        dialog.show();

        if (dialog.isOK()) {
          myTextField.setText(dialog.getValue());
          stopCellEditing();
        }
        else {
          cancelCellEditing();
        }
      }
    });
  }

  @Override
  public Component getRendererComponent(JTable table, EditedStyleItem value, boolean isSelected, boolean hasFocus, int row, int column) {
    myItem = value;
    final Component component;
    if (column == 0) {
      component = table.getDefaultRenderer(String.class).getTableCellRendererComponent(table, ThemeEditorUtils.getDisplayHtml(myItem), isSelected, hasFocus, row, column);
    } else {
      myLabel.setText(myItem.getValue());
      component = myLabel;
    }

    return component;
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myItem = value;
    myTextField.setText(myItem.getValue());
    return myBox;
  }

  @Override
  public String getEditorValue() {
    return myTextField.getText();
  }

  private class FlagDialog extends DialogWrapper {
    private final HashSet<String> mySelectedFlags = new HashSet<String>();

    public FlagDialog() {
      super(false);
      String value = myItem.getValue();
      if (!StringUtil.isEmpty(value)) {
        for (String flag : Splitter.on("|").split(value)) {
          mySelectedFlags.add(flag);
        }
      }
      setTitle("Flag Options");
      init();
    }

    private class CheckBoxListener implements ActionListener {

      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox checkbox = (JCheckBox)e.getSource();
        String name = checkbox.getText();
        if (mySelectedFlags.contains(name)) {
          mySelectedFlags.remove(name);
        }
        else {
          mySelectedFlags.add(name);
        }
      }
    }

    private class FlagCheckBox extends JCheckBox {
      LightweightHint myTooltipHint;
      final String myToolTipText;

      public FlagCheckBox(@NotNull String name, @Nullable String toolTipText) {
        super(name);
        myToolTipText = toolTipText;
        addActionListener(new CheckBoxListener());
        addMouseListener(new MouseAdapter() {

          @Override
          public void mouseEntered(MouseEvent e) {
            showTooltip(e);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            if (myTooltipHint != null) {
              myTooltipHint.hide();
              myTooltipHint = null;
            }
          }
        });
      }

      public void showTooltip(MouseEvent e) {
        if (myToolTipText == null) {
          return;
        }
        Point point = e.getPoint();
        if (myTooltipHint == null) {
          HintHint hintHint = new HintHint(this, point).setAwtTooltip(true).setContentActive(false);
          final JLayeredPane layeredPane = this.getRootPane().getLayeredPane();
          final JEditorPane pane = IdeTooltipManager.initPane(new Html(myToolTipText.replaceAll("\\s+", " ")), hintHint, layeredPane);
          myTooltipHint = new LightweightHint(pane);
          myTooltipHint.show(this, point.x, point.y, null, hintHint);
        }
        else {
          myTooltipHint.setLocation(new RelativePoint(this, point));
        }
      }

      @Override
      protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        if (myTooltipHint != null && !myTooltipHint.isRealPopup()) {
          showTooltip(e);
        }
      }
    }

    @Override
    protected JComponent createCenterPanel() {
      Box box = new Box(BoxLayout.PAGE_AXIS);
      AttributeDefinition attrDefinition =
        ResolutionUtils.getAttributeDefinition(myItem.getSourceStyle().getConfiguration(), myItem.getSelectedValue());
      if (attrDefinition != null) {
        String[] flagNames = attrDefinition.getValues();
        for (String flagName : flagNames) {
          FlagCheckBox flag = new FlagCheckBox(flagName, attrDefinition.getValueDoc(flagName));
          if (mySelectedFlags.contains(flagName)) {
            flag.setSelected(true);
          }
          box.add(flag);
        }
      }
      return box;
    }

    public String getValue() {
      return Joiner.on("|").join(mySelectedFlags);
    }
  }
}
