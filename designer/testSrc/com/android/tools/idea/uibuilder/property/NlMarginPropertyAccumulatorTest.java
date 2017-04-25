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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.google.common.collect.Table;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class NlMarginPropertyAccumulatorTest extends PropertyTestCase {
  private Table<String, String, NlPropertyItem> myTable;
  private NlPropertyAccumulator myAccumulator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTable = getPropertyTable(Collections.singletonList(myButton));
    myAccumulator = new NlMarginPropertyAccumulator(
      "Layout_Margin", ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_START,
      ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM);
  }

  public void testIsApplicable() {
    assertThat(myAccumulator.isApplicable(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN))).isTrue();
    assertThat(myAccumulator.isApplicable(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT))).isTrue();
    assertThat(myAccumulator.isApplicable(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT))).isTrue();
    assertThat(myAccumulator.isApplicable(myTable.get(ANDROID_URI, ATTR_PADDING))).isFalse();
  }

  public void testProcess() {
    assertThat(myAccumulator.process(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN))).isTrue();
    assertThat(myAccumulator.process(myTable.get(ANDROID_URI, ATTR_TEXT))).isFalse();
  }

  public void testOnlyLayoutMarginPropertiesAreChildrenOfGroup() {
    for (NlPropertyItem property : myTable.values()) {
      myAccumulator.process(property);
    }
    PTableGroupItem group = myAccumulator.getGroupNode();
    assertThat(group.getName()).isEqualTo("Layout_Margin");

    List<PTableItem> items = group.getChildren();
    List<String> names = items.stream().map(PTableItem::getName).collect(Collectors.toList());
    List<String> namesWithPrefix = names.stream().filter(name -> name.startsWith("layout_margin")).collect(Collectors.toList());

    assertThat(names).containsAllOf(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_TOP);
    assertThat(names).containsExactlyElementsIn(namesWithPrefix);
  }

  public void testLabelReportedWithoutThePrefix() {
    for (NlPropertyItem property : myTable.values()) {
      myAccumulator.process(property);
    }
    PTableGroupItem group = myAccumulator.getGroupNode();
    assertThat(group.getChildLabel(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN))).isEqualTo("all");
    assertThat(group.getChildLabel(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT))).isEqualTo("left");
    assertThat(group.getChildLabel(myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT))).isEqualTo("right");
  }

  public void testValueOfGroup() {
    myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT).setValue("20dp");
    myTable.get(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM).setValue("30dp");

    for (NlPropertyItem property : myTable.values()) {
      myAccumulator.process(property);
    }

    PTableGroupItem group = myAccumulator.getGroupNode();
    JTable table = new PTable(new PTableModel());
    Component component = group.getCellRenderer().getTableCellRendererComponent(table, group, false, false, 10, 1);
    assertThat(component.toString()).isEqualTo("[?, 20dp, ?, ?, 30dp]");
  }
}
