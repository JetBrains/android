// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.intellij.psi.PsiClass;
import java.util.concurrent.TimeUnit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.annotations.NotNull;

import static com.android.testutils.AsyncTestUtils.waitForCondition;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;

public class CustomViewGroupHandlerTest extends AndroidTestCase {
  private static final String MY_ABSOLUTE_LAYOUT = "p1.p2.MyAbsoluteLayout";
  private static final String MY_LINEAR_LAYOUT = "p1.p2.MyAbsoluteLayout.MyLinearLayout";
  private String myTagName;
  private String myClassName;
  private ViewGroupHandler myDefaultHandler;

  @Language("JAVA")
  String innerClass =
    "package p1.p2;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.widget.AbsoluteLayout;\n" +
    "import android.widget.LinearLayout;\n" +
    "\n" +
    "public class MyAbsoluteLayout extends AbsoluteLayout {\n" +
    "    public MyImageView(Context context) {\n" +
    "        super(context);\n" +
    "    }\n" +
    "    public static class MyLinearLayout extends LinearLayout {\n" +
    "        public MyLinearLayout(Context context) {\n" +
    "            super(context);\n" +
    "        }\n" +
    "    }\n" +
    "}";

  private void setUpMyClasses(@NotNull String tagName) throws Exception {
    myFixture.addClass(innerClass);
    PsiClass psiClass = myFixture.findClass(tagName);
    myTagName = tagName;
    myClassName = PackageClassConverter.getQualifiedName(psiClass);
    ViewHandlerManager manager = ViewHandlerManager.get(getProject());
    boolean[] wasUpdated = new boolean[1];
    Runnable updated = () -> { wasUpdated[0] = true; };
    ViewHandler handler = manager.getHandlerOrDefault(myTagName, updated);
    if (handler == ViewHandlerManager.TEMP) {
      waitForCondition(10, TimeUnit.SECONDS, () -> wasUpdated[0]);
      handler = manager.getHandlerOrDefault(myTagName, updated);
    }
    myDefaultHandler = (ViewGroupHandler)handler;
  }

  public void testGetXml() throws Exception {
    setUpMyClasses(MY_ABSOLUTE_LAYOUT);
    ViewHandler handler = new CustomViewGroupHandler(myDefaultHandler, null, myTagName, myClassName,
                                                     null, null, null, emptyList(), emptyList());
    @Language("XML")
    String expected =
      "<p1.p2.MyAbsoluteLayout\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\">\n" +
      "\n" +
      "</p1.p2.MyAbsoluteLayout>\n";
    assertThat(handler.getXml(myTagName, XmlType.COMPONENT_CREATION)).isEqualTo(expected);
  }

  public void testGetSpecifiedXml() throws Exception {
    setUpMyClasses(MY_LINEAR_LAYOUT);
    ViewHandler handler = new CustomViewGroupHandler(myDefaultHandler, null, myTagName, myClassName,
                                                     "<myxml/>", null, null, emptyList(), emptyList());
    assertThat(handler.getXml(myTagName, XmlType.COMPONENT_CREATION)).isEqualTo("<myxml/>");
  }

  public void testGetXmlOfInnerClass() throws Exception {
    setUpMyClasses(MY_LINEAR_LAYOUT);
    ViewHandler handler = new CustomViewGroupHandler(myDefaultHandler, null, myTagName, myClassName,
                                                     null, null, null, emptyList(), emptyList());

    @Language("XML")
    String expected =
      "<view\n" +
      "    class=\"p1.p2.MyAbsoluteLayout$MyLinearLayout\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\">\n" +
      "\n" +
      "</view>\n";
    assertThat(handler.getXml(myTagName, XmlType.COMPONENT_CREATION)).isEqualTo(expected);
  }
}
