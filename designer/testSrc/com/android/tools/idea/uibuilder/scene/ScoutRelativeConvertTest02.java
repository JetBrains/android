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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.scout.ScoutDirectConvert;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Test a simple horizontal spread inside chain
 */
public class ScoutRelativeConvertTest02 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(RELATIVE_LAYOUT)
                   .id("@+id/content_main")
                   .withBounds(0, 0, 720, 1024)
                   .width("360dp")
                   .height("512dp")
                   .withAttribute(ANDROID_URI, ATTR_PADDING_BOTTOM, "5dp")
                   .withAttribute(ANDROID_URI, ATTR_PADDING_LEFT, "7dp")
                   .withAttribute(ANDROID_URI, ATTR_PADDING_RIGHT, "11dp")
                   .withAttribute(ANDROID_URI, ATTR_PADDING_TOP, "13dp")
                   .children(
                     component(LINEAR_LAYOUT)
                       .id("@+id/layout1")
                       .withBounds(361, 8, 98, 34)
                       .width("wrap_content")
                       .height("wrap_content")
                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_TOP, VALUE_TRUE)
                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_START, VALUE_TRUE)
                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, "56dp")
                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, "53dp")
                       .children(
                         component(TEXT_VIEW)
                           .id("@+id/textView4")
                           .withBounds(361, 8, 98, 34)
                           .withAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, "1")
                           .withAttribute(ANDROID_URI, ATTR_TEXT, "TextView1")
                           .width("wrap_content")
                           .height("wrap_content"),
                         component(TEXT_VIEW)
                           .id("@+id/textView4")
                           .withBounds(361, 8, 98, 34)
                           .withAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, "1")
                           .withAttribute(ANDROID_URI, ATTR_INPUT_TYPE, "textPersonName")
                           .withAttribute(ANDROID_URI, ATTR_TEXT, "Name")
                           .withAttribute(ANDROID_URI, ATTR_EMS, "10")
                           .width("wrap_content")
                           .height("wrap_content")
                       ),
                     component(LINEAR_LAYOUT)
                       .id("@+id/layout2")
                       .withBounds(361, 200, 98, 34)
                       .width("wrap_content")
                       .height("wrap_content")
                       .withAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_HORIZONTAL)
                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, "@+id/layout1")
                       .children(
                         component(TEXT_VIEW)
                           .id("@+id/TextView2")
                           .withBounds(361, 200, 98, 34)
                           .withAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, "1")
                           .withAttribute(ANDROID_URI, ATTR_TEXT, "TextView1")
                           .width("wrap_content")
                           .height("wrap_content"),
                         component(TEXT_VIEW)
                           .id("@+id/editText8")
                           .withBounds(361, 200, 98, 34)
                           .withAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, "1")
                           .withAttribute(ANDROID_URI, ATTR_INPUT_TYPE, "textPersonName")
                           .withAttribute(ANDROID_URI, ATTR_TEXT, "Name")
                           .withAttribute(ANDROID_URI, ATTR_EMS, "10")
                           .width("wrap_content")
                           .height("wrap_content")
                       )
                   ));
  }

  public void testRTLScene() {
    myScreen.get("@+id/content_main")
      .expectXml("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "  android:id=\"@+id/content_main\"\n" +
                 "  android:layout_width=\"360dp\"\n" +
                 "  android:layout_height=\"512dp\"\n" +
                 "  android:paddingBottom=\"5dp\"\n" +
                 "  android:paddingLeft=\"7dp\"\n" +
                 "  android:paddingRight=\"11dp\"\n" +
                 "  android:paddingTop=\"13dp\">\n" +
                 "\n" +
                 "  <LinearLayout\n" +
                 "    android:id=\"@+id/layout1\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:layout_alignParentTop=\"true\"\n" +
                 "    android:layout_alignParentStart=\"true\"\n" +
                 "    android:layout_marginStart=\"56dp\"\n" +
                 "    android:layout_marginTop=\"53dp\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/textView4\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:text=\"TextView1\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/textView4\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:inputType=\"textPersonName\"\n" +
                 "      android:text=\"Name\"\n" +
                 "      android:ems=\"10\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "</LinearLayout>\n" +
                 "\n" +
                 "  <LinearLayout\n" +
                 "    android:id=\"@+id/layout2\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:orientation=\"horizontal\"\n" +
                 "    android:layout_below=\"@+id/layout1\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/TextView2\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:text=\"TextView1\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/editText8\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:inputType=\"textPersonName\"\n" +
                 "      android:text=\"Name\"\n" +
                 "      android:ems=\"10\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "</LinearLayout>\n" +
                 "\n" +
                 "</RelativeLayout>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    WriteCommandAction.runWriteCommandAction(myFacet.getModule().getProject(), () -> {
      ScoutDirectConvert.directProcess(myModel.getComponents().get(0));
    });
    myScreen.get("@+id/content_main")
      .expectXml("<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:id=\"@+id/content_main\"\n" +
                 "  android:layout_width=\"360dp\"\n" +
                 "  android:layout_height=\"512dp\"\n" +
                 "  android:paddingBottom=\"5dp\"\n" +
                 "  android:paddingLeft=\"7dp\"\n" +
                 "  android:paddingRight=\"11dp\"\n" +
                 "  android:paddingTop=\"13dp\">\n" +
                 "\n" +
                 "  <LinearLayout\n" +
                 "    android:id=\"@+id/layout1\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "      android:layout_marginStart=\"56dp\"\n" +
                 "    android:layout_marginTop=\"53dp\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/textView4\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:text=\"TextView1\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/textView4\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:inputType=\"textPersonName\"\n" +
                 "      android:text=\"Name\"\n" +
                 "      android:ems=\"10\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "</LinearLayout>\n" +
                 "\n" +
                 "  <LinearLayout\n" +
                 "    android:id=\"@+id/layout2\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:orientation=\"horizontal\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "      app:layout_constraintTop_toBottomOf=\"@+id/layout1\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/TextView2\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:text=\"TextView1\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "    <TextView\n" +
                 "      android:id=\"@+id/editText8\"\n" +
                 "      android:layout_weight=\"1\"\n" +
                 "      android:inputType=\"textPersonName\"\n" +
                 "      android:text=\"Name\"\n" +
                 "      android:ems=\"10\"\n" +
                 "      android:layout_width=\"wrap_content\"\n" +
                 "      android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "</LinearLayout>\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>");
  }
}
