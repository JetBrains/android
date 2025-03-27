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
package com.android.tools.idea.apk.viewer.diff;

import com.android.annotations.NonNull;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.util.HumanReadableUtil;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.apk.analyzer.internal.ApkDiffEntry;
import com.android.tools.apk.analyzer.internal.ApkDiffParser;
import com.android.tools.apk.analyzer.internal.ApkEntry;
import com.android.tools.apk.analyzer.internal.ApkFileByFileDiffParser;
import com.android.tools.idea.apk.viewer.ApkViewPanel.FutureCallBackAdapter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.Function;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

public class ApkDiffPanel {

  private static final ListeningExecutorService ourExecutorService = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private JPanel myContainer;
  @SuppressWarnings("unused") // used by the .form file
  private JComponent myColumnTreePane;
  private JCheckBox myCalculateFileByFileCheckBox;

  @NonNull private final VirtualFile myOldApk;
  @NonNull private final VirtualFile myNewApk;

  private Tree myTree;
  private DefaultTreeModel myTreeModel;

  private final AtomicReference<DefaultMutableTreeNode> myFbfDiffTreeNode = new AtomicReference<>(null);

  private static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  private static final int TEXT_RENDERER_VERT_PADDING = 4;

  public ApkDiffPanel(@NotNull VirtualFile oldApk, @NotNull VirtualFile newApk) {
    myOldApk = oldApk;
    myNewApk = newApk;

    setupUI();
    myCalculateFileByFileCheckBox.addItemListener(e -> {
      if (myCalculateFileByFileCheckBox.isSelected()) {
        myCalculateFileByFileCheckBox.setEnabled(false);
        constructFbfTree();
      }
      else {
        constructDiffTree();
      }
    });

    constructDiffTree();
  }

  private void constructFbfTree() {
    DefaultMutableTreeNode node = myFbfDiffTreeNode.get();
    if (node != null) {
      setRootNode(node);
      myCalculateFileByFileCheckBox.setEnabled(true);
      return;
    }

    FileByFileProgressDialog dialog = new FileByFileProgressDialog();
    ListenableFuture<DefaultMutableTreeNode> future = ourExecutorService.submit(() -> {
      Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
      try (ArchiveContext context1 = Archives.open(VfsUtilCore.virtualToIoFile(myOldApk).toPath());
           ArchiveContext context2 = Archives.open(VfsUtilCore.virtualToIoFile(myNewApk).toPath())) {
        return ApkFileByFileDiffParser.createTreeNode(context1, context2, dialog::onUpdate);
      }
      finally {
        dialog.closeDialog();
      }
    });

    Futures.addCallback(
      future,
      new FutureCallback<>() {
        @Override
        public void onSuccess(DefaultMutableTreeNode result) {
          myFbfDiffTreeNode.set(result);
          setRootNode(result);
          myCalculateFileByFileCheckBox.setEnabled(true);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          myCalculateFileByFileCheckBox.setEnabled(true);
          myCalculateFileByFileCheckBox.setSelected(false);
        }
      }, EdtExecutorService.getInstance());
    dialog.showDialog(() -> future.cancel(true));
  }

  private void constructDiffTree() {
    // construct the main tree
    ListenableFuture<DefaultMutableTreeNode> treeStructureFuture = ourExecutorService.submit(() -> {
      try (ArchiveContext archiveContext1 = Archives.open(VfsUtilCore.virtualToIoFile(myOldApk).toPath());
           ArchiveContext archiveContext2 = Archives.open(VfsUtilCore.virtualToIoFile(myNewApk).toPath())) {
        return ApkDiffParser.createTreeNode(archiveContext1, archiveContext2);
      }
    });
    FutureCallBackAdapter<DefaultMutableTreeNode> setRootNode = new FutureCallBackAdapter<>() {
      @Override
      public void onSuccess(DefaultMutableTreeNode result) {
        setRootNode(result);
        myCalculateFileByFileCheckBox.setEnabled(true);
      }
    };
    Futures.addCallback(treeStructureFuture, setRootNode, EdtExecutorService.getInstance());
  }

  private void createUIComponents() {
    myTreeModel = new DefaultTreeModel(new LoadingNode());
    myTree = new Tree(myTreeModel);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true); // show root node only when showing LoadingNode
    myTree.setPaintBusy(true);

    Convertor<TreePath, String> convertor = path -> {
      ApkEntry e = ApkEntry.fromNode(path.getLastPathComponent());
      if (e == null) {
        return null;
      }

      return e.getPath().toString();
    };

    TreeSpeedSearch.installOn(myTree, true, convertor);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("File")
                   .setPreferredWidth(600)
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new NameRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Old Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer(ApkDiffEntry::getOldSize)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("New Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer(ApkDiffEntry::getNewSize)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Diff Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer(ApkEntry::getSize)));
    myColumnTreePane = builder.build();
  }

  @NotNull
  public JComponent getContainer() {
    return myContainer;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  private void setRootNode(@NotNull DefaultMutableTreeNode root) {
    myTreeModel = new DefaultTreeModel(root);

    ApkEntry entry = ApkEntry.fromNode(root);
    assert entry != null;

    myTree.setPaintBusy(false);
    myTree.setRootVisible(true);
    myTree.expandPath(new TreePath(root));
    myTree.setModel(myTreeModel);
  }

  private void setupUI() {
    createUIComponents();
    myContainer = new JPanel();
    myContainer.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myContainer.add(myColumnTreePane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          new Dimension(400, -1), new Dimension(400, 300), null, 0, false));
    myCalculateFileByFileCheckBox = new JCheckBox();
    myCalculateFileByFileCheckBox.setEnabled(false);
    myCalculateFileByFileCheckBox.setText("Show File-By-File patch size (may take a long time)");
    myCalculateFileByFileCheckBox.setToolTipText("This is a size estimation for the update that Play store sends to the device");
    myContainer.add(myCalculateFileByFileCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  public JComponent getRootComponent() { return myContainer; }

  // Duplicated from ApkViewPanel.SizeRenderer until the diff entries are unified into the ArchiveEntry data class.
  public static class SizeRenderer extends ColoredTreeCellRenderer {
    private final Function<ApkEntry, Long> mySizeMapper;

    public SizeRenderer(Function<ApkEntry, Long> sizeMapper) {
      mySizeMapper = sizeMapper;
      setTextAlign(SwingConstants.RIGHT);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      ApkEntry entry = ApkEntry.fromNode(value);
      ApkEntry root = ApkEntry.fromNode(tree.getModel().getRoot());

      if (entry == null || root == null) {
        return;
      }

      append(HumanReadableUtil.getHumanizedSize(mySizeMapper.fun(entry)));
    }
  }

  static class NameRenderer extends ColoredTreeCellRenderer {

    NameRenderer() { }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      ApkEntry entry = ApkEntry.fromNode(value);
      ApkEntry root = ApkEntry.fromNode(tree.getModel().getRoot());

      if (entry == null || root == null) {
        return;
      }

      append(entry.getName());
    }
  }
}
