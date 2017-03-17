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
import com.android.tools.idea.apk.viewer.ApkFileEditorComponent;
import com.android.tools.idea.ddms.EdtExecutor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public class DexFileViewer implements ApkFileEditorComponent {
  private final Disposable myDisposable;
  private final JBLoadingPanel myLoadingPanel;

  private final Tree myTree;
  private final JPanel myTopPanel;

  @NotNull private final VirtualFile myDexFile;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myApkFolder;

  private final FilteredTreeModel myFilteredTreeModel;


  public DexFileViewer(@NotNull Project project, @NotNull VirtualFile dexFile, @NotNull VirtualFile apkFolder) {
    myProject = project;
    myDexFile = dexFile;
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
                   .setName("Defined Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MethodCountRenderer(true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Referenced Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MethodCountRenderer(false)));

    JComponent columnTree = builder.build();
    myLoadingPanel.add(columnTree, BorderLayout.CENTER);
    myTopPanel = new JPanel(new BorderLayout());
    myLoadingPanel.add(myTopPanel, BorderLayout.NORTH);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new ShowFieldsAction(filterOptions));
    actionGroup.add(new ShowMethodsAction(filterOptions));
    actionGroup.add(new ShowReferencedAction(filterOptions));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    myTopPanel.add(toolbar.getComponent(), BorderLayout.WEST);

    initDex();
  }

  public void initDex(){
    DexParser dexParser = new DexParser(MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE), myDexFile);
    ListenableFuture<PackageTreeNode> future = dexParser.constructMethodRefCountTree();
    Futures.addCallback(future, new FutureCallback<PackageTreeNode>() {
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

    SimpleColoredComponent titleComponent = new SimpleColoredComponent();
    Futures.addCallback(dexParser.getDexFileStats(), new FutureCallback<DexFileStats>() {
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

      if (!node.hasClassDefinition()){
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, null));
      } else {
        append(node.getName());
      }

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
        case FIELD:
          setIcon(PlatformIcons.FIELD_ICON);
          break;
      }
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
      if (value instanceof PackageTreeNode && !((PackageTreeNode)value).getNodeType().equals(PackageTreeNode.NodeType.FIELD)) {
        PackageTreeNode node = (PackageTreeNode)value;
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
}

