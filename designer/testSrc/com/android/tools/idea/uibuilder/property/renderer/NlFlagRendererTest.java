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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.google.common.collect.Iterators;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class NlFlagRendererTest extends PropertyTestCase {
  private NlFlagRenderer myRenderer;
  private PTable myTable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRenderer = new NlFlagRenderer();
    myTable = new PTable(new PTableModel());
  }

  public void testGravity() {
    NlFlagPropertyItem property = (NlFlagPropertyItem)getProperty(myButton, ATTR_GRAVITY);
    property.setValue("top|bottom");

    checkCustomize(property, "[top, bottom]");
  }

  public void testNonFlagItem() {
    PTableItem item = mock(PTableItem.class);
    checkCustomize(item, "");
  }

  private void checkCustomize(@NotNull PTableItem item, @NotNull String expected) {
    myRenderer.clear();
    myRenderer.customizeCellRenderer(myTable, item, false, false, 10, 1);
    if (expected.isEmpty()) {
      assertThat(myRenderer.iterator().hasNext()).isFalse();
    }
    else {
      assertThat(Iterators.getOnlyElement(myRenderer.iterator())).isEqualTo(expected);
    }
  }
}
