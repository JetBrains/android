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

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.util.MockNlComponent;
import com.google.common.collect.ImmutableSet;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import icons.StudioIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.tools.idea.uibuilder.handlers.ViewTagHandler.SUITABLE_LAYOUT_CLASS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ViewTagHandler}.
 */
public class ViewTagHandlerTest extends AndroidTestCase {
  @Language("XML")
  private static final String TEST_LAYOUT_SOURCE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
    "  <view android:id=\"@+id/verical_divider1dp\"\n" +
    "        android:layout_width=\"1dp\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:background=\"?android:attr/listDivider\"/>\n" +
    "  <view android:id=\"@+id/horizontal_divider1dp\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"1dp\"\n" +
    "        android:background=\"?android:attr/listDivider\"/>\n" +
    "  <view android:id=\"@+id/verical_divider1px\"\n" +
    "        android:layout_width=\"1px\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:background=\"?android:attr/listDivider\"/>n" +
    "  <view android:id=\"@+id/horizontal_divider1px\" " +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"1px\"\n" +
    "        android:background=\"?android:attr/listDivider\"/>\n" +
    "  <view android:id=\"@+id/view1\"\n" +
    "        android:layout_width=\"wrap_content\"\n" +
    "        android:layout_height=\"wrap_content\"/>\n" +
    "  <view android:id=\"@+id/view2\"\n" +
    "        android:layout_width=\"wrap_content\"\n" +
    "        android:layout_height=\"wrap_content\"\n" +
    "        class='\"android.widget.Button\"'/>\n" +
    "</RelativeLayout>\n";

  private XmlTag[] myViewTags;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", TEST_LAYOUT_SOURCE);
    myViewTags = xmlFile.getRootTag().getSubTags();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myViewTags = null;
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Checks the {@link ViewTagHandler#isViewSuitableForLayout} method.
   */
  public void testIsViewSuitableForLayout() {
    assertTrue(SUITABLE_LAYOUT_CLASS.test("com.example.myownpackage.TestView"));
    assertTrue(SUITABLE_LAYOUT_CLASS.test(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT.defaultName()));
    assertFalse(SUITABLE_LAYOUT_CLASS.test(SdkConstants.FQCN_IMAGE_BUTTON));
  }

  public void testIcon() {
    ViewTagHandler handler = new ViewTagHandler();
    for (XmlTag tag : myViewTags) {
      NlComponent component = MockNlComponent.create(tag);
      assertThat(handler.getIcon(component)).isEqualTo(getExpectedIcon(component.getId()));
    }
  }

  public void testOnCreate() {
    Set<String> classes = ImmutableSet.of(CLASS_VIEW);
    ViewTagHandler handler = new ViewTagHandler();
    for (XmlTag tag : myViewTags) {
      NlComponent component = MockNlComponent.create(tag);

      try (MockedStatic<ViewEditor> editor = Mockito.mockStatic(ViewEditor.class)) {
        handler.onCreate(null, component, InsertType.CREATE);
        int time = component.getId().equals("view1") ? 1 : 0;
        editor.verify(
          () -> ViewEditor.displayClassInput(eq(component.getModel()), eq("Views"), eq(classes), eq(SUITABLE_LAYOUT_CLASS), isNull()),
          times(time)
        );
      }
    }
  }

  @NotNull
  private static Icon getExpectedIcon(@NotNull String id) {
    switch (id) {
      case "verical_divider1dp":
      case "verical_divider1px":
        return StudioIcons.LayoutEditor.Palette.VERTICAL_DIVIDER;
      case "horizontal_divider1dp":
      case "horizontal_divider1px":
        return StudioIcons.LayoutEditor.Palette.HORIZONTAL_DIVIDER;
      default:
        return StudioIcons.LayoutEditor.Palette.VIEW;
    }
  }
}
