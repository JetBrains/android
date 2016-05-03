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
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.model.SegmentType.*;

public class LinearLayoutHandlerTest extends AbstractViewHandlerTest {

  public void testDragNothing() throws Exception {
    surface().screen(createModel())
      .get("@id/myText1")
      .resize(TOP, RIGHT)
      .drag(0, 0)
      .release()
      .expectWidth("100dp")
      .expectHeight("100dp");
  }

  public void testCancel() throws Exception {
    surface().screen(createModel())
      .get("@id/myText1")
      .resize(TOP)
      .drag(20, 30)
      .cancel()
      .expectWidth("100dp")
      .expectHeight("100dp");
  }

  public void testDragBottomRight() throws Exception {
    surface().screen(createModel())
      .get("@id/myText1")
      .resize(BOTTOM, RIGHT)
      .drag(20, 30)
      .release()
      .expectWidth("120dp")
      .expectHeight("130dp");
  }

  public void testResizeTopLeft() throws Exception {
    surface().screen(createModel())
      .get("@id/myText1")
      .resize(TOP, LEFT)
      .drag(-20, -30)
      .release()
      .expectWidth("120dp")
      .expectHeight("130dp");
  }

  public void testDrag() throws Exception {
    surface().screen(createModel())
      .get("@id/myText1")
      .drag()
      .drag(20, 30)
      .release()
      .primary()
      .expectWidth("100dp")
      .expectHeight("100dp")
      .expectXml("<TextView\n" +
                 "            android:id=\"@id/myText1\"\n" +
                 "            android:layout_width=\"100dp\"\n" +
                 "            android:layout_height=\"100dp\"/>");
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("linear.xml",
                     component(LINEAR_LAYOUT)
                       .withBounds(0, 0, 1000, 1000)
                       .matchParentWidth()
                       .matchParentHeight()
                       .children(
                         component(TEXT_VIEW)
                           .withBounds(100, 100, 100, 100)
                           .id("@id/myText1")
                           .width("100dp")
                           .height("100dp"),
                         component(BUTTON)
                           .withBounds(100, 200, 100, 100)
                           .id("@id/myText2")
                           .width("100dp")
                           .height("100dp")
                           .withAttribute("android:layout_weight", "1.0")
                     ));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100}",
                 LayoutTestUtilities.toTree(model.getComponents()));
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(getProject()).reformat(model.getFile());
      }
    });
    return model;
  }
}