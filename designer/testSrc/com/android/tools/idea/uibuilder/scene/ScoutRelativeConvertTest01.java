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
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.scout.ScoutDirectConvert;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 *  Test a simple horizontal spread inside chain
 */
public class ScoutRelativeConvertTest01 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(RELATIVE_LAYOUT)
                   .id("@+id/content_main")
                   .withBounds(0, 0, 720, 1024)
                   .width("360dp")
                   .height("512dp")
                   .children(

                     component(TEXT_VIEW)
                       .id("@+id/a")
                       .withBounds(262, 8, 98, 34)
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_ALIGN_PARENT_LEFT,VALUE_TRUE)
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_ALIGN_PARENT_START,VALUE_TRUE)
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_ALIGN_PARENT_TOP,VALUE_TRUE)
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_LEFT,"50dp")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_TOP,"84dp")
                       .width("wrap_content")
                       .height("wrap_content"),
                     component(TEXT_VIEW)
                       .id("@+id/b")
                       .withBounds(361, 8, 98, 34)
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_ALIGN_END,"@+id/a")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_ALIGN_RIGHT,"@+id/a")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_BELOW,"@+id/a")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_TOP,"17dp")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_RIGHT,"40dp")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_END,"40dp")
                       .width("wrap_content")
                       .height("wrap_content"),
                     component(TEXT_VIEW)
                       .id("@+id/d")
                       .withBounds(459, 8, 253, 34)

                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_BELOW,"@+id/b")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_TO_RIGHT_OF,"@+id/b")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_TO_END_OF,"@+id/b")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_LEFT,"0dp")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_START,"0dp")
                       .withAttribute(ANDROID_URI,ATTR_LAYOUT_MARGIN_END,"40dp")
                       .width("wrap_content")
                       .height("wrap_content")
                   ));
  }

  public void testRTLScene() {
    myScreen.get("@+id/content_main")
      .expectXml("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "  android:id=\"@+id/content_main\"\n" +
                 "  android:layout_width=\"360dp\"\n" +
                 "  android:layout_height=\"512dp\">\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:id=\"@+id/a\"\n" +
                 "    android:layout_alignParentLeft=\"true\"\n" +
                 "    android:layout_alignParentStart=\"true\"\n" +
                 "    android:layout_alignParentTop=\"true\"\n" +
                 "    android:layout_marginLeft=\"50dp\"\n" +
                 "    android:layout_marginTop=\"84dp\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:id=\"@+id/b\"\n" +
                 "    android:layout_alignEnd=\"@+id/a\"\n" +
                 "    android:layout_alignRight=\"@+id/a\"\n" +
                 "    android:layout_below=\"@+id/a\"\n" +
                 "    android:layout_marginTop=\"17dp\"\n" +
                 "    android:layout_marginRight=\"40dp\"\n" +
                 "    android:layout_marginEnd=\"40dp\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"/>\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:id=\"@+id/d\"\n" +
                 "    android:layout_below=\"@+id/b\"\n" +
                 "    android:layout_toRightOf=\"@+id/b\"\n" +
                 "    android:layout_toEndOf=\"@+id/b\"\n" +
                 "    android:layout_marginLeft=\"0dp\"\n" +
                 "    android:layout_marginStart=\"0dp\"\n" +
                 "    android:layout_marginEnd=\"40dp\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"/>\n" +
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
                 "  android:layout_height=\"512dp\">\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:id=\"@+id/a\"\n" +
                 "      android:layout_marginLeft=\"50dp\"\n" +
                 "    android:layout_marginTop=\"84dp\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "      app:layout_constraintLeft_toLeftOf=\"parent\" />\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:id=\"@+id/b\"\n" +
                 "      android:layout_marginTop=\"17dp\"\n" +
                 "    android:layout_marginRight=\"40dp\"\n" +
                 "    android:layout_marginEnd=\"40dp\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "      app:layout_constraintTop_toBottomOf=\"@+id/a\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"@+id/a\"\n" +
                 "      app:layout_constraintRight_toRightOf=\"@+id/a\" />\n" +
                 "\n" +
                 "  <TextView\n" +
                 "    android:id=\"@+id/d\"\n" +
                 "      android:layout_marginLeft=\"40dp\"\n" +
                 "    android:layout_marginStart=\"40dp\"\n" +
                 "    android:layout_marginEnd=\"40dp\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "      app:layout_constraintLeft_toRightOf=\"@+id/b\"\n" +
                 "      app:layout_constraintTop_toBottomOf=\"@+id/b\"\n" +
                 "      app:layout_constraintStart_toEndOf=\"@+id/b\" />\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>");
  }
}