/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomNode;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.EnumInfoCache;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.HierarchyNode;
import com.android.tools.rpclib.schema.AtomReader;
import com.intellij.openapi.Disposable;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.util.Map;

/**
 * Need to create a renderer for each tree this attaches to.
 */
public class AtomTreeRenderer implements TreeCellRenderer, Disposable {
  @NotNull private static DefaultTreeCellRenderer defaultRenderer = new DefaultTreeCellRenderer();
  private EnumInfoCache myEnumInfoCache;
  private AtomReader myAtomReader;
  @NotNull private Map<DefaultMutableTreeNode, Component> myPool = new HashMap<DefaultMutableTreeNode, Component>();

  public AtomTreeRenderer() {
    defaultRenderer.setOpenIcon(UIUtil.getTreeExpandedIcon());
    defaultRenderer.setClosedIcon(UIUtil.getTreeCollapsedIcon());
  }

  public void init(@NotNull EnumInfoCache enumInfoCache, @NotNull AtomReader atomReader) {
    myEnumInfoCache = enumInfoCache;
    myAtomReader = atomReader;
  }

  public void clear() {
    clearCache();
    myEnumInfoCache = null;
    myAtomReader = null;
  }

  public void clearCache() {
    myPool.clear();
  }

  @Nullable
  @Override
  public Component getTreeCellRendererComponent(@NotNull JTree jTree,
                                                @NotNull Object o,
                                                boolean selected,
                                                boolean expanded,
                                                boolean isLeaf,
                                                int row,
                                                boolean hasFocus) {
    assert (o instanceof DefaultMutableTreeNode);
    if (!selected) {
      Component cachedComponent = myPool.get(o);
      if (cachedComponent != null) {
        // Caching fixes an issue with Swing rendering.
        return cachedComponent;
      }
    }

    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)o;
    Object userObject = treeNode.getUserObject();
    Component newComponent;

    assert (userObject instanceof AtomNode || userObject instanceof HierarchyNode);
    if (userObject instanceof AtomNode) {
      newComponent = ((AtomNode)userObject).getComponent(myEnumInfoCache, myAtomReader, jTree, treeNode, selected);
    }
    else {
      newComponent = ((HierarchyNode)userObject).getComponent(selected);
    }

    if (!selected) { // Don't cache selected rows.
      myPool.put(treeNode, newComponent);
    }
    return newComponent;
  }

  @Nullable
  public Component getInteractiveComponent(@NotNull Object o) {
    if (o instanceof DefaultMutableTreeNode) {
      return myPool.get(o);
    }
    return null;
  }

  @Override
  public void dispose() {
  }
}
