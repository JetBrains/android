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
package com.android.tools.idea.uibuilder.structure;

import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.ChangeType;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.NlTreeWriter;
import com.android.tools.idea.common.model.NlTreeWriterKt;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlDropEvent;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Point;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enable drop of components dragged onto the component tree.
 * Both internal moves and drags from the palette and other structure panes are supported.
 */
public class NlDropListener extends DropTargetAdapter {

  /**
   * Attributes that can safely be copied when morphing the view
   */
  private static final HashSet<String> ourCopyableAttributes = new HashSet<>(Arrays.asList(
    ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_ID, ATTR_BACKGROUND
  ));

  private final List<NlComponent> myDragged;
  private final NlComponentTree myTree;
  private DnDTransferItem myTransferItem;
  private NlComponent myDragReceiver;
  private NlComponent myNextDragSibling;
  private final NlDropInsertionPicker myInsertionPicker;

  public NlDropListener(@NotNull NlComponentTree tree) {
    myDragged = new ArrayList<>();
    myTree = tree;
    myInsertionPicker = new NlDropInsertionPicker(tree);
  }

  @Override
  public void dragEnter(@NotNull DropTargetDragEvent dragEvent) {
    NlDropEvent event = new NlDropEvent(dragEvent);
    InsertType type = captureDraggedComponents(event, true /* preview */);
    if (type != null) {
      updateInsertionPoint(event);
    }
  }

  @Override
  public void dragOver(@NotNull DropTargetDragEvent dragEvent) {
    NlDropEvent event = new NlDropEvent(dragEvent);
    updateInsertionPoint(event);
  }

  @Override
  public void dragExit(@NotNull DropTargetEvent event) {
    myTree.clearInsertionPoint();
    clearDraggedComponents();
  }

  @Override
  public void drop(@NotNull DropTargetDropEvent dropEvent) {
    NlDropInsertionPicker.Result finderResult =
      myInsertionPicker.findInsertionPointAt(dropEvent.getLocation(), myDragged, true);
    if (finderResult != null) {
      if (finderResult.shouldDelegate) {
        DelegatedTreeEvent.Type type = DelegatedTreeEvent.Type.DROP;
        boolean eventHandled = NlTreeUtil.delegateEvent(type, myTree, finderResult.receiver, finderResult.row);
        if (eventHandled) {
          dropEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
          dropEvent.dropComplete(true);
        }
      }
      else {
        NlDropEvent event = new NlDropEvent(dropEvent);
        InsertType insertType = captureDraggedComponents(event, false /* not as preview */);
        myDragReceiver = finderResult.receiver;
        myNextDragSibling = finderResult.nextComponent;
        performDrop(event, insertType);
      }
    }
    myTree.clearInsertionPoint();
    clearDraggedComponents();
  }

  @Nullable
  private InsertType captureDraggedComponents(@NotNull NlDropEvent event, boolean isPreview) {
    clearDraggedComponents();
    Scene scene = myTree.getScene();
    if (scene == null) {
      return null;
    }
    NlTreeWriter treeWriter = scene.getSceneManager().getModel().getTreeWriter();
    if (event.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
      try {
        myTransferItem = (DnDTransferItem)event.getTransferable().getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        InsertType insertType = determineInsertType(event, isPreview);
        if (insertType == InsertType.MOVE) {
          myDragged.addAll(NlTreeUtil.keepOnlyAncestors(scene.getDesignSurface().getSelectionModel().getSelection()));
        }
        else {
          // TODO: support nav editor
          myDragged.addAll(NlTreeUtil.keepOnlyAncestors(treeWriter.createComponents(myTransferItem, insertType)));
        }
        return insertType;
      }
      catch (IOException | UnsupportedFlavorException exception) {
        Logger.getInstance(NlDropListener.class).warn(exception);
      }
    }
    return null;
  }

  @NotNull
  private InsertType determineInsertType(@NotNull NlDropEvent event, boolean isPreview) {
    NlModel model = myTree.getDesignerModel();
    if (model == null || myTransferItem == null) {
      return InsertType.MOVE;
    }
    DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
    return model.getTreeWriter().determineInsertType(dragType, myTransferItem, isPreview, true /* generateIds */);
  }

  private void clearDraggedComponents() {
    myDragged.clear();
  }

  /**
   * @see NlDropInsertionPicker#findInsertionPointAt(Point, List)
   */
  private void updateInsertionPoint(@NotNull NlDropEvent event) {
    NlDropInsertionPicker.Result result = myInsertionPicker.findInsertionPointAt(event.getLocation(), myDragged, true);
    if (result == null) {
      myTree.clearInsertionPoint();
      event.reject();
    }
    else {
      myDragReceiver = result.receiver;
      myNextDragSibling = result.nextComponent;
      myTree.markInsertionPoint(result.row, result.depth);

      // This determines how the DnD source acts to a completed drop.
      // If we set the accepted drop action to DndConstants.ACTION_MOVE then the source should delete the source component.
      // When we move a component within the current designer the addComponents call will remove the source component in the transaction.
      // Only when we move a component from a different designer (handled as a InsertType.COPY) would we mark this as a ACTION_MOVE.
      event.accept(determineInsertType(event, true));
    }
  }

  private void performDrop(@NotNull final NlDropEvent event, final InsertType insertType) {
    myTree.skipNextUpdateDelay();
    NlModel model = myTree.getDesignerModel();
    assert model != null;

    if (NlComponentHelperKt.isGroup(myDragReceiver) &&
        model.getTreeWriter().canAddComponents(myDragged, myDragReceiver, myDragReceiver.getChild(0), true)) {
      performNormalDrop(event, insertType, model);
    }
    else {
      // Not a viewgroup, but let's give a chance to the handler to do something with the drop event
      ViewGroupHandler handler = NlComponentHelperKt.getViewGroupHandler(myDragReceiver, () -> {});
      if (handler != null) {
        handler.performDrop(model, event, myDragReceiver, myDragged, myNextDragSibling, insertType);
      }
    }
  }

  /**
   * Perform the drop action normally without changing the type of component
   *  @param event      The DropTargetDropEvent from {@link #performDrop(NlDropEvent, InsertType)}
   * @param insertType The InsertType from {@link #performDrop(NlDropEvent, InsertType)}
   * @param model      {@link NlComponentTree#getDesignerModel()}
   */
  private void performNormalDrop(@NotNull NlDropEvent event, @NotNull InsertType insertType, @NotNull NlModel model) {
    try {
      Scene scene = myTree.getScene();
      DesignSurface<?> surface = scene != null ? scene.getDesignSurface() : null;
      if (surface != null) {
        NlTreeWriterKt.addComponentsAndSelectedIfCreated(model.getTreeWriter(),
                                                         myDragged,
                                                         myDragReceiver,
                                                         myNextDragSibling,
                                                         insertType,
                                                         surface.getSelectionModel());
      }
      else {
        model.getTreeWriter().addComponents(myDragged, myDragReceiver, myNextDragSibling, insertType, null);
      }

      event.accept(insertType);

      event.complete();
      model.notifyModified(ChangeType.DROP);

      if (scene != null) {
        scene.getDesignSurface().getSelectionModel().setSelection(myDragged);
        myTree.requestFocus();
      }
    }
    catch (Exception exception) {
      Logger.getInstance(NlDropListener.class).warn(exception);
      event.reject();
    }
  }
}