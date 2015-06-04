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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.renderer.NlDefaultRenderer;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.Set;

public class NlReferenceEditor extends AbstractTableCellEditor implements ActionListener {
  private final JPanel myPanel;
  private final JBLabel myLabel;
  private final EditorTextField myEditorTextField;
  private final FixedSizeButton myBrowseButton;

  private NlProperty myProperty;
  private String myValue;

  public NlReferenceEditor() {
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myLabel = new JBLabel();
    myPanel.add(myLabel, BorderLayout.LINE_START);

    myEditorTextField = new EditorTextField();
    myPanel.add(myEditorTextField, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myEditorTextField
      .registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    selectTextOnFocusGain(myEditorTextField);

    myBrowseButton.addActionListener(this);
  }

  public static void selectTextOnFocusGain(EditorTextField textField) {
    textField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent focusEvent) {
        Object source = focusEvent.getSource();
        if (source instanceof EditorComponentImpl) {
          EditorImpl editor = ((EditorComponentImpl)source).getEditor();
          editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
        }
      }
    });
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;
    myProperty = (NlProperty)value;

    Icon icon = NlDefaultRenderer.getIcon(myProperty);
    myLabel.setIcon(icon);
    myLabel.setVisible(icon != null);

    String propValue = StringUtil.notNullize(myProperty.getValue());
    myValue = propValue;
    myEditorTextField.setText(propValue);

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myValue;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myBrowseButton) {
      ChooseResourceDialog dialog = showResourceChooser(myProperty);
      if (dialog.showAndGet()) {
        myValue = dialog.getResourceName();
        stopCellEditing();
      } else {
        cancelCellEditing();
      }
    }
    else if (e.getSource() == myEditorTextField) {
      myValue = myEditorTextField.getDocument().getText();
      stopCellEditing();
    }
  }

  public static ChooseResourceDialog showResourceChooser(@NotNull NlProperty p) {
    Module m = p.getComponent().getModel().getModule();
    AttributeDefinition definition = p.getDefinition();
    Set<AttributeFormat> formats = definition != null ? definition.getFormats() : EnumSet.allOf(AttributeFormat.class);

    // for some special known properties, we can narrow down the possible types (rather than the all encompassing reference type)
    String type = definition != null ? AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(definition.getName()) : null;
    ResourceType[] types = type == null ? ResourceEditor.convertTypes(formats) : new ResourceType[]{ResourceType.getEnum(type)};

    return new ChooseResourceDialog(m, types, p.getValue(), p.getComponent().getTag());
  }
}
