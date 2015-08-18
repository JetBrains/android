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
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.StateTreeNode;
import com.android.tools.idea.editors.gfxtrace.renderers.StateTreeRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.idea.editors.gfxtrace.rpc.*;
import com.android.tools.idea.editors.gfxtrace.schema.*;
import com.android.tools.rpclib.binary.Decoder;
import com.google.common.util.concurrent.FutureCallback;
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
import java.io.ByteArrayInputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class StateController implements GfxController {
  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);

  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final SimpleTree myTree;

  @NotNull private AtomicLong myAtomicAtomId = new AtomicLong(-1);
  @Nullable private StructInfo myStateHierarchy;

  public StateController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrollPane) {
    myEditor = editor;
    myTree = new SimpleTree();
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new StateTreeRenderer());
    myTree.getEmptyText().setText(SELECT_CAPTURE);
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), editor.getProject());
    myLoadingPanel.add(myTree);
    scrollPane.setViewportView(myLoadingPanel);
  }

  @Nullable
  private static DefaultMutableTreeNode constructStateNode(@NotNull String name, @NotNull TypeKind typeKind, @Nullable Object value) {
    StateTreeNode thisNode = null;
    switch (typeKind) {
      case Bool:
      case S8:
      case U8:
      case S16:
      case U16:
      case S32:
      case U32:
      case S64:
      case U64:
      case F32:
      case F64:
      case String:
      case Pointer:
        thisNode = new StateTreeNode(name, value);
        break;

      case Enum:
        assert (value instanceof EnumValue);
        EnumValue enumValue = (EnumValue)value;
        for (EnumEntry entry : enumValue.info.getEntries()) {
          if (enumValue.value == entry.getValue()) {
            thisNode = new StateTreeNode(name, entry.getName());
            break;
          }
        }
        if (thisNode == null) {
          LOG.warn("Invalid bitfield value passed in: " + enumValue.value);
        }
        break;

      case Array:
        assert (value instanceof Array);
        Array array = (Array)value;
        thisNode = new StateTreeNode(name, null);
        ArrayInfo arrayInfo = array.info;
        Object[] elements = array.elements;
        int i = 0;
        for (Object element : elements) {
          DefaultMutableTreeNode childNode = constructStateNode(Integer.toString(i++), arrayInfo.getElementType().getKind(), element);
          if (childNode != null) {
            thisNode.add(childNode);
          }
        }
        break;

      case Struct:
        assert (value instanceof Struct);
        Struct struct = (Struct)value;
        thisNode = new StateTreeNode(name, null);
        for (Field field : struct.fields) {
          DefaultMutableTreeNode childNode = constructStateNode(field.info.getName(), field.info.getType().getKind(), field.value);
          if (childNode != null) {
            thisNode.add(childNode);
          }
        }
        break;

      case Class:
        assert (value instanceof com.android.tools.idea.editors.gfxtrace.schema.Class);
        com.android.tools.idea.editors.gfxtrace.schema.Class gfxClass = (com.android.tools.idea.editors.gfxtrace.schema.Class)value;
        thisNode = new StateTreeNode(name, null);
        for (Field field : gfxClass.fields) {
          DefaultMutableTreeNode childNode = constructStateNode(field.info.getName(), field.info.getType().getKind(), field.value);
          if (childNode != null) {
            thisNode.add(childNode);
          }
        }
        break;

      case Map:
        assert (value instanceof Map);
        Map map = (Map)value;
        thisNode = new StateTreeNode(name, null);
        for (MapEntry mapEntry : map.elements) {
          DefaultMutableTreeNode childNode = constructStateNode(mapEntry.key.toString(), map.info.getValueType().getKind(), mapEntry.value);
          if (childNode != null) {
            thisNode.add(childNode);
          }
        }
        break;

      case Memory:
        // Skip, since this is not supported at the moment.
        thisNode = null;
        break;

      case Any:
        // Skip, since this is not supported at the moment.
        thisNode = null;
        break;

      default:
        throw new RuntimeException("Attempting to decode unsupported types.");
    }

    return thisNode;
  }

  @Override
  public void startLoad() {
    myTree.getEmptyText().setText("");
  }

  @Override
  public void commitData(@NotNull GfxContextChangeState state) {
    myStateHierarchy = state.myCaptureChangeState.mySchema.getState();
    myTree.getEmptyText().setText("Select an atom");
  }

  public void updateTreeModelFromAtomId(final long atomId) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert (myEditor.getCaptureId() != null);
    assert (myEditor.getContext() != null);
    assert (myStateHierarchy != null);

    final Client client = myEditor.getClient();
    final StructInfo stateHierarchy = myStateHierarchy;
    final CaptureId captureId = myEditor.getCaptureId();
    final int contextId = myEditor.getContext();
    myAtomicAtomId.set(atomId);

    if (!myLoadingPanel.isLoading()) {
      myTree.getEmptyText().setText("");
      myLoadingPanel.startLoading();
    }

    ListenableFuture<TreeNode> nodeFuture = myEditor.getService().submit(new Callable<TreeNode>() {
      @Override
      @Nullable
      public TreeNode call() throws Exception {
        BinaryId binaryId = client.GetState(captureId, contextId, atomId).get();
        Binary stateBinary = client.ResolveBinary(binaryId).get();
        Struct stateStruct = (Struct)Unpack.Type(stateHierarchy, new Decoder(new ByteArrayInputStream(stateBinary.getData())));
        return constructStateNode(stateStruct.info.getName(), TypeKind.Struct, stateStruct);
      }
    });
    Futures.addCallback(nodeFuture, new FutureCallback<TreeNode>() {
      @Override
      public void onSuccess(@Nullable TreeNode result) {
        if (myAtomicAtomId.get() != atomId) {
          return;
        }

        myTree.setModel(new DefaultTreeModel(result));
        myTree.updateUI();
        myLoadingPanel.stopLoading();
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myLoadingPanel.stopLoading();
        LOG.error(t);
      }
    }, EdtExecutor.INSTANCE);
  }

  @Override
  public void clear() {
    myStateHierarchy = null;
    clearCache();
  }

  @Override
  public void clearCache() {
    myAtomicAtomId.set(-1);
    myTree.setModel(null);
    myTree.updateUI();
  }
}
