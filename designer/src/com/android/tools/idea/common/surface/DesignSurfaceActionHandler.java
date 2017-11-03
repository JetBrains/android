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
package com.android.tools.idea.common.surface;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.MacUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

public class DesignSurfaceActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  private final DesignSurface mySurface;

  public DesignSurfaceActionHandler(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    NlModel model = mySurface.getModel();
    if (model == null) {
      return;
    }
    CopyPasteManager.getInstance().setContents(
      CopyCutTransferable.createCopyTransferable(model.getSelectionAsTransferable()));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return hasNonEmptySelection();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
    {// TODO Remove this dirty fix
      // The Mac AWT copy paste manager is wrappring the Transferable into a ProxyTransferable that just delegate
      // method call to its delegate without giving access to it so it is impossible to use the CopyCutTransferable
      // For now, we just use the old way that was half broken
      //
      if (SystemInfo.isMac) {
        performCopy(dataContext);
        deleteElement(dataContext);
        return;
      }
    }

    NlModel model = mySurface.getModel();
    if (model == null) {
      return;
    }
    CopyPasteManager.getInstance().setContents(
      CopyCutTransferable.createCutTransferable(model.getSelectionAsTransferable()));
    deleteElement(dataContext);
  }

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return hasNonEmptySelection();
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    SceneView sceneView = mySurface.getCurrentSceneView();
    if (sceneView == null) {
      return;
    }
    SelectionModel selectionModel = sceneView.getSelectionModel();
    NlModel model = sceneView.getModel();
    model.delete(selectionModel.getSelection());
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return hasNonEmptySelection();
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    pasteOperation(false /* check and perform the actual paste */);
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return pasteOperation(true /* check only */);
  }

  private boolean hasNonEmptySelection() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    return sceneView != null && !sceneView.getSelectionModel().isEmpty();
  }

  private boolean pasteOperation(boolean checkOnly) {
    SceneView sceneView = mySurface.getCurrentSceneView();
    if (sceneView == null) {
      return false;
    }

    List<NlComponent> selection = sceneView.getSelectionModel().getSelection();
    NlComponent receiver = !selection.isEmpty() ? selection.get(0) : null;

    if (receiver == null) {
      // In the case where there is no selection but we only have a root component, use that one
      List<NlComponent> components = sceneView.getModel().getComponents();
      if (components.size() == 1) {
        receiver = components.get(0);
      }
    }

    if (receiver == null) {
      return false;
    }
    NlComponent before;
    NlModel model = sceneView.getModel();
    ViewHandlerManager handlerManager = ViewHandlerManager.get(model.getProject());
    ViewHandler handler = handlerManager.getHandler(receiver);
    if (handler instanceof ViewGroupHandler) {
      before = receiver.getChild(0);
    }
    else {
      before = receiver.getNextSibling();
      receiver = receiver.getParent();
      if (receiver == null) {
        return false;
      }
    }

    DnDTransferItem dndTransferItem;
    InsertType insertType;
    DragType dragType;

    if (!SystemInfo.isMac) {
      CopyCutTransferable item = getClipboardData(checkOnly);
      dndTransferItem = item != null ? item.getDndTransferItem() : null;
      if (dndTransferItem == null) {
        return false;
      }
      dragType = item.isCut() ? DragType.MOVE : DragType.PASTE;
    }
    else {
      dragType = DragType.PASTE;
      dndTransferItem = getMacClipboardData();
      if (dndTransferItem == null) {
        return false;
      }
    }
    insertType = model.determineInsertType(dragType, dndTransferItem, checkOnly);

    // TODO: support nav editor
    List<NlComponent> pasted = NlModelHelperKt.createComponents(model, sceneView, dndTransferItem, insertType);
    if (!model.canAddComponents(pasted, receiver, before)) {
      return false;
    }
    if (checkOnly) {
      return true;
    }
    model.addComponents(pasted, receiver, before, insertType);
    return true;
  }

  @Nullable
  private static CopyCutTransferable getClipboardData(boolean checkOnly) {
    CopyPasteManager instance = CopyPasteManager.getInstance();
    Transferable contents = instance.getContents();
    if (contents == null || !(contents instanceof CopyCutTransferable)) {
      return null;
    }
    CopyCutTransferable transferItem = (CopyCutTransferable)contents;

    if (!checkOnly && transferItem.isCut()) {
      // Once we used a cut item, it should behave has a copied item to avoid duplicate ids.
      // Since we can't modify the clipboard directly, we only modify the item
      // and return a copy of it.
      transferItem.consumeCut();
      return new CopyCutTransferable(transferItem.myTransferable, true);
    }
    return transferItem;
  }

  @Nullable
  private static DnDTransferItem getMacClipboardData() {
    try {
      Object data = CopyPasteManager.getInstance().getContents(ItemTransferable.DESIGNER_FLAVOR);
      if (!(data instanceof DnDTransferItem)) {
        return null;
      }
      return (DnDTransferItem)data;
    }
    catch (Exception e) {
      return null;
    }
  }

  private static class CopyCutTransferable implements Transferable {

    private final Transferable myTransferable;
    private boolean myCut;

    /**
     * Wrapper class to mark the underlying {@link Transferable} as cut if needed.
     * This is used to make a cut/paste action behave like a move and not a copy or insertion.
     */
    private CopyCutTransferable(@NotNull Transferable transferable, boolean isCut) {
      myTransferable = transferable;
      myCut = isCut;
    }

    /**
     * @see #consumeCut()
     * @see #isCut()
     */
    @NotNull
    private static CopyCutTransferable createCutTransferable(@NotNull Transferable transferable) {
      return new CopyCutTransferable(transferable, true);
    }

    @NotNull
    private static CopyCutTransferable createCopyTransferable(@NotNull Transferable transferable) {
      return new CopyCutTransferable(transferable, false);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return myTransferable.getTransferDataFlavors();
    }

    @Override
    public boolean isDataFlavorSupported(@NotNull DataFlavor flavor) {
      return myTransferable.isDataFlavorSupported(flavor);
    }

    @Override
    public Object getTransferData(@NotNull DataFlavor dataFlavor) throws UnsupportedFlavorException, IOException {
      return myTransferable.getTransferData(dataFlavor);
    }

    /**
     * @return true if the {@link Transferable} is coming from a cut action false otherwise
     */
    public boolean isCut() {
      return myCut;
    }

    /**
     * Once the cut item has been paste, if it is pasted again, it should behave as a copy/paste.
     * This method should be called to mark the cut as consumed.
     */
    private void consumeCut() {
      myCut = false;
    }

    @Nullable
    private DnDTransferItem getDndTransferItem() {
      try {
        Object data = getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        return data instanceof DnDTransferItem ? ((DnDTransferItem)data) : null;
      }
      catch (UnsupportedFlavorException | IOException e) {
        return null;
      }
    }
  }
}
