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

import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.utils.Pair;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint.*;

/**
 * Enable drop of components dragged onto the component tree.
 * Both internal moves and drags from the palette and other structure panes are supported.
 */
public class NlDropListener extends DropTargetAdapter {

  /**
   * Attributes that can safely be copied when morphing the view
   */
  public static final HashSet<String> ourCopyableAttributes = new HashSet<>(Arrays.asList(
    ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_ID, ATTR_BACKGROUND
  ));

  private final List<NlComponent> myDragged;
  private final NlComponentTree myTree;
  private DnDTransferItem myTransferItem;
  protected NlComponent myDragReceiver;
  protected NlComponent myNextDragSibling;

  public NlDropListener(@NotNull NlComponentTree tree) {
    myDragged = new ArrayList<>();
    myTree = tree;
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
    clearInsertionPoint();
    clearDraggedComponents();
  }

  @Override
  public void drop(@NotNull DropTargetDropEvent dropEvent) {
    NlDropEvent event = new NlDropEvent(dropEvent);
    InsertType insertType = captureDraggedComponents(event, false /* not as preview */);
    if (findInsertionPoint(event) != null) {
      performDrop(dropEvent, insertType);
    }
    clearInsertionPoint();
    clearDraggedComponents();
  }

  @Nullable
  private InsertType captureDraggedComponents(@NotNull NlDropEvent event, boolean isPreview) {
    clearDraggedComponents();
    ScreenView screenView = myTree.getScreenView();
    if (screenView == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    if (event.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
      try {
        myTransferItem = (DnDTransferItem)event.getTransferable().getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        InsertType insertType = determineInsertType(event, isPreview);
        if (insertType.isMove()) {
          myDragged.addAll(keepOnlyAncestors(model.getSelectionModel().getSelection()));
        }
        else {
          Collection<NlComponent> captured = ApplicationManager.getApplication()
            .runWriteAction((Computable<Collection<NlComponent>>)() -> model.createComponents(screenView, myTransferItem, insertType));

          if (captured != null) {
            myDragged.addAll(keepOnlyAncestors(captured));
          }
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

  private void updateInsertionPoint(@NotNull NlDropEvent event) {
    Pair<TreePath, InsertionPoint> point = findInsertionPoint(event);
    if (point == null) {
      clearInsertionPoint();
      event.reject();
    }
    else {
      myTree.markInsertionPoint(point.getFirst(), point.getSecond());

      // This determines how the DnD source acts to a completed drop.
      // If we set the accepted drop action to DndConstants.ACTION_MOVE then the source should delete the source component.
      // When we move a component within the current designer the addComponents call will remove the source component in the transaction.
      // Only when we move a component from a different designer (handled as a InsertType.COPY) would we mark this as a ACTION_MOVE.
      event.accept(determineInsertType(event, true) == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);
    }
  }

  protected boolean shouldInsert(@NotNull NlComponent receiver, @Nullable InsertionPoint insertionPoint) {
    ViewHandler handler = receiver.getViewHandler();
    return insertionPoint == INSERT_INTO &&
           (handler instanceof ViewGroupHandler
            || (isMorphableToViewGroup(receiver) && !isReceiverChild(receiver, myDragged)));
  }

  protected boolean canAddComponent(@NotNull NlModel model, @NotNull NlComponent receiver) {
    return model.canAddComponents(myDragged, receiver, receiver.getChild(0))
           || (isMorphableToViewGroup(receiver) && !isReceiverChild(receiver, myDragged));
  }

  /**
   * @return true if the receiver can be safely morphed into a view group
   */
  private static boolean isMorphableToViewGroup(@NotNull NlComponent receiver) {
    return VIEW.equals(receiver.getTagName()) && receiver.getAttribute(TOOLS_URI, ATTR_MOCKUP) != null;
  }

  @Nullable
  private Pair<TreePath, InsertionPoint> findInsertionPoint(@NotNull NlDropEvent event) {
    myDragReceiver = null;
    myNextDragSibling = null;
    TreePath path = myTree.getClosestPathForLocation(event.getLocation().x, event.getLocation().y);
    if (path == null) {
      return null;
    }
    ScreenView screenView = myTree.getScreenView();
    if (screenView == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    NlComponent component = (NlComponent)path.getLastPathComponent();

    if (component == null) {
      return null;
    }

    Rectangle bounds = myTree.getPathBounds(path);
    if (bounds == null) {
      return null;
    }
    InsertionPoint insertionPoint = findTreeStateInsertionPoint(event.getLocation().y, bounds);

    if (shouldInsert(component, insertionPoint)) {
      if (!canAddComponent(model, component)) {
        return null;
      }
      myDragReceiver = component;
      myNextDragSibling = component.getChild(0);
    }
    else {
      NlComponent parent = component.getParent();

      if (parent == null) {
        return null;
      }
      else {
        if (parent.getViewHandler() == null || !model.canAddComponents(myDragged, parent, component)) {
          return null;
        }
        insertionPoint = event.getLocation().y > bounds.getCenterY() ? INSERT_AFTER : INSERT_BEFORE;
        myDragReceiver = parent;

        if (insertionPoint == INSERT_BEFORE) {
          myNextDragSibling = component;
        }
        else {
          myNextDragSibling = component.getNextSibling();
        }
      }
    }
    return Pair.of(path, insertionPoint);
  }

  @NotNull
  protected static InsertionPoint findTreeStateInsertionPoint(@SwingCoordinate int y, @SwingCoordinate @NotNull Rectangle bounds) {
    int delta = bounds.height / 9;
    if (bounds.y + delta > y) {
      return INSERT_BEFORE;
    }
    if (bounds.y + bounds.height - delta < y) {
      return INSERT_AFTER;
    }
    return INSERT_INTO;
  }

  private void clearInsertionPoint() {
    myTree.markInsertionPoint(null, INSERT_BEFORE);
  }

  protected void performDrop(@NotNull final DropTargetDropEvent event, final InsertType insertType) {
    myTree.skipNextUpdateDelay();
    NlModel model = myTree.getDesignerModel();
    assert model != null;

    if (myDragReceiver.isGroup() && model.canAddComponents(myDragged, myDragReceiver, myDragReceiver.getChild(0))) {
      performNormalDrop(event, insertType, model);
    }
    else if (!myDragReceiver.isRoot()
             && !isReceiverChild(myDragReceiver, myDragged)
             && isMorphableToViewGroup(myDragReceiver)) {
      morphReceiverIntoViewGroup(model);
      performNormalDrop(event, insertType, model);
    } else {
      // Not a viewgroup, but let's give a chance to the handler to do something with the drop event
      ViewHandler handler = myDragReceiver.getViewHandler();
      if (handler instanceof ViewGroupHandler) {
        ViewGroupHandler groupHandler = (ViewGroupHandler) handler;
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
      model.addComponents(myDragged, myDragReceiver, myNextDragSibling, insertType);

      // This determines how the DnD source acts to a completed drop.
      // If we set the accepted drop action to DndConstants.ACTION_MOVE then the source should delete the source component.
      // When we move a component within the current designer the addComponents call will remove the source component in the transaction.
      // Only when we move a component from a different designer (handled as a InsertType.COPY) would we mark this as a ACTION_MOVE.
      event.acceptDrop(insertType == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);

      event.dropComplete(true);
      model.notifyModified(NlModel.ChangeType.DROP);
    }
    catch (Exception exception) {
      Logger.getInstance(NlDropListener.class).warn(exception);
      event.rejectDrop();
    }
  }

  /**
   * Morph the receiver into a constraint layout and add the dragged component to it.
   *
   * @param model {@link NlComponentTree#getDesignerModel()}
   */
  private void morphReceiverIntoViewGroup(@NotNull NlModel model) {

    final AttributesTransaction transaction = myDragReceiver.startAttributeTransaction();
    for (AttributeSnapshot attribute : myDragReceiver.getAttributes()) {
      if (!TOOLS_PREFIX.equals(attribute.prefix) && !ourCopyableAttributes.contains(attribute.name)
          && attribute.namespace != null) {
        transaction.removeAttribute(attribute.namespace, attribute.name);
      }
    }
    new WriteCommandAction(model.getProject(), model.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        final XmlTag tag = myDragReceiver.getTag();
        tag.setName(CONSTRAINT_LAYOUT);
        myDragReceiver.setTag(tag);
        transaction.commit();
      }
    }.execute();
  }

  /**
   * Modified dragged to keep only the elements that have no ancestor in the selection
   *
   * @param dragged the dragged element
   */
  private static Collection<NlComponent> keepOnlyAncestors(@NotNull Collection<NlComponent> dragged) {
    final Set<NlComponent> selection = Sets.newIdentityHashSet();
    selection.addAll(dragged);
    Stack<NlComponent> toTraverse = new Stack<>();
    for (NlComponent selectedElement : dragged) {
      final List<NlComponent> children = selectedElement.getChildren();
      // recursively delete children from the selection
      toTraverse.addAll(children);
      while (!toTraverse.isEmpty()) {
        // Traverse the subtree for each children
        NlComponent child = toTraverse.pop();
        toTraverse.addAll(child.getChildren());
        if (selection.contains(child)) {
          selection.remove(child);
        }
      }
    }
    return selection;
  }

  /**
   * Check if receiver is a child of an element from to Add
   *
   * @param receiver
   * @param toAdd
   * @return true if toAdd element have receiver as child or grand-child
   */
  private static boolean isReceiverChild(@NotNull NlComponent receiver, @NotNull List<NlComponent> toAdd) {
    NlComponent same = receiver;
    for (NlComponent component : toAdd) {
      while (same != null) {
        if (same == component) {
          return true;
        }
        same = same.getParent();
      }
    }
    return false;
  }
}
