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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

public class DesignSurfaceActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  private final DesignSurface mySurface;
  private CopyPasteManager myCopyPasteManager;

  public DesignSurfaceActionHandler(@NotNull DesignSurface surface) {
    this(surface, CopyPasteManager.getInstance());
  }

  @VisibleForTesting
  DesignSurfaceActionHandler(@NotNull DesignSurface surface, @NotNull CopyPasteManager copyPasteManager) {
    mySurface = surface;
    myCopyPasteManager = copyPasteManager;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return;
    }
    if (!mySurface.getSelectionModel().isEmpty()) {
      myCopyPasteManager.setContents(mySurface.getSelectionAsTransferable());
    }
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return false;
    }
    return hasNonEmptySelection();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return false;
    }
    return true;
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return;
    }
    if (!mySurface.getSelectionModel().isEmpty()) {
      ItemTransferable transferable = mySurface.getSelectionAsTransferable();
      try {
        DnDTransferItem transferItem = (DnDTransferItem)transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        transferItem.setIsCut();
        myCopyPasteManager.setContents(transferable);
      }
      catch (UnsupportedFlavorException e) {
        performCopy(dataContext); // Fallback to simple copy/delete
      }
      deleteElement(dataContext);
    }
  }

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return false;
    }
    return hasNonEmptySelection();
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return false;
    }
    return true;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    NlModel model = mySurface.getModel();
    if (model == null) {
      return;
    }
    SelectionModel selectionModel = mySurface.getSelectionModel();
    model.delete(selectionModel.getSelection());
    selectionModel.clear();
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return hasNonEmptySelection();
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return;
    }
    pasteOperation(false /* check and perform the actual paste */);
  }

  /**
   * returns true if the action should be shown.
   */
  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return false;
    }
    // The execution of this method must be quick as it is called in regularly when updating
    // the actions' presentations
    return mySurface.getSelectionModel().getSelection().size() <= 1;
  }

  /**
   * Called by {@link com.intellij.ide.actions.PasteAction} to check if pasteOperation() should be called
   */
  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    // TODO: support nav editor
    if (!(mySurface instanceof NlDesignSurface)) {
      return false;
    }
    return pasteOperation(true /* check only */);
  }

  private boolean hasNonEmptySelection() {
    return !mySurface.getSelectionModel().isEmpty();
  }

  private boolean pasteOperation(boolean checkOnly) {
    SceneView sceneView = mySurface.getCurrentSceneView();
    if (sceneView == null) {
      return false;
    }

    List<NlComponent> selection = mySurface.getSelectionModel().getSelection();
    if(selection.size() > 1) {
      // This is aleady reflected in isPastePossible but let's ensure we
      // can' past an element if two components are selected to avoid unexpected behaviors like
      // when two ViewGroup are selected.
      return false;
    }
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

    DnDTransferItem transferItem = getClipboardData();
    if (transferItem == null) {
      return false;
    }

    DragType dragType = transferItem.isCut() ? DragType.MOVE : DragType.PASTE;
    InsertType insertType = model.determineInsertType(dragType, transferItem, checkOnly);

    // TODO: support nav editor
    List<NlComponent> pasted = NlModelHelperKt.createComponents(model, sceneView, transferItem, insertType);
    if (!model.canAddComponents(pasted, receiver, before, checkOnly)) {
      return false;
    }
    if (checkOnly) {
      return true;
    }
    transferItem.consumeCut();
    model.addComponents(pasted, receiver, before, insertType, sceneView.getSurface());
    return true;
  }

  @Nullable
  private static DnDTransferItem getClipboardData() {
    CopyPasteManager instance = CopyPasteManager.getInstance();
    Transferable contents = instance.getContents();
    if (contents == null) {
      return null;
    }
    try {
      return (DnDTransferItem)contents.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
    }
    catch (UnsupportedFlavorException | IOException e) {
      return null;
    }
  }
}
