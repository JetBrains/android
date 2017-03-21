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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.structure.NlDropListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Handler for ConstraintHelper objects
 */
public class ConstraintHelperHandler extends ViewGroupHandler {

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
    if (receiver.isOrHasSuperclass(CLASS_CONSTRAINT_LAYOUT_HELPER)) {
      try {
        for (NlComponent toDrag : dragged) {
          InsertType insert = insertType;
          NlComponent component = toDrag;
          if (insertType.isMove() && toDrag.getParent() != receiver) {
            insert = InsertType.CREATE;
            XmlTag tag = receiver.getTag().createChildTag(TAG, null, null, false);
            tag.setAttribute(PREFIX_ANDROID + ATTR_ID, toDrag.getAttribute(ANDROID_URI, ATTR_ID));
            component = new NlComponent(model, tag);
          }
          model.addTags(Arrays.asList(component), receiver, before, insert);
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
}
