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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.allocations.ColumnTreeBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.text.DecimalFormat;
import java.util.List;

public class ApkViewPanel implements TreeSelectionListener {
  private JPanel myContainer;
  @SuppressWarnings("unused") // added to the container in the form
  private JScrollPane myColumnTreePane;
  private SimpleColoredComponent myNameComponent;
  private SimpleColoredComponent mySizeComponent;
  private AnimatedIcon myNameAsyncIcon;
  private AnimatedIcon mySizeAsyncIcon;
  private Tree myTree;

  private DefaultTreeModel myTreeModel;
  private Listener myListener;

  public interface Listener {
    void selectionChanged(@Nullable ApkEntry entry);
  }

  public ApkViewPanel(@NotNull ApkParser apkParser) {
    // construct the main tree along with the uncompressed sizes
    ListenableFuture<DefaultMutableTreeNode> treeStructureFuture = apkParser.constructTreeStructure();
    FutureCallBackAdapter<DefaultMutableTreeNode> setRootNode = new FutureCallBackAdapter<DefaultMutableTreeNode>() {
      @Override
      public void onSuccess(DefaultMutableTreeNode result) {
        setRootNode(result);
      }
    };
    Futures.addCallback(treeStructureFuture, setRootNode, EdtExecutor.INSTANCE);

    // in parallel, kick off computation of the compressed archive, and once its available, refresh the tree
    ListenableFuture<DefaultMutableTreeNode> compressedTreeFuture = apkParser.constructTreeStructureWithCompressedSizes();
    FutureCallBackAdapter<DefaultMutableTreeNode> refreshTree = new FutureCallBackAdapter<DefaultMutableTreeNode>() {
      @Override
      public void onSuccess(DefaultMutableTreeNode result) {
        refreshTree();
      }
    };
    Futures.addCallback(compressedTreeFuture, refreshTree, EdtExecutor.INSTANCE);

    mySizeComponent.setToolTipText(AndroidBundle.message("apk.viewer.size.types.tooltip"));
    myContainer.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // identify and set the application name and version
    myNameAsyncIcon.setVisible(true);
    myNameComponent.append("Parsing Manifest");
    Futures.addCallback(apkParser.getApplicationInfo(), new FutureCallBackAdapter<AndroidApplicationInfo>() {
      @Override
      public void onSuccess(AndroidApplicationInfo result) {
        setAppInfo(result);
      }
    }, EdtExecutor.INSTANCE);

    // obtain and set the download size
    mySizeAsyncIcon.setVisible(true);
    mySizeComponent.append("Estimating download size..");
    Futures.addCallback(Futures.successfulAsList(apkParser.getUncompressedApkSize(), apkParser.getCompressedFullApkSize()),
                        new FutureCallBackAdapter<List<Long>>() {
                          @Override
                          public void onSuccess(List<Long> result) {
                            Long uncompressed = result.get(0);
                            Long compressed = result.get(1);
                            setApkSizes(uncompressed, compressed);
                          }
                        }, EdtExecutor.INSTANCE);
  }

  private void createUIComponents() {
    myNameAsyncIcon = new AsyncProcessIcon("aapt xmltree manifest");
    mySizeAsyncIcon = new AsyncProcessIcon("estimating apk size");

    myTreeModel = new DefaultTreeModel(new LoadingNode());
    myTree = new Tree(myTreeModel);
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true); // show root node only when showing LoadingNode
    myTree.setPaintBusy(true);

    TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myTree, path -> {
      ApkEntry e = ApkEntry.fromNode(path.getLastPathComponent());
      if (e == null) {
        return null;
      }

      return e.file.getPath();
    }, true);

    // Provides the percentage of the node size to the total size of the APK
    PercentRenderer.PercentProvider percentProvider = (jTree, value, row) -> {
      ApkEntry entry = ApkEntry.fromNode(value);
      ApkEntry rootEntry = ApkEntry.fromNode(jTree.getModel().getRoot());
      if (entry == null || rootEntry == null) {
        return 0;
      }
      else if (!entry.isCompressedSizeKnown()) {
        return 0;
      }
      else {
        return (double)entry.getCompressedSize() / rootEntry.size;
      }
    };

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("File")
                   .setPreferredWidth(600)
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setRenderer(new NameRenderer(treeSpeedSearch)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Raw File Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setRenderer(new SizeRenderer(false)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Download Size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setRenderer(new SizeRenderer(true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("% of Total Download size")
                   .setPreferredWidth(150)
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setRenderer(new PercentRenderer(percentProvider))
      );
    myColumnTreePane = (JScrollPane)builder.build();
    myTree.addTreeSelectionListener(this);
  }

  public void setListener(@NotNull Listener listener) {
    myListener = listener;
  }

  private void setRootNode(@NotNull DefaultMutableTreeNode root) {
    myTreeModel = new DefaultTreeModel(root);

    ApkEntry entry = ApkEntry.fromNode(root);
    assert entry != null;

    myTree.setPaintBusy(!entry.isCompressedSizeKnown());
    myTree.setRootVisible(false);
    myTree.setModel(myTreeModel);
  }

  private void refreshTree() {
    myTree.setPaintBusy(false);
    myTreeModel.reload();
  }

  private void setApkSizes(long uncompressed, long compressedFullApk) {
    mySizeComponent.clear();

    if (mySizeAsyncIcon != null) {
      mySizeAsyncIcon.setVisible(false);
      Disposer.dispose(mySizeAsyncIcon);
      mySizeAsyncIcon = null;
    }

    mySizeComponent.setIcon(AllIcons.General.BalloonInformation);
    mySizeComponent.append("Raw File Size: ");
    mySizeComponent.append(getHumanizedSize(uncompressed), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    mySizeComponent.append(", Download Size: ");
    mySizeComponent.append(getHumanizedSize(compressedFullApk), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  private void setAppInfo(@NotNull AndroidApplicationInfo appInfo) {
    myNameComponent.clear();

    if (myNameAsyncIcon != null) {
      myNameAsyncIcon.setVisible(false);
      Disposer.dispose(myNameAsyncIcon);
      myNameAsyncIcon = null;
    }

    myNameComponent.append(appInfo.packageId, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    myNameComponent.append(" (version ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    myNameComponent.append(appInfo.versionName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myNameComponent.append(")", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @NotNull
  public JComponent getContainer() {
    return myContainer;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    ApkEntry selectedEntry = ApkEntry.fromNode(e.getPath().getLastPathComponent());
    if (myListener != null) {
      myListener.selectionChanged(selectedEntry);
    }
  }

  private static String getHumanizedSize(long sizeInBytes) {
    long kilo = 1024;
    long mega = kilo * kilo;

    DecimalFormat formatter = new DecimalFormat("#.#");

    if (sizeInBytes > mega) {
      return formatter.format((double)sizeInBytes / mega) + " MB";
    }
    else if (sizeInBytes > kilo) {
      return formatter.format((double)sizeInBytes / kilo) + " KB";
    }
    else {
      return sizeInBytes + " B";
    }
  }

  private static class FutureCallBackAdapter<V> implements FutureCallback<V> {
    @Override
    public void onSuccess(V result) {
    }

    @Override
    public void onFailure(Throwable t) {
    }
  }

  private static class NameRenderer extends ColoredTreeCellRenderer {
    private final TreeSpeedSearch mySpeedSearch;

    public NameRenderer(@NotNull TreeSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
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
      if (entry == null) {
        if (value instanceof LoadingNode) {
          append(value.toString());
        }
        return;
      }

      VirtualFile file = entry.file;
      setIcon(getIconFor(file));

      SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), file.getName(), attr.getStyle(), attr.getFgColor(),
                                 attr.getBgColor(), this);
    }

    @NotNull
    private static Icon getIconFor(@NotNull VirtualFile file) {
      String fileName = file.getName();

      if (!file.isDirectory()) {
        if (fileName == SdkConstants.FN_ANDROID_MANIFEST_XML) {
          return AndroidIcons.ManifestFile;
        }
        else if (fileName.endsWith(SdkConstants.DOT_DEX)) {
          return AllIcons.FileTypes.JavaClass;
        }
        Icon ftIcon = file.getFileType().getIcon();
        return ftIcon == null ? AllIcons.FileTypes.Any_type : ftIcon;
      }
      else {
        if (fileName.equals(SdkConstants.FD_RES)) {
          return AllIcons.Modules.ResourcesRoot;
        }
        return AllIcons.Modules.SourceFolder;
      }
    }
  }

  private static class SizeRenderer extends ColoredTreeCellRenderer {
    private final boolean myUseCompressedSize;

    public SizeRenderer(boolean useCompressedSize) {
      myUseCompressedSize = useCompressedSize;
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

      setTextAlign(SwingConstants.RIGHT);

      if (myUseCompressedSize) {
        if (entry.isCompressedSizeKnown()) {
          append(getHumanizedSize(entry.getCompressedSize()));
        }
      }
      else {
        append(getHumanizedSize(entry.size));
      }
    }
  }
}
