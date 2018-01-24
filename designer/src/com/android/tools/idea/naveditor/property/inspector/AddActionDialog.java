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
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ENTER_ANIM;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_EXIT_ANIM;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.FRAGMENT;

public class AddActionDialog extends DialogWrapper {
  @VisibleForTesting
  JComboBox<NlComponent> myFromComboBox;
  @VisibleForTesting
  JComboBox<DestinationListEntry> myDestinationComboBox;
  @VisibleForTesting
  JComboBox<ValueWithDisplayString> myEnterComboBox;
  @VisibleForTesting
  JComboBox<ValueWithDisplayString> myExitComboBox;
  @VisibleForTesting
  JComboBox<NlComponent> myPopToComboBox;
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

  private NlComponent myPreviousPopTo;
  private boolean myPreviousInclusive;

  private final NlComponent myParent;

  public enum Defaults {NORMAL, RETURN_TO_SOURCE, GLOBAL}

  /**
   * Create a new action for the given component
   */
  protected AddActionDialog(@NotNull Defaults defaultsType,
                            @Nullable NlComponent existingAction,
                            @NotNull NlComponent parent,
                            @Nullable ResourceResolver resourceResolver) {
    super(false);
    myParent = parent;
    NlModel model = parent.getModel();
    setUpComponents(model, resourceResolver);

    myFromComboBox.addItem(parent);

    if (existingAction != null) {
      setupFromExisting(existingAction);
    }
    else {
      setDefaults(defaultsType);
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

  private void setDefaults(@NotNull Defaults type) {
    myDestinationComboBox.addItem(null);
    populateDestinations();
    if (type == Defaults.GLOBAL) {
      myFromComboBox.addItem(myParent.getParent());
      myFromComboBox.setSelectedIndex(myFromComboBox.getItemCount() - 1);
      selectItem(myDestinationComboBox, entry -> entry.getComponent(), myParent);
    }
    else if (type == Defaults.RETURN_TO_SOURCE) {
      myPopToComboBox.setSelectedItem(myParent);
      myInclusiveCheckBox.setSelected(true);
      selectItem(myDestinationComboBox, entry -> entry.isReturnToSource(), true);
    }
  }

  private void populateDestinations() {
    Map<NlComponent, List<NlComponent>> byParent =
      NavComponentHelperKt.getVisibleDestinations(myParent).stream()
        .filter(component -> component.getParent() != null)
        .collect(Collectors.groupingBy(c -> c.getParent(),
                                       LinkedHashMap::new,
                                       Collectors.mapping(Function.identity(), Collectors.toList())));
    // Add parent and siblings
    if (!myParent.isRoot()) {
      myDestinationComboBox.addItem(new DestinationListEntry(myParent.getParent()));
      byParent.get(myParent.getParent()).forEach(c -> myDestinationComboBox
        .addItem(new DestinationListEntry(c)));
    }
    else {
      // If this is the root, we need to explicitly add it.
      myDestinationComboBox.addItem(new DestinationListEntry(myParent));
    }
    // Add return to source
    myDestinationComboBox.addItem(DestinationListEntry.RETURN_TO_SOURCE);
    // Add children if we're a nav
    if (NavComponentHelperKt.isNavigation(myParent) && byParent.containsKey(myParent)) {
      myDestinationComboBox.addItem(DestinationListEntry.SEPARATOR);
      byParent.get(myParent).forEach(c -> myDestinationComboBox.addItem(new DestinationListEntry(c)));
    }
    // Add siblings of ancestors
    if (byParent.keySet().stream().anyMatch(c -> (c != myParent && c != myParent.getParent()))) {
      myDestinationComboBox.addItem(DestinationListEntry.SEPARATOR);
      for (NlComponent nav : byParent.keySet()) {
        if (nav == myParent || nav == myParent.getParent()) {
          continue;
        }
        myDestinationComboBox.addItem(new DestinationListEntry.Parent(nav));
        for (NlComponent child : byParent.get(nav)) {
          if (!byParent.containsKey(child)) {
            myDestinationComboBox.addItem(new DestinationListEntry(child));
          }
        }
      }
    }
  }

  private void populatePopTo() {
    myPopToComboBox.addItem(null);
    List<NlComponent> navs = myParent.getModel().flattenComponents()
      .filter(c -> NavComponentHelperKt.isNavigation(c))
      .collect(Collectors.toList());
    for (NlComponent nav : navs) {
      myPopToComboBox.addItem(nav);
      for (NlComponent component : nav.getChildren()) {
        if (NavComponentHelperKt.isDestination(component) && !NavComponentHelperKt.isNavigation(component)) {
          myPopToComboBox.addItem(component);
        }
      }
    }
  }

  private void setupFromExisting(@NotNull NlComponent action) {
    myFromComboBox.addItem(action.getParent());
    //noinspection ConstantConditions
    if (!action.getParent().isRoot()) {
      myFromComboBox.addItem(action.getParent());
    }

    String destination = NavComponentHelperKt.getActionDestinationId(action);
    if (destination != null) {
      //noinspection ConstantConditions
      myDestinationComboBox.addItem(
        new DestinationListEntry(NavComponentHelperKt.findVisibleDestination(action.getParent(), destination)));
      myDestinationComboBox.setSelectedIndex(0);
    }
    myDestinationComboBox.setEnabled(false);

    selectItem(myPopToComboBox, c -> c.getAttribute(ANDROID_URI, ATTR_ID), NavigationSchema.ATTR_POP_UP_TO, AUTO_URI, action);
    myInclusiveCheckBox.setSelected(NavComponentHelperKt.getInclusive((action)));
    selectItem(myEnterComboBox, ValueWithDisplayString::getValue, ATTR_ENTER_ANIM, AUTO_URI, action);
    selectItem(myExitComboBox, ValueWithDisplayString::getValue, ATTR_EXIT_ANIM, AUTO_URI, action);
    mySingleTopCheckBox.setSelected(NavComponentHelperKt.getSingleTop(action));
    myDocumentCheckBox.setSelected(NavComponentHelperKt.getDocument(action));
    myClearTaskCheckBox.setSelected(NavComponentHelperKt.getClearTask(action));
  }

  private static <T, U> void selectItem(@NotNull JComboBox<T> comboBox,
                                 @NotNull Function<T, U> valueGetter,
                                 @Nullable U targetValue) {
    for (int i = 0; i < comboBox.getItemCount(); i++) {
      T item = comboBox.getItemAt(i);
      U value = item == null ? null : valueGetter.apply(item);
      if (Objects.equals(targetValue, value)) {
        comboBox.setSelectedIndex(i);
        return;
      }
    }
  }

  private static <T> void selectItem(@NotNull JComboBox<T> comboBox,
                                             @NotNull Function<T, String> valueGetter,
                                             @NotNull String attrName,
                                             @Nullable String namespace,
                                             @NotNull NlComponent component) {
    String targetValue = component.getAttribute(namespace, attrName);
    targetValue = stripPlus(targetValue);
    selectItem(comboBox, c -> stripPlus(valueGetter.apply(c)), targetValue);
  }

  private static String stripPlus(String targetValue) {
    if (targetValue != null) {
      if (targetValue.startsWith("@+")) {
        targetValue = "@" + targetValue.substring(2);
      }
    }
    return targetValue;
  }


  private void setUpComponents(@NotNull NlModel model,
                               @Nullable ResourceResolver resourceResolver) {
    ListCellRendererWrapper<NlComponent> sourceRenderer = new ListCellRendererWrapper<NlComponent>() {
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

    myFromComboBox.setRenderer(sourceRenderer);
    myFromComboBox.setEnabled(false);

    ListCellRendererWrapper<DestinationListEntry> destinationRenderer = new ListCellRendererWrapper<DestinationListEntry>() {
      @Override
      public void customize(JList list, DestinationListEntry value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("None");
        }
        else if (value.isReturnToSource()) {
          setText("↵ Source");
        }
        else if (value.isSeparator()) {
          setSeparator();
        }
        else {
          NlComponent component = value.getComponent();
          String text = NavComponentHelperKt.getUiName(component, resourceResolver);
          NlComponent valueParent = component.getParent();
          if (valueParent != myParent.getParent() && component != myParent.getParent() && valueParent != myParent) {
            if (value.isParent()) {
              setFont(list.getFont().deriveFont(Font.BOLD));
            }
            else if (index != -1) {
              text = "  " + text;
            }
          }
          if (component == myParent) {
            text = "↻ " + text;
          }
          if (component.getParent() == null) {
            text += " (Root)";
          }
          setText(text);
        }
      }
    };

    myDestinationComboBox.setRenderer(destinationRenderer);

    ResourceManager resourceManager = LocalResourceManager.getInstance(model.getModule());
    myDestinationComboBox.addItemListener(event -> {
      myEnterComboBox.removeAllItems();
      myExitComboBox.removeAllItems();
      myEnterComboBox.addItem(new ValueWithDisplayString("None", null));
      myExitComboBox.addItem(new ValueWithDisplayString("None", null));
      NlComponent component = ((DestinationListEntry)event.getItem()).getComponent();
      boolean isFragment = false;
      if (component != null) {
        isFragment = NavComponentHelperKt.getDestinationType(component) == FRAGMENT;
      }
      if (resourceManager != null) {
        AnimationEditorKt.getAnimatorsPopupContent(resourceManager, isFragment)
          .forEach(item -> {
            myEnterComboBox.addItem(item);
            myExitComboBox.addItem(item);
          });
      }
    });

    myDestinationComboBox.addItemListener(event -> {
      DestinationListEntry item = (DestinationListEntry)event.getItem();
      if (event.getStateChange() != ItemEvent.SELECTED && item != null) {
        return;
      }
      if (item != null && item.isReturnToSource()) {
        myPreviousPopTo = (NlComponent)myPopToComboBox.getSelectedItem();
        myPreviousInclusive = myInclusiveCheckBox.isSelected();
        myPopToComboBox.setSelectedItem(myParent);
        myPopToComboBox.setEnabled(false);
        myInclusiveCheckBox.setSelected(true);
        myInclusiveCheckBox.setEnabled(false);
      }
      else {
        if (!myPopToComboBox.isEnabled()) {
          myPopToComboBox.setSelectedItem(myPreviousPopTo);
          myInclusiveCheckBox.setSelected(myPreviousInclusive);
          myPopToComboBox.setEnabled(true);
          myInclusiveCheckBox.setEnabled(true);
        }
      }
    });

    myEnterComboBox.addItem(new ValueWithDisplayString("None", null));
    myExitComboBox.addItem(new ValueWithDisplayString("None", null));

    populatePopTo();
    myPopToComboBox.setRenderer(new ListCellRendererWrapper<NlComponent>() {
      @Override
      public void customize(JList list,
                            NlComponent value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        if (value == null) {
          setText("None");
        }
        else {
          String text = NavComponentHelperKt.getUiName(value, resourceResolver);
          if (NavComponentHelperKt.isNavigation(value)) {
            setFont(list.getFont().deriveFont(Font.BOLD));
          }
          else if (index != -1) {
            text = "  " + text;
          }
          if (value.getParent() == null) {
            text += " (Root)";
          }
          setText(text);
        }
      }
    });
  }

  @NotNull
  public NlComponent getSource() {
    //noinspection ConstantConditions
    return (NlComponent)myFromComboBox.getSelectedItem();
  }

  @Nullable
  public NlComponent getDestination() {
    DestinationListEntry item = (DestinationListEntry)myDestinationComboBox.getSelectedItem();
    return item == null ? null : item.getComponent();
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
    NlComponent component = (NlComponent)myPopToComboBox.getSelectedItem();
    return component == null ? null : stripPlus(component.getAttribute(ANDROID_URI, ATTR_ID));
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
    //noinspection ConstantConditions
    if (myDestinationComboBox.getSelectedItem() == null && myPopToComboBox.getSelectedItem() == null) {
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

  @VisibleForTesting
  static class DestinationListEntry {
    protected NlComponent myComponent;

    private static DestinationListEntry SEPARATOR = new DestinationListEntry(null) {
      @Override
      public boolean isSeparator() {
        return true;
      }
    };

    private static DestinationListEntry RETURN_TO_SOURCE = new DestinationListEntry(null) {
      @Override
      public boolean isReturnToSource() {
        return true;
      }
    };

    static class Parent extends DestinationListEntry {
      public Parent(@Nullable NlComponent component) {
        super(component);
      }

      @Override
      public boolean isParent() {
        return true;
      }
    }

    public DestinationListEntry(@Nullable NlComponent component) {
      myComponent = component;
    }

    public boolean isSeparator() {
      return false;
    }

    public boolean isParent() {
      return false;
    }

    public boolean isReturnToSource() {
      return false;
    }

    public NlComponent getComponent() {
      return myComponent;
    }
  }
}
