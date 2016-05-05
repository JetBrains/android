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
package com.android.tools.idea.apk.viewer.dex;

import com.android.tools.idea.apk.viewer.ApkFileEditorComponent;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.allocations.ColumnTreeBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public class DexFileViewer implements ApkFileEditorComponent {
  private final Disposable myDisposable;
  private final JBLoadingPanel myLoadingPanel;

  private final Tree myTree;

  public DexFileViewer(@NotNull VirtualFile dexFile) {
    //noinspection Convert2Lambda // we need a new instance of this disposable every time, not just a lambda method
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myDisposable);
    myLoadingPanel.startLoading();

    DefaultTreeModel treeModel = new DefaultTreeModel(new LoadingNode());

    myTree = new Tree(treeModel);
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    new TreeSpeedSearch(myTree, path -> {
      Object o = path.getLastPathComponent();
      if (!(o instanceof PackageTreeNode)) {
        return "";
      }

      PackageTreeNode node = (PackageTreeNode)o;
      return node.getName();
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Class")
                   .setPreferredWidth(500)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new PackageTreeNodeRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Referenced Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MethodCountRenderer()));
    JComponent columnTree = builder.build();
    myLoadingPanel.add(columnTree, BorderLayout.CENTER);

    DexParser dexParser = new DexParser(MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE), dexFile);
    ListenableFuture<PackageTreeNode> future = dexParser.constructMethodRefCountTree();
    Futures.addCallback(future, new FutureCallback<PackageTreeNode>() {
      @Override
      public void onSuccess(PackageTreeNode result) {
        myTree.setModel(new DefaultTreeModel(result));
        myTree.setRootVisible(false);
        myLoadingPanel.stopLoading();
      }

      @Override
      public void onFailure(Throwable t) {
      }
    }, EdtExecutor.INSTANCE);

    SimpleColoredComponent titleComponent = new SimpleColoredComponent();
    Futures.addCallback(dexParser.getDexFileStats(), new FutureCallback<DexParser.DexFileStats>() {
      @Override
      public void onSuccess(DexParser.DexFileStats result) {
        titleComponent.setIcon(AllIcons.General.Information);
        titleComponent.append("This dex file references ");
        titleComponent.append(Integer.toString(result.referencedMethodCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        titleComponent.append(" methods");
      }

      @Override
      public void onFailure(Throwable t) {
      }
    });
    myLoadingPanel.add(titleComponent, BorderLayout.NORTH);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLoadingPanel;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposable);
  }

  private static class PackageTreeNodeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof PackageTreeNode)) {
        return;
      }

      PackageTreeNode node = (PackageTreeNode)value;
      append(node.getName());
      switch (node.getNodeType()) {
        case PACKAGE:
          setIcon(PlatformIcons.PACKAGE_ICON);
          break;
        case CLASS:
          setIcon(PlatformIcons.CLASS_ICON);
          break;
        case METHOD:
          setIcon(PlatformIcons.METHOD_ICON);
          break;
      }
    }
  }

  private static class MethodCountRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof PackageTreeNode) {
        PackageTreeNode node = (PackageTreeNode)value;
        append(Integer.toString(node.getMethodRefCount()));
      }
    }
  }
}
