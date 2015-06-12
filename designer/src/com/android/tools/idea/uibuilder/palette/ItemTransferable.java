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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.NonNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

public class ItemTransferable implements Transferable {
  public static final DataFlavor PALETTE_FLAVOR = new DataFlavor(DnDTransferItem.class, "Palette Item");

  private final DnDTransferItem myItem;

  public ItemTransferable(@NonNull DnDTransferItem item) {
    myItem = item;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[]{PALETTE_FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor dataFlavor) {
    return PALETTE_FLAVOR.equals(dataFlavor);
  }

  @Override
  public Object getTransferData(DataFlavor dataFlavor) throws UnsupportedFlavorException {
    if (PALETTE_FLAVOR.equals(dataFlavor)) {
      return myItem;
    }
    throw new UnsupportedFlavorException(dataFlavor);
  }
}
