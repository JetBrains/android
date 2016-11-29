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

import com.android.tools.idea.uibuilder.property.ptable.PTableGroupItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableModel;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NlPropertiesPanelTest extends AndroidTestCase {
  @Mock
  RowFilter.Entry<? extends PTableModel, Integer> myEntry;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
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
}
