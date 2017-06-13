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
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;

import java.util.Collections;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class BrowsePanelTest extends PropertyTestCase {

  public void testGetResourceTypes() {
    Map<String, NlProperty> props = getPropertyMap(Collections.singletonList(myTextView));
    assertThat(BrowsePanel.hasResourceChooser(props.get(SdkConstants.ATTR_ID))).isFalse();
    assertThat(BrowsePanel.hasResourceChooser(props.get(SdkConstants.ATTR_TEXT))).isTrue();
    assertThat(BrowsePanel.hasResourceChooser(props.get(SdkConstants.ATTR_BACKGROUND))).isTrue();
    assertThat(BrowsePanel.hasResourceChooser(props.get(SdkConstants.ATTR_TYPEFACE))).isFalse();
  }
}
