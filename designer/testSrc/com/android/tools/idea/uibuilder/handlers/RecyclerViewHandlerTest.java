/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.ANDROIDX_RECYCLER_VIEW_ARTIFACT;
import static com.android.SdkConstants.BUTTON;
import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import org.jetbrains.annotations.NotNull;

public class RecyclerViewHandlerTest extends LayoutTestCase {

  public void testXmlForDragPreview() {
    RecyclerViewHandler handler = new RecyclerViewHandler();
    assertThat(handler.getXml(RECYCLER_VIEW.newName(), XmlType.DRAG_PREVIEW)).isEqualTo(
      "<androidx.recyclerview.widget.RecyclerView\n" +
      "    android:layout_width=\"wrap_content\"\n" +
      "    android:layout_height=\"wrap_content\" />\n"
    );
  }

  public void testDrag() {
    ScreenFixture screen = screen(createModel());
    screen
      .get("@id/myButton")
      .drag()
      .drag(10, 10)
      .release();

    screen
      .get("@id/myView")
      .expectXml("<android.support.v7.widget.RecyclerView\n" +
                 "        android:id=\"@id/myView\"\n" +
                 "        android:layout_width=\"980dp\"\n" +
                 "        android:layout_height=\"980dp\" />");
  }

  public void testDragIntoRecyclerView() {
    ScreenFixture screen = screen(createModel());
    screen
      .get("@id/myButton")
      .drag()
      .drag(0, -300)
      .release();

    screen
      .get("@id/myView")
      .expectXml("<android.support.v7.widget.RecyclerView\n" +
                 "    android:id=\"@id/myView\"\n" +
                 "    android:layout_width=\"980dp\"\n" +
                 "    android:layout_height=\"980dp\"/>");

    // Should not able to drag into RecyclerView
    assertEmpty(screen.get("@id/myView").getSceneComponent().getChildren());
  }

  public void testGetGradleCoordinateId() {
    RecyclerViewHandler handler = new RecyclerViewHandler();
    assertThat(handler.getGradleCoordinateId(RECYCLER_VIEW.oldName()).toString()).isEqualTo(RECYCLER_VIEW_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(RECYCLER_VIEW.newName()).toString()).isEqualTo(ANDROIDX_RECYCLER_VIEW_ARTIFACT);
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = model("linear.xml",
                                 component(ABSOLUTE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(RECYCLER_VIEW.oldName())
                                       .withBounds(10, 10, 880, 880)
                                       .id("@id/myView")
                                       .width("980dp")
                                       .height("980dp"),
                                     component(BUTTON)
                                       .withBounds(10, 910, 50, 50)
                                       .id("@id/myButton")
                                       .width("50dp")
                                       .height("50dp")
                                   ));
    return builder.build();
  }
}
