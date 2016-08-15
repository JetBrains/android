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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.structure.NlComponentTree;
import com.android.tools.idea.uibuilder.structure.NlDropListener;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint.INSERT_INTO;

/**
 * Same as {@link NlDropListener}, but one view is dragged on a NON ViewGroup,
 * the target view will be transformed into a ConstraintLayout
 */
public class MockupDropListener extends NlDropListener {

  public static final HashSet<String> ourCopyableAttributes = new HashSet<>(Arrays.asList(
    ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_ID, ATTR_BACKGROUND
  ));

  public MockupDropListener(@NotNull NlComponentTree tree) {
    super(tree);
  }

  @Override
  protected boolean shouldInsert(NlComponent component, NlComponentTree.InsertionPoint insertionPoint) {
    return insertionPoint == INSERT_INTO && VIEW.equals(component.getTagName());
  }

  @Override
  protected boolean canAddComponent(NlModel model, NlComponent component) {
    return true;
  }

  @Override
  protected void performDrop(@NotNull DropTargetDropEvent event, InsertType insertType) {
    NlModel model = myTree.getDesignerModel();
    assert model != null;

    // If the receiver is already a ViewGroup, use the default DnD behavior
    if (myDragReceiver.isOrHasSuperclass(CLASS_VIEWGROUP) &&
        model.canAddComponents(myDragged, myDragReceiver, myDragReceiver.getChild(0))) {
      super.performDrop(event, insertType);
    }
    else if (!myDragReceiver.isRoot() && !isReceiverChild(myDragReceiver, myDragged)) {
      // If the receiver is not a ViewGroup and none of the elements is a parent of the receiver
      // Create a new constraint layout that be the parent of the dragged components
      final NlComponent parent = myDragReceiver.getParent();
      assert parent != null;
      final XmlTag parentTag = parent.getTag();
      final XmlTag viewGroupTag = parentTag.createChildTag(CONSTRAINT_LAYOUT, "", null, false);

      // Copy the attribute of the transformed view into the new one
      final List<AttributeSnapshot> attributes = myDragReceiver.getAttributes();
      for (AttributeSnapshot attribute : attributes) {
        if (TOOLS_PREFIX.equals(attribute.prefix) || ourCopyableAttributes.contains(attribute.name)) {
          viewGroupTag.setAttribute(attribute.prefix + ":" + attribute.name, attribute.prefix, attribute.value);
        }
      }

      // Add the dragged element to the newly created ConstraintLayout
      for (NlComponent child : myDragged) {
        viewGroupTag.addSubTag(child.getTag(), false);
      }

      final NlComponent viewGroup = new NlComponent(model, viewGroupTag);
      final XmlAttribute id = viewGroupTag.getAttribute(ANDROID_NS_NAME_PREFIX + ATTR_ID);
      String commandTitle = String.format("move selection into View %s",
                                          id != null
                                          ? NlComponent.stripId(id.getValue()) : "");
      new WriteCommandAction(model.getProject(), commandTitle, model.getFile()) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          // Write the new viewgroup
          model.addComponents(Collections.singletonList(viewGroup), parent, null, insertType);
          model.notifyModified(NlModel.ChangeType.DROP);

          event.acceptDrop(insertType == InsertType.COPY ? event.getDropAction() : DnDConstants.ACTION_COPY);
          event.dropComplete(true);

          // Delete the original component
          model.delete(Collections.singletonList(myDragReceiver));
          model.delete(myDragged);
        }
      }.execute();
    }
  }

  /**
   * Check if receiver is a child of an element from to Add
   *
   * @param receiver
   * @param toAdd
   * @return true if toAdd element have receiver as child or grand-child
   */
  private static boolean isReceiverChild(NlComponent receiver, List<NlComponent> toAdd) {
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
