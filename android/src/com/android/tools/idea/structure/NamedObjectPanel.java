/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.List;
import java.util.Map;

public class NamedObjectPanel extends BuildFilePanel implements ListSelectionListener, DocumentListener, ItemListener {
  private JPanel myPanel;
  private JBList myList;
  private JTextField myObjectName;
  private JSplitPane mySplitPane;
  private JPanel myRightPane;
  private JPanel myDetailsPane;
  private final BuildFileKey myBuildFileKey;
  private final String myNewItemName;
  private final DefaultListModel myListModel;
  private NamedObject myCurrentObject;
  private final BiMap<BuildFileKey, JComponent> myProperties = HashBiMap.create();
  private boolean myIsUpdating;


  public NamedObjectPanel(@NotNull Project project, @NotNull String moduleName, @NotNull BuildFileKey buildFileKey,
                          @NotNull String newItemName) {
    super(project, moduleName);
    myBuildFileKey = buildFileKey;
    myNewItemName = newItemName;
    myListModel = new DefaultListModel();
    myObjectName.getDocument().addDocumentListener(this);

    myList = new JBList();
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.addListSelectionListener(this);
    myList.setModel(myListModel);
    myList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b2) {
        return super.getListCellRendererComponent(jList, ((NamedObject)o).getName(), i, b, b2);
      }
    });
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        addObject();
      }
    });
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        removeObject();
      }
    });
    decorator.disableUpDownActions();
    mySplitPane.setLeftComponent(decorator.createPanel());
    mySplitPane.setDividerLocation(200);
    myRightPane.setBorder(IdeBorderFactory.createBorder());

    NamedObject.Factory objectFactory = (NamedObject.Factory)myBuildFileKey.getValueFactory();
    if (objectFactory == null) {
      throw new IllegalArgumentException("Can't instantiate a NamedObjectPanel for BuildFileKey " + myBuildFileKey.toString());
    }

    GridLayoutManager layout = new GridLayoutManager(objectFactory.getProperties().size(), 2);
    myDetailsPane.setLayout(layout);
    GridConstraints constraints = new GridConstraints();
    constraints.setAnchor(GridConstraints.ANCHOR_WEST);
    for (BuildFileKey property : objectFactory.getProperties()) {
      constraints.setColumn(0);
      constraints.setFill(GridConstraints.FILL_NONE);
      constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
      myDetailsPane.add(new JBLabel(property.getDisplayName()), constraints);
      constraints.setColumn(1);
      constraints.setFill(GridConstraints.FILL_HORIZONTAL);
      constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
      JComponent component;
      switch(property.getType()) {
        case BOOLEAN:
          constraints.setFill(GridConstraints.FILL_NONE);
          ComboBox comboBox = new ComboBox(new EnumComboBoxModel<ThreeStateBoolean>(ThreeStateBoolean.class));
          comboBox.addItemListener(this);
          component = comboBox;
          break;
        case FILE:
        case FILE_AS_STRING:
          TextFieldWithBrowseButton fileField = new TextFieldWithBrowseButton();
          FileChooserDescriptor d = new FileChooserDescriptor(true, false, false, true, false, false);
          d.setShowFileSystemRoots(true);
          fileField.addBrowseFolderListener(new TextBrowseFolderListener(d));
          fileField.getTextField().getDocument().addDocumentListener(this);
          component = fileField;
          break;
        case REFERENCE:
          ComboBox editableComboBox = new ComboBox();
          BuildFileKey referencedType = property.getReferencedType();
          if (referencedType != null && myGradleBuildFile != null) {
            List<NamedObject> referencedObjects = (List<NamedObject>)myGradleBuildFile.getValue(referencedType);
            if (referencedObjects != null) {
              for (NamedObject o : referencedObjects) {
                editableComboBox.addItem(o.getName());
              }
            }
          }
          editableComboBox.setEditable(true);
          editableComboBox.addItemListener(this);
          ((JTextComponent)editableComboBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(this);
          component = editableComboBox;
          break;
        case STRING:
        case INTEGER:
        default:
          JBTextField textField = new JBTextField();
          textField.getDocument().addDocumentListener(this);
          component = textField;
          break;
      }
      myDetailsPane.add(component, constraints);
      myProperties.put(property, component);
      constraints.setRow(constraints.getRow() + 1);
    }
  }

  @Override
  public void init() {
    super.init();
    if (myGradleBuildFile == null) {
      return;
    }
    List<NamedObject> namedObjects = (List<NamedObject>)myGradleBuildFile.getValue(myBuildFileKey);
    if (namedObjects != null) {
      for (NamedObject object : namedObjects) {
        myListModel.addElement(object);
      }
    }
    myList.updateUI();
    updateUiFromCurrentObject();
  }

  @Override
  public void apply() {
    if (!myModified ||  myGradleBuildFile == null) {
      return;
    }
    List<NamedObject> objects = Lists.newArrayList();
    for (int i = 0; i < myListModel.size(); i++) {
      objects.add((NamedObject)myListModel.get(i));
    }
    myGradleBuildFile.setValue(myBuildFileKey, objects);

    myModified = false;
  }

  private void addObject() {
    int num = 1;
    String name = myNewItemName;
    while (getNamedItem(name) != null) {
      name = myNewItemName + num++;
    }
    myListModel.addElement(new NamedObject(name));
    myList.setSelectedIndex(myListModel.size() - 1);
    myList.updateUI();
    myModified = true;
  }

  private void removeObject() {
    int selectedIndex = myList.getSelectedIndex();
    if (selectedIndex < 0) {
      return;
    }
    myListModel.remove(selectedIndex);
    myList.setSelectedIndex(Math.max(0, Math.min(selectedIndex, myListModel.size() - 1)));
    myList.updateUI();
    myModified = true;
  }

  @Nullable
  private NamedObject getNamedItem(@NotNull String name) {
    for (int i = 0; i < myListModel.size(); i++) {
      NamedObject object = (NamedObject)myListModel.get(i);
      if (object.getName().equals(name)) {
        return object;
      }
    }
    return null;
  }

  @Override
  public void insertUpdate(@NotNull DocumentEvent documentEvent) {
    updateCurrentObjectFromUi();
  }

  @Override
  public void removeUpdate(@NotNull DocumentEvent documentEvent) {
    updateCurrentObjectFromUi();
  }

  @Override
  public void changedUpdate(@NotNull DocumentEvent documentEvent) {
    updateCurrentObjectFromUi();
  }

  @Override
  public void itemStateChanged(ItemEvent event) {
    if (event.getStateChange() == ItemEvent.SELECTED) {
      updateCurrentObjectFromUi();
    }
  }

  @Override
  protected void addItems(@NotNull JPanel parent) {
    add(myPanel, BorderLayout.CENTER);
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void valueChanged(@NotNull ListSelectionEvent listSelectionEvent) {
    updateUiFromCurrentObject();
  }

  @NotNull
  private static String getReferencePrefix(@NotNull BuildFileKey key) {
    BuildFileKey referencedType = key.getReferencedType();
    if (referencedType != null) {
      String path = referencedType.getPath();
      String lastLeaf = path.substring(path.lastIndexOf('/') + 1);
      return lastLeaf + ".";
    } else {
      return "";
    }
  }

  /**
   * Reads the state of the UI form objects and writes them into the currently selected object in the list, setting the dirty bit as
   * appropriate.
   */
  private void updateCurrentObjectFromUi() {
    if (myIsUpdating || myCurrentObject == null) {
      return;
    }
    String newName = myObjectName.getText();
    if (newName != null && !myCurrentObject.getName().equals(newName)) {
      myCurrentObject.setName(newName);
      myList.updateUI();
      myModified = true;
    }
    for (Map.Entry<BuildFileKey, JComponent> entry : myProperties.entrySet()) {
      BuildFileKey key = entry.getKey();
      JComponent component = entry.getValue();
      Object currentValue = myCurrentObject.getValue(key);
      Object newValue;
      switch(key.getType()) {
        case BOOLEAN:
          newValue = ((ThreeStateBoolean)((ComboBox)component).getSelectedItem()).getValue();
          break;
        case FILE:
        case FILE_AS_STRING:
          newValue = ((TextFieldWithBrowseButton)component).getText();
          if ("".equals(newValue)) {
            newValue = null;
          }
          if (newValue != null) {
            newValue = new File(newValue.toString());
          }
          break;
        case INTEGER:
          try {
            newValue = Integer.valueOf(((JBTextField)component).getText());
          } catch (Exception e) {
            newValue = null;
          }
          break;
        case REFERENCE:
          newValue = ((ComboBox)component).getEditor().getItem();
          String newStringValue = (String)newValue;
          if (newStringValue != null && newStringValue.isEmpty()) {
            newStringValue = null;
          }
          String prefix = getReferencePrefix(key);
          if (newStringValue != null && !newStringValue.startsWith(prefix)) {
            newStringValue = prefix + newStringValue;
          }
          newValue = newStringValue;
          break;
        case STRING:
        default:
          newValue = ((JBTextField)component).getText();
          if ("".equals(newValue)) {
            newValue = null;
          }
          break;
      }
      if (!Objects.equal(currentValue, newValue)) {
        myCurrentObject.setValue(key, newValue);
        myModified = true;
      }
    }
  }

  /**
   * Updates the form UI objects to reflect the currently selected object. Clears the objects and disables them if there is no selected
   * object.
   */
  private void updateUiFromCurrentObject() {
    myIsUpdating = true;
    int selectedIndex = myList.getSelectedIndex();
    NamedObject currentObject = selectedIndex >= 0 ? (NamedObject)myListModel.get(selectedIndex) : null;
    myCurrentObject = currentObject;
    myObjectName.setText(currentObject != null ? currentObject.getName() : "");
    myObjectName.setEnabled(currentObject != null);
    for (Map.Entry<BuildFileKey, JComponent> entry : myProperties.entrySet()) {
      BuildFileKey key = entry.getKey();
      JComponent component = entry.getValue();
      Object value = currentObject != null ? currentObject.getValue(key) : null;
      switch(key.getType()) {
        case BOOLEAN:
          ((ComboBox)component).setSelectedIndex(ThreeStateBoolean.forValue((Boolean)value).ordinal());
          break;
        case FILE:
        case FILE_AS_STRING:
          ((TextFieldWithBrowseButton)component).setText(value != null ? value.toString() : "");
          break;
        case REFERENCE:
          String stringValue = (String)value;
          String prefix = getReferencePrefix(key);
          if (stringValue == null) {
            stringValue = "";
          } else if (stringValue.startsWith(prefix)) {
            stringValue = stringValue.substring(prefix.length());
          }
          ((ComboBox)component).setSelectedItem(stringValue);
          break;
        case INTEGER:
        case STRING:
        default:
          ((JBTextField)component).setText(value != null ? value.toString() : "");
          break;
      }
      component.setEnabled(currentObject != null);
    }
    myIsUpdating = false;
  }
}
