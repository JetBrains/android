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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileKeyType;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.android.tools.idea.sdk.DefaultSdks;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;

public class KeyValuePane extends JPanel implements DocumentListener, ItemListener {
  private final BiMap<BuildFileKey, JComponent> myProperties = HashBiMap.create();
  private boolean myIsUpdating;
  private Map<BuildFileKey, Object> myCurrentObject;
  private boolean myModified;
  private final Project myProject;

  private final Set<String> myInstalledApis = new LinkedHashSet<String>();
  private final Set<String> myInstalledBuildTools = new LinkedHashSet<String>();
  private final List<String> myJavaCompatibility = ImmutableList.of(
    "JavaVersion.VERSION_1_6",
    "JavaVersion.VERSION_1_7"
  );

  private final Map<BuildFileKey, Iterable<String>> myKeysWithKnownValues =
    ImmutableMap.<BuildFileKey, Iterable<String>>builder()
      .put(BuildFileKey.MIN_SDK_VERSION, myInstalledApis)
      .put(BuildFileKey.TARGET_SDK_VERSION, myInstalledApis)
      .put(BuildFileKey.COMPILE_SDK_VERSION, myInstalledApis)
      .put(BuildFileKey.BUILD_TOOLS_VERSION, myInstalledBuildTools)
      .put(BuildFileKey.SOURCE_COMPATIBILITY, myJavaCompatibility)
      .put(BuildFileKey.TARGET_COMPATIBILITY, myJavaCompatibility)
      .build();

  public KeyValuePane(Project project) {
    myProject = project;
    for (Sdk sdk : DefaultSdks.getEligibleAndroidSdks()) {
      AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (sdkAdditionalData == null) {
        continue;
      }
      AndroidPlatform androidPlatform = sdkAdditionalData.getAndroidPlatform();
      if (androidPlatform == null) {
        continue;
      }
      IAndroidTarget target = androidPlatform.getTarget();
      AndroidVersion version = target.getVersion();
      String codename = version.getCodename();
      myInstalledApis.add(codename != null ? codename : Integer.toString(version.getApiLevel()));
      myInstalledBuildTools.add(target.getBuildToolInfo().getRevision().toString());
    }
  }

  public void setCurrentObject(@Nullable Map<BuildFileKey, Object> currentObject) {
    myCurrentObject = currentObject;
  }

  public void init(GradleBuildFile gradleBuildFile, Collection<BuildFileKey>properties) {
    GridLayoutManager layout = new GridLayoutManager(properties.size() + 1, 2);
    setLayout(layout);
    GridConstraints constraints = new GridConstraints();
    constraints.setAnchor(GridConstraints.ANCHOR_WEST);
    constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    for (BuildFileKey property : properties) {
      constraints.setColumn(0);
      constraints.setFill(GridConstraints.FILL_NONE);
      constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
      add(new JBLabel(property.getDisplayName()), constraints);
      constraints.setColumn(1);
      constraints.setFill(GridConstraints.FILL_HORIZONTAL);
      constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
      JComponent component;
      ComboBox comboBox;
      switch(property.getType()) {
        case BOOLEAN:
          constraints.setFill(GridConstraints.FILL_NONE);
          comboBox = getComboBox(false);
          comboBox.setModel(new EnumComboBoxModel<ThreeStateBoolean>(ThreeStateBoolean.class));
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
          constraints.setFill(GridConstraints.FILL_NONE);
          comboBox = getComboBox(true);
          BuildFileKey referencedType = property.getReferencedType();
          if (shouldUseComboBox(property)) {
            for (String s : myKeysWithKnownValues.get(property)) {
              comboBox.addItem(s);
            }
          } else if (referencedType != null && gradleBuildFile != null) {
            List<NamedObject> referencedObjects = (List<NamedObject>)gradleBuildFile.getValue(referencedType);
            if (referencedObjects != null) {
              for (NamedObject o : referencedObjects) {
                comboBox.addItem(o.getName());
              }
            }
          }
          component = comboBox;
          break;
        case CLOSURE:
        case STRING:
        case INTEGER:
        default:
          if (shouldUseComboBox(property)) {
            constraints.setFill(GridConstraints.FILL_NONE);
            comboBox = getComboBox(true);
            for (String s : myKeysWithKnownValues.get(property)) {
              comboBox.addItem(s);
            }
            component = comboBox;
          } else {
            JBTextField textField = new JBTextField();
            textField.getDocument().addDocumentListener(this);
            component = textField;
          }
          break;
      }
      add(component, constraints);
      myProperties.put(property, component);
      constraints.setRow(constraints.getRow() + 1);
    }
    constraints.setColumn(0);
    constraints.setVSizePolicy(GridConstraints.FILL_VERTICAL);
    constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    add(new JBLabel(""), constraints);
    updateUiFromCurrentObject();
  }

  private ComboBox getComboBox(boolean editable) {
    ComboBox anotherComboBox = new ComboBox();
    anotherComboBox.addItemListener(this);
    if (editable) {
      anotherComboBox.setEditable(true);
      ((JTextComponent)anotherComboBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(this);
    }
    return anotherComboBox;
  }


  /**
   * Reads the state of the UI form objects and writes them into the currently selected object in the list, setting the dirty bit as
   * appropriate.
   */
  private void updateCurrentObjectFromUi() {
    if (myIsUpdating || myCurrentObject == null) {
      return;
    }
    for (Map.Entry<BuildFileKey, JComponent> entry : myProperties.entrySet()) {
      BuildFileKey key = entry.getKey();
      JComponent component = entry.getValue();
      Object currentValue = myCurrentObject.get(key);
      Object newValue;
      BuildFileKeyType type = key.getType();
      switch(type) {
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
            if (shouldUseComboBox(key)) {
              newValue = Integer.valueOf(((ComboBox)component).getEditor().getItem().toString());
            } else {
              newValue = Integer.valueOf(((JBTextField)component).getText());
            }
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
        case CLOSURE:
        case STRING:
        default:
          if (shouldUseComboBox(key)) {
            newValue = ((ComboBox)component).getEditor().getItem();
            if ("".equals(newValue)) {
              newValue = null;
            }
          } else {
            newValue = ((JBTextField)component).getText();
            if ("".equals(newValue)) {
              newValue = null;
            }
          }

          if (type == BuildFileKeyType.CLOSURE && newValue != null) {
            List newListValue = new ArrayList();
            for (String o : Splitter.on(',').omitEmptyStrings().trimResults().split((String)newValue)) {
              newListValue.add(key.getValueFactory().parse(o, myProject));
            }
            newValue = newListValue;
          }
          break;
      }
      if (!Objects.equal(currentValue, newValue)) {
        myCurrentObject.put(key, newValue);
        myModified = true;
      }
    }
  }

  /**
   * Updates the form UI objects to reflect the currently selected object. Clears the objects and disables them if there is no selected
   * object.
   */
  public void updateUiFromCurrentObject() {
    myIsUpdating = true;
    for (Map.Entry<BuildFileKey, JComponent> entry : myProperties.entrySet()) {
      BuildFileKey key = entry.getKey();
      JComponent component = entry.getValue();
      Object value = myCurrentObject != null ? myCurrentObject.get(key) : null;
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
        case CLOSURE:
          if (value instanceof List) {
            value = Joiner.on(", ").join((List)value);
          }
          // Fall through to INTEGER/STRING/default case
        case INTEGER:
        case STRING:
        default:
          if (shouldUseComboBox(key)) {
            ((ComboBox)component).setSelectedItem(value != null ? value.toString() : "");
          } else {
            ((JBTextField)component).setText(value != null ? value.toString() : "");
          }
          break;
      }
      component.setEnabled(myCurrentObject != null);
    }
    myIsUpdating = false;
  }

  private boolean shouldUseComboBox(BuildFileKey key) {
    return myKeysWithKnownValues.containsKey(key);
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

  public boolean isModified() {
    return myModified;
  }

  public void clearModified() {
    myModified = false;
  }
}
