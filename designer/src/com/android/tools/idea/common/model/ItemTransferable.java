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
package com.android.tools.idea.common.model;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import org.jetbrains.annotations.NotNull;

public class ItemTransferable implements Transferable {
  public static final DataFlavor DESIGNER_FLAVOR = new DataFlavor(DnDTransferComponent.class, "Designer Item");
  public static final DataFlavor NAV_FLAVOR = new DataFlavor(DnDTransferComponent.class, "Nav Editor Item");

  private final DnDTransferItem myItem;

  public ItemTransferable(@NotNull DnDTransferItem item) {
    myItem = item;
  }

  @NotNull
  public ItemTransferable merge(@NotNull ItemTransferable other) {
    return new ItemTransferable(myItem.merge(other.myItem));
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[]{DESIGNER_FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor dataFlavor) {
    return DESIGNER_FLAVOR.equals(dataFlavor);
  }

  @Override
  public Object getTransferData(DataFlavor dataFlavor) throws UnsupportedFlavorException {
    if (DESIGNER_FLAVOR.equals(dataFlavor)) {
      return myItem;
    }
    throw new UnsupportedFlavorException(dataFlavor);
  }
}
