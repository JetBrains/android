/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;
import static com.android.SdkConstants.ATTR_TEXT_APPEARANCE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class InspectorPanelTest extends PropertyTestCase {
  private PropertiesComponent myOldPropertiesComponent;
  private PropertiesComponent myPropertiesComponent;
  private Disposable myDisposable;
  private InspectorPanel myInspector;
  private Multimap<Integer, Component> myComponents;
  private Map<String, Integer> myLabelToRowNumber;
  private Map<String, Integer> myLabelToGroupSize;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPropertiesComponent = mock(PropertiesComponent.class);
    myOldPropertiesComponent = registerApplicationComponent(PropertiesComponent.class, myPropertiesComponent);
    myDisposable = Disposer.newDisposable();
    myInspector = new InspectorPanel(myPropertiesManager, myDisposable, new JLabel());
    List<NlComponent> components = ImmutableList.of(myTextView);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    myInspector.setComponent(components, properties, myPropertiesManager);
    myComponents = findComponents(myInspector);
    myLabelToRowNumber = identifyLabelledRows(myComponents);
    myLabelToGroupSize = Collections.singletonMap(ATTR_TEXT_APPEARANCE, 7);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      registerApplicationComponent(PropertiesComponent.class, myOldPropertiesComponent);
      Disposer.dispose(myDisposable);
    }
    finally {
      super.tearDown();
    }
  }

  public void testTextAppearanceGroupInitiallyClosed() {
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkVisibleRowsWithoutFilter();
  }

  public void testFilter() {
    myInspector.setFilter("textA");
    UIUtil.dispatchAllInvocationEvents();

    checkVisibleRowsWithFilter(ATTR_TEXT_APPEARANCE, ATTR_TEXT_ALIGNMENT);
  }

  public void testComponentsRestoredAfterFiltering() {
    myInspector.setFilter("textA");
    UIUtil.dispatchAllInvocationEvents();
    myInspector.setFilter("");
    UIUtil.dispatchAllInvocationEvents();

    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkVisibleRowsWithoutFilter();
  }

  public void testExpandGroup() {
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();

    JLabel label = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(ATTR_TEXT_APPEARANCE)));
    assert label != null : "Cannot find label for group property: " + ATTR_TEXT_APPEARANCE;
    fireMouseClick(label);

    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isTrue();
    checkVisibleRowsWithoutFilter();
    verify(myPropertiesComponent).setValue("inspector.open.textAppearance", true);
  }

  public void testClickOnLabelWithFilterDoesNotExpand() {
    myInspector.setFilter("textA");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isNull();

    JLabel label = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(ATTR_TEXT_APPEARANCE)));
    assert label != null : "Cannot find label for group property: " + ATTR_TEXT_APPEARANCE;
    fireMouseClick(label);

    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isNull();
  }

  private static void fireMouseClick(@NotNull JLabel label) {
    MouseEvent event = mock(MouseEvent.class);
    for (MouseListener listener : label.getMouseListeners()) {
      listener.mouseClicked(event);
    }
  }

  @Nullable
  private Boolean isGroupOpen(@NotNull String propertyName) {
    JLabel label = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(propertyName)));
    assert label != null : "Cannot find label for group property: " + propertyName;
    if (Objects.equals(label.getIcon(), UIManager.get("Tree.expandedIcon"))) {
      return Boolean.TRUE;
    }
    if (Objects.equals(label.getIcon(), UIManager.get("Tree.collapsedIcon"))) {
      return Boolean.FALSE;
    }
    return null;
  }

  private void checkVisibleRowsWithoutFilter() {
    Set<Component> visible = new HashSet<>();
    Set<Component> invisible = new HashSet<>();
    for (String propertyName : myLabelToGroupSize.keySet()) {
      int row = myLabelToRowNumber.get(propertyName);
      int length = myLabelToGroupSize.get(propertyName);
      if (isGroupOpen(propertyName) == Boolean.FALSE) {
        for (int index = 1; index <= length; index++) {
          invisible.addAll(myComponents.get(row + index));
        }
      }
    }
    visible.addAll(myComponents.values());
    visible.removeAll(invisible);
    checkVisibleComponents(visible, invisible);
  }

  private void checkVisibleRowsWithFilter(@NotNull String... propertyNames) {
    Set<Component> visible = new HashSet<>();
    Set<Component> invisible = new HashSet<>();
    for (String propertyName : propertyNames) {
      visible.addAll(myComponents.get(myLabelToRowNumber.get(propertyName)));
    }
    // Last 2 rows should always be visible:
    int rows = myComponents.keySet().size();
    visible.addAll(myComponents.get(rows - 1));
    visible.addAll(myComponents.get(rows - 2));
    invisible.addAll(myComponents.values());
    invisible.removeAll(visible);
    checkVisibleComponents(visible, invisible);
  }

  private static void checkVisibleComponents(@NotNull Set<Component> visible, @NotNull Set<Component> invisible) {
    for (Component component : visible) {
      assertThat(component.isVisible()).isTrue();
    }
    for (Component component : invisible) {
      assertThat(component.isVisible()).isFalse();
    }
  }

  @NotNull
  private static Multimap<Integer, Component> findComponents(@NotNull InspectorPanel inspector) {
    Multimap<Integer, Component> components = ArrayListMultimap.create();
    JPanel panel = (JPanel)inspector.getComponent(0);
    GridLayoutManager layout = (GridLayoutManager)panel.getLayout();
    for (Component component : panel.getComponents()) {
      GridConstraints constraints = layout.getConstraintsForComponent(component);
      int row = constraints.getRow();
      components.put(row, component);
    }
    return components;
  }

  @NotNull
  private static Map<String, Integer> identifyLabelledRows(@NotNull Multimap<Integer, Component> components) {
    Map<String, Integer> labelToRowNumber = new HashMap<>();
    for (int row : components.keySet()) {
      JLabel label = findFirstLabelWithText(components.get(row));
      if (label != null) {
        labelToRowNumber.put(label.getText(), row);
      }
    }
    return labelToRowNumber;
  }

  @Nullable
  private static JLabel findFirstLabelWithText(@NotNull Collection<Component> components) {
    for (Component component : components) {
      if (component instanceof JLabel) {
        JLabel label = (JLabel)component;
        if (!StringUtil.isEmpty(label.getText())) {
          return label;
        }
      }
    }
    return null;
  }
}
