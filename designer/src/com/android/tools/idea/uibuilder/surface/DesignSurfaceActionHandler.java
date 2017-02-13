/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class DesignSurfaceActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  private final DesignSurface mySurface;

  public DesignSurfaceActionHandler(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    SceneView sceneView = mySurface.getCurrentSceneView();
    if (sceneView == null) {
      return;
    }
    CopyPasteManager.getInstance().setContents(sceneView.getModel().getSelectionAsTransferable());
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
    performCopy(dataContext);
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

    DnDTransferItem item = getClipboardData();
    if (item == null) {
      return false;
    }
    InsertType insertType = model.determineInsertType(DragType.PASTE, item, checkOnly);
    List<NlComponent> pasted = model.createComponents(sceneView, item, insertType);
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
  private static DnDTransferItem getClipboardData() {
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
}
