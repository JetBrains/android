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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomNodeData;
import com.android.tools.idea.editors.gfxtrace.renderers.SchemaTreeRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;

public class AtomController extends TreeController {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  private final PathStore<AtomsPath> myAtomsPath = new PathStore<AtomsPath>();
  private boolean mDisableActivation = false;

  public AtomController(@NotNull GfxTraceEditor editor, @NotNull Project project, @NotNull JBScrollPane scrollPane) {
    super(editor, scrollPane, GfxTraceEditor.SELECT_CAPTURE);
    myTree.setLargeModel(true); // Set some performance optimizations for large models.
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        if (mDisableActivation || !myAtomsPath.isValid()) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node == null || node.getUserObject() == null) return;
        Object object = node.getUserObject();
        if (object instanceof AtomGroup) {
          myEditor.activatePath(myAtomsPath.getPath().index(((AtomGroup)object).getRange().getLast()));
        } else if (object instanceof AtomNodeData) {
          myEditor.activatePath(myAtomsPath.getPath().index(((AtomNodeData)object).index));
        }
      }
    });

  }

  public void selectDeepestVisibleNode(long atomIndex) {
    Object object = myTree.getModel().getRoot();
    assert(object instanceof DefaultMutableTreeNode);
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)object;
    selectDeepestVisibleNode(root, new TreePath(root), atomIndex);
  }

  public void selectDeepestVisibleNode(DefaultMutableTreeNode node, TreePath path, long atomIndex) {
    if (node.isLeaf() || !myTree.isExpanded(path)) {
      try {
        mDisableActivation = true;
        myTree.setSelectionPath(path);
        myTree.scrollPathToVisible(path);
        return;
      } finally {
        mDisableActivation = false;
      }
    }
    // Search through the list for now.
    for (Enumeration it = node.children(); it.hasMoreElements(); ) {
      Object obj = it.nextElement();
      assert (obj instanceof DefaultMutableTreeNode);
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)obj;
      Object object = child.getUserObject();
      boolean matches = false;
      if((object instanceof AtomGroup) &&
         (((AtomGroup)object).getRange().contains(atomIndex))) {
          matches = true;
      } else if((object instanceof AtomNodeData) &&
                ((((AtomNodeData)object).index == atomIndex))) {
        matches = true;
      }
      if (matches) {
        selectDeepestVisibleNode(child, path.pathByAddingChild(child), atomIndex);
      }
    }
  }

  @Override  public void notifyPath(Path path) {
    boolean updateAtoms = false;
    if (path instanceof CapturePath) {
      updateAtoms |= myAtomsPath.update(((CapturePath)path).atoms());
    }
    if (path instanceof AtomPath) {
      selectDeepestVisibleNode(((AtomPath)path).getIndex());
    }
    if (updateAtoms && myAtomsPath.isValid()) {
      myTree.getEmptyText().setText("");
      myLoadingPanel.startLoading();
      final ListenableFuture<AtomList> atomF = myEditor.getClient().get(myAtomsPath.getPath());
      final ListenableFuture<AtomGroup> hierarchyF = myEditor.getClient().get(myAtomsPath.getPath().getCapture().hierarchy());
      Futures.addCallback(Futures.allAsList(atomF, hierarchyF), new LoadingCallback<java.util.List<BinaryObject>>(LOG, myLoadingPanel) {
        @Override
        public void onSuccess(@Nullable final java.util.List<BinaryObject> all) {
          myLoadingPanel.stopLoading();
          final AtomList atoms = (AtomList)all.get(0);
          final AtomGroup group = (AtomGroup)all.get(1);
          final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Stream", true);
          group.addChildren(root, atoms);
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              // Back in the UI thread here
              setRoot(root);
            }
          });
        }
      });
    }
  }
}