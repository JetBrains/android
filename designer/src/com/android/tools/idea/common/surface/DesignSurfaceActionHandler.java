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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.*;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

public abstract class DesignSurfaceActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  protected final DesignSurface<?> mySurface;
  private CopyPasteManager myCopyPasteManager;

  public DesignSurfaceActionHandler(@NotNull DesignSurface<?> surface) {
    this(surface, CopyPasteManager.getInstance());
  }

  @NotNull
  protected abstract DataFlavor getFlavor();

  protected DesignSurfaceActionHandler(@NotNull DesignSurface<?> surface, @NotNull CopyPasteManager copyPasteManager) {
    mySurface = surface;
    myCopyPasteManager = copyPasteManager;
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    if (!mySurface.getSelectionModel().isEmpty()) {
      myCopyPasteManager.setContents(mySurface.getSelectionAsTransferable());
    }
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
    if (!mySurface.getSelectionModel().isEmpty()) {
      ItemTransferable transferable = mySurface.getSelectionAsTransferable();
      try {
        DnDTransferItem transferItem = (DnDTransferItem)transferable.getTransferData(getFlavor());
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
    return hasNonEmptySelection();
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
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

  @Nullable
  @VisibleForTesting
  public abstract NlComponent getPasteTarget();

  @VisibleForTesting
  public abstract boolean canHandleChildren(@NotNull NlComponent component,
                                     @NotNull List<NlComponent> pasted);

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    pasteOperation(false /* check and perform the actual paste */);
  }

  /**
   * returns true if the action should be shown.
   */
  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return getPasteTarget() != null && getClipboardData() != null;
  }

  /**
   * Called by {@link com.intellij.ide.actions.PasteAction} to check if pasteOperation() should be called
   */
  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return pasteOperation(true /* check only */);
  }

  private boolean hasNonEmptySelection() {
    return !mySurface.getSelectionModel().isEmpty();
  }

  private boolean pasteOperation(boolean checkOnly) {
    NlComponent receiver = getPasteTarget();
    if (receiver == null) {
      return false;
    }
    NlModel model = receiver.getModel();

    DnDTransferItem transferItem = getClipboardData();
    if (transferItem == null) {
      return false;
    }

    DragType dragType = transferItem.isCut() ? DragType.MOVE : DragType.PASTE;
    InsertType insertType = model.determineInsertType(dragType, transferItem, checkOnly);

    List<NlComponent> pasted = model.createComponents(transferItem, insertType);

    NlComponent before = null;
    if (canHandleChildren(receiver, pasted)) {
      before = receiver.getChild(0);
    }
    else {
      while (!canHandleChildren(receiver, pasted)) {
        before = receiver.getNextSibling();
        receiver = receiver.getParent();
        if (receiver == null) {
          return false;
        }
      }
    }

    if (!model.canAddComponents(pasted, receiver, before, checkOnly)) {
      return false;
    }
    if (checkOnly) {
      return true;
    }
    transferItem.consumeCut();
    UtilsKt.addComponentsAndSelectedIfCreated(model, pasted, receiver, before, insertType, mySurface.getSelectionModel());
    if (insertType == InsertType.PASTE) {
      mySurface.getSelectionModel().setSelection(pasted);
    }
    return true;
  }

  @Nullable
  private DnDTransferItem getClipboardData() {
    Transferable contents = myCopyPasteManager.getContents();
    return contents != null ? DnDTransferItem.getTransferItem(contents, false) : null;
  }
}
