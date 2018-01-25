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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class InspectorPanelTest extends PropertyTestCase {
  private PropertiesComponent myPropertiesComponent;
  private InspectorPanel myInspector;
  private Multimap<Integer, Component> myComponents;
  private Map<String, Integer> myLabelToRowNumber;
  private Map<Component, String> myComponentToLabel;

  private static final String DESIGN_POSTFIX = "_design";
  private static final String[] TEXT_APPEARANCE_PROPERTIES =
    {ATTR_FONT_FAMILY, ATTR_TYPEFACE, ATTR_TEXT_SIZE, ATTR_LINE_SPACING_EXTRA, ATTR_TEXT_COLOR, ATTR_TEXT_ALIGNMENT, ATTR_TEXT_STYLE};
  private static final String[] TEXT_VIEW_PROPERTIES =
    {ATTR_TEXT, ATTR_TEXT + DESIGN_POSTFIX, ATTR_CONTENT_DESCRIPTION, ATTR_TEXT_APPEARANCE,
      ATTR_FONT_FAMILY, ATTR_TYPEFACE, ATTR_TEXT_SIZE, ATTR_LINE_SPACING_EXTRA, ATTR_TEXT_COLOR, ATTR_TEXT_ALIGNMENT, ATTR_TEXT_STYLE};

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPropertiesComponent = mock(PropertiesComponent.class);
    registerApplicationComponent(PropertiesComponent.class, myPropertiesComponent);
    myInspector = new NlInspectorPanel(getTestRootDisposable(), new JLabel());
  }

  private void init(@NotNull NlComponent... componentArray) {
    List<NlComponent> components = Arrays.asList(componentArray);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    myInspector.setComponent(components, properties, myPropertiesManager);
    myComponents = findComponents(myInspector);
    myLabelToRowNumber = identifyLabelledRows(myComponents);
    myComponentToLabel = getComponentToLabelMap(myComponents, myLabelToRowNumber);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myPropertiesComponent = null;
      myInspector = null;
      myComponents = null;
      myLabelToRowNumber = null;
      myComponentToLabel = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testTextAppearanceGroupInitiallyClosed() {
    init(myTextView);
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkInvisibleRowsWithoutFilter(TEXT_APPEARANCE_PROPERTIES);
  }

  public void testFilter() {
    init(myTextView);
    myInspector.setFilter("textA");
    UIUtil.dispatchAllInvocationEvents();

    checkVisibleRowsWithFilter(ATTR_TEXT_APPEARANCE, ATTR_TEXT_ALIGNMENT);
  }

  public void testEnterInFilterWithNoFilterSet() {
    init(myTextView);
    KeyEvent event = new KeyEvent(myInspector, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myInspector.enterInFilter(event);

    assertThat(event.isConsumed()).isFalse();
  }

  public void testEnterInFilterWithMultipleMatchingProperties() {
    init(myTextView);
    myInspector.setFilter("text");
    UIUtil.dispatchAllInvocationEvents();
    KeyEvent event = new KeyEvent(myInspector, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myInspector.enterInFilter(event);

    assertThat(event.isConsumed()).isFalse();
  }

  public void testEnterInFilter() {
    init(myTextView);
    myInspector.setFilter("textAli");
    UIUtil.dispatchAllInvocationEvents();
    KeyEvent event = new KeyEvent(myInspector, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myInspector.enterInFilter(event);

    assertThat(event.isConsumed()).isTrue();
  }

  public void testComponentsRestoredAfterFiltering() {
    init(myTextView);
    myInspector.setFilter("textA");
    UIUtil.dispatchAllInvocationEvents();
    myInspector.setFilter("");
    UIUtil.dispatchAllInvocationEvents();

    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkInvisibleRowsWithoutFilter(TEXT_APPEARANCE_PROPERTIES);
  }

  public void testExpandGroup() {
    init(myTextView);
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();

    JLabel label = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(ATTR_TEXT_APPEARANCE)));
    assert label != null : "Cannot find label for group property: " + ATTR_TEXT_APPEARANCE;
    fireMouseClick(label);

    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isTrue();
    checkInvisibleRowsWithoutFilter();
    verify(myPropertiesComponent).setValue("inspector.open.textAppearance", true);
  }

  public void testExpandParentGroupWithClosedChildGroup() {
    // Setup:
    init(myTextView);
    JLabel parent = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(TEXT_VIEW)));

    // Check initial setup:
    assertThat(isGroupOpen(TEXT_VIEW)).isTrue();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkInvisibleRowsWithoutFilter(TEXT_APPEARANCE_PROPERTIES);

    // Now close the parent:
    fireMouseClick(parent);

    // Check that both sections are closed:
    assertThat(isGroupOpen(TEXT_VIEW)).isFalse();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkInvisibleRowsWithoutFilter(TEXT_VIEW_PROPERTIES);

    // Then reopen the parent:
    fireMouseClick(parent);

    // Assert initial setup is restored (this is the actual test):
    assertThat(isGroupOpen(TEXT_VIEW)).isTrue();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isFalse();
    checkInvisibleRowsWithoutFilter(TEXT_APPEARANCE_PROPERTIES);
  }

  public void testCloseParentGroupWithOpenChildGroup() {
    // Setup:
    init(myTextView);
    JLabel parent = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(TEXT_VIEW)));
    JLabel child = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(ATTR_TEXT_APPEARANCE)));
    fireMouseClick(child);

    // Check initial setup:
    assertThat(isGroupOpen(TEXT_VIEW)).isTrue();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isTrue();
    checkInvisibleRowsWithoutFilter();

    // Now close the parent:
    fireMouseClick(parent);

    // Check that both sections are closed:
    assertThat(isGroupOpen(TEXT_VIEW)).isFalse();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isTrue();
    checkInvisibleRowsWithoutFilter(TEXT_VIEW_PROPERTIES);

    // Then reopen the parent:
    fireMouseClick(parent);

    // Assert initial setup is restored (this is the actual test):
    assertThat(isGroupOpen(TEXT_VIEW)).isTrue();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isTrue();
    checkInvisibleRowsWithoutFilter();
  }

  public void testClickOnLabelWithFilterDoesNotExpand() {
    init(myTextView);
    myInspector.setFilter("textA");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isNull();

    JLabel label = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(ATTR_TEXT_APPEARANCE)));
    assert label != null : "Cannot find label for group property: " + ATTR_TEXT_APPEARANCE;
    fireMouseClick(label);

    assertThat(isGroupOpen(ATTR_TEXT_APPEARANCE)).isNull();
  }

  public void testProgressBarWithoutFiltering() {
    init(myProgressBar);
    UIUtil.dispatchAllInvocationEvents();

    checkInvisibleRowsWithoutFilter(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT);
  }

  public void testFilterInProgressBar() {
    init(myProgressBar);
    myInspector.setFilter("draw");
    UIUtil.dispatchAllInvocationEvents();

    checkVisibleRowsWithFilter(ATTR_PROGRESS_DRAWABLE, ATTR_INDETERMINATE_DRAWABLE);
  }

  public void testComponentsRestoredInProgressBarAfterFiltering() {
    init(myProgressBar);
    myInspector.setFilter("draw");
    UIUtil.dispatchAllInvocationEvents();
    myInspector.setFilter("");
    UIUtil.dispatchAllInvocationEvents();

    checkInvisibleRowsWithoutFilter(ATTR_INDETERMINATE_DRAWABLE, ATTR_INDETERMINATE_TINT);
  }

  private static void fireMouseClick(@NotNull JLabel label) {
    MouseEvent event = mock(MouseEvent.class);
    for (MouseListener listener : label.getMouseListeners()) {
      listener.mouseClicked(event);
    }
  }

  @Nullable
  private Boolean isGroupOpen(@NotNull String labelName) {
    JLabel label = findFirstLabelWithText(myComponents.get(myLabelToRowNumber.get(labelName)));
    assert label != null : "Cannot find label for group property: " + labelName;
    if (Objects.equals(label.getIcon(), UIManager.get("Tree.expandedIcon"))) {
      return Boolean.TRUE;
    }
    if (Objects.equals(label.getIcon(), UIManager.get("Tree.collapsedIcon"))) {
      return Boolean.FALSE;
    }
    return null;
  }

  private void checkInvisibleRowsWithoutFilter(@NotNull String... expectedInvisiblePropertyNames) {
    Set<Component> visible = new HashSet<>(myComponents.values());
    Set<Component> invisible = new HashSet<>();
    for (String propertyName : expectedInvisiblePropertyNames) {
      invisible.addAll(myComponents.get(myLabelToRowNumber.get(propertyName)));
    }
    visible.removeAll(invisible);
    checkVisibleComponents(visible, invisible);
  }

  private void checkVisibleRowsWithFilter(@NotNull String... propertyNames) {
    Set<Component> visible = new HashSet<>();
    Set<Component> invisible = new HashSet<>(myComponents.values());
    for (String propertyName : propertyNames) {
      visible.addAll(myComponents.get(myLabelToRowNumber.get(propertyName)));
    }
    // Last 2 rows should always be visible:
    int rows = myComponents.keySet().size();
    visible.addAll(myComponents.get(rows - 1));
    visible.addAll(myComponents.get(rows - 2));
    invisible.removeAll(visible);
    checkVisibleComponents(visible, invisible);
  }

  private void checkVisibleComponents(@NotNull Set<Component> visible, @NotNull Set<Component> invisible) {
    for (Component component : visible) {
      assertThat(component.isVisible()).named("Component for: " + getLabel(component) + " is visible").isTrue();
    }
    for (Component component : invisible) {
      assertThat(component.isVisible()).named("Component for: " + getLabel(component) + " is visible").isFalse();
    }
  }

  @NotNull
  private String getLabel(@NotNull Component component) {
    String label = myComponentToLabel.get(component);
    return label != null ? label : "unknown";
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
        String propertyName = StringUtil.removeHtmlTags(label.getText());
        if (label.getIcon() == StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY) {
          propertyName += DESIGN_POSTFIX;
        }
        labelToRowNumber.put(propertyName, row);
      }
    }
    return labelToRowNumber;
  }

  private static Map<Component, String> getComponentToLabelMap(@NotNull Multimap<Integer, Component> components,
                                                               @NotNull Map<String, Integer> labelToRow) {
    Map<Component, String> componentToLabel = new IdentityHashMap<>();
    for (Map.Entry<String, Integer> labelAndRow : labelToRow.entrySet()) {
      for (Component component : components.get(labelAndRow.getValue())) {
        componentToLabel.put(component, labelAndRow.getKey());
      }
    }
    return componentToLabel;
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
