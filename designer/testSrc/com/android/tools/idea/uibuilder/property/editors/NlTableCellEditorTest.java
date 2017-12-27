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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.google.common.collect.Table;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NlTableCellEditorTest extends PropertyTestCase {
  private NlComponentEditor myEditor;
  private BrowsePanel myBrowsePanel;
  private NlTableCellEditor myCellEditor;
  private PTableModel myTableModel;
  private PTable myTable;
  private NlPropertyItem myElevationProperty;
  private NlPropertyItem myTextProperty;
  private NlPropertyItem myTextDesignProperty;
  private JComponent myEditorComponent;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myEditor = mock(NlComponentEditor.class);
    myBrowsePanel = mock(BrowsePanel.class);
    myEditorComponent = new JPanel();
    when(myEditor.getComponent()).thenReturn(myEditorComponent);

    myCellEditor = new NlTableCellEditor();
    myCellEditor.init(myEditor, myBrowsePanel);
    myTableModel = new PTableModel();
    myTable = new PTable(myTableModel);

    Table<String, String, NlPropertyItem> properties = getPropertyTable(Collections.singletonList(myTextView));
    List<PTableItem> items = new ArrayList<>();
    myElevationProperty = properties.get(ANDROID_URI, ATTR_ELEVATION);
    myTextProperty = properties.get(ANDROID_URI, ATTR_TEXT);
    myTextDesignProperty = properties.get(ANDROID_URI, ATTR_TEXT).getDesignTimeProperty();
    items.add(myElevationProperty);
    items.add(myTextProperty);
    items.add(myTextDesignProperty);
    items.add(properties.get(ANDROID_URI, ATTR_TEXT_APPEARANCE));
    myTableModel.setItems(items);
  }

  public void testGetDesignState() {
    assertThat(NlTableCellEditor.getDesignState(myTable, 0)).isEqualTo(PropertyDesignState.MISSING_DESIGN_PROPERTY);
    assertThat(NlTableCellEditor.getDesignState(myTable, 1)).isEqualTo(PropertyDesignState.HAS_DESIGN_PROPERTY);
    assertThat(NlTableCellEditor.getDesignState(myTable, 2)).isEqualTo(PropertyDesignState.IS_REMOVABLE_DESIGN_PROPERTY);
    assertThat(NlTableCellEditor.getDesignState(myTable, 3)).isEqualTo(PropertyDesignState.MISSING_DESIGN_PROPERTY);
    assertThat(NlTableCellEditor.getDesignState(myTable, 4)).isEqualTo(PropertyDesignState.NOT_APPLICABLE);
  }

  public void testGetTableCellEditorComponentOfTextProperty() {
    assertThat(myCellEditor.getTableCellEditorComponent(myTable, myTextProperty, false, 1, 1)).isSameAs(myEditorComponent);
    verify(myEditor).setProperty(myTextProperty);
    verify(myBrowsePanel).setDesignState(PropertyDesignState.HAS_DESIGN_PROPERTY);
    assertThat(myCellEditor.getTable()).isSameAs(myTable);
    assertThat(myCellEditor.getRow()).isEqualTo(1);
  }

  public void testGetTableCellEditorComponentOfTextDesignProperty() {
    assertThat(myCellEditor.getTableCellEditorComponent(myTable, myTextDesignProperty, false, 2, 1)).isSameAs(myEditorComponent);
    verify(myEditor).setProperty(myTextDesignProperty);
    verify(myBrowsePanel).setDesignState(PropertyDesignState.IS_REMOVABLE_DESIGN_PROPERTY);
    assertThat(myCellEditor.getTable()).isSameAs(myTable);
    assertThat(myCellEditor.getRow()).isEqualTo(2);
  }

  public void testStopCellEditing() {
    myCellEditor.getTableCellEditorComponent(myTable, myTextProperty, false, 1, 1);

    myCellEditor.stopCellEditing();
    verify(myEditor).setProperty(EmptyProperty.INSTANCE);
    assertThat(myCellEditor.getTable()).isNull();
    assertThat(myCellEditor.getRow()).isEqualTo(-1);
  }

  public void testAddDesignPropertyForElevation() {
    myCellEditor.getTableCellEditorComponent(myTable, myElevationProperty, false, 0, 1);

    myCellEditor.addDesignProperty();
    NlPropertyItem property = (NlPropertyItem)myTableModel.getValueAt(1, 1);
    assertThat(property.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(property.getName()).isEqualTo(ATTR_ELEVATION);
  }

  public void testRemoveTextDesignProperty() {
    myCellEditor.getTableCellEditorComponent(myTable, myTextDesignProperty, false, 2, 1);

    myCellEditor.removeDesignProperty();
    NlPropertyItem property = (NlPropertyItem)myTableModel.getValueAt(2, 1);
    assertThat(property.getName()).isEqualTo(ATTR_TEXT_APPEARANCE);
  }
}
