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
package com.android.tools.idea.uibuilder.handlers;

import com.android.SdkConstants;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests for {@link ViewTagHandler}.
 */
public class ViewTagHandlerTest extends AndroidTestCase {

  /**
   * Checks the {@link ViewTagHandler#isViewSuitableForLayout} method.
   */
  public void testIsViewSuitableForLayout() {
    assertTrue(ViewTagHandler.isViewSuitableForLayout("com.example.myownpackage.TestView"));
    assertTrue(ViewTagHandler.isViewSuitableForLayout(SdkConstants.CLASS_CONSTRAINT_LAYOUT));
    assertFalse(ViewTagHandler.isViewSuitableForLayout(SdkConstants.FQCN_IMAGE_BUTTON));
  }
}
