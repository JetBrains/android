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

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.structure.NlDropListener;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Handler for ConstraintHelper objects
 */
public class ConstraintHelperHandler extends ViewGroupHandler {

  public static final boolean USE_HELPER_TAGS = false;

  /**
   * Gives a chance to the ViewGroupHandler to handle drop on elements that are not ViewGroup.
   * For ConstraintHelper instances, we'll insert tags elements referencing the dropped components.
   */
  @Override
  public void performDrop(@NotNull NlModel model,
                          @NotNull DropTargetDropEvent event,
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
            if (insertType.isMove() && toDrag.getParent() != receiver) {
              insert = InsertType.CREATE;
              XmlTag tag = receiver.getTag().createChildTag(TAG, null, null, false);
              tag.setAttribute(PREFIX_ANDROID + ATTR_ID, toDrag.getAttribute(ANDROID_URI, ATTR_ID));
              component = model.createComponent(tag);
            }
            model.addTags(Arrays.asList(component), receiver, before, insert);
          }
        }
        else {
          final AttributesTransaction transaction = receiver.startAttributeTransaction();
          String originalIdsList = transaction.getAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
          List<String> draggedIds = new ArrayList<>();
          for (NlComponent component : dragged) {
            draggedIds.add(NlComponentHelperKt.ensureLiveId(component));
          }
          String idList = flatIdList(originalIdsList, draggedIds, false);
          transaction.setAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS, idList);
          new WriteCommandAction(model.getProject(), model.getFile()) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              transaction.commit();
            }
          }.execute();
        }
        event.acceptDrop(insertType == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);
        event.dropComplete(true);
        model.notifyModified(NlModel.ChangeType.DROP);
      }
      catch (Exception exception) {
        Logger.getInstance(NlDropListener.class).warn(exception);
        event.rejectDrop();
      }
    }
  }

  /**
   * Utility function to add or remove a list of id to an existing flat list (represented as a string)
   *
   * @param ids    the original flat list
   * @param list   the list of ids
   * @param remove if true, the ids will be removed
   * @return an updated flat list
   */
  static
  @Nullable
  String flatIdList(@Nullable String ids, @NotNull List<String> list, boolean remove) {
    HashSet<String> idsSet = new HashSet();
    if (ids != null) {
      String[] originalList = ids.split(",");
      for (int i = 0; i < originalList.length; i++) {
        idsSet.add(originalList[i]);
      }
    }
    for (String id : list) {
      if (remove) {
        idsSet.remove(id);
      }
      else {
        idsSet.add(id);
      }
    }
    if (idsSet.size() == 0) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String id : idsSet) {
      if (!first) {
        builder.append(",");
      }
      builder.append(id);
      first = false;
    }
    return builder.toString();
  }

  /**
   * Allows us to remove reference of components about to be deleted
   *
   * @param parent
   * @param id
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
        element.getTag().delete();
      }
    }
    else {
      for (NlComponent child : parent.getChildren()) {
        if (NlComponentHelperKt.isOrHasSuperclass(child, CLASS_CONSTRAINT_LAYOUT_HELPER)) {
          String ids = child.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
          if (ids != null) {
            ids = flatIdList(ids, Arrays.asList(id), true);
            final AttributesTransaction transaction = child.startAttributeTransaction();
            transaction.setAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS, ids);
            NlModel model = child.getModel();
            new WriteCommandAction(model.getProject(), model.getFile()) {
              @Override
              protected void run(@NotNull Result result) throws Throwable {
                transaction.commit();
              }
            }.execute();
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
          return list[i];
        }
      }
      return component.getChild(i);
    }
    return null;
  }

  /**
   * Delete the given reference in the component
   *
   * @param component the component we need to update
   * @param id        the reference to remove
   */
  public void deleteReference(@NotNull NlComponent component, @NotNull String id) {
    String ids = component.getLiveAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS);
    if (ids != null) {
      ids = flatIdList(ids, Arrays.asList(id), true);
      final AttributesTransaction transaction = component.startAttributeTransaction();
      transaction.setAttribute(SHERPA_URI, CONSTRAINT_REFERENCED_IDS, ids);
      NlModel model = component.getModel();
      new WriteCommandAction(model.getProject(), model.getFile()) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          transaction.commit();
        }
      }.execute();
    }
  }
}
