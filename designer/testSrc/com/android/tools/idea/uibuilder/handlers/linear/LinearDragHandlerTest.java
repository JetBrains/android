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
package com.android.tools.idea.uibuilder.handlers.linear;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.GuiInputHandler;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.common.LayoutTestUtilities;
import com.google.common.collect.ImmutableList;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;

import static org.mockito.Mockito.mock;

public class LinearDragHandlerTest extends LayoutTestCase {

  /**
   * Simulate a drag of a component in an outer LinearLayout from the
   * component tree to an inner linar layout.
   *
   * The dragged compoenent should have been deleted from the outer
   * linear layout and added into the inner one.
   */
  public void testDragFromTree() {
    SyncNlModel model = model("model.xml",
                              component("LinearLayout")
                                .id("@+id/outer")
                                .withBounds(0, 0, 100, 100)
                                .children(
                                  component("Button")
                                    .id("@+id/button")
                                    .withBounds(0, 0, 10, 10),
                                  component("LinearLayout")
                                    .viewObject(linearLayout())
                                    .id("@+id/inner")
                                    .withBounds(10, 0, 90, 100)
                                    .children(component("TextView")
                                                .withBounds(10, 0, 10, 10),
                                              component("TextView")
                                                .withBounds(20, 0, 10, 10)))).build();
    NlComponent button = model.find("button");
    DesignSurface<?> surface = LayoutTestUtilities.createScreen(model).getSurface();
    surface.getScene().buildDisplayList(new DisplayList(), 0);
    surface.getSelectionModel().setSelection(ImmutableList.of(button));
    surface.setModel(model);
    Transferable transferable = surface.getSelectionAsTransferable();
    GuiInputHandler manager = surface.getGuiInputHandler();
    manager.startListening();
    LayoutTestUtilities.dragDrop(manager, 0, 0, 13, 0, transferable, DnDConstants.ACTION_MOVE);
    assertEquals(3, model.find("inner").getChildCount());
    assertEquals("button", model.find("inner").getChild(1).getId());
    assertEquals(1, model.find("outer").getChildCount());
  }

  private static android.widget.LinearLayout linearLayout() {
    return mock(android.widget.LinearLayout.class);
  }
}