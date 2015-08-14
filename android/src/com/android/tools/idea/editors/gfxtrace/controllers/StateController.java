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
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.StateTreeNode;
import com.android.tools.idea.editors.gfxtrace.renderers.StateTreeRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.android.tools.idea.editors.gfxtrace.service.path.StatePath;
import com.android.tools.rpclib.binary.BinaryObject;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class StateController implements PathListener {
  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);

  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final SimpleTree myTree;

  @NotNull private AtomicLong myAtomicAtomIndex = new AtomicLong(-1);

  public StateController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrollPane) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myTree = new SimpleTree();
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new StateTreeRenderer());
    myTree.getEmptyText().setText(GfxTraceEditor.SELECT_CAPTURE);
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), editor.getProject());
    myLoadingPanel.add(myTree);
    scrollPane.setViewportView(myLoadingPanel);
  }

  @Nullable
  private static DefaultMutableTreeNode constructStateNode(@NotNull String name, @Nullable Object value) {
    return new StateTreeNode(name, value);
  }

  public void updateTreeModelFromAtomPath(AtomPath path) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final ServiceClient client = myEditor.getClient();
    final StatePath statePath = path.stateAfter();

    if (!myLoadingPanel.isLoading()) {
      myTree.getEmptyText().setText("");
      myLoadingPanel.startLoading();
    }

    ListenableFuture<TreeNode> nodeFuture = myEditor.getExecutor().submit(new Callable<TreeNode>() {
      @Override
      @Nullable
      public TreeNode call() throws Exception {
        BinaryObject value = client.get(statePath).get();
        return constructStateNode("state", value);
      }
    });
    Futures.addCallback(nodeFuture, new LoadingCallback<TreeNode>(LOG, myLoadingPanel) {
      @Override
      public void onSuccess(@Nullable TreeNode result) {
        myTree.setModel(new DefaultTreeModel(result));
        myTree.updateUI();
        myLoadingPanel.stopLoading();
      }
    }, EdtExecutor.INSTANCE);
  }

  public void clear() {
    myAtomicAtomIndex.set(-1);
    myTree.setModel(null);
    myTree.updateUI();
  }

  @Override
  public void notifyPath(Path path) {
    myTree.getEmptyText().setText(GfxTraceEditor.SELECT_ATOM);
  }
}
