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

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking;
import com.android.tools.idea.ui.resourcemanager.model.ResourceDataManagerKt;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.IOException;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DnDTransferItem {
  private final boolean myFromPalette;
  private final long myModelId;
  private final ImmutableList<DnDTransferComponent> myComponents;
  private final ImmutableList<String> myReferences;
  private boolean myIsCut;

  /**
   * Create a drag and drop item for a new component from the palette.
   */
  public DnDTransferItem(@NotNull DnDTransferComponent component) {
    this(true, 0, ImmutableList.of(component), ImmutableList.of());
  }

  /**
   * Create a drag and drop item for existing designer components.
   */
  public DnDTransferItem(long modelId, @NotNull ImmutableList<DnDTransferComponent> components) {
    this(false, modelId, components, ImmutableList.of());
  }

  /**
   * Create a drag and drop item for existing designer components and component references.
   *
   * Component references are represented by a list of component ids. A drop will add these ids to the "constraint_referenced_ids"
   * of the component they are dropped on.
   */
  public DnDTransferItem(long modelId, @NotNull ImmutableList<DnDTransferComponent> components, @NotNull ImmutableList<String> references) {
    this(false, modelId, components, references);
  }

  private DnDTransferItem(
    boolean fromPalette,
    long modelId,
    @NotNull ImmutableList<DnDTransferComponent> components,
    @NotNull ImmutableList<String> references
  ) {
    myFromPalette = fromPalette;
    myModelId = modelId;
    myComponents = components;
    myReferences = references;
  }

  /**
   * Create a [DndTransferItem] that holds all components and references from the current [DndTransferItem] and [other].
   *
   * This is used when multiple items are selected in the component tree.
   */
  @NotNull
  public DnDTransferItem merge(@NotNull DnDTransferItem other) {
    return new DnDTransferItem(
      myFromPalette,
      myModelId,
      ImmutableList.<DnDTransferComponent>builder().addAll(myComponents).addAll(other.myComponents).build(),
      ImmutableList.<String>builder().addAll(myReferences).addAll(other.myReferences).build()
    );
  }

  @Nullable
  public static DnDTransferItem getTransferItem(@NotNull Transferable transferable, boolean allowPlaceholder) {
    try {
      if (transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
        return (DnDTransferItem)transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
      }

      if (transferable.isDataFlavorSupported(ResourceDataManagerKt.RESOURCE_URL_FLAVOR)) {
        ResourceUrl url = (ResourceUrl)transferable.getTransferData(ResourceDataManagerKt.RESOURCE_URL_FLAVOR);
        DnDTransferItem item = fromResourceUrl(url);
        if (item != null) {
          return item;
        }
      }

      if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        String xml = (String)transferable.getTransferData(DataFlavor.stringFlavor);
        if (!StringUtil.isEmpty(xml)) {
          return new DnDTransferItem(new DnDTransferComponent("", xml, 200, 100));
        }
      }
    }
    catch (InvalidDnDOperationException ex) {
      if (!allowPlaceholder) {
        return null;
      }
      String defaultXml = "<placeholder xmlns:android=\"http://schemas.android.com/apk/res/android\"/>";
      return new DnDTransferItem(new DnDTransferComponent("", defaultXml, 200, 100));
    }
    catch (IOException | UnsupportedFlavorException ex) {
      Logger.getInstance(DnDTransferItem.class).warn(ex);
    }
    return null;
  }

  @Nullable
  private static DnDTransferItem fromResourceUrl(@NotNull ResourceUrl url) {
    String representation;
    String tag;
    ResourceManagerTracking.INSTANCE.logDragOnViewGroup(url.type);
    // TODO(caen) Delegate this to the view Handlers
    if (url.type == ResourceType.LAYOUT) {
      representation = String.format("<include layout=\"%s\"/>", url.toString());
      tag = SdkConstants.TAG_INCLUDE;
    }
    else if (url.type == ResourceType.COLOR || url.type == ResourceType.DRAWABLE || url.type == ResourceType.MIPMAP) {
      String size = url.type == ResourceType.COLOR ? "50dp" : "wrap_content";

      @Language("XML")
      String xml = "<ImageView\n" +
                   "    android:layout_width=\"%1$s\"\n" +
                   "    android:layout_height=\"%1$s\"\n" +
                   "    android:src=\"%2$s\"/>";
      representation = String.format(xml, size, url.toString());

      tag = SdkConstants.IMAGE_VIEW;
    }
    else {
      return null;
    }
    return new DnDTransferItem(new DnDTransferComponent(tag, representation, 100, 100));
  }

  public boolean isFromPalette() {
    return myFromPalette;
  }

  public long getModelId() {
    return myModelId;
  }

  public void setIsCut() {
    myIsCut = true;
  }

  public boolean isCut() {
    return myIsCut;
  }

  public void consumeCut() {
    myIsCut = false;
  }

  public ImmutableList<DnDTransferComponent> getComponents() {
    return myComponents;
  }

  public ImmutableList<String> getReferences() {
    return myReferences;
  }
}
