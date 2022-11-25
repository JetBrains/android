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
package com.android.tools.idea.uibuilder.handlers.common;

import com.android.SdkConstants;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.GuiInputHandler;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.common.LayoutTestUtilities;
import com.google.common.collect.ImmutableList;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;

public class CommonDragHandlerTest extends LayoutTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StudioFlags.NELE_DRAG_PLACEHOLDER.override(true);
  }

  @Override
  protected void tearDown() throws Exception {
    StudioFlags.NELE_DRAG_PLACEHOLDER.clearOverride();
    super.tearDown();
  }

  public void testDragFromTree() {
    SyncNlModel model = model("model.xml",
                              component(SdkConstants.LINEAR_LAYOUT)
                                .withBounds(0, 0, 100, 100)
                                .id("@+id/outer")
                                .children(
                                  component(SdkConstants.BUTTON)
                                    .withBounds(0, 0, 10, 10)
                                    .id("@+id/button"),
                                  component(SdkConstants.LINEAR_LAYOUT)
                                    .withBounds(10, 0, 90, 100)
                                    .id("@+id/inner")
                                    .children(
                                      component(SdkConstants.TEXT_VIEW)
                                        .withBounds(10, 0, 10, 10)
                                        .id("@+id/textView1"),
                                      component(SdkConstants.TEXT_VIEW)
                                        .withBounds(20, 0, 10, 10)
                                        .id("@+id/textView2")
                                    )
                                )).build();
    NlComponent button = model.find("button");
    DesignSurface<?> surface = LayoutTestUtilities.createScreen(model).getSurface();
    surface.getScene().buildDisplayList(new DisplayList(), 0);
    surface.getSelectionModel().setSelection(ImmutableList.of(button));
    surface.setModel(model);
    Transferable transferable = surface.getSelectionAsTransferable();
    GuiInputHandler manager = surface.getGuiInputHandler();
    manager.startListening();
    LayoutTestUtilities.dragDrop(manager, 0, 0, 40, 0, transferable, DnDConstants.ACTION_MOVE);
    assertEquals(3, model.find("inner").getChildCount());
    assertEquals("button", model.find("inner").getChild(2).getId());
    assertEquals(1, model.find("outer").getChildCount());
  }
}
