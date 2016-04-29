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
package com.android.tools.idea.gradle.structure.editors;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileKeyType;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.sdklib.AndroidTargetHash.getAddonHashString;
import static org.jetbrains.android.sdk.AndroidSdkUtils.getTargetLabel;

public class KeyValuePane extends JPanel implements DocumentListener, ItemListener {
  /**
   * Listener class that gets called any time the value for the given key is modified in the UI. This should be used to mark that
   * value is "dirty" and ensure it gets written out to the build file.
   */
  public interface ModificationListener {
    void modified(@NotNull BuildFileKey key);
  }

  private final BiMap<BuildFileKey, JComponent> myProperties = HashBiMap.create();
  private boolean myIsUpdating;
  private Map<BuildFileKey, Object> myCurrentBuildFileObject;
  private Map<BuildFileKey, Object> myCurrentModelObject;
  private final Project myProject;
  private final ModificationListener myListener;

  /**
   * This structure lets us define known values to populate combo boxes for some keys. The user can choose one of those known values from
   * the combo box, or enter a custom value. This structure is a map, where the key to the map is the BuildFileKey and the value is a
   * sub-map. This sub-map lets us show different strings in the combo box in the UI than what we actually read and write to the underlying
   * build file. For example, you can have a targetSdkVersion of 20 in the build file, but it will show that in the UI as
   * "API 20: Android 4.4 (KitKat Wear)". This sub-map is bi-directional, and has keys of the values that appear in the build file, and
   * values as what appears in the UI. For simple cases where the UI shows the same thing that appears in the build file, this can be
   * an identity mapping.
   */
  private final Map<BuildFileKey, BiMap<String, String>> myKeysWithKnownValues;

  public KeyValuePane(@NotNull Project project, @NotNull ModificationListener listener) {
    myProject = project;
    myListener = listener;
    AndroidSdkHandler sdkHandler = null;
    AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (androidSdkData != null) {
      sdkHandler = androidSdkData.getSdkHandler();
    }
    // Use immutable maps with builders for our built-in value maps because ImmutableBiMap ensures that iteration order is the same as
    // insertion order.
    ImmutableBiMap.Builder<String, String> buildToolsMapBuilder = ImmutableBiMap.builder();
    ImmutableBiMap.Builder<String, String> apisMapBuilder = ImmutableBiMap.builder();
    ImmutableBiMap.Builder<String, String> compiledApisMapBuilder = ImmutableBiMap.builder();

    if (sdkHandler != null) {
      ProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
      RepositoryPackages packages = sdkHandler.getSdkManager(logger).getPackages();
      for (LocalPackage p : packages.getLocalPackagesForPrefix(SdkConstants.FD_BUILD_TOOLS)) {
        buildToolsMapBuilder.put(p.getVersion().toString(), p.getVersion().toString());
      }

      for (IAndroidTarget target : sdkHandler.getAndroidTargetManager(logger).getTargets(logger)) {
        String label = getTargetLabel(target);
        String apiString, platformString;
        if (target.isPlatform()) {
          platformString = apiString = target.getVersion().getApiString();
          apisMapBuilder.put(apiString, label);
        } else {
          platformString = getAddonHashString(target.getVendor(), target.getName(), target.getVersion());
        }
        compiledApisMapBuilder.put(platformString, label);
      }
    }

    BiMap<String, String> installedBuildTools = buildToolsMapBuilder.build();
    BiMap<String, String> installedApis = apisMapBuilder.build();
    BiMap<String, String> installedCompileApis = compiledApisMapBuilder.build();
    BiMap<String, String> javaCompatibility = ImmutableBiMap.of("JavaVersion.VERSION_1_6", "1.6", "JavaVersion.VERSION_1_7", "1.7");

    myKeysWithKnownValues = ImmutableMap.<BuildFileKey, BiMap<String, String>>builder()
        .put(BuildFileKey.MIN_SDK_VERSION, installedApis)
        .put(BuildFileKey.TARGET_SDK_VERSION, installedApis)
        .put(BuildFileKey.COMPILE_SDK_VERSION, installedCompileApis)
        .put(BuildFileKey.BUILD_TOOLS_VERSION, installedBuildTools)
        .put(BuildFileKey.SOURCE_COMPATIBILITY, javaCompatibility)
        .put(BuildFileKey.TARGET_COMPATIBILITY, javaCompatibility)
        .build();
  }

  /**
   * Sets the current object as seen by parsing the build file directly. This controls what the user explicitly sets through the build file.
   * Any keys that are set to null are unset in the build file and will take on default values when the build file is executed.
   */
  public void setCurrentBuildFileObject(@Nullable Map<BuildFileKey, Object> currentBuildFileObject) {
    myCurrentBuildFileObject = currentBuildFileObject;
  }

  /**
   * Sets the current object as seen by querying the Gradle model after the build file is evaluated. This shows the user what the build file
   * will actually do, showing the default values of keys (as supplied by the plugin) that are otherwise not explicitly set in the file.
   */
  public void setCurrentModelObject(@Nullable Map<BuildFileKey, Object> currentModelObject) {
    myCurrentModelObject = currentModelObject;
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
      JBLabel label = new JBLabel(property.getDisplayName());
      add(label, constraints);
      constraints.setColumn(1);
      constraints.setFill(GridConstraints.FILL_HORIZONTAL);
      constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
      JComponent component;
      switch(property.getType()) {
        case BOOLEAN: {
          constraints.setFill(GridConstraints.FILL_NONE);
          ComboBox comboBox = getComboBox(false);
          comboBox.addItem("");
          comboBox.addItem("true");
          comboBox.addItem("false");
          comboBox.setPrototypeDisplayValue("(false) ");
          component = comboBox;
          break;
        }
        case FILE:
        case FILE_AS_STRING: {
          JBTextField textField = new JBTextField();
          TextFieldWithBrowseButton fileField = new TextFieldWithBrowseButton(textField);
          FileChooserDescriptor d = new FileChooserDescriptor(true, false, false, true, false, false);
          d.setShowFileSystemRoots(true);
          fileField.addBrowseFolderListener(new TextBrowseFolderListener(d));
          fileField.getTextField().getDocument().addDocumentListener(this);
          component = fileField;
          break;
        }
        case REFERENCE: {
          constraints.setFill(GridConstraints.FILL_NONE);
          ComboBox comboBox = getComboBox(true);
          if (hasKnownValues(property)) {
            for (String s : myKeysWithKnownValues.get(property).values()) {
              comboBox.addItem(s);
            }
          }
          // If there are no hardcoded values, the combo box's values will get populated later when the panel for the container reference
          // type wakes up and notifies us of its current values.
          component = comboBox;
          break;
        }
        case CLOSURE:
        case STRING:
        case INTEGER:
        default: {
          if (hasKnownValues(property)) {
            constraints.setFill(GridConstraints.FILL_NONE);
            ComboBox comboBox = getComboBox(true);
            for (String s : myKeysWithKnownValues.get(property).values()) {
              comboBox.addItem(s);
            }
            component = comboBox;
          }
          else {
            JBTextField textField = new JBTextField();
            textField.getDocument().addDocumentListener(this);
            component = textField;
          }
          break;
        }
      }
      add(component, constraints);
      label.setLabelFor(component);
      myProperties.put(property, component);
      constraints.setRow(constraints.getRow() + 1);
    }
    constraints.setColumn(0);
    constraints.setVSizePolicy(GridConstraints.FILL_VERTICAL);
    constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    add(new JBLabel(""), constraints);
    updateUiFromCurrentObject();
  }

  public void updateReferenceValues(@NotNull BuildFileKey containerProperty, @NotNull Iterable<String> values) {
    BuildFileKey itemType = containerProperty.getItemType();
    if (itemType == null) {
      return;
    }
    ComboBox comboBox = (ComboBox)myProperties.get(itemType);
    if (comboBox == null) {
      return;
    }
    myIsUpdating = true;
    try {
      String currentValue = comboBox.getEditor().getItem().toString();
      comboBox.removeAllItems();
      for (String value : values) {
        comboBox.addItem(value);
      }
      comboBox.setSelectedItem(currentValue);
    } finally {
      myIsUpdating = false;
    }
  }

  private ComboBox getComboBox(boolean editable) {
    ComboBox comboBox = new ComboBox();
    comboBox.addItemListener(this);
    comboBox.setEditor(new ComboBoxEditor() {
      private final JBTextField myTextField = new JBTextField();

      @Override
      public Component getEditorComponent() {
        return myTextField;
      }

      @Override
      public void setItem(Object o) {
        myTextField.setText(o != null ? o.toString() : "");
      }

      @Override
      public Object getItem() {
        return myTextField.getText();
      }

      @Override
      public void selectAll() {
        myTextField.selectAll();
      }

      @Override
      public void addActionListener(ActionListener actionListener) {
      }

      @Override
      public void removeActionListener(ActionListener actionListener) {
      }
    });
    comboBox.setEditable(true);
    JBTextField editorComponent = (JBTextField)comboBox.getEditor().getEditorComponent();
    editorComponent.setEditable(editable);
    editorComponent.getDocument().addDocumentListener(this);
    return comboBox;
  }


  /**
   * Reads the state of the UI form objects and writes them into the currently selected object in the list, setting the dirty bit as
   * appropriate.
   */
  private void updateCurrentObjectFromUi() {
    if (myIsUpdating || myCurrentBuildFileObject == null) {
      return;
    }
    for (Map.Entry<BuildFileKey, JComponent> entry : myProperties.entrySet()) {
      BuildFileKey key = entry.getKey();
      JComponent component = entry.getValue();
      Object currentValue = myCurrentBuildFileObject.get(key);
      Object newValue;
      BuildFileKeyType type = key.getType();
      switch(type) {
        case BOOLEAN: {
          ComboBox comboBox = (ComboBox)component;
          JBTextField editorComponent = (JBTextField)comboBox.getEditor().getEditorComponent();
          int index = comboBox.getSelectedIndex();
          if (index == 2) {
            newValue = Boolean.FALSE;
            editorComponent.setForeground(JBColor.BLACK);
          }
          else if (index == 1) {
            newValue = Boolean.TRUE;
            editorComponent.setForeground(JBColor.BLACK);
          }
          else {
            newValue = null;
            editorComponent.setForeground(JBColor.GRAY);
          }
          break;
        }
        case FILE:
        case FILE_AS_STRING: {
          newValue = ((TextFieldWithBrowseButton)component).getText();
          if ("".equals(newValue)) {
            newValue = null;
          }
          if (newValue != null) {
            newValue = new File(newValue.toString());
          }
          break;
        }
        case INTEGER: {
          try {
            if (hasKnownValues(key)) {
              String newStringValue = ((ComboBox)component).getEditor().getItem().toString();
              newStringValue = getMappedValue(myKeysWithKnownValues.get(key).inverse(), newStringValue);
              newValue = Integer.valueOf(newStringValue);
            }
            else {
              newValue = Integer.valueOf(((JBTextField)component).getText());
            }
          }
          catch (Exception e) {
            newValue = null;
          }
          break;
        }
        case REFERENCE: {
          newValue = ((ComboBox)component).getEditor().getItem();
          String newStringValue = (String)newValue;
          if (hasKnownValues(key)) {
            newStringValue = getMappedValue(myKeysWithKnownValues.get(key).inverse(), newStringValue);
          }
          if (newStringValue != null && newStringValue.isEmpty()) {
            newStringValue = null;
          }
          String prefix = getReferencePrefix(key);
          if (newStringValue != null && !newStringValue.startsWith(prefix)) {
            newStringValue = prefix + newStringValue;
          }
          newValue = newStringValue;
          break;
        }
        case CLOSURE:
        case STRING:
        default: {
          if (hasKnownValues(key)) {
            String newStringValue = ((ComboBox)component).getEditor().getItem().toString();
            newStringValue = getMappedValue(myKeysWithKnownValues.get(key).inverse(), newStringValue);
            if (newStringValue.isEmpty()) {
              newStringValue = null;
            }
            newValue = newStringValue;
          }
          else {
            newValue = ((JBTextField)component).getText();
            if ("".equals(newValue)) {
              newValue = null;
            }
          }

          if (type == BuildFileKeyType.CLOSURE && newValue != null) {
            List newListValue = new ArrayList();
            for (String s : Splitter.on(',').omitEmptyStrings().trimResults().split((String)newValue)) {
              newListValue.add(key.getValueFactory().parse(s, myProject));
            }
            newValue = newListValue;
          }
          break;
        }
      }
      if (!Objects.equal(currentValue, newValue)) {
        if (newValue == null) {
          myCurrentBuildFileObject.remove(key);
        } else {
          myCurrentBuildFileObject.put(key, newValue);
        }
        if (GradleBuildFile.shouldWriteValue(currentValue, newValue)) {
          myListener.modified(key);
        }
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
      Object value = myCurrentBuildFileObject != null ? myCurrentBuildFileObject.get(key) : null;
      final Object modelValue = myCurrentModelObject != null ? myCurrentModelObject.get(key) : null;
      switch(key.getType()) {
        case BOOLEAN: {
          ComboBox comboBox = (ComboBox)component;
          String text = formatDefaultValue(modelValue);
          comboBox.removeItemAt(0);
          comboBox.insertItemAt(text, 0);
          JBTextField editorComponent = (JBTextField)comboBox.getEditor().getEditorComponent();
          if (Boolean.FALSE.equals(value)) {
            comboBox.setSelectedIndex(2);
            editorComponent.setForeground(JBColor.BLACK);
          } else if (Boolean.TRUE.equals(value)) {
            comboBox.setSelectedIndex(1);
            editorComponent.setForeground(JBColor.BLACK);
          } else {
            comboBox.setSelectedIndex(0);
            editorComponent.setForeground(JBColor.GRAY);
          }
          break;
        }
        case FILE:
        case FILE_AS_STRING: {
          TextFieldWithBrowseButton fieldWithButton = (TextFieldWithBrowseButton)component;
          fieldWithButton.setText(value != null ? value.toString() : "");
          JBTextField textField = (JBTextField)fieldWithButton.getTextField();
          textField.getEmptyText().setText(formatDefaultValue(modelValue));
          break;
        }
        case REFERENCE: {
          String stringValue = (String)value;
          if (hasKnownValues(key) && stringValue != null) {
            stringValue = getMappedValue(myKeysWithKnownValues.get(key), stringValue);
          }
          String prefix = getReferencePrefix(key);
          if (stringValue == null) {
            stringValue = "";
          }
          else if (stringValue.startsWith(prefix)) {
            stringValue = stringValue.substring(prefix.length());
          }
          ComboBox comboBox = (ComboBox)component;
          JBTextField textField = (JBTextField)comboBox.getEditor().getEditorComponent();
          textField.getEmptyText().setText(formatDefaultValue(modelValue));
          comboBox.setSelectedItem(stringValue);
          break;
        }
        case CLOSURE:
          if (value instanceof List) {
            value = Joiner.on(", ").join((List)value);
          }
          // Fall through to INTEGER/STRING/default case
        case INTEGER:
        case STRING:
        default: {
          if (hasKnownValues(key)) {
            if (value != null) {
              value = getMappedValue(myKeysWithKnownValues.get(key), value.toString());
            }
            ComboBox comboBox = (ComboBox)component;
            comboBox.setSelectedItem(value != null ? value.toString() : "");
            JBTextField textField = (JBTextField)comboBox.getEditor().getEditorComponent();
            textField.getEmptyText().setText(formatDefaultValue(modelValue));
          }
          else {
            JBTextField textField = (JBTextField)component;
            textField.setText(value != null ? value.toString() : "");
            textField.getEmptyText().setText(formatDefaultValue(modelValue));
          }
          break;
        }
      }
      component.setEnabled(myCurrentBuildFileObject != null);
    }
    myIsUpdating = false;
  }

  @NotNull
  private static String formatDefaultValue(@Nullable Object modelValue) {
    if (modelValue == null) {
      return "";
    }
    String s = modelValue.toString();
    return !s.isEmpty() ? "(" + s + ")" : "";
  }

  @NotNull
  private static String getMappedValue(@NotNull BiMap<String, String> map, @NotNull String value) {
    if (map.containsKey(value)) {
      value = map.get(value);
    }
    return value;
  }

  private boolean hasKnownValues(BuildFileKey key) {
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
    BuildFileKey containerType = key.getContainerType();
    if (containerType != null) {
      String path = containerType.getPath();
      String lastLeaf = path.substring(path.lastIndexOf('/') + 1);
      return lastLeaf + ".";
    } else {
      return "";
    }
  }
}
