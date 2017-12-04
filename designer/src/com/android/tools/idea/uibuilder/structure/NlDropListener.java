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

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;

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
    captureDraggedComponents(event, true /* preview */);
    updateInsertionPoint(event);
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
    NlDropInsertionPicker.Result finderResult = myInsertionPicker.findInsertionPointAt(dropEvent.getLocation(), myDragged);
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
        performDrop(dropEvent, insertType);
      }
    }
    myTree.clearInsertionPoint();
    clearDraggedComponents();
  }

  @Nullable
  private InsertType captureDraggedComponents(@NotNull NlDropEvent event, boolean isPreview) {
    clearDraggedComponents();
    SceneView screenView = myTree.getScreenView();
    if (screenView == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    if (event.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
      try {
        myTransferItem = (DnDTransferItem)event.getTransferable().getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        InsertType insertType = determineInsertType(event, isPreview);
        if (insertType.isMove()) {
          myDragged.addAll(NlTreeUtil.keepOnlyAncestors(screenView.getSelectionModel().getSelection()));
        }
        else {
          // TODO: support nav editor
          myDragged.addAll(NlTreeUtil.keepOnlyAncestors(NlModelHelperKt.createComponents(model, screenView, myTransferItem, insertType)));
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
      return InsertType.MOVE_INTO;
    }
    DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
    return model.determineInsertType(dragType, myTransferItem, isPreview);
  }

  private void clearDraggedComponents() {
    myDragged.clear();
  }

  /**
   * @see NlDropInsertionPicker#findInsertionPointAt(Point, List)
   */
  private void updateInsertionPoint(@NotNull NlDropEvent event) {
    NlDropInsertionPicker.Result result = myInsertionPicker.findInsertionPointAt(event.getLocation(), myDragged);
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
      event.accept(determineInsertType(event, true) == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);
    }
  }

  private void performDrop(@NotNull final DropTargetDropEvent event, final InsertType insertType) {
    myTree.skipNextUpdateDelay();
    NlModel model = myTree.getDesignerModel();
    assert model != null;

    if (NlComponentHelperKt.isGroup(myDragReceiver) && model.canAddComponents(myDragged, myDragReceiver, myDragReceiver.getChild(0))) {
      performNormalDrop(event, insertType, model);
    }
    else if (!myDragReceiver.isRoot()
             && !NlComponentUtil.isDescendant(myDragReceiver, myDragged)
             && NlComponentHelperKt.isMorphableToViewGroup(myDragReceiver)) {
      morphReceiverIntoViewGroup();
      performNormalDrop(event, insertType, model);
    }
    else {
      // Not a viewgroup, but let's give a chance to the handler to do something with the drop event
      ViewHandler handler = NlComponentHelperKt.getViewHandler(myDragReceiver);
      if (handler instanceof ViewGroupHandler) {
        ViewGroupHandler groupHandler = (ViewGroupHandler)handler;
        groupHandler.performDrop(model, event, myDragReceiver, myDragged, myNextDragSibling, insertType);
      }
    }
  }

  /**
   * Perform the drop action normally without changing the type of component
   *
   * @param event      The DropTargetDropEvent from {@link #performDrop(DropTargetDropEvent, InsertType)}
   * @param insertType The InsertType from {@link #performDrop(DropTargetDropEvent, InsertType)}
   * @param model      {@link NlComponentTree#getDesignerModel()}
   */
  private void performNormalDrop(@NotNull DropTargetDropEvent event, @NotNull InsertType insertType, @NotNull NlModel model) {
    try {
      SceneView view = myTree.getScreenView();
      ViewEditor editor = view != null ? ViewEditorImpl.getOrCreate(view) : null;
      model.addComponents(myDragged, myDragReceiver, myNextDragSibling, insertType, editor);

      // This determines how the DnD source acts to a completed drop.
      // If we set the accepted drop action to DndConstants.ACTION_MOVE then the source should delete the source component.
      // When we move a component within the current designer the addComponents call will remove the source component in the transaction.
      // Only when we move a component from a different designer (handled as a InsertType.COPY) would we mark this as a ACTION_MOVE.
      event.acceptDrop(insertType == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);

      event.dropComplete(true);
      model.notifyModified(NlModel.ChangeType.DROP);

      if (view != null) {
        view.getSelectionModel().setSelection(myDragged);
        myTree.requestFocus();
      }
    }
    catch (Exception exception) {
      Logger.getInstance(NlDropListener.class).warn(exception);
      event.rejectDrop();
    }
  }

  /**
   * Morph the receiver into a constraint layout and add the dragged component to it.
   */
  private void morphReceiverIntoViewGroup() {

    final AttributesTransaction transaction = myDragReceiver.startAttributeTransaction();
    for (AttributeSnapshot attribute : myDragReceiver.getAttributes()) {
      if (!TOOLS_PREFIX.equals(attribute.prefix) && !ourCopyableAttributes.contains(attribute.name)
          && attribute.namespace != null) {
        transaction.removeAttribute(attribute.namespace, attribute.name);
      }
    }

    NlWriteCommandAction.run(myDragReceiver, "", () -> {
      XmlTag tag = myDragReceiver.getTag();
      tag.setName(CONSTRAINT_LAYOUT);

      myDragReceiver.setTag(tag);
      transaction.commit();
    });
  }
}