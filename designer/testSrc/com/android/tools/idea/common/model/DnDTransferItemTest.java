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

import static org.junit.Assert.*;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.ui.resourcemanager.view.ResourceDragHandlerKt;
import com.android.tools.idea.ui.resourcemanager.viewmodel.ResourceDataManagerKt;
import com.intellij.ide.dnd.TransferableList;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class DnDTransferItemTest {

  @Test
  public void getTransferItem() {

    Transferable transferable = new Transferable() {


      @Override
      public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ResourceDataManagerKt.RESOURCE_URL_FLAVOR};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor == ResourceDataManagerKt.RESOURCE_URL_FLAVOR;
      }

      @NotNull
      @Override
      public Object getTransferData(DataFlavor flavor) {
        return ResourceUrl.create("namespace", ResourceType.DRAWABLE, "name");
      }
    };
    DnDTransferItem item = DnDTransferItem.getTransferItem(transferable, false);
    assertEquals("<ImageView\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"wrap_content\"\n" +
                 "    android:src=\"@namespace:drawable/name\"/>",
                 item.getComponents().get(0).getRepresentation());
  }

  @Test
  public void getLayoutTransferItem() {

    Transferable transferable = new Transferable() {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ResourceDataManagerKt.RESOURCE_URL_FLAVOR};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor == ResourceDataManagerKt.RESOURCE_URL_FLAVOR;
      }

      @NotNull
      @Override
      public Object getTransferData(DataFlavor flavor) {
        return ResourceUrl.create("namespace", ResourceType.LAYOUT, "name");
      }
    };

    DnDTransferItem item = DnDTransferItem.getTransferItem(transferable, false);
    assertEquals("<include layout=\"@namespace:layout/name\"/>",
                 item.getComponents().get(0).getRepresentation());
  }
}