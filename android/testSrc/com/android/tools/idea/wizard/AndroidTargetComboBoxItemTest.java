/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.MockAddonTarget;
import com.android.sdklib.internal.repository.MockPlatformTarget;
import junit.framework.TestCase;

/**
 * Tests for the Combo box item used for API level/Build API settings in
 * the new project wizard.
 */
public class AndroidTargetComboBoxItemTest extends TestCase {
  public void testGetLabel() throws Exception {
    IAndroidTarget platformTarget = new MockPlatformTarget(18, 2);
    assertEquals("API 18: Android 4.3 (Jelly Bean)", ConfigureAndroidModuleStep.AndroidTargetComboBoxItem.getLabel(platformTarget));

    IAndroidTarget unknownTarget = new MockPlatformTarget(-1, 1);
    assertEquals("API -1", ConfigureAndroidModuleStep.AndroidTargetComboBoxItem.getLabel(unknownTarget));

    IAndroidTarget platformPreviewTarget = new MockPlatformTarget(100, 1);
    assertEquals("API 100: Android null", ConfigureAndroidModuleStep.AndroidTargetComboBoxItem.getLabel(platformPreviewTarget));
  }
}
