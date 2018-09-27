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

import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.flexbox.FlexboxLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler;
import com.android.tools.idea.uibuilder.property.MockNlComponent;
import com.intellij.psi.xml.XmlFile;

public class ViewHandlerManagerTest extends LayoutTestCase {

  public void testBasicHandlers() {
    ViewHandlerManager viewManager = getProject().getComponent(ViewHandlerManager.class);
    assertSame(viewManager, getProject().getComponent(ViewHandlerManager.class));

    assertTrue(viewManager.getHandler("LinearLayout") instanceof LinearLayoutHandler);
    assertTrue(viewManager.getHandler("android.widget.LinearLayout") instanceof LinearLayoutHandler);
    assertTrue(viewManager.getHandler("RelativeLayout") instanceof RelativeLayoutHandler);
    assertTrue(viewManager.getHandler("android.widget.RelativeLayout") instanceof RelativeLayoutHandler);
    assertTrue(viewManager.getHandler("merge") instanceof MergeHandler);
    assertTrue(viewManager.getHandler("layout") instanceof LayoutHandler);

    assertSame(viewManager.getHandler("LinearLayout"), viewManager.getHandler("LinearLayout"));
    if (FlexboxLayoutHandler.FLEXBOX_ENABLE_FLAG) {
      assertTrue(viewManager.getHandler(SdkConstants.FLEXBOX_LAYOUT) instanceof FlexboxLayoutHandler);
    }
  }

  public void testMergeHandler() {
    String xml = "<merge/>\n";
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/merge.xml", xml);
    NlComponent root = MockNlComponent.create(file.getRootTag());
    ViewHandlerManager viewManager = getProject().getComponent(ViewHandlerManager.class);
    ViewHandler handler = viewManager.getHandler(root);
    assertTrue(handler instanceof MergeHandler);
    assertThat(handler.getInspectorProperties()).containsExactly(
      TOOLS_NS_NAME_PREFIX + ATTR_SHOW_IN, TOOLS_NS_NAME_PREFIX + ATTR_PARENT_TAG);
  }

  public void testMergeHandlerWithLinearLayoutParentTag() {
    String xml = "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                 "    tools:parentTag=\"LinearLayout\">\n" +
                 "\n" +
                 "</merge>";
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/merge.xml", xml);
    NlComponent root = MockNlComponent.create(file.getRootTag());
    ViewHandlerManager viewManager = getProject().getComponent(ViewHandlerManager.class);
    ViewHandler handler = viewManager.getHandler(root);
    assertTrue(handler instanceof MergeDelegateHandler);

    // This handler should have inspector properties from <merge> and <LinearLayout>
    assertThat(handler.getInspectorProperties()).containsExactly(
      TOOLS_NS_NAME_PREFIX + ATTR_SHOW_IN, TOOLS_NS_NAME_PREFIX + ATTR_PARENT_TAG, ATTR_ORIENTATION, ATTR_GRAVITY);
  }
}
