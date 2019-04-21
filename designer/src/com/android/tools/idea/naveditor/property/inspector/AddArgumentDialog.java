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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.Lists;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.naveditor.model.NavComponentHelperKt.*;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE;
import static org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT;

public class AddArgumentDialog extends DialogWrapper {
  private JPanel myContentPanel;
  @VisibleForTesting JCheckBox myNullableCheckBox;
  private JTextField myNameTextField;
  private JPanel myDefaultValuePanel;
  @VisibleForTesting JComboBox<String> myDefaultValueComboBox;
  @VisibleForTesting JTextField myDefaultValueTextField;
  private JBLabel myNullableLabel;
  private JComboBox<Type> myTypeComboBox;
  @Nullable private final NlComponent myExistingComponent;
  @NotNull private final NlComponent myParent;

  private enum Type {
    INFERRED("<inferred type>", null),
    INTEGER("Integer", "integer"),
    LONG("Long", "long"),
    BOOLEAN("Boolean", "boolean"),
    STRING("String", "string"),
    REFERENCE("Resource Reference", "reference"),
    CUSTOM("Custom Parcelable...", "custom");

    String display;
    String attrValue;

    Type(String display, String attrValue) {
      this.display = display;
      this.attrValue = attrValue;
    }

    @Override
    public String toString() {
      return display;
    }
  }

  private String myCustomType;
  private Type mySelectedType;
  private MutableCollectionComboBoxModel<String> myDefaultValueComboModel = new MutableCollectionComboBoxModel<>();

  public AddArgumentDialog(@Nullable NlComponent existing, @NotNull NlComponent parent) {
    super(false);
    init();

    myParent = parent;
    myExistingComponent = existing;
    for (Type t : Type.values()) {
      myTypeComboBox.addItem(t);
    }
    myTypeComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (index == -1 && value == Type.CUSTOM) {
        label.setText(myCustomType);
      }
      else {
        label.setText(value.display);
      }
    }));

    myTypeComboBox.setEditable(false);

    myDefaultValueComboBox.setModel(myDefaultValueComboModel);
    if (existing != null) {

      setName(getArgumentName(existing));
      setType(getTypeAttr(existing));
      setNullable(getNullable(existing));
      setDefaultValue(NavComponentHelperKt.getDefaultValue(existing));
      myOKAction.putValue(Action.NAME, "Update");
      setTitle("Update Argument Link");
    }
    else {
      ((CardLayout)myDefaultValuePanel.getLayout()).show(myDefaultValuePanel, "textDefaultValue");
      myOKAction.putValue(Action.NAME, "Add");
      setTitle("Add Argument Link");
    }

    myTypeComboBox.addActionListener(event -> {
      if ("comboBoxChanged".equals(event.getActionCommand())) {
        newTypeSelected();
      }
    });

    myDefaultValueComboBox.setRenderer(SimpleListCellRenderer.create("No default value", Functions.id()));
  }

  @VisibleForTesting
  void setType(@Nullable String typeStr) {
    if (typeStr == null) {
      myTypeComboBox.setSelectedItem(Type.INFERRED);
    }
    else {
      boolean found = false;
      for (Type type : Type.values()) {
        if (typeStr.equals(type.attrValue)) {
          myTypeComboBox.setSelectedItem(type);
          found = true;
          break;
        }
      }
      if (!found) {
        myCustomType = typeStr;
        myTypeComboBox.setSelectedItem(Type.CUSTOM);
      }
    }
    updateUi();
  }

  private void newTypeSelected() {
    if (myTypeComboBox.getSelectedItem() == Type.CUSTOM) {
      Project project = myParent.getModel().getProject();
      PsiClass parcelable = ClassUtil.findPsiClass(PsiManager.getInstance(project), CLASS_PARCELABLE);
      PsiClass current = null;
      if (myCustomType != null) {
        current = ClassUtil.findPsiClass(PsiManager.getInstance(project), myCustomType);
      }
      TreeClassChooser chooser =
        TreeClassChooserFactory.getInstance(project)
                               .createInheritanceClassChooser(
                                 "Select Parcelable Class",
                                 GlobalSearchScope.allScope(project),
                                 parcelable,
                                 current);
      chooser.showDialog();
      PsiClass selection = chooser.getSelected();
      if (selection != null) {
        myCustomType = selection.getQualifiedName();
      }
      else {
        myTypeComboBox.setSelectedItem(mySelectedType);
      }
    }
    updateUi();
  }

  private void updateUi() {
    Type newType = (Type)myTypeComboBox.getSelectedItem();
    if (newType != mySelectedType) {
      boolean nullable = newType == Type.STRING || newType == Type.CUSTOM;
      myNullableCheckBox.setEnabled(nullable);
      myNullableLabel.setEnabled(nullable);
      if (!nullable) {
        myNullableCheckBox.setSelected(false);
      }
      if (newType == Type.BOOLEAN) {
        ((CardLayout)myDefaultValuePanel.getLayout()).show(myDefaultValuePanel, "comboDefaultValue");
        myDefaultValueComboModel.update(Lists.newArrayList(null, "true", "false"));
      }
      else if (newType == Type.CUSTOM) {
        ((CardLayout)myDefaultValuePanel.getLayout()).show(myDefaultValuePanel, "comboDefaultValue");
        myDefaultValueComboModel.update(Lists.newArrayList(null, "@null"));
      }
      else {
        myDefaultValueTextField.setText("");
        ((CardLayout)myDefaultValuePanel.getLayout()).show(myDefaultValuePanel, "textDefaultValue");
      }
      mySelectedType = newType;
    }
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @VisibleForTesting
  @Nullable
  String getType() {
    Type selectedType = (Type)myTypeComboBox.getSelectedItem();
    return selectedType == Type.CUSTOM ? myCustomType : selectedType.attrValue;
  }

  @VisibleForTesting
  @Nullable
  String getDefaultValue() {
    if (mySelectedType == Type.BOOLEAN || mySelectedType == Type.CUSTOM) {
      return (String)myDefaultValueComboBox.getSelectedItem();
    }
    else {
      return myDefaultValueTextField.getText();
    }
  }

  @VisibleForTesting
  void setDefaultValue(String defaultValue) {
    if (mySelectedType == Type.BOOLEAN || mySelectedType == Type.CUSTOM) {
      myDefaultValueComboBox.setSelectedItem(defaultValue);
    }
    else {
      myDefaultValueTextField.setText(defaultValue);
    }
  }

  @VisibleForTesting
  boolean isNullable() {
    return myNullableCheckBox.isSelected();
  }

  @VisibleForTesting
  void setNullable(boolean nullable) {
    myNullableCheckBox.setSelected(nullable);
  }

  @VisibleForTesting
  String getName() {
    return myNameTextField.getText();
  }

  @VisibleForTesting
  void setName(@Nullable String name) {
    myNameTextField.setText(name);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String name = getName();
    if (name == null || name.isEmpty()) {
      return new ValidationInfo("Name must be set", myNameTextField);
    }
    if (myParent.getChildren().stream()
                .anyMatch(c -> c != myExistingComponent
                               && isArgument(c)
                               && getArgumentName(c).equals(name))) {
      return new ValidationInfo("Name must be unique", myNameTextField);
    }
    String defaultValue = getDefaultValue();
    if (defaultValue != null && !defaultValue.isEmpty()) {
      Object type = myTypeComboBox.getSelectedItem();
      if (type == Type.LONG) {
        if (!defaultValue.endsWith("L")) {
          defaultValue += "L";
        }
        try {
          Long.parseLong(defaultValue.substring(0, defaultValue.length() - 1));
        }
        catch (NumberFormatException e) {
          return new ValidationInfo("Long default values must be in the format '1234L'");
        }
      }
      if (type == Type.INTEGER) {
        try {
          Integer.parseInt(defaultValue);
        }
        catch (NumberFormatException e) {
          return new ValidationInfo("Default value must be an integer");
        }
      }
      if (type == Type.REFERENCE) {
        ResourceUrl url = ResourceUrl.parse(defaultValue);
        if (url == null) {
          return new ValidationInfo("Reference not correctly formatted");
        }
        ResourceResolver resourceResolver = myParent.getModel().getConfiguration().getResourceResolver();
        if (resourceResolver != null) {
          ResourceValue resourceValue = ApplicationManager.getApplication().runReadAction((Computable<ResourceValue>)(
            () -> ResourceHelper.resolve(resourceResolver, url, myParent.getTag())));
          if (resourceValue == null) {
            return new ValidationInfo("Resource does not exist");
          }
        }
      }
    }
    return null;
  }

  public void save() {
    WriteCommandAction.runWriteCommandAction(myParent.getModel().getProject(), () -> {
      NlComponent realComponent = myExistingComponent;
      if (realComponent == null) {
        XmlTag tag = myParent.getTag().createChildTag(TAG_ARGUMENT, null, null, false);
        realComponent = myParent.getModel().createComponent(null, tag, myParent, null, InsertType.CREATE);
      }
      setArgumentName(realComponent, getName());
      setTypeAttr(realComponent, getType());
      if (isNullable()) {
        NavComponentHelperKt.setNullable(realComponent, true);
      }
      else {
        realComponent.removeAttribute(AUTO_URI, ATTR_NULLABLE);
      }
      String defaultValue = getDefaultValue();
      if (defaultValue != null && !defaultValue.isEmpty()) {
        if (myTypeComboBox.getSelectedItem() == Type.LONG && !defaultValue.endsWith("L")) {
          defaultValue += "L";
        }
        NavComponentHelperKt.setDefaultValue(realComponent, defaultValue);
      }
      else {
        realComponent.removeAndroidAttribute(ATTR_DEFAULT_VALUE);
      }
    });
  }
}
