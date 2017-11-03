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

import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.google.common.collect.Table;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class NlPropertyAccumulatorTest extends PropertyTestCase {
  private Table<String, String, NlPropertyItem> myTable;
  private NlPropertyAccumulator myAccumulator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTable = getPropertyTable(Collections.singletonList(myButton));
    myAccumulator = new NlPropertyAccumulator.PropertyNamePrefixAccumulator("Padding", "padding");
  }

  public void testProcess() {
    assertThat(myAccumulator.process(myTable.get(ANDROID_URI, ATTR_PADDING_BOTTOM))).isTrue();
    assertThat(myAccumulator.process(myTable.get(ANDROID_URI, ATTR_TEXT))).isFalse();
  }

  public void testOnlyPaddingPropertiesAreChildrenOfGroup() {
    for (NlPropertyItem property : myTable.values()) {
      myAccumulator.process(property);
    }
    PTableGroupItem group = myAccumulator.getGroupNode();
    assertThat(group.getName()).isEqualTo("Padding");

    List<PTableItem> items = group.getChildren();
    List<String> names = items.stream().map(PTableItem::getName).collect(Collectors.toList());
    List<String> namesWithPrefix = names.stream().filter(name -> name.startsWith("padding")).collect(Collectors.toList());

    assertThat(names).containsAllOf(ATTR_PADDING_BOTTOM, ATTR_PADDING_TOP, ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT);
    assertThat(names).containsExactlyElementsIn(namesWithPrefix);
  }

  public void testLabelReportedWithoutThePrefix() {
    for (NlPropertyItem property : myTable.values()) {
      myAccumulator.process(property);
    }
    PTableGroupItem group = myAccumulator.getGroupNode();
    assertThat(group.getChildLabel(myTable.get(ANDROID_URI, ATTR_PADDING))).isEqualTo("all");
    assertThat(group.getChildLabel(myTable.get(ANDROID_URI, ATTR_PADDING_TOP))).isEqualTo("top");
    assertThat(group.getChildLabel(myTable.get(ANDROID_URI, ATTR_PADDING_BOTTOM))).isEqualTo("bottom");
  }
}
