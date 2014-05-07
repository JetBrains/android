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
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;

public class NamedObjectPanel extends BuildFilePanel implements DocumentListener, ListSelectionListener {
  private JPanel myPanel;
  private JBList myList;
  private JTextField myObjectName;
  private JSplitPane mySplitPane;
  private JPanel myRightPane;
  private KeyValuePane myDetailsPane;
  private final BuildFileKey myBuildFileKey;
  private final String myNewItemName;
  private final DefaultListModel myListModel;
  private NamedObject myCurrentObject;


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

    NamedObject.Factory objectFactory = (NamedObject.Factory)myBuildFileKey.getValueFactory();
    if (objectFactory == null) {
      throw new IllegalArgumentException("Can't instantiate a NamedObjectPanel for BuildFileKey " + myBuildFileKey.toString());
    }
    myDetailsPane.init(myGradleBuildFile, objectFactory.getProperties());
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
    myDetailsPane.clearModified();
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
  protected void addItems(@NotNull JPanel parent) {
    add(myPanel, BorderLayout.CENTER);
  }

  @Override
  public boolean isModified() {
    return myModified || myDetailsPane.isModified();
  }

  @Override
  public void valueChanged(@NotNull ListSelectionEvent listSelectionEvent) {
    updateUiFromCurrentObject();
  }

  private void updateCurrentObjectFromUi() {
    String newName = myObjectName.getText();
    if (newName != null && !myCurrentObject.getName().equals(newName)) {
      myCurrentObject.setName(newName);
      myList.updateUI();
      myModified = true;
    }
  }

  private void updateUiFromCurrentObject() {
    int selectedIndex = myList.getSelectedIndex();
    NamedObject currentObject = selectedIndex >= 0 ? (NamedObject)myListModel.get(selectedIndex) : null;
    myCurrentObject = currentObject;
    myObjectName.setText(currentObject != null ? currentObject.getName() : "");
    myObjectName.setEnabled(currentObject != null);
    myDetailsPane.setCurrentObject(myCurrentObject != null ? myCurrentObject.getValues() : null);
    myDetailsPane.updateUiFromCurrentObject();
  }
}
