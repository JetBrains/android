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
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.RECYCLER_VIEW;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class RecyclerViewHandlerTest extends LayoutTestCase {
  private int selectionUpdateCount;

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
    assertThat(selectionUpdateCount).isAtLeast(1);
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

    // RecyclerViewHandler.createDragHandler() returns null i.e. the RecyclerView does not accept
    // views being dragged from the palette.
    // [Notice: this test does not drag components from the palette, but underlying the drag logic
    //  is currently used for dragging components from the palette.]

    // It is an error if the selection model reported a new selection after dropping this component
    // on the RecyclerView.
    assertEquals(0, selectionUpdateCount);
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
    SyncNlModel model = builder.build();
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    selectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
        selectionUpdateCount++;
      }
    });
    return model;
  }
}
