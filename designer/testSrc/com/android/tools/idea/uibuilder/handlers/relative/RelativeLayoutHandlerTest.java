/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.relative;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public class RelativeLayoutHandlerTest extends LayoutTestCase {

  public void testMoveDoesNotReorderComponents() throws Exception {
    //noinspection XmlUnusedNamespaceDeclaration
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(10, 10)
      .release()
      .primary()
      .parent()
      .expectXml("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\" />\n" +
                 "\n" +
                 "    <CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"110dp\"\n" +
                 "        android:layout_marginTop=\"110dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_below=\"@id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@id/checkbox\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_marginTop=\"80dp\" />\n" +
                 "</RelativeLayout>");
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/button")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_alignParentTop", "true")
                                       .withAttribute("android:layout_alignParentLeft", "true")
                                       .withAttribute("android:layout_alignParentStart", "true")
                                       .withAttribute("android:layout_marginTop", "100dp")
                                       .withAttribute("android:layout_marginLeft", "100dp")
                                       .withAttribute("android:layout_marginStart", "100dp"),

                                     component(CHECK_BOX)
                                       .withBounds(300, 300, 20, 20)
                                       .id("@id/checkbox")
                                       .width("20dp")
                                       .height("20dp")
                                       .withAttribute("android:layout_below", "@id/button")
                                       .withAttribute("android:layout_toRightOf", "@id/button")
                                       .withAttribute("android:layout_marginLeft", "100dp")
                                       .withAttribute("android:layout_marginTop", "100dp"),

                                     component(TEXT_VIEW)
                                       .withBounds(400, 400, 100, 100)
                                       .id("@id/textView")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_below", "@id/checkbox")
                                       .withAttribute("android:layout_toRightOf", "@id/checkbox")
                                       .withAttribute("android:layout_marginLeft", "80dp")
                                       .withAttribute("android:layout_marginTop", "80dp")
                                   ));
    NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
                 "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[400,400:100x100}",
                 LayoutTestUtilities.toTree(model.getComponents()));

    format(model.getFile());
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\" />\n" +
                 "\n" +
                 "    <CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\" />\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_below=\"@id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@id/checkbox\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_marginTop=\"80dp\" />\n" +
                 "</RelativeLayout>\n", model.getFile().getText());
    return model;
  }
}
