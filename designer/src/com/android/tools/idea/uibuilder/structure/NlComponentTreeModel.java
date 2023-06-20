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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import java.util.Locale;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NlComponentTreeModel implements TreeModel {
  private final NlComponent myRoot;

  NlComponentTreeModel() {
    myRoot = null;
  }

  NlComponentTreeModel(@NotNull NlModel model) {
    List<NlComponent> components = model.getComponents();
    myRoot = components.isEmpty() ? null : components.get(0);
  }

  @Nullable
  @Override
  public Object getRoot() {
    return myRoot;
  }

  @NotNull
  @Override
  public Object getChild(@NotNull Object parent, int i) {
    if (!(parent instanceof NlComponent)) {
      throw new IllegalArgumentException(String.format(Locale.US, "Parent can only be an NlComponent but is %s.", parent.toString()));
    }
    NlComponent component = (NlComponent)parent;

    ViewGroupHandler handler = NlComponentHelperKt.getLayoutHandler(component);
    int count = handler == null ? ((NlComponent)parent).getChildCount() : handler.getComponentTreeChildCount(component);
    if (i < 0 || i >= count) {
      Logger.getInstance(NlComponentTreeModel.class).error(
        String.format(Locale.US, "Index out of bounds for NlComponent.getChild. Index %d,  Parent: %s", i, ((NlComponent)parent).getTagName()));
      // We return null because we logged the error before and according
      // to the documentation of getChild, the object can be null if the index is illegal.
      return "";
    }


    Object object = handler == null ? component.getChild(i) : handler.getComponentTreeChild(component, i);
    return object != null ? object : "";
  }

  @Override
  public int getChildCount(@NotNull Object parent) {
    if (parent instanceof NlComponent) {
      NlComponent component = (NlComponent)parent;
      ViewGroupHandler handler = NlComponentHelperKt.getLayoutHandler(component);
      if (handler != null) {
        return handler.getComponentTreeChildCount(component);
      }
      return component.getChildCount();
    }
    else {
      return 0;
    }
  }

  @Override
  public boolean isLeaf(@NotNull Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(@Nullable TreePath path, @Nullable Object newValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getIndexOfChild(@NotNull Object parent, @NotNull Object child) {
    // noinspection SuspiciousMethodCalls
    return ((NlComponent)parent).getChildren().indexOf(child);
  }

  @Override
  public void addTreeModelListener(@Nullable TreeModelListener listener) {
  }

  @Override
  public void removeTreeModelListener(@Nullable TreeModelListener listener) {
  }
}
