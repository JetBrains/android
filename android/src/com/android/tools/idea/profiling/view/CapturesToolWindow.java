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
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.List;

public class CapturesToolWindow extends BulkFileListener.Adapter implements Disposable, HierarchyListener, CaptureService.CaptureListener {
  @NotNull private final AbstractTreeBuilder myBuilder;
  @NotNull private final CapturesTreeStructure myStructure;
  @NotNull private Project myProject;
  @NotNull private SimpleTree myTree;
  @Nullable private MessageBusConnection myConnection;

  public CapturesToolWindow(@NotNull final Project project) {

    myProject = project;
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new SimpleTree(model);
    myTree.setRootVisible(false);

    myStructure = new CapturesTreeStructure(myProject);
    myBuilder = new AbstractTreeBuilder(myTree, model, myStructure, null);
    Disposer.register(this, myBuilder);

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
  }

  @NotNull
  public JComponent getComponent() {
    return myTree;
  }

  @Override
  public void dispose() {
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
      } else {
        if (myConnection != null) {
          myConnection.disconnect();
          myConnection = null;
        }
      }
    }
  }

  @Override
  public void onCreate(final Capture capture) {
    myStructure.update();
    myBuilder.updateFromRoot();
    myTree.setSelectedNode(myBuilder, myStructure.getNode(capture), true);
  }
}
