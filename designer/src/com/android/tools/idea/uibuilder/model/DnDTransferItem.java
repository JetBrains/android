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
package com.android.tools.idea.uibuilder.model;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.IOException;

public class DnDTransferItem {
  private final boolean myFromPalette;
  private final long myModelId;
  private final ImmutableList<DnDTransferComponent> myComponents;

  /**
   * Create a drag and drop item for a new component from the palette.
   */
  public DnDTransferItem(@NotNull DnDTransferComponent component) {
    this(true, 0, ImmutableList.of(component));
  }

  /**
   * Create a drag and drop item for existing designer components.
   */
  public DnDTransferItem(long modelId, @NotNull ImmutableList<DnDTransferComponent> components) {
    this(false, modelId, components);
  }

  private DnDTransferItem(boolean fromPalette, long modelId, @NotNull ImmutableList<DnDTransferComponent> components) {
    myFromPalette = fromPalette;
    myModelId = modelId;
    myComponents = components;
  }

  @Nullable
  public static DnDTransferItem getTransferItem(@NotNull Transferable transferable, boolean allowPlaceholder) {
    DnDTransferItem item = null;
    try {
      if (transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
        item = (DnDTransferItem)transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
      }
      else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        String xml = (String)transferable.getTransferData(DataFlavor.stringFlavor);
        if (!StringUtil.isEmpty(xml)) {
          item = new DnDTransferItem(new DnDTransferComponent("", xml, 200, 100));
        }
      }
    }
    catch (InvalidDnDOperationException ex) {
      if (!allowPlaceholder) {
        return null;
      }
      String defaultXml = "<placeholder xmlns:android=\"http://schemas.android.com/apk/res/android\"/>";
      item = new DnDTransferItem(new DnDTransferComponent("", defaultXml, 200, 100));
    }
    catch (IOException | UnsupportedFlavorException ex) {
      Logger.getInstance(DnDTransferItem.class).warn(ex);
    }
    return item;
  }

  public boolean isFromPalette() {
    return myFromPalette;
  }

  public long getModelId() {
    return myModelId;
  }

  public ImmutableList<DnDTransferComponent> getComponents() {
    return myComponents;
  }
}
