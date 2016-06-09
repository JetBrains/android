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

import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.model.SegmentType.*;

public class AbsoluteLayoutHandlerTest extends AbstractViewHandlerTest {

  public void testDragNothing() throws Exception {
    surface().screen(createModel())
      .get("@id/myText")
      .resize(TOP, RIGHT)
      .drag(0, 0)
      .release()
      .expectWidth("100dp")
      .expectHeight("100dp");
  }

  public void testCancel() throws Exception {
    surface().screen(createModel())
      .get("@id/myText")
      .resize(TOP)
      .drag(20, 30)
      .cancel()
      .expectWidth("100dp")
      .expectHeight("100dp");
  }

  public void testDragBottomRight() throws Exception {
    surface().screen(createModel())
      .get("@id/myText")
      .resize(BOTTOM, RIGHT)
      .drag(20, 30)
      .release()
      .expectWidth("120dp")
      .expectHeight("130dp");
  }

  public void testResizeTopLeft() throws Exception {
    surface().screen(createModel())
      .get("@id/myText")
      .resize(TOP, LEFT)
      .drag(-20, -30)
      .release()
      .expectWidth("120dp")
      .expectHeight("130dp")
      .expectAttribute(ANDROID_URI, ATTR_LAYOUT_X, "80dp")
      .expectAttribute(ANDROID_URI, ATTR_LAYOUT_Y, "70dp");
  }

  public void testDrag() throws Exception {
    surface().screen(createModel())
      .get("@id/myText")
      .drag()
      .drag(20, 30)
      .release()
      .primary()
      .expectAttribute(ANDROID_URI, ATTR_LAYOUT_X, "120dp")
      .expectAttribute(ANDROID_URI, ATTR_LAYOUT_Y, "130dp")
      .expectWidth("100dp")
      .expectHeight("100dp")
      .expectXml("<TextView\n" +
                 "            android:id=\"@id/myText\"\n" +
                 "            android:layout_width=\"100dp\"\n" +
                 "            android:layout_height=\"100dp\"\n" +
                 "            android:layout_x=\"120dp\"\n" +
                 "            android:layout_y=\"130dp\"/>");
  }

  public void testDragToLayout() throws Exception {
    // Drag from one layout to another
    ModelBuilder builder = model("absolute2.xml",
                                 component(LINEAR_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .withAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL)
                                   .children(component(TEXT_VIEW).withBounds(0, 0, 1000, 150).id("@id/myText").width("100dp").height("100dp")
                                               .withAttribute("android:layout_x", "100dp").withAttribute("android:layout_y", "100dp"),
                                             component(ABSOLUTE_LAYOUT).withBounds(0, 150, 1000, 850).id("myAbsLayout").matchParentWidth()
                                               .matchParentHeight()));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,0:1000x150}\n" +
                 "    NlComponent{tag=<AbsoluteLayout>, bounds=[0,150:1000x850}",
                 LayoutTestUtilities.toTree(model.getComponents()));


    surface().screen(createModel()).get("@id/myText")
      .drag()
      .drag(50, 300) // into AbsoluteLayout child
      .release()
      .primary()
      .parent().expectXml("<AbsoluteLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                          "                xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                          "                android:layout_width=\"match_parent\"\n" +
                          "                android:layout_height=\"match_parent\">\n" +
                          "\n" +
                          "    <TextView\n" +
                          "            android:id=\"@id/myText\"\n" +
                          "            android:layout_width=\"100dp\"\n" +
                          "            android:layout_height=\"100dp\"\n" +
                          "            android:layout_x=\"150dp\"\n" +
                          "            android:layout_y=\"400dp\"/>\n" +
                          "</AbsoluteLayout>");
  }

  public void testDragToLayout2() throws Exception {
    // Drag from one layout to another
    ModelBuilder builder = model("absolute3.xml",
                                 component(LINEAR_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .withAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL)
                                   .children(component(ABSOLUTE_LAYOUT).withBounds(0, 0, 1000, 850).id("myAbsLayout").matchParentWidth()
                                               .height("0dp").withAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, VALUE_1),
                                             component(TEXT_VIEW).withBounds(0, 850, 1000, 150).id("@id/myText").width("1000px")
                                               .height("150px")));
    @Language("XML") String xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                  "              xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                  "  android:layout_width=\"match_parent\"\n" +
                                  "  android:layout_height=\"match_parent\"\n" +
                                  "  android:orientation=\"vertical\">\n" +
                                  "  <AbsoluteLayout\n" +
                                  "    android:id=\"myAbsLayout\"\n" +
                                  "    android:layout_width=\"match_parent\"\n" +
                                  "    android:layout_height=\"0dp\"\n" +
                                  "    android:layout_weight=\"1\"/>\n" +
                                  "  <TextView\n" +
                                  "    android:id=\"@id/myText\"\n" +
                                  "    android:layout_width=\"1000px\"\n" +
                                  "    android:layout_height=\"150px\"/>\n" +
                                  "</LinearLayout>\n";
    assertEquals(xml, builder.toXml());

    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<AbsoluteLayout>, bounds=[0,0:1000x850}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,850:1000x150}",
                 LayoutTestUtilities.toTree(model.getComponents()));

    surface().screen(model)
      .get("@id/myText")
      .drag()
      .drag(50, -300) // into AbsoluteLayout sibling
      .release()
      .primary().parent().parent()
      .expectXml("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "              xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "  android:layout_width=\"match_parent\"\n" +
                 "  android:layout_height=\"match_parent\"\n" +
                 "  android:orientation=\"vertical\">\n" +
                 "  <AbsoluteLayout\n" +
                 "    android:id=\"myAbsLayout\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"0dp\"\n" +
                 "    android:layout_weight=\"1\">\n" +
                 "\n" +
                 "      <TextView\n" +
                 "              android:id=\"@id/myText\"\n" +
                 "              android:layout_width=\"1000px\"\n" +
                 "              android:layout_height=\"150px\"\n" +
                 "              android:layout_x=\"50dp\"\n" +
                 "              android:layout_y=\"550dp\"/>\n" +
                 "  </AbsoluteLayout>\n" +
                 "</LinearLayout>");
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("absolute.xml",
                     component(ABSOLUTE_LAYOUT)
                       .withBounds(0, 0, 1000, 1000)
                       .matchParentWidth()
                       .matchParentHeight()
                       .children(
                         component(TEXT_VIEW)
                           .withBounds(100, 100, 100, 100)
                           .id("@id/myText")
                           .width("100dp")
                           .height("100dp")
                           .withAttribute("android:layout_x", "100dp")
                           .withAttribute("android:layout_y", "100dp")
                     ));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<AbsoluteLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}",
                 LayoutTestUtilities.toTree(model.getComponents()));

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(getProject()).reformat(model.getFile());
      }
    });
    assertEquals("<AbsoluteLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "                xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "                android:layout_width=\"match_parent\"\n" +
                 "                android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "            android:id=\"@id/myText\"\n" +
                 "            android:layout_width=\"100dp\"\n" +
                 "            android:layout_height=\"100dp\"\n" +
                 "            android:layout_x=\"100dp\"\n" +
                 "            android:layout_y=\"100dp\"/>\n" +
                 "</AbsoluteLayout>\n", model.getFile().getText());
    return model;
  }
}