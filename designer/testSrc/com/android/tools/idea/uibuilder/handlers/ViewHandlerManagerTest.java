/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.handlers.flexbox.FlexboxLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler;

public class ViewHandlerManagerTest extends LayoutTestCase {
  public void test() {
    ViewHandlerManager viewManager = getProject().getComponent(ViewHandlerManager.class);
    assertSame(viewManager, getProject().getComponent(ViewHandlerManager.class));

    assertTrue(viewManager.getHandler("LinearLayout") instanceof LinearLayoutHandler);
    assertTrue(viewManager.getHandler("android.widget.LinearLayout") instanceof LinearLayoutHandler);
    assertTrue(viewManager.getHandler("RelativeLayout") instanceof RelativeLayoutHandler);
    assertTrue(viewManager.getHandler("android.widget.RelativeLayout") instanceof RelativeLayoutHandler);

    assertSame(viewManager.getHandler("LinearLayout"), viewManager.getHandler("LinearLayout"));
    if (FlexboxLayoutHandler.FLEXBOX_ENABLE_FLAG) {
      assertTrue(viewManager.getHandler(SdkConstants.FLEXBOX_LAYOUT) instanceof FlexboxLayoutHandler);
    }
  }
}