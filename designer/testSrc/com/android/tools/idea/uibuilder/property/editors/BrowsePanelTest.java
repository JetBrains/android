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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BrowsePanelTest extends PropertyTestCase {

  public void testGetResourceTypes() {
    Map<String, NlProperty> props = getPropertyMap(Collections.singletonList(myTextView));
    assertThat(BrowsePanel.hasBrowseDialog(props.get(SdkConstants.ATTR_ID))).isFalse();
    assertThat(BrowsePanel.hasBrowseDialog(props.get(SdkConstants.ATTR_TEXT))).isTrue();
    assertThat(BrowsePanel.hasBrowseDialog(props.get(SdkConstants.ATTR_BACKGROUND))).isTrue();
    assertThat(BrowsePanel.hasBrowseDialog(props.get(SdkConstants.ATTR_TYPEFACE))).isFalse();
  }

  public void testGetResourceTypesForView() {
    Map<String, NlProperty> props = getPropertyMap(Collections.singletonList(myViewTag));
    assertThat(BrowsePanel.hasBrowseDialog(props.get(SdkConstants.ATTR_CLASS))).isTrue();
  }

  public void testGetResourceTypesForFragment() {
    Map<String, NlProperty> props = getPropertyMap(Collections.singletonList(myFragment));
    assertThat(BrowsePanel.hasBrowseDialog(props.get(SdkConstants.ATTR_NAME))).isTrue();
  }

  public void testMouseMovedLeftOfButtons() {
    PTable table = mock(PTable.class);
    BrowsePanel panel = new BrowsePanel(null, true);
    panel.doLayout();

    Rectangle rect = new Rectangle(0, 0, 200, 100);
    MouseEvent event = new MouseEvent(panel, 0, 0, 0, 10, 20, 1, false);
    panel.mouseMoved(table, event, rect);

    verify(table).setExpandableItemsEnabled(true);
  }

  public void testMouseMovedOverButtons() {
    PTable table = mock(PTable.class);
    BrowsePanel panel = new BrowsePanel(null, true);
    panel.doLayout();

    Rectangle rect = new Rectangle(0, 0, 200, 100);
    MouseEvent event = new MouseEvent(panel, 0, 0, 0, 180, 20, 1, false);
    panel.mouseMoved(table, event, rect);

    verify(table).setExpandableItemsEnabled(false);
  }
}
