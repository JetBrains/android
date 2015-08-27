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
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.StateNodeData;
import com.android.tools.idea.editors.gfxtrace.renderers.SchemaTreeRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class StateController implements PathListener {
  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);

  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final SimpleTree myTree;

  private final PathStore<StatePath> myStatePath = new PathStore<StatePath>();

  public StateController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrollPane) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myTree = new SimpleTree();
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new SchemaTreeRenderer());
    myTree.getEmptyText().setText(GfxTraceEditor.SELECT_ATOM);
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), editor.getProject());
    myLoadingPanel.add(myTree);
    scrollPane.setViewportView(myLoadingPanel);
  }

  @Nullable
  private static DefaultMutableTreeNode constructStateNode(@Nullable Object key, @Nullable Object value) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();
    Object render = value;
    if (value instanceof Dynamic) {
      render = null;
      node.setUserObject(new StateNodeData(key, value));
      Dynamic dynamic = (Dynamic)value;
      for (int index = 0; index < dynamic.getFieldCount(); ++index) {
        node.add(constructStateNode(dynamic.getFieldInfo(index), dynamic.getFieldValue(index)));
      }
    } else if (value instanceof Map) {
      render = null;
      node.setUserObject(new StateNodeData(key, value));
      Map<?,?> map = (Map)value;
      for (java.util.Map.Entry entry : map.entrySet()) {
        node.add(constructStateNode(entry.getKey(), entry.getValue()));
      }
    }
    node.setUserObject(new StateNodeData(key, render));
    return node;
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateState = false;
    if (path instanceof AtomPath) {
      updateState |= myStatePath.update(((AtomPath)path).stateAfter());
    }
    if (updateState && myStatePath.isValid()) {
      Futures.addCallback(myEditor.getClient().get(myStatePath.getPath()), new LoadingCallback<Object>(LOG, myLoadingPanel) {
        @Override
        public void onSuccess(@Nullable final Object state) {
          final DefaultMutableTreeNode stateNode = constructStateNode("state", state);
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              // Back in the UI thread here
              myTree.setModel(new DefaultTreeModel(stateNode));
              myTree.updateUI();
              myLoadingPanel.stopLoading();
            }
          });
        }
      });
    }
  }
}
