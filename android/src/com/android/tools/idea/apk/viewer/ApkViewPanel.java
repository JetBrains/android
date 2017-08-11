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
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.apk.analyzer.*;
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.google.common.base.Function;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ApkAnalyzerStats;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private JButton myCompareWithButton;
  private Tree myTree;

  private DefaultTreeModel myTreeModel;
  private Listener myListener;

  public interface Listener {
    void selectionChanged(@Nullable ArchiveTreeNode[] entry);
    void selectApkAndCompare();
  }

  public ApkViewPanel(@NotNull ApkParser apkParser) {
    // construct the main tree along with the uncompressed sizes
    ListenableFuture<ArchiveNode> treeStructureFuture = apkParser.constructTreeStructure();
    FutureCallBackAdapter<ArchiveNode> setRootNode = new FutureCallBackAdapter<ArchiveNode>() {
      @Override
      public void onSuccess(ArchiveNode result) {
        setRootNode(result);
      }
    };
    Futures.addCallback(treeStructureFuture, setRootNode, EdtExecutor.INSTANCE);

    // once we have the tree, kick off computation of the compressed archive, and once its available, refresh the tree
    ListenableFuture<ArchiveNode> compressedTreeFuture =
      Futures.transform(treeStructureFuture, (Function<ArchiveNode, ArchiveNode>)input -> {
        assert input != null;
        return apkParser.updateTreeWithDownloadSizes(input);
      }, PooledThreadExecutor.INSTANCE);
    Futures.addCallback(compressedTreeFuture, new FutureCallBackAdapter<ArchiveNode>() {
      @Override
      public void onSuccess(ArchiveNode result) {
        ArchiveTreeStructure.sort(result, (o1, o2) -> Longs.compare(o2.getData().getDownloadFileSize(), o1.getData().getDownloadFileSize()));
        refreshTree();
      }
    }, EdtExecutor.INSTANCE);

    mySizeComponent.setToolTipText(AndroidBundle.message("apk.viewer.size.types.tooltip"));
    myContainer.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    myCompareWithButton.addActionListener(e -> {
      if (myListener != null) {
        myListener.selectApkAndCompare();
      }
    });

    // identify and set the application name and version
    myNameAsyncIcon.setVisible(true);
    myNameComponent.append("Parsing Manifest");

    //find a suitable archive that has an AndroidManifest.xml file in the root ("/")
    //for APKs, this will always be the APK itself
    //for ZIP files (AIA bundles), this will be the first found APK using breadth-first search
    ListenableFuture<AndroidApplicationInfo> applicationInfo =
      Futures.transformAsync(treeStructureFuture,
                             input -> {
                               assert input != null;
                               return apkParser.getApplicationInfo(Archives.getFirstManifestArchive(input));
                             }, PooledThreadExecutor.INSTANCE);

    Futures.addCallback(applicationInfo, new FutureCallBackAdapter<AndroidApplicationInfo>() {
      @Override
      public void onSuccess(AndroidApplicationInfo result) {
        setAppInfo(result);
      }
    }, EdtExecutor.INSTANCE);


    // obtain and set the download size
    mySizeAsyncIcon.setVisible(true);
    mySizeComponent.append("Estimating download size..");
    ListenableFuture<Long> uncompressedApkSize = apkParser.getUncompressedApkSize();
    ListenableFuture<Long> compressedFullApkSize = apkParser.getCompressedFullApkSize();
    Futures.addCallback(Futures.successfulAsList(uncompressedApkSize, compressedFullApkSize),
                        new FutureCallBackAdapter<List<Long>>() {
                          @Override
                          public void onSuccess(List<Long> result) {
                            if (result != null) {
                              long uncompressed = result.get(0);
                              Long compressed = result.get(1);
                              setApkSizes(uncompressed, compressed == null ? 0 : compressed.longValue());
                            }
                          }
                        }, EdtExecutor.INSTANCE);

    Futures.addCallback(Futures.allAsList(uncompressedApkSize, compressedFullApkSize, applicationInfo),
                        new FutureCallBackAdapter<List<Object>>() {
                          @Override
                          public void onSuccess(@Nullable List<Object> result) {
                            if (result == null) {
                              return;
                            }

                            int size = result.size();
                            long uncompressed = size > 0 && result.get(0) instanceof Long ? (Long)result.get(0) : -1;
                            long compressed = size > 1 && result.get(1) instanceof Long ? (Long)result.get(1) : -1;
                            String applicationId =
                              size > 2 && result.get(2) instanceof AndroidApplicationInfo ? ((AndroidApplicationInfo)result
                                .get(2)).packageId : "unknown";

                            UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                                             .setKind(AndroidStudioEvent.EventKind.APK_ANALYZER_STATS)
                                                             .setProjectId(AnonymizerUtil.anonymizeUtf8(applicationId))
                                                             .setApkAnalyzerStats(
                                                               ApkAnalyzerStats.newBuilder().setCompressedSize(compressed)
                                                                 .setUncompressedSize(uncompressed)
                                                                 .build()));
                          }
                        });
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
      Object lastPathComponent = path.getLastPathComponent();
      if (!(lastPathComponent instanceof ArchiveTreeNode)) {
        return null;
      }
      return ((ArchiveTreeNode)lastPathComponent).getData().getPath().toString();
    }, true);

    // Provides the percentage of the node size to the total size of the APK
    PercentRenderer.PercentProvider percentProvider = (jTree, value, row) -> {
      if (!(value instanceof ArchiveTreeNode)) {
        return 0;
      }

      ArchiveTreeNode entry = (ArchiveTreeNode)value;
      ArchiveTreeNode rootEntry = (ArchiveTreeNode)jTree.getModel().getRoot();

      if (entry.getData().getDownloadFileSize() < 0) {
        return 0;
      }
      else {
        return (double)entry.getData().getDownloadFileSize() / rootEntry.getData().getDownloadFileSize();
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

  private void setRootNode(@NotNull ArchiveNode root) {
    myTreeModel = new DefaultTreeModel(root);
    myTree.setPaintBusy(root.getData().getDownloadFileSize() < 0);
    myTree.setRootVisible(false);
    myTree.setModel(myTreeModel);
  }

  private void refreshTree() {
    myTree.setPaintBusy(false);
    myTree.removeTreeSelectionListener(this);
    TreePath[] selected = myTree.getSelectionPaths();
    myTreeModel.reload();
    myTree.setSelectionPaths(selected);
    myTree.addTreeSelectionListener(this);
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
    if (myListener != null) {
      TreePath[] paths = ((Tree)e.getSource()).getSelectionPaths();
      ArchiveTreeNode[] components;
      if (paths == null){
        components = null;
      } else {
        components = new ArchiveTreeNode[paths.length];
        for (int i = 0; i < paths.length; i++) {
          if (!(paths[i].getLastPathComponent() instanceof ArchiveTreeNode)) {
            myListener.selectionChanged(null);
            return;
          }
          components[i] = (ArchiveTreeNode)paths[i].getLastPathComponent();
        }
      }
      myListener.selectionChanged(components);
    }
  }

  public static String getHumanizedSize(long sizeInBytes) {
    long kilo = 1024;
    long mega = kilo * kilo;

    DecimalFormat formatter = new DecimalFormat("#.#");

    int sign = sizeInBytes < 0 ? -1 : 1;

    sizeInBytes = Math.abs(sizeInBytes);

    if (sizeInBytes > mega) {
      return formatter.format((sign * sizeInBytes) / (double)mega) + " MB";
    }
    else if (sizeInBytes > kilo) {
      return formatter.format((sign * sizeInBytes) / (double)kilo) + " KB";
    }
    else {
      return (sign * sizeInBytes) + " B";
    }
  }

  public static class FutureCallBackAdapter<V> implements FutureCallback<V> {
    @Override
    public void onSuccess(V result) {
    }

    @Override
    public void onFailure(@NotNull Throwable t) {
    }
  }

  public static class NameRenderer extends ColoredTreeCellRenderer {
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
      if (!(value instanceof ArchiveNode)) {
        append(value.toString());
        return;
      }

      ArchiveEntry entry = ((ArchiveNode)value).getData();

      Path path = entry.getPath();
      setIcon(getIconFor(path));

      Path base = path.getFileName();
      String name = base == null ? "" : base.toString();
      name = StringUtil.trimEnd(name, "/");

      SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), name, attr.getStyle(), attr.getFgColor(),
                                 attr.getBgColor(), this);
    }

    @NotNull
    private static Icon getIconFor(@NotNull Path path) {
      Path base = path.getFileName();
      String fileName = base == null ? "" : base.toString();

      if (!Files.isDirectory(path)) {
        if (fileName == SdkConstants.FN_ANDROID_MANIFEST_XML) {
          return AndroidIcons.ManifestFile;
        }
        else if (fileName.endsWith(SdkConstants.DOT_DEX)) {
          return AllIcons.FileTypes.JavaClass;
        }

        FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
        Icon ftIcon = fileType.getIcon();
        return ftIcon == null ? AllIcons.FileTypes.Any_type : ftIcon;
      }
      else {
        fileName = StringUtil.trimEnd(fileName, "/");
        if (fileName.equals(SdkConstants.FD_RES)) {
          return AllIcons.Modules.ResourcesRoot;
        }
        return AllIcons.Modules.SourceFolder;
      }
    }
  }

  private static class SizeRenderer extends ColoredTreeCellRenderer {
    private final boolean myUseDownloadSize;

    public SizeRenderer(boolean useDownloadSize) {
      myUseDownloadSize = useDownloadSize;
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
      if (!(value instanceof ArchiveTreeNode)) {
        return;
      }

      ArchiveEntry data = ((ArchiveTreeNode)value).getData();
      long size = myUseDownloadSize ? data.getDownloadFileSize() : data.getRawFileSize();
      if (size > 0) {
        append(getHumanizedSize(size));
      }
    }
  }
}
