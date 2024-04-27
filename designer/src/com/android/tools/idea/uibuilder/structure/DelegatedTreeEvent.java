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
package com.android.tools.idea.uibuilder.structure;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.List;

/**
 * Object to be passed to a {@link DelegatedTreeEventHandler} with a simplified view
 * of the selection in the tree and the event associated with the selection.
 */
public class DelegatedTreeEvent {

  private final List<Object> mySelected;
  private final Object myNextSibling;
  private final NlComponentTree myTree;
  private final Type myType;

  public enum Type {
    DROP, DELETE
  }

  /**
   * @param tree           The {@link NlComponentTree} on which the event began
   * @param type           The type of event
   * @param selectedObject The list of the selected object in the tree. We abstract all the {@link TreePath}
   *                       logic to the handler.
   * @param nextSibling
   */
  public DelegatedTreeEvent(@NotNull NlComponentTree tree,
                            @NotNull Type type,
                            @NotNull List<Object> selectedObject,
                            @Nullable Object nextSibling) {
    myTree = tree;
    myType = type;
    mySelected = selectedObject;
    myNextSibling = nextSibling;
  }

  /**
   * The {@link NlComponentTree} on which the event occurred
   */
  @NotNull
  public NlComponentTree getTree() {
    return myTree;
  }

  /**
   * The type of the event
   */
  @NotNull
  public Type getType() {
    return myType;
  }

  /**
   * The selected objects in the tree. This is used to have directly access to the object
   * without having to deal with the {@link TreePath}. The {@link DelegatedTreeEventHandler} should
   * check and cast the object to the type it expects.
   */
  @NotNull
  public List<Object> getSelected() {
    return mySelected;
  }

  /**
   * @return In case a {@link Type#DROP} event, returns the next sibling at the insertion point
   * for the selected components.
   */
  @Nullable
  public Object getNextSibling() {
    return myNextSibling;
  }
}
