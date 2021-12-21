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
package com.android.tools.idea.uibuilder.handlers.constraint;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_HELPER;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.PREFIX_ANDROID;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.TAG;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentReference;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlDropEvent;
import com.android.tools.idea.uibuilder.structure.DelegatedTreeEvent;
import com.android.tools.idea.uibuilder.structure.DelegatedTreeEventHandler;
import com.android.tools.idea.uibuilder.structure.NlDropListener;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for ConstraintHelper objects
 */
public class ConstraintHelperHandler extends ViewGroupHandler implements DelegatedTreeEventHandler {

  public static final boolean USE_HELPER_TAGS = false;

  /**
   * Gives a chance to the ViewGroupHandler to handle drop on elements that are not ViewGroup.
   * For ConstraintHelper instances, we'll insert tags elements referencing the dropped components.
   */
  @Override
  public void performDrop(@NotNull NlModel model,
                          @NotNull NlDropEvent event,
                          @NotNull NlComponent receiver,
                          @NotNull List<NlComponent> dragged,
                          @Nullable NlComponent before,
                          @NotNull InsertType insertType) {
    if (NlComponentHelperKt.isOrHasSuperclass(receiver, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
      try {
        if (USE_HELPER_TAGS) {
          for (NlComponent toDrag : dragged) {
            InsertType insert = insertType;
            NlComponent component = toDrag;
            if (insertType == InsertType.MOVE && toDrag.getParent() != receiver) {
              insert = InsertType.CREATE;
              XmlTag tag = receiver.getTagDeprecated().createChildTag(TAG, null, null, false);
              tag.setAttribute(PREFIX_ANDROID + ATTR_ID, toDrag.getAttribute(ANDROID_URI, ATTR_ID));
              component = model.createComponent(tag);
            }
            model.addTags(Collections.singletonList(component), receiver, before, insert);
          }
        }
        else {
          addComponentsIds(receiver, dragged);

          if (NlComponentHelperKt.isOrHasSuperclass(receiver, CLASS_CONSTRAINT_LAYOUT_FLOW)) {
            for (NlComponent toDrag : dragged) {
              // if a widget is dropped into a Flow helper, we want to remove the absolute position attributes.
              if (insertType == InsertType.MOVE && toDrag.getParent() != receiver) {
                AttributesTransaction transaction = toDrag.startAttributeTransaction();
                transaction.setAttribute(TOOLS_URI, "layout_editor_absoluteX", null);
                transaction.setAttribute(TOOLS_URI, "layout_editor_absoluteY", null);
                NlWriteCommandActionUtil.run(toDrag, "", transaction::commit);
              }
            }
          }
        }
        event.accept(insertType);
        event.complete();
        model.notifyModified(NlModel.ChangeType.DROP);
      }
      catch (Exception exception) {
        Logger.getInstance(NlDropListener.class).warn(exception);
        event.reject();
      }
    }
  }

  private static void addComponentsIds(@NotNull NlComponent receiver, @NotNull List<NlComponent> dragged) {
    List<String> draggedIds = new ArrayList<>();
    for (NlComponent component : dragged) {
      draggedIds.add(NlComponentHelperKt.ensureLiveId(component));
    }
    addReferencesIds(receiver, draggedIds, null);
  }

  @Override
  public boolean holdsReferences() {
    return true;
  }

  public void removeReference(@NotNull NlComponent component, @NotNull String id) {
    deleteReferences(component, ImmutableList.of(id));
  }

  @Override
  public void addReferences(@NotNull NlComponent component, @NotNull List<String> ids, @Nullable String before) {
    addReferencesIds(component, ids, before);
  }

  private static void addReferencesIds(@NotNull NlComponent receiver,
                                       @NotNull List<String> draggedIds,
                                       @Nullable String before) {
    AttributesTransaction transaction = receiver.startAttributeTransaction();
    String originalIdsList = transaction.getAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
    String idList = addIds(originalIdsList, draggedIds, before);
    writeIds(receiver, transaction, idList);
  }

  @Nullable
  private static String removeIds(@NotNull String originalIds, @NotNull List<String> toRemove) {
    String[] splitIds = originalIds.split(",");
    List<String> strings = new ArrayList<>(splitIds.length);
    Collections.addAll(strings, splitIds);
    strings.removeAll(toRemove);
    return strings.isEmpty() ? null : String.join(",", strings);
  }

  /**
   * Utility function to add or remove a list of id to an existing flat list (represented as a string)
   *
   * @param ids    the original flat list
   * @param newIds the list of ids
   * @param before The id to insert ids before. If null, ids will be inserted at the end
   */
  @Nullable
  private static String addIds(@Nullable String ids, @NotNull List<String> newIds, @Nullable String before) {
    if (newIds.isEmpty()) {
      return ids;
    }

    ArrayList<String> idsList;
    if (ids != null) {
      String[] splitIds = ids.split(",");
      idsList = new ArrayList<>(splitIds.length);
      Collections.addAll(idsList, splitIds);
      idsList.removeAll(newIds);
    }
    else {
      idsList = new ArrayList<>();
    }

    int insertionIndex = before == null ? -1 : idsList.indexOf(before);
    if (insertionIndex >= 0) {
      idsList.addAll(insertionIndex, newIds);
    }
    else {
      idsList.addAll(newIds);
    }

    return String.join(",", new LinkedHashSet<>(idsList));
  }

  /**
   * Allows us to remove reference of components about to be deleted
   */
  public static void willDelete(@NotNull NlComponent parent, @NotNull String id) {
    if (USE_HELPER_TAGS) {
      ArrayList<NlComponent> toRemove = new ArrayList<>();
      for (NlComponent child : parent.getChildren()) {
        if (NlComponentHelperKt.isOrHasSuperclass(child, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
          for (NlComponent reference : child.getChildren()) {
            if (reference.getId().equals(id)) {
              toRemove.add(reference);
            }
          }
        }
      }
      for (NlComponent element : toRemove) {
        NlComponent p = element.getParent();
        if (p != null) {
          p.removeChild(element);
        }
        element.getTagDeprecated().delete();
      }
    }
    else {
      for (NlComponent child : parent.getChildren()) {
        if (NlComponentHelperKt.isOrHasSuperclass(child, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
          String ids = child.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
          if (ids != null) {
            ids = removeIds(ids, Collections.singletonList(id));
            AttributesTransaction transaction = child.startAttributeTransaction();
            writeIds(child, transaction, ids);
          }
        }
      }
    }
  }

  @Override
  public int getComponentTreeChildCount(@NotNull Object element) {
    if (element instanceof NlComponent) {
      NlComponent component = (NlComponent)element;
      if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
        String ids = component.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
        if (ids != null) {
          String[] list = ids.split(",");
          return list.length;
        }
      }
    }
    return 0;
  }

  @Override
  public Object getComponentTreeChild(@NotNull Object element, int i) {
    if (element instanceof NlComponent) {
      NlComponent component = (NlComponent)element;
      if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
        String ids = component.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
        if (ids != null) {
          String[] list = ids.split(",");
          return StudioFlags.NELE_NEW_COMPONENT_TREE.get() &&
                 StudioFlags.USE_COMPONENT_TREE_TABLE.get() ? new NlComponentReference(component, list[i]) : list[i];
        }
      }
      return component.getChild(i);
    }
    return null;
  }

  @Override
  public List<?> getComponentTreeChildren(@NotNull Object element) {
    if (element instanceof NlComponent) {
      NlComponent component = (NlComponent)element;
      if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
        String ids = component.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
        if (ids != null) {
          List<String> list = Arrays.asList(ids.split(","));
          if (!StudioFlags.NELE_NEW_COMPONENT_TREE.get()) {
            return list;
          }
          return list.stream().map(id -> new NlComponentReference(component, id)).collect(Collectors.toList());
        }
      }
      return component.getChildren();
    }
    return Collections.emptyList();
  }

  /**
   * Delete the given references in the component
   *
   * @param component the component we need to update
   * @param ids        the reference to remove
   */
  private void deleteReferences(@NotNull NlComponent component, @NotNull List<String> ids) {
    String refs = component.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
    if (refs != null) {
      refs = removeIds(refs, ids);
      AttributesTransaction transaction = component.startAttributeTransaction();
      writeIds(component, transaction, refs);
    }
  }

  /**
   * Execute a WriteCommandAction to write idList into the {@link com.android.SdkConstants#CONSTRAINT_REFERENCED_IDS} attribute using the
   * provided transaction
   *
   * @param component   The Constraint Helper component to modify
   * @param transaction A started {@link AttributesTransaction} from component
   * @param idList      The id list to write or null to remove the attribute
   */
  private static void writeIds(@NotNull NlComponent component,
                               @NotNull AttributesTransaction transaction,
                               @Nullable String idList) {
    transaction.setAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS, idList);
    NlWriteCommandActionUtil.run(component, "", transaction::commit);
  }

  @Override
  public boolean handleTreeEvent(@NotNull DelegatedTreeEvent event, @NotNull NlComponent constraintHelper) {
    if (event.getType() == DelegatedTreeEvent.Type.DELETE) {
      handleDeletion(event, constraintHelper);
    }
    else if (event.getType() == DelegatedTreeEvent.Type.DROP) {
      handleHelperIdDrop(event, constraintHelper);
    }
    return true;
  }

  private void handleDeletion(@NotNull DelegatedTreeEvent event, @NotNull NlComponent constraintHelper) {
    for (Object last : event.getSelected()) {
      if (last instanceof String) {
        deleteReferences(constraintHelper, ImmutableList.of((String)last));
      }
    }
  }

  private static void handleHelperIdDrop(@NotNull DelegatedTreeEvent event, @NotNull NlComponent component) {
    List<Object> selected = event.getSelected();
    List<String> ids = selected.stream()
      .filter(o -> o instanceof String)
      .map(o -> ((String)o))
      .collect(Collectors.toList());

    Object sibling = event.getNextSibling();
    String nextSibling = sibling instanceof String ? ((String)sibling) : null;
    addReferencesIds(component, ids, nextSibling);
  }

  @SuppressWarnings("ForLoopReplaceableByForEach")
  @Override
  public Transferable getTransferable(TreePath[] paths) {
    List<String> barriersList = new ArrayList<>();
    for (int i = 0; i < paths.length; i++) {
      Object component = paths[i].getLastPathComponent();
      if (component instanceof String) {
        barriersList.add((String)component);
      }
    }
    if (barriersList.isEmpty()) {
      return null;
    }

    return new BarrierTransferable(barriersList);
  }

  /**
   * {@link Transferable} for barrier references
   */
  private static class BarrierTransferable implements Transferable {
    public static final DataFlavor BARRIER_FLAVOR = new DataFlavor(BarrierTransferable.class, "Barrier Item");
    private final List<String> myBarrierReferences;

    public BarrierTransferable(List<String> barrierReferences) {
      myBarrierReferences = barrierReferences;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{BARRIER_FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return BARRIER_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (BARRIER_FLAVOR.equals(flavor)) {
        return myBarrierReferences;
      }
      throw new UnsupportedFlavorException(flavor);
    }
  }
}
