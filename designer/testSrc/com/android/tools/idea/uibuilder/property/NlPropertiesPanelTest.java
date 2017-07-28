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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
import static com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PROPERTY_MODE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class NlPropertiesPanelTest extends PropertyTestCase {
  @Mock
  private RowFilter.Entry<? extends PTableModel, Integer> myEntry;
  private InspectorPanel myInspector;
  private NlPropertiesPanel myPanel;
  private MyTable myTable;
  private PTableModel myModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
    myModel = new PTableModel();
    myTable = new MyTable(myModel);
    myInspector = spy(new InspectorPanel(myPropertiesManager, getTestRootDisposable(), new JPanel()));
    myPanel = new NlPropertiesPanel(myPropertiesManager, getTestRootDisposable(), myTable, myInspector);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myTable.removeEditor();
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myEntry = null;
      myInspector = null;
      myPanel = null;
      myTable = null;
      myModel = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testSelectionIsRestoredAfterPropertyUpdate() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    NlProperty elevation = properties.get(ANDROID_URI, ATTR_ELEVATION);
    myPanel.setItems(components, properties, myPropertiesManager);
    int row = findRowOf(ANDROID_URI, ATTR_ELEVATION);
    myTable.setRowSelectionInterval(row, row);

    myPanel.setItems(components, getPropertyTable(components), myPropertiesManager);
    assertThat(myTable.getSelectedItem()).isEqualTo(elevation);
  }

  public void testSelectionIsRestoredAfterSelectionChange() {
    List<NlComponent> initialComponents = Collections.singletonList(myButton);
    myPanel.setItems(initialComponents, getPropertyTable(initialComponents), myPropertiesManager);
    int row = findRowOf(ANDROID_URI, ATTR_ELEVATION);
    myTable.setRowSelectionInterval(row, row);

    List<NlComponent> newComponents = Collections.singletonList(myCheckBox1);
    Table<String, String, NlPropertyItem> newProperties = getPropertyTable(newComponents);
    NlProperty elevation = newProperties.get(ANDROID_URI, ATTR_ELEVATION);
    myPanel.setItems(newComponents, newProperties, myPropertiesManager);
    assertThat(myTable.getSelectedItem()).isSameAs(elevation);
  }

  public void testSelectionIsRestoredAfterFilterUpdateIfPossible() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    NlProperty elevation = properties.get(ANDROID_URI, ATTR_ELEVATION);
    myPanel.setItems(components, properties, myPropertiesManager);
    int row = findRowOf(ANDROID_URI, ATTR_ELEVATION);
    myTable.setRowSelectionInterval(row, row);

    myPanel.setFilter("e");
    assertThat(myTable.getSelectedItem()).isSameAs(elevation);
    myPanel.setFilter("el");
    assertThat(myTable.getSelectedItem()).isSameAs(elevation);
    myPanel.setFilter("eleva");
    assertThat(myTable.getSelectedItem()).isSameAs(elevation);
    myPanel.setFilter("");
    assertThat(myTable.getSelectedItem()).isSameAs(elevation);
  }

  public void testEnterIsIgnoredIfNoFilter() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    myPanel.setAllPropertiesPanelVisible(true);
    myPanel.setItems(components, properties, myPropertiesManager);
    KeyEvent event = new KeyEvent(myPanel, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myPanel.getFilterKeyListener().keyPressed(event);

    assertThat(myPanel.getTable().isEditing()).isFalse();
    assertThat(event.isConsumed()).isFalse();
  }

  public void testEnterIsIgnoredIfFilterIsNotUnique() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    myPanel.setAllPropertiesPanelVisible(true);
    myPanel.setItems(components, properties, myPropertiesManager);
    myPanel.setFilter("el");
    KeyEvent event = new KeyEvent(myPanel, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myPanel.getFilterKeyListener().keyPressed(event);

    assertThat(myPanel.getTable().isEditing()).isFalse();
    assertThat(myPanel.getTable().getRowCount()).isGreaterThan(1);
    assertThat(event.isConsumed()).isFalse();
  }

  public void testEnterCausesStartEditingInTable() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    NlProperty elevation = properties.get(ANDROID_URI, ATTR_ELEVATION);
    myPanel.setAllPropertiesPanelVisible(true);
    myPanel.setItems(components, properties, myPropertiesManager);
    myPanel.setFilter("eleva");
    KeyEvent event = new KeyEvent(myPanel, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myPanel.getFilterKeyListener().keyPressed(event);

    assertThat(myPanel.getTable().isEditing()).isTrue();
    assertThat(myPanel.getTable().getRowCount()).isEqualTo(1);
    assertThat(myPanel.getTable().getValueAt(0, 1)).isSameAs(elevation);
    assertThat(event.isConsumed()).isTrue();
  }

  public void testEnterCausesStartEditingOfClosedFlagsInTable() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    NlProperty textStyle = properties.get(ANDROID_URI, ATTR_TEXT_STYLE);
    myPanel.setAllPropertiesPanelVisible(true);
    myPanel.setItems(components, properties, myPropertiesManager);
    myTable.resetRequestFocusCount();

    myPanel.setFilter("textSt");
    KeyEvent event = new KeyEvent(myPanel, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myPanel.getFilterKeyListener().keyPressed(event);

    assertThat(myTable.getRequestFocusCount()).isEqualTo(1);
    assertThat(myPanel.getTable().isEditing()).isFalse();
    assertThat(myPanel.getTable().getRowCount()).isEqualTo(4);  // textStyle group with 3 values
    assertThat(myPanel.getTable().getValueAt(0, 1)).isSameAs(textStyle);
    assertThat(myPanel.getTable().getSelectedRow()).isEqualTo(0);
    assertThat(event.isConsumed()).isTrue();
  }

  public void testEnterCausesStartEditingOfOpenFlagsInTable() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    NlProperty textStyle = properties.get(ANDROID_URI, ATTR_TEXT_STYLE);
    myPanel.setAllPropertiesPanelVisible(true);
    myPanel.setItems(components, properties, myPropertiesManager);
    int textStyleRow = findRowOf(ANDROID_URI, ATTR_TEXT_STYLE);
    myModel.expand(textStyleRow);
    myTable.resetRequestFocusCount();

    myPanel.setFilter("textSt");
    KeyEvent event = new KeyEvent(myPanel, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myPanel.getFilterKeyListener().keyPressed(event);

    assertThat(myTable.getRequestFocusCount()).isEqualTo(1);
    assertThat(myPanel.getTable().isEditing()).isFalse();
    assertThat(myPanel.getTable().getRowCount()).isEqualTo(4);  // textStyle group with 3 values
    assertThat(myPanel.getTable().getValueAt(0, 1)).isSameAs(textStyle);
    assertThat(myPanel.getTable().getSelectedRow()).isEqualTo(0);
    assertThat(event.isConsumed()).isTrue();
  }

  public void testEnterCausesStartEditingInInspector() {
    List<NlComponent> components = Collections.singletonList(myButton);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    myPanel.setItems(components, properties, myPropertiesManager);
    myPanel.setFilter("eleva");
    KeyEvent event = new KeyEvent(myPanel, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    myPanel.getFilterKeyListener().keyPressed(event);

    verify(myInspector).setFilter(eq("eleva"));
    verify(myInspector).enterInFilter(eq(event));
    assertThat(myPanel.getTable().isEditing()).isFalse();
  }

  public void testFilterSimpleMatch() {
    PTableItem item = mock(PTableItem.class);
    when(item.getName()).thenReturn("layout_bottom_of");
    when(myEntry.getValue(0)).thenReturn(item);

    NlPropertiesPanel.MyFilter filter = new NlPropertiesPanel.MyFilter();
    filter.setPattern("bott");
    assertTrue(filter.include(myEntry));
  }

  public void testFilterSimpleMismatch() {
    PTableItem item = mock(PTableItem.class);
    when(item.getName()).thenReturn("layout_height");
    when(myEntry.getValue(0)).thenReturn(item);

    NlPropertiesPanel.MyFilter filter = new NlPropertiesPanel.MyFilter();
    filter.setPattern("bott");
    assertFalse(filter.include(myEntry));
  }

  public void testFilterParentIsAMatch() {
    PTableItem item = mock(PTableItem.class);
    PTableGroupItem group = mock(PTableGroupItem.class);
    when(item.getName()).thenReturn("top");
    when(item.getParent()).thenReturn(group);
    when(group.getName()).thenReturn("padding");
    when(myEntry.getValue(0)).thenReturn(item);

    NlPropertiesPanel.MyFilter filter = new NlPropertiesPanel.MyFilter();
    filter.setPattern("padd");
    assertTrue(filter.include(myEntry));
  }

  public void testFilterParentIsNotAMatch() {
    PTableItem item = mock(PTableItem.class);
    PTableGroupItem group = mock(PTableGroupItem.class);
    when(item.getName()).thenReturn("top");
    when(item.getParent()).thenReturn(group);
    when(group.getName()).thenReturn("padding");
    when(myEntry.getValue(0)).thenReturn(item);

    NlPropertiesPanel.MyFilter filter = new NlPropertiesPanel.MyFilter();
    filter.setPattern("constra");
    assertFalse(filter.include(myEntry));
  }

  public void testFilterChildIsAMatch() {
    PTableItem item = mock(PTableItem.class);
    PTableGroupItem group = mock(PTableGroupItem.class);
    when(item.getName()).thenReturn("bottom");
    when(item.getParent()).thenReturn(group);
    when(group.getName()).thenReturn("padding");
    when(group.getChildren()).thenReturn(ImmutableList.of(item));
    when(myEntry.getValue(0)).thenReturn(group);

    NlPropertiesPanel.MyFilter filter = new NlPropertiesPanel.MyFilter();
    filter.setPattern("bott");
    assertTrue(filter.include(myEntry));
  }

  public void testFilterChildIsNotAMatch() {
    PTableItem item = mock(PTableItem.class);
    PTableGroupItem group = mock(PTableGroupItem.class);
    when(item.getName()).thenReturn("top");
    when(item.getParent()).thenReturn(item);
    when(group.getName()).thenReturn("padding");
    when(group.getChildren()).thenReturn(ImmutableList.of(item));
    when(myEntry.getValue(0)).thenReturn(group);

    NlPropertiesPanel.MyFilter filter = new NlPropertiesPanel.MyFilter();
    filter.setPattern("bott");
    assertFalse(filter.include(myEntry));
  }

  public void testDefaultMode() {
    assertThat(myPanel.isAllPropertiesPanelVisible()).isFalse();
  }

  public void testInitialModeIsReadFromOptions() {
    PropertiesComponent.getInstance().setValue(PROPERTY_MODE, PropertiesViewMode.TABLE.name());
    Disposer.dispose(myPanel);
    myPanel = new NlPropertiesPanel(myPropertiesManager, getTestRootDisposable(), myTable, myInspector);
    assertThat(myPanel.isAllPropertiesPanelVisible()).isTrue();
  }

  public void testInitialModeFromMalformedOptionValueIsIgnored() {
    PropertiesComponent.getInstance().setValue(PROPERTY_MODE, "malformed");
    Disposer.dispose(myPanel);
    myPanel = new NlPropertiesPanel(myPropertiesManager, getTestRootDisposable(), myTable, myInspector);
    assertThat(myPanel.isAllPropertiesPanelVisible()).isFalse();
  }

  public void testInitialModeIsSavedToOptions() {
    assertThat(PropertiesComponent.getInstance().getValue(PROPERTY_MODE)).isNull();
    myPanel.setAllPropertiesPanelVisible(true);
    assertThat(PropertiesComponent.getInstance().getValue(PROPERTY_MODE)).isEqualTo(PropertiesViewMode.TABLE.name());
  }

  public void testActivatePreferredEditor() {
    boolean[] called = new boolean[1];
    List<NlComponent> components = Collections.singletonList(myTextView);
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    myPanel.setAllPropertiesPanelVisible(true);
    myPanel.setRestoreToolWindow(() -> called[0] = true);
    myPanel.setItems(components, properties, myPropertiesManager);

    myPanel.activatePreferredEditor(ATTR_TEXT, false);
    assertThat(myPanel.isAllPropertiesPanelVisible()).isFalse();
    assertThat(called[0]).isTrue();
    verify(myInspector).activatePreferredEditor(ATTR_TEXT, false);
  }

  private int findRowOf(@NotNull String namespace, @NotNull String name) {
    for (int row = 0; row < myModel.getRowCount(); row++) {
      PTableItem item = (PTableItem)myModel.getValueAt(row, 0);
      if (item != null && name.equals(item.getName()) && namespace.equals(item.getNamespace())) {
        return row;
      }
    }
    return -1;
  }

  private static class MyTable extends PTable {
    private int myRequestFocusCount;

    public MyTable(@NotNull PTableModel model) {
      super(model);
    }

    @Override
    public void requestFocus() {
      super.requestFocus();
      myRequestFocusCount++;
    }

    private void resetRequestFocusCount() {
      myRequestFocusCount = 0;
    }

    private int getRequestFocusCount() {
      return myRequestFocusCount;
    }
  }
}
