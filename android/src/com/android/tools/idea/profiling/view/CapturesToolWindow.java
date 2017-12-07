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
package com.android.tools.idea.profiling.view;

import com.android.annotations.Nullable;
import com.android.tools.idea.ddms.hprof.RunHprofConvAndSaveAsAction;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.android.tools.idea.profiling.view.nodes.CaptureNode;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CapturesToolWindow
  implements Disposable, HierarchyListener, CaptureService.CaptureListener, DataProvider, DeleteProvider, BulkFileListener {
  public static final String TREE_NAME = "CapturesPaneTree";

  @NotNull public static final DataKey<Capture[]> CAPTURE_ARRAY = DataKey.create("CaptureArray");

  @NotNull private final AbstractTreeBuilder myBuilder;
  @NotNull private final CapturesTreeStructure myStructure;
  @NotNull private Project myProject;
  @NotNull private SimpleTree myTree;
  @NotNull private JScrollPane myComponent;
  @Nullable private MessageBusConnection myConnection;

  private static final Logger LOG = Logger.getInstance(CapturesToolWindow.class);

  public CapturesToolWindow(@NotNull final Project project) {

    myProject = project;
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new SimpleTree(model);
    myTree.setName(TREE_NAME);
    myTree.setRootVisible(false);
    myComponent = ScrollPaneFactory.createScrollPane(myTree);

    myStructure = new CapturesTreeStructure(myProject);
    myBuilder = new AbstractTreeBuilder(myTree, model, myStructure, null);
    Disposer.register(this, myBuilder);
    Disposer.register(project, this);

    myBuilder.initRootNode();
    myBuilder.getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        myBuilder.getUi().expandAll(null);
      }
    });

    myTree.addHierarchyListener(this);
    CaptureService.getInstance(myProject).addListener(this);

    CaptureService.getInstance(myProject).update();
    myStructure.update();

    myTree.setPopupGroup(getPopupActions(), "Context");
    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, this);
  }

  private ActionGroup getPopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new ExpandAllAction(myTree));
    group.add(new CollapseAllAction(myTree));
    group.addSeparator();
    group.add(new RevealFileAction());
    group.add(new RenameCaptureFileAction(myTree));
    group.add(new DeleteAction());
    group.addSeparator();
    group.add(new RunHprofConvAndSaveAsAction());

    return group;
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void dispose() {
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    CaptureService service = CaptureService.getInstance(myProject);
    VirtualFile captures = service.getCapturesDirectory();
    if (captures == null) {
      if (!service.getCaptures().isEmpty()) {
        queueUpdate();
      }
      return;
    }
    for (VFileEvent event : events) {
      if (event.getFile() != null && VfsUtilCore.isAncestor(captures, event.getFile(), false)) {
        queueUpdate();
        return;
      }
    }
  }

  public void queueUpdate() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        CaptureService.getInstance(myProject).update();
        myStructure.update();
        myBuilder.updateFromRoot();
      }
    });
  }

  @Override
  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
    if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
      // We only want to listen to VFS_CHANGES events when the tool window is opened.
      if (myTree.isShowing()) {
        if (myConnection == null) {
          myConnection = myProject.getMessageBus().connect(myProject);
          myConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
        }
      }
      else {
        if (myConnection != null) {
          myConnection.disconnect();
          myConnection = null;
        }
      }
    }
  }

  @Override
  public void onReady(final Capture capture) {
    myStructure.update();
    myBuilder.updateFromRoot();
    myTree.setSelectedNode(myBuilder, myStructure.getNode(capture), true);
  }

  @NotNull
  private VirtualFile[] getSelectedFiles() {
    CaptureNode[] nodes = getSelectedCaptureNodes();
    VirtualFile[] files = new VirtualFile[nodes.length];
    for (int i = 0; i < nodes.length; ++i) {
      files[i] = nodes[i].getCapture().getFile();
    }
    return files;
  }

  @NotNull
  private Capture[] getSelectedCaptures() {
    CaptureNode[] nodes = getSelectedCaptureNodes();
    Capture[] captures = new Capture[nodes.length];
    for (int i = 0; i < nodes.length; ++i) {
      captures[i] = nodes[i].getCapture();
    }
    return captures;
  }

  @NotNull
  private CaptureNode[] getSelectedCaptureNodes() {
    SimpleNode[] nodes = myTree.getSelectedNodesIfUniform();
    return nodes.length > 0 && nodes[0] instanceof CaptureNode
           ? Arrays.copyOf(nodes, nodes.length, CaptureNode[].class)
           : new CaptureNode[0];
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      VirtualFile[] files = getSelectedFiles();
      return files.length == 1 ? files[0] : null;
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return this;
    }
    else if (CAPTURE_ARRAY.is(dataId)) {
      return getSelectedCaptures();
    }
    return null;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    final VirtualFile[] files = getSelectedFiles();
    if (files.length > 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (VirtualFile file : files) {
            try {
              file.delete(null);
            }
            catch (IOException e) {
              LOG.error("Cannot delete file " + file.getPath());
            }
          }
        }
      });
    }
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return getSelectedFiles().length > 0;
  }
}
