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
package com.android.tools.idea.apk.viewer.dex;

import com.android.tools.idea.apk.viewer.dex.tree.DexElementNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexFieldNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexMethodNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class FilteredTreeModel implements TreeModel {

  @NotNull private final FilterOptions myFilterOptions;
  @NotNull private final DefaultTreeModel myModel;

  public FilteredTreeModel(@NotNull DefaultTreeModel model) {
    myFilterOptions = new FilterOptions();
    myModel = model;
  }

  public void reload(){
    myModel.reload();
  }

  @NotNull
  public FilterOptions getFilterOptions() {
    return myFilterOptions;
  }

  public void setRoot(DexElementNode node){
    myModel.setRoot(node);
  }

  @Override
  public Object getRoot() {
    return myModel.getRoot();
  }

  @Override
  public Object getChild(Object parent, int index) {
    if (parent instanceof DexElementNode) {
      DexElementNode result;
      for (int i = 0, n = myModel.getChildCount(parent); i < n; i++) {
        result = (DexElementNode)myModel.getChild(parent, i);
        if (myFilterOptions.matches(result)) {
          if (index == 0) {
            return result;
          }
          else {
            index--;
          }
        }
      }
    } else {
      return myModel.getChild(parent, index);
    }

    return null;
  }

  @Override
  public int getChildCount(Object parent) {
    if (parent instanceof DexElementNode) {
      int count = 0;
      DexElementNode result;
      for (int i = 0, n = myModel.getChildCount(parent); i < n; i++) {
        result = (DexElementNode)myModel.getChild(parent, i);
        if (myFilterOptions.matches(result)) {
          count++;
        }
      }
      return count;
    } else {
      return myModel.getChildCount(parent);
    }
  }

  @Override
  public boolean isLeaf(Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    myModel.valueForPathChanged(path, newValue);
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    if (parent instanceof DexElementNode && child instanceof DexElementNode) {
      int index = 0;
      DexElementNode result;
      for (int i = 0, n = myModel.getChildCount(parent); i < n; i++) {
        result = (DexElementNode)myModel.getChild(parent, i);
        if (myFilterOptions.matches(result)) {
          if (result.equals(child)) {
            return index;
          }
          else {
            index++;
          }
        }
      }
    } else {
      return myModel.getIndexOfChild(parent, child);
    }

    return -1;
  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {
    myModel.addTreeModelListener(l);
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    myModel.removeTreeModelListener(l);
  }

  public class FilterOptions {
    private boolean myShowMethods = true;
    private boolean myShowFields = true;
    private boolean myShowReferencedNodes = true;
    private boolean myShowRemovedNodes = false;

    private FilterOptions(){
    }

    public void setShowMethods(boolean showMethods) {

      myShowMethods = showMethods;
      FilteredTreeModel.this.reload();
    }

    public void setShowFields(boolean showFields) {
      myShowFields = showFields;
      FilteredTreeModel.this.reload();
    }

    public void setShowReferencedNodes(boolean showReferencedNodes) {
      myShowReferencedNodes = showReferencedNodes;
      FilteredTreeModel.this.reload();
    }

    public void setShowRemovedNodes(boolean showRemovedNodes) {
      myShowRemovedNodes = showRemovedNodes;
      FilteredTreeModel.this.reload();
    }

    public boolean matches(DexElementNode node){
      return ((myFilterOptions.myShowFields || !(node instanceof DexFieldNode))
             && (myFilterOptions.myShowMethods || !(node instanceof DexMethodNode))
             && (myFilterOptions.myShowReferencedNodes || node.hasClassDefinition())
             && (myFilterOptions.myShowRemovedNodes || !node.isRemoved()));
    }

    public boolean isShowMethods() {
      return myShowMethods;
    }

    public boolean isShowFields() {
      return myShowFields;
    }

    public boolean isShowReferencedNodes() {
      return myShowReferencedNodes;
    }

    public boolean isShowRemovedNodes() {
      return myShowRemovedNodes;
    }
  }
}
