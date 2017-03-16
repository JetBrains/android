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

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.apk.dex.DexFiles;
import com.android.tools.idea.apk.viewer.ApkFileEditorComponent;
import com.android.tools.idea.apk.viewer.dex.tree.AbstractDexTreeNode;
import com.android.tools.idea.apk.viewer.dex.tree.FieldTreeNode;
import com.android.tools.idea.apk.viewer.dex.tree.PackageTreeNode;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.proguard.ProguardUsagesMap;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.util.concurrent.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DexFileViewer implements ApkFileEditorComponent {
  private final Disposable myDisposable;
  private final JBLoadingPanel myLoadingPanel;

  private final Tree myTree;
  private final JPanel myTopPanel;

  @NotNull private final VirtualFile myDexFile;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myApkFolder;
  private final FilteredTreeModel myFilteredTreeModel;

  @Nullable private ProguardMappings myProguardMappings;
  private boolean myDeobfuscateNames;


  public DexFileViewer(@NotNull Project project, @NotNull VirtualFile dexFile, @NotNull VirtualFile apkFolder) {
    myDexFile = dexFile;
    myProject = project;
    myApkFolder = apkFolder;

    //noinspection Convert2Lambda // we need a new instance of this disposable every time, not just a lambda method
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myDisposable);
    myLoadingPanel.startLoading();

    myFilteredTreeModel = new FilteredTreeModel(new DefaultTreeModel(new LoadingNode()));
    FilteredTreeModel.FilterOptions filterOptions = myFilteredTreeModel.getFilterOptions();

    myTree = new Tree(myFilteredTreeModel);
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    new TreeSpeedSearch(myTree, path -> {
      Object o = path.getLastPathComponent();
      if (!(o instanceof AbstractDexTreeNode)) {
        return "";
      }

      AbstractDexTreeNode node = (AbstractDexTreeNode)o;
      return node.getName();
    }, true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Class")
                   .setPreferredWidth(500)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(AbstractDexTreeNode::getName).reversed())
                   .setRenderer(new DexTreeNodeRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Defined Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(AbstractDexTreeNode::getDefinedMethodsCount))
                   .setRenderer(new MethodCountRenderer(true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Referenced Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(AbstractDexTreeNode::getMethodRefCount))
                   .setRenderer(new MethodCountRenderer(false)));

    builder.setTreeSorter((Comparator<AbstractDexTreeNode> comparator, SortOrder order) -> {
      if (comparator != null){
        comparator = comparator.reversed();
        Object root = myFilteredTreeModel.getRoot();
        if (root instanceof AbstractDexTreeNode) {
          TreePath selectionPath = myTree.getSelectionPath();
          ((AbstractDexTreeNode)root).sort(comparator);
          myFilteredTreeModel.reload();
          myTree.setSelectionPath(selectionPath);
          myTree.scrollPathToVisible(selectionPath);
        }
      }
    });

    JComponent columnTree = builder.build();
    myLoadingPanel.add(columnTree, BorderLayout.CENTER);
    myTopPanel = new JPanel(new BorderLayout());
    myLoadingPanel.add(myTopPanel, BorderLayout.NORTH);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new ShowFieldsAction(filterOptions));
    actionGroup.add(new ShowMethodsAction(filterOptions));
    actionGroup.add(new ShowReferencedAction(filterOptions));
    actionGroup.addSeparator();
    actionGroup.add(new ShowRemovedNodesAction(filterOptions));
    actionGroup.add(new DeobfuscateNodesAction());
    actionGroup.add(new LoadProguardAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    myTopPanel.add(toolbar.getComponent(), BorderLayout.WEST);

    initDex();
  }

  public void selectProguardMapping() {
    SelectProguardMapsDialog dialog = new SelectProguardMapsDialog(myProject, myApkFolder);
    if (!dialog.showAndGet()) { // user cancelled
      return;
    }

    ProguardMappingFiles mappingFiles = dialog.getMappingFiles();

    List<String> loaded = new ArrayList<>(3);
    List<String> errors = new ArrayList<>(3);

    VirtualFile mappingFile = mappingFiles.mappingFile;
    ProguardMap proguardMap = new ProguardMap();
    if (mappingFile != null) {
      try {
        proguardMap.readFromReader(new InputStreamReader(mappingFile.getInputStream(), Charsets.UTF_8));
        loaded.add(mappingFile.getName());
      }
      catch (IOException | ParseException e) {
        errors.add(mappingFile.getName());
        proguardMap = null;
      }
    }

    VirtualFile seedsFile = mappingFiles.seedsFile;
    ProguardSeedsMap seeds = null;
    if (seedsFile != null) {
      try {
        seeds = ProguardSeedsMap.parse(new InputStreamReader(seedsFile.getInputStream(), Charsets.UTF_8));
        loaded.add(seedsFile.getName());
      }
      catch (IOException e) {
        errors.add(seedsFile.getName());
      }
    }

    //automatically enable deobfuscation if loading mapping file for the first time
    if ((myProguardMappings == null || myProguardMappings.map == null) && proguardMap != null) {
      myDeobfuscateNames = true;
    }

    VirtualFile usageFile = mappingFiles.usageFile;
    ProguardUsagesMap usage = null;
    if (usageFile != null) {
      try {
        usage = ProguardUsagesMap.parse(new InputStreamReader(usageFile.getInputStream(), Charsets.UTF_8));
        loaded.add(usageFile.getName());
      }
      catch (IOException e) {
        errors.add(usageFile.getName());
      }
    }

    myProguardMappings = loaded.isEmpty() ? null : new ProguardMappings(proguardMap, seeds, usage);
    if (errors.isEmpty() && loaded.isEmpty()) {
      Messages.showWarningDialog("No Proguard mapping files found. The filenames must match one of: mapping.txt, seeds.txt, usage.txt",
                                 "Load Proguard Mappings...");
    }
    else if (errors.isEmpty()) {
      Messages.showInfoMessage("Successfully loaded maps from: " + StringUtil.join(loaded, ", "),
                               "Load Proguard Mappings...");
    }
    else {
      Messages.showErrorDialog("Successfully loaded maps from: " + StringUtil.join(loaded, ",") + "\n"
                               + "There were problems loading: " + StringUtil.join(errors, ", "),
                               "Load Proguard Mappings...");
    }

    initDex();
  }

  public void initDex() {
    ListeningExecutorService pooledThreadExecutor = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);
    ListenableFuture<DexBackedDexFile> dexFileFuture = pooledThreadExecutor.submit(() -> DexFiles.getDexFile(myDexFile));

    ListenableFuture<PackageTreeNode> treeNodeFuture = Futures.transform(dexFileFuture, new Function<DexBackedDexFile, PackageTreeNode>() {
      @javax.annotation.Nullable
      @Override
      public PackageTreeNode apply(@javax.annotation.Nullable DexBackedDexFile input) {
        assert input != null;
        PackageTreeCreator treeCreator = new PackageTreeCreator(myProguardMappings, myDeobfuscateNames);
        return treeCreator.constructPackageTree(input);
      }
    }, pooledThreadExecutor);

    Futures.addCallback(treeNodeFuture, new FutureCallback<PackageTreeNode>() {
      @Override
      public void onSuccess(PackageTreeNode result) {
        myLoadingPanel.stopLoading();
        myTree.setRootVisible(false);
        myFilteredTreeModel.setRoot(result);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myLoadingPanel.stopLoading();
      }
    }, EdtExecutor.INSTANCE);

    ListenableFuture<DexFileStats> dexStatsFuture = Futures.transform(dexFileFuture, new Function<DexBackedDexFile, DexFileStats>() {
      @javax.annotation.Nullable
      @Override
      public DexFileStats apply(@javax.annotation.Nullable DexBackedDexFile input) {
        assert input != null;
        return DexFileStats.create(input);
      }
    }, pooledThreadExecutor);

    SimpleColoredComponent titleComponent = new SimpleColoredComponent();
    Futures.addCallback(dexStatsFuture, new FutureCallback<DexFileStats>() {
      @Override
      public void onSuccess(DexFileStats result) {
        titleComponent.setIcon(AllIcons.General.Information);
        titleComponent.append("This dex file defines ");
        titleComponent.append(Integer.toString(result.classCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        titleComponent.append(" classes with ");
        titleComponent.append(Integer.toString(result.definedMethodCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        titleComponent.append(" methods, and references ");
        titleComponent.append(Integer.toString(result.referencedMethodCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        titleComponent.append(" methods.");
        myTopPanel.add(titleComponent, BorderLayout.EAST);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        titleComponent.setIcon(AllIcons.General.Error);
        titleComponent.append("Error parsing dex file: " + t.getMessage());
        myTopPanel.add(titleComponent, BorderLayout.EAST);
      }
    }, EdtExecutor.INSTANCE);
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

  private static class DexTreeNodeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof AbstractDexTreeNode)) {
        return;
      }

      AbstractDexTreeNode node = (AbstractDexTreeNode)value;

      if (node.isSeed()) {
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));
      }
      else if (node.isRemoved()) {
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT | SimpleTextAttributes.STYLE_ITALIC, null));
      }
      else if (!node.hasClassDefinition()) {
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, null));
      }
      else {
        append(node.getName());
      }

      setIcon(node.getIcon());
    }
  }

  private static class MethodCountRenderer extends ColoredTreeCellRenderer {
    private final boolean myShowDefinedCount;

    public MethodCountRenderer(boolean showDefinedCount) {
      myShowDefinedCount = showDefinedCount;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof AbstractDexTreeNode && !(value instanceof FieldTreeNode)) {
        AbstractDexTreeNode node = (AbstractDexTreeNode)value;
        int count = myShowDefinedCount ? node.getDefinedMethodsCount() : node.getMethodRefCount();
        append(Integer.toString(count));
      }
    }
  }

  private static class ShowFieldsAction extends ToggleAction {

    @NotNull private final FilteredTreeModel.FilterOptions myFilterOptions;

    public ShowFieldsAction(@NotNull FilteredTreeModel.FilterOptions options) {
      super("Show fields", "Toggle between show/hide fields", PlatformIcons.FIELD_ICON);
      myFilterOptions = options;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myFilterOptions.isShowFields();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myFilterOptions.setShowFields(state);
    }
  }

  private static class ShowMethodsAction extends ToggleAction {

    @NotNull private final FilteredTreeModel.FilterOptions myFilterOptions;

    public ShowMethodsAction(@NotNull FilteredTreeModel.FilterOptions options) {
      super("Show methods", "Toggle between show/hide methods", PlatformIcons.METHOD_ICON);
      myFilterOptions = options;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myFilterOptions.isShowMethods();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myFilterOptions.setShowMethods(state);
    }
  }

  private static class ShowReferencedAction extends ToggleAction {

    @NotNull private final FilteredTreeModel.FilterOptions myFilterOptions;

    public ShowReferencedAction(@NotNull FilteredTreeModel.FilterOptions options) {
      super("Show referenced-only nodes", "Toggle between show/hide referenced-only nodes", AllIcons.ObjectBrowser.ShowMembers);
      myFilterOptions = options;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myFilterOptions.isShowReferencedNodes();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myFilterOptions.setShowReferencedNodes(state);
    }
  }

  private class ShowRemovedNodesAction extends ToggleAction {

    @NotNull private final FilteredTreeModel.FilterOptions myFilterOptions;

    public ShowRemovedNodesAction(@NotNull
                                    FilteredTreeModel.FilterOptions options) {
      super("Show removed nodes", "Toggle between show/hide nodes removed by Proguard", AllIcons.ObjectBrowser.CompactEmptyPackages);
      myFilterOptions = options;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myFilterOptions.isShowRemovedNodes();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myFilterOptions.setShowRemovedNodes(state);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myProguardMappings != null && myProguardMappings.usage != null);
    }
  }

  private class DeobfuscateNodesAction extends ToggleAction {

    public DeobfuscateNodesAction() {
      super("Deobfuscate names", "Deobfuscate names using Proguard mapping", AllIcons.ObjectBrowser.AbbreviatePackageNames);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myDeobfuscateNames;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myDeobfuscateNames = state;
      initDex();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myProguardMappings != null && myProguardMappings.map != null);
    }
  }

  private class LoadProguardAction extends AnAction {
    public LoadProguardAction() {
      super("Load Proguard mappings...", null, EmptyIcon.ICON_0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      selectProguardMapping();
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      if (myProguardMappings != null) {
        e.getPresentation().setText("Change Proguard mappings...");
      }
      else {
        e.getPresentation().setText("Load Proguard mappings...");
      }
    }
  }
}

