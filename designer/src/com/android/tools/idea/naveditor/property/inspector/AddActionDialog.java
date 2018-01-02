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
package com.android.tools.idea.naveditor.property.inspector;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.property.editors.AnimationEditorKt;
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_EXIT_ANIM;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.FRAGMENT;

public class AddActionDialog extends DialogWrapper {
  @VisibleForTesting
  JComboBox<NlComponent> myFromComboBox;
  @VisibleForTesting
  JComboBox<NlComponent> myDestinationComboBox;
  @VisibleForTesting
  JComboBox<ValueWithDisplayString> myEnterComboBox;
  @VisibleForTesting
  JComboBox<ValueWithDisplayString> myExitComboBox;
  @VisibleForTesting
  JComboBox<ValueWithDisplayString> myPopToComboBox;
  @VisibleForTesting
  JCheckBox myInclusiveCheckBox;
  @VisibleForTesting
  JCheckBox mySingleTopCheckBox;
  @VisibleForTesting
  JCheckBox myDocumentCheckBox;
  @VisibleForTesting
  JCheckBox myClearTaskCheckBox;
  @VisibleForTesting
  JPanel myContentPanel;

  public enum Defaults {NORMAL, RETURN_TO_SOURCE, GLOBAL}

  /**
   * Create a new action for the given component
   */
  protected AddActionDialog(@NotNull Defaults defaultsType,
                            @Nullable NlComponent existingAction,
                            @NotNull NlComponent parent,
                            @Nullable ResourceResolver resourceResolver) {
    super(false);
    NlModel model = parent.getModel();
    setUpComponents(model, resourceResolver);

    myFromComboBox.addItem(parent);

    if (existingAction != null) {
      setupFromExisting(existingAction);
    }
    else {
      setDefaults(defaultsType, parent);
    }

    init();

    if (existingAction == null) {
      myOKAction.putValue(Action.NAME, "Add");
      setTitle("Add Action");
    }
    else {
      myOKAction.putValue(Action.NAME, "Update");
      setTitle("Update Action");
    }
  }

  private void setDefaults(@NotNull Defaults type, @NotNull NlComponent parent) {
    myDestinationComboBox.addItem(null);
    NavComponentHelperKt.getVisibleDestinations(parent).forEach(c -> myDestinationComboBox.addItem(c));
    if (type == Defaults.GLOBAL) {
      myFromComboBox.addItem(parent.getParent());
      myFromComboBox.setSelectedIndex(myFromComboBox.getItemCount() - 1);
      myDestinationComboBox.setSelectedItem(parent);
    }
    else if (type == Defaults.RETURN_TO_SOURCE) {
      selectItem(myPopToComboBox, ATTR_ID, ANDROID_URI, parent);
      myInclusiveCheckBox.setSelected(true);
      myDestinationComboBox.setSelectedItem(null);
    }
  }

  private void setupFromExisting(@NotNull NlComponent action) {
    myFromComboBox.addItem(action.getParent());
    if (!action.getParent().isRoot()) {
      myFromComboBox.addItem(action.getParent());
    }

    String destination = NavComponentHelperKt.getActionDestinationId(action);
    if (destination != null) {
      //noinspection ConstantConditions
      myDestinationComboBox.addItem(NavComponentHelperKt.findVisibleDestination(action.getParent(), destination));
      myDestinationComboBox.setSelectedIndex(0);
    }
    myDestinationComboBox.setEnabled(false);

    selectItem(myPopToComboBox, NavigationSchema.ATTR_POP_UP_TO, AUTO_URI, action);
    myInclusiveCheckBox.setSelected(NavComponentHelperKt.getInclusive((action)));
    selectItem(myEnterComboBox, ATTR_ENTER_ANIM, AUTO_URI, action);
    selectItem(myExitComboBox, ATTR_EXIT_ANIM, AUTO_URI, action);
    mySingleTopCheckBox.setSelected(NavComponentHelperKt.getSingleTop(action));
    myDocumentCheckBox.setSelected(NavComponentHelperKt.getDocument(action));
    myClearTaskCheckBox.setSelected(NavComponentHelperKt.getClearTask(action));
  }

  private static void selectItem(@NotNull JComboBox<ValueWithDisplayString> comboBox,
                                 @NotNull String attrName,
                                 @Nullable String namespace,
                                 @NotNull NlComponent component) {
    String value = component.getAttribute(namespace, attrName);
    if (value != null) {
      if (value.startsWith("@+")) {
        value = "@" + value.substring(2);
      }
      for (int i = 0; i < comboBox.getItemCount(); i++) {
        if (value.equals(comboBox.getItemAt(i).getValue())) {
          comboBox.setSelectedIndex(i);
          return;
        }
      }
    }
  }

  private void setUpComponents(@NotNull NlModel model,
                               @Nullable ResourceResolver resourceResolver) {
    ListCellRendererWrapper<NlComponent> componentRenderer = new ListCellRendererWrapper<NlComponent>() {
      @Override
      public void customize(JList list, NlComponent value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("None");
        }
        else {
          setText(NavComponentHelperKt.getUiName(value, resourceResolver));
        }
      }
    };

    myFromComboBox.setRenderer(componentRenderer);
    myFromComboBox.setEnabled(false);

    myDestinationComboBox.setRenderer(componentRenderer);

    ResourceManager resourceManager = LocalResourceManager.getInstance(model.getModule());
    myDestinationComboBox.addItemListener(event -> {
      myEnterComboBox.removeAllItems();
      myExitComboBox.removeAllItems();
      myEnterComboBox.addItem(new ValueWithDisplayString("None", null));
      myExitComboBox.addItem(new ValueWithDisplayString("None", null));
      AnimationEditorKt.getAnimatorsPopupContent(resourceManager,
                                                 NavComponentHelperKt.getDestinationType((NlComponent)event.getItem()) == FRAGMENT)
        .forEach(item -> {
          myEnterComboBox.addItem(item);
          myExitComboBox.addItem(item);
        });
    });
    myEnterComboBox.addItem(new ValueWithDisplayString("None", null));
    myExitComboBox.addItem(new ValueWithDisplayString("None", null));

    myPopToComboBox.addItem(new ValueWithDisplayString("None", null));
    model.flattenComponents().filter(c -> NavComponentHelperKt.isDestination(c))
      .forEach(c -> myPopToComboBox.addItem(new ValueWithDisplayString(NavComponentHelperKt.getUiName(c, resourceResolver),
                                                                       "@id/" + c.getId())));
  }

  @NotNull
  public NlComponent getSource() {
    //noinspection ConstantConditions
    return (NlComponent)myFromComboBox.getSelectedItem();
  }

  @Nullable
  public NlComponent getDestination() {
    //noinspection ConstantConditions
    return (NlComponent)myDestinationComboBox.getSelectedItem();
  }

  @Nullable
  public String getEnterTransition() {
    //noinspection ConstantConditions
    return ((ValueWithDisplayString)myEnterComboBox.getSelectedItem()).getValue();
  }

  @Nullable
  public String getExitTransition() {
    //noinspection ConstantConditions
    return ((ValueWithDisplayString)myExitComboBox.getSelectedItem()).getValue();
  }

  @Nullable
  public String getPopTo() {
    return ((ValueWithDisplayString)myPopToComboBox.getSelectedItem()).getValue();
  }

  public boolean isInclusive() {
    return myInclusiveCheckBox.isSelected();
  }

  public boolean isSingleTop() {
    return mySingleTopCheckBox.isSelected();
  }

  public boolean isDocument() {
    return myDocumentCheckBox.isSelected();
  }

  public boolean isClearTask() {
    return myClearTaskCheckBox.isSelected();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myDestinationComboBox.getSelectedItem() == null &&
        ((ValueWithDisplayString)myPopToComboBox.getSelectedItem()).getValue() == null) {
      return new ValidationInfo("Destination must be set!", myDestinationComboBox);
    }
    return super.doValidate();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
