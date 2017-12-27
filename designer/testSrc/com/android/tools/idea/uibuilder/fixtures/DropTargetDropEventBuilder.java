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
package com.android.tools.idea.uibuilder.fixtures;

import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.adtui.common.SwingCoordinate;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDropEvent;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DropTargetDropEventBuilder {
  private final DropTargetContext myDropTargetContext;
  private final int myX;
  private final int myY;
  private final Transferable myTransferable;
  private Object mySource = LayoutTestUtilities.class;
  private int mySourceActions = DnDConstants.ACTION_COPY_OR_MOVE;
  private int myDropAction = DnDConstants.ACTION_COPY;

  public DropTargetDropEventBuilder(DropTargetContext dropTargetContext,
                                    @SwingCoordinate int x,
                                    @SwingCoordinate int y,
                                    Transferable transferable) {
    myDropTargetContext = dropTargetContext;
    myX = x;
    myY = y;
    myTransferable = transferable;
  }

  public DropTargetDropEventBuilder withSource(Object source) {
    mySource = source;
    return this;
  }

  public DropTargetDropEventBuilder withSourceActions(int actions) {
    mySourceActions = actions;
    return this;
  }

  public DropTargetDropEventBuilder withDropAction(int action) {
    myDropAction = action;
    return this;
  }

  public DropTargetDropEvent build() {
    DropTargetDropEvent event = mock(DropTargetDropEvent.class);
    when(event.getSource()).thenReturn(mySource);
    when(event.getLocation()).thenReturn(new Point(myX, myY));
    when(event.getDropTargetContext()).thenReturn(myDropTargetContext);
    when(event.getDropAction()).thenReturn(myDropAction);
    when(event.getSourceActions()).thenReturn(mySourceActions);
    when(event.getTransferable()).thenReturn(myTransferable);
    DataFlavor[] flavors = myTransferable.getTransferDataFlavors();
    when(event.getCurrentDataFlavors()).thenReturn(flavors);
    when(event.getCurrentDataFlavorsAsList()).thenReturn(Arrays.asList(flavors));
    return event;
  }
}
