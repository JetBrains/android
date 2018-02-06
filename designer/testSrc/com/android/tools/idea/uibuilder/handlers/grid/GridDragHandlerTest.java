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
package com.android.tools.idea.uibuilder.handlers.grid;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.SyncLayoutlibSceneManager;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.command.WriteCommandAction;

import java.util.Collections;
import java.util.List;

public final class GridDragHandlerTest extends LayoutTestCase {
  private GridDragHandler handler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    GridLayout viewObject = new GridLayout();
    viewObject.setVerticalAxis(new Axis(new int[]{0, 1024}));
    viewObject.setHorizontalAxis(new Axis(new int[]{0, 248, 248, 248, 248, 248, 248, 248, 248, 520, 768}));
    viewObject.setRowCount(1);
    viewObject.setColumnCount(10);

    // @formatter:off
    ComponentDescriptor button1 = component(SdkConstants.BUTTON)
      .withBounds(0, 160, 248, 96)
      .withAttribute("android:layout_row", "0")
      .withAttribute("android:layout_column", "0")
      .layoutParamsObject(new LayoutParams(new Spec(new Interval(0, 1)), new Spec(new Interval(0, 1))));

    ComponentDescriptor button2 = component(SdkConstants.BUTTON)
      .withBounds(520, 160, 248, 96)
      .withAttribute("android:layout_row", "0")
      .withAttribute("android:layout_column", "9")
      .layoutParamsObject(new LayoutParams(new Spec(new Interval(0, 1)), new Spec(new Interval(9, 10))));

    ComponentDescriptor layout = component(SdkConstants.GRID_LAYOUT)
      .withBounds(0, 160, 768, 1024)
      .viewObject(viewObject)
      .children(button1, button2);

    SyncNlModel model = model("grid_layout.xml", layout)
      .build();
    // @formatter:on

    Scene scene = new SyncLayoutlibSceneManager(model).getScene();
    scene.buildDisplayList(new DisplayList(), 0);

    List<NlComponent> components = Collections.emptyList();
    ScreenView screen = new ScreenFixture(model).getScreen();
    handler = new GridDragHandler(editor(screen), new ViewGroupHandler(), scene.getRoot(), components, DragType.CREATE);
  }

  public void testCommitCellHasChild() {
    handler.start(315, 105, 0);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> handler.commit(630, 210, 0, InsertType.MOVE_INTO));

    NlComponent[][] children = handler.getInfo().getChildren();
    NlComponent child = children[handler.getStartRow()][handler.getStartColumn()];

    assertEquals("0", child.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW));
    assertEquals("9", child.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN));
  }

  public void testCommit() {
    handler.start(315, 105, 0);
    handler.update(235, 105, 0);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      handler.commit(470, 210, 0, InsertType.MOVE_INTO);
    });

    NlComponent[][] children = handler.getInfo().getChildren();
    NlComponent child = children[handler.getStartRow()][handler.getStartColumn()];

    assertEquals("0", child.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_ROW));
    assertEquals("1", child.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_COLUMN));
  }

  private static final class GridLayout {
    @SuppressWarnings("unused") private Object mVerticalAxis;
    @SuppressWarnings("unused") private Object mHorizontalAxis;
    private int rowCount;
    private int columnCount;

    private void setVerticalAxis(Axis verticalAxis) {
      mVerticalAxis = verticalAxis;
    }

    private void setHorizontalAxis(Axis horizontalAxis) {
      mHorizontalAxis = horizontalAxis;
    }

    @SuppressWarnings("unused")
    int getRowCount() {
      return rowCount;
    }

    private void setRowCount(int rowCount) {
      this.rowCount = rowCount;
    }

    @SuppressWarnings("unused")
    int getColumnCount() {
      return columnCount;
    }

    private void setColumnCount(int columnCount) {
      this.columnCount = columnCount;
    }
  }

  private static final class Axis {
    @SuppressWarnings("unused") private final Object locations;

    private Axis(int[] locations) {
      this.locations = locations;
    }
  }

  private static final class LayoutParams {
    @SuppressWarnings("unused") final Object rowSpec;
    @SuppressWarnings("unused") final Object columnSpec;

    private LayoutParams(Spec rowSpec, Spec columnSpec) {
      this.rowSpec = rowSpec;
      this.columnSpec = columnSpec;
    }
  }

  private static final class Spec {
    @SuppressWarnings("unused") private final Object span;

    private Spec(Interval span) {
      this.span = span;
    }
  }

  private static final class Interval {
    @SuppressWarnings("unused") private final int min;
    @SuppressWarnings("unused") private final int max;

    private Interval(int min, int max) {
      this.min = min;
      this.max = max;
    }
  }
}
