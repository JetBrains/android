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
import com.android.tools.adtui.util.HumanReadableUtil;
import com.android.tools.apk.analyzer.FilteredTreeModel;
import com.android.tools.apk.analyzer.dex.DexFileStats;
import com.android.tools.apk.analyzer.dex.DexFiles;
import com.android.tools.apk.analyzer.dex.DexReferences;
import com.android.tools.apk.analyzer.dex.DexViewFilters;
import com.android.tools.apk.analyzer.dex.PackageTreeCreator;
import com.android.tools.apk.analyzer.dex.ProguardMappings;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.apk.analyzer.dex.tree.DexPackageNode;
import com.android.tools.apk.analyzer.internal.ProguardMappingFiles;
import com.android.tools.idea.apk.viewer.ApkFileEditorComponent;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.proguard.ProguardUsagesMap;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.IconManager;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.tree.TreeModelAdapter;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

public class DexFileViewer extends UserDataHolderBase implements ApkFileEditorComponent, FileEditor {
  private final Disposable myDisposable;
  private final JBLoadingPanel myLoadingPanel;

  private final Tree myTree;
  private final JPanel myTopPanel;

  @NotNull private final Path[] myDexFiles;
  @NotNull private final Project myProject;
  @Nullable private final VirtualFile myApkFolder;
  @NotNull private final DexViewFilters myDexFilters;
  private final DexTreeNodeRenderer myDexTreeRenderer;

  @Nullable private ProguardMappings myProguardMappings;
  private boolean myDeobfuscateNames;
  private ListenableFuture<DexReferences> myDexReferences;

  @NotNull public static final NotificationGroup LOGGING_NOTIFICATION =
    NotificationGroup.logOnlyGroup("APK Analyzer (Info)", PluginId.getId("org.jetbrains.android"));
  @NotNull public static final NotificationGroup BALLOON_NOTIFICATION =
    NotificationGroup.balloonGroup("APK Analyzer (Important)", PluginId.getId("org.jetbrains.android"));

  public DexFileViewer(@NotNull Project project, @NotNull Path[] dexFiles, @Nullable VirtualFile apkFolder) {
    myDexFiles = dexFiles;
    myProject = project;
    myApkFolder = apkFolder;

    // we need a new instance of this disposable every time, not just a lambda method
    myDisposable = Disposer.newDisposable();

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myDisposable);
    myLoadingPanel.startLoading();

    myTree = new Tree(new DefaultTreeModel(new LoadingNode()));
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    TreeSpeedSearch.installOn(myTree, true, path -> {
      Object o = path.getLastPathComponent();
      if (!(o instanceof DexElementNode)) {
        return "";
      }

      DexElementNode node = (DexElementNode)o;
      return node.getName();
    });

    myDexTreeRenderer = new DexTreeNodeRenderer();

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Class")
                   .setPreferredWidth(500)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(DexElementNode::getName).reversed())
                   .setRenderer(myDexTreeRenderer))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Defined Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(DexElementNode::getMethodDefinitionsCount))
                   .setRenderer(new MethodCountRenderer(true)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Referenced Methods")
                   .setPreferredWidth(100)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(DexElementNode::getMethodReferencesCount))
                   .setRenderer(new MethodCountRenderer(false)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Size")
                   .setPreferredWidth(50)
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setComparator(Comparator.comparing(DexElementNode::getSize))
                   .setRenderer(new SizeRenderer()));

    builder.setTreeSorter((Comparator<DexElementNode> comparator, SortOrder order) -> {
      if (comparator != null) {
        TreeModel model = myTree.getModel();
        TreePath selectionPath = myTree.getSelectionPath();

        Object root = model.getRoot();
        if (root instanceof DexElementNode) {
          ((DexElementNode)root).sort(comparator.reversed());
        }

        if (model instanceof DefaultTreeModel) {
          ((DefaultTreeModel)model).reload();
        }

        myTree.setSelectionPath(selectionPath);
        myTree.scrollPathToVisible(selectionPath);
      }
    });

    JComponent columnTree = builder.build();
    myLoadingPanel.add(columnTree, BorderLayout.CENTER);
    myTopPanel = new JPanel(new BorderLayout());
    myLoadingPanel.add(myTopPanel, BorderLayout.NORTH);

    myDexFilters = new DexViewFilters();

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new ShowFieldsAction(myTree, myDexFilters));
    actionGroup.add(new ShowMethodsAction(myTree, myDexFilters));
    actionGroup.add(new ShowReferencedAction(myTree, myDexFilters));
    actionGroup.addSeparator();
    actionGroup.add(new ShowRemovedNodesAction(myTree, myDexFilters));
    actionGroup.add(new DeobfuscateNodesAction());
    actionGroup.add(new LoadProguardAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    myTopPanel.add(toolbar.getComponent(), BorderLayout.WEST);

    ActionGroup group = createPopupActionGroup(myTree);
    PopupHandler.installPopupMenu(myTree, group, ActionPlaces.UNKNOWN);

    initDex();
  }

  @NotNull
  private ActionGroup createPopupActionGroup(@NotNull Tree tree) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ShowDisassemblyAction(tree, () -> {
      if (myDeobfuscateNames && myProguardMappings != null) {
        return myProguardMappings.map;
      } else {
        return null;
      }
    }));
    group.add(new ShowReferencesAction(tree, this));
    group.add(new GenerateProguardKeepRuleAction(tree));
    return group;
  }

  public void selectProguardMapping() {
    SelectProguardMapsDialog dialog = new SelectProguardMapsDialog(myProject, myApkFolder);
    try {
      if (!dialog.showAndGet()) { // user cancelled
        return;
      }

      ProguardMappingFiles mappingFiles = dialog.getMappingFiles();
      List<String> loaded = new ArrayList<>(3);
      List<String> errors = new ArrayList<>(3);

      Path mappingFile = mappingFiles.mappingFile;
      ProguardMap proguardMap = new ProguardMap();
      if (mappingFile != null) {
        try {
          proguardMap.readFromReader(new InputStreamReader(Files.newInputStream(mappingFile), StandardCharsets.UTF_8));
          loaded.add(mappingFile.getFileName().toString());
        }
        catch (IOException | ParseException e) {
          errors.add(mappingFile.getFileName().toString());
          proguardMap = null;
        }
      }

      Path seedsFile = mappingFiles.seedsFile;
      ProguardSeedsMap seeds = null;
      if (seedsFile != null) {
        try {
          seeds = ProguardSeedsMap.parse(new InputStreamReader(Files.newInputStream(seedsFile), StandardCharsets.UTF_8));
          loaded.add(seedsFile.getFileName().toString());
        }
        catch (IOException e) {
          errors.add(seedsFile.getFileName().toString());
        }
      }

      //automatically enable deobfuscation if loading mapping file for the first time
      if ((myProguardMappings == null || myProguardMappings.map == null) && proguardMap != null) {
        myDeobfuscateNames = true;
      }

      Path usageFile = mappingFiles.usageFile;
      ProguardUsagesMap usage = null;
      if (usageFile != null) {
        try {
          usage = ProguardUsagesMap.parse(new InputStreamReader(Files.newInputStream(usageFile), StandardCharsets.UTF_8));
          loaded.add(usageFile.getFileName().toString());
        }
        catch (IOException e) {
          errors.add(usageFile.getFileName().toString());
        }
      }

      myProguardMappings = loaded.isEmpty() ? null : new ProguardMappings(proguardMap, seeds, usage);
      if (errors.isEmpty() && loaded.isEmpty()) {
        BALLOON_NOTIFICATION.createNotification("APK Analyzer couldn't find any ProGuard mapping files. " +
                                                "The filenames must match one of: mapping.txt, seeds.txt, usage.txt",
                                                MessageType.ERROR).notify(myProject);
      }
      else if (errors.isEmpty()) {
        LOGGING_NOTIFICATION.createNotification("APK Analyzer successfully loaded maps from: " + StringUtil.join(loaded, ", "),
                                                MessageType.INFO).notify(myProject);
      }
      else if (loaded.isEmpty()) {
        BALLOON_NOTIFICATION.createNotification("APK Analyzer encountered problems loading: " + StringUtil.join(errors, ", "),
                                                MessageType.WARNING).notify(myProject);
      }
      else {
        BALLOON_NOTIFICATION.createNotification("APK Analyzer successfully loaded maps from: " + StringUtil.join(loaded, ",") + "\n"
                                                + "There were problems loading: " + StringUtil.join(errors, ", "),
                                                MessageType.WARNING).notify(myProject);
      }

      myDexTreeRenderer.setMappings(myProguardMappings);
      initDex();
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), "Error Loading Mappings...");
    }
  }

  public void initDex() {
    ListeningExecutorService pooledThreadExecutor = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);
    ListenableFuture<Map<Path, DexBackedDexFile>> dexFileFuture = pooledThreadExecutor.submit(() -> {
      Map<Path, DexBackedDexFile> dexFiles = Maps.newHashMapWithExpectedSize(myDexFiles.length);
      for (int i = 0; i < myDexFiles.length; i++) {
        dexFiles.put(myDexFiles[i], DexFiles.getDexFile(myDexFiles[i]));
      }
      return dexFiles;
    });

    ListenableFuture<DexPackageNode> treeNodeFuture =
      Futures.transform(dexFileFuture, new Function<Map<Path, DexBackedDexFile>, DexPackageNode>() {
        @NotNull
        @Override
        public DexPackageNode apply(@Nullable Map<Path, DexBackedDexFile> input) {
          assert input != null;
          PackageTreeCreator treeCreator = new PackageTreeCreator(myProguardMappings, myDeobfuscateNames);
          return treeCreator.constructPackageTree(input);
        }
      }, pooledThreadExecutor);

    Futures.addCallback(treeNodeFuture, new FutureCallback<DexPackageNode>() {
      @Override
      public void onSuccess(DexPackageNode result) {
        myLoadingPanel.stopLoading();
        myTree.setRootVisible(false);
        TreeModel treeModel = new FilteredTreeModel<>(result, myDexFilters);
        myTree.setModel(treeModel);

        //this has to be added AFTER the Model is added to the Tree because change events are sent to listeners in order from last to first
        //otherwise, any root change event would wipe out the expandedDescendants list before we have a chance to read it
        treeModel.addTreeModelListener(new TreeModelAdapter() {
          @Override
          protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
            Enumeration<TreePath> expanded = myTree.getExpandedDescendants(new TreePath(myTree.getModel().getRoot()));
            if (expanded == null) {
              return;
            }
            // Schedule a runnable to expand the gathered paths later,
            // so that all the other listeners get a chance to process the tree changes first.
            ApplicationManager.getApplication().invokeLater(() -> {
              for (TreePath path : Collections.list(expanded)) {
                myTree.expandPath(path);
              }
            });
          }
        });
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myLoadingPanel.stopLoading();
      }
    }, EdtExecutorService.getInstance());

    ListenableFuture<DexFileStats> dexStatsFuture =
      Futures.transform(dexFileFuture, new Function<Map<Path, DexBackedDexFile>, DexFileStats>() {
        @NotNull
        @Override
        public DexFileStats apply(@Nullable Map<Path, DexBackedDexFile> input) {
          assert input != null;
          return DexFileStats.create(input.values());
        }
      }, pooledThreadExecutor);

    //this will never change for a given dex file, regardless of proguard mappings
    //so it doesn't make sense to recompute every time
    if (((BorderLayout)myTopPanel.getLayout()).getLayoutComponent(BorderLayout.EAST) == null) {
      SimpleColoredComponent titleComponent = new SimpleColoredComponent();
      titleComponent.setIcon(AllIcons.Actions.Refresh);
      titleComponent.append("Loading dex stats");
      myTopPanel.add(titleComponent, BorderLayout.EAST);

      Futures.addCallback(dexStatsFuture, new FutureCallback<DexFileStats>() {
        @Override
        public void onSuccess(DexFileStats result) {
          titleComponent.clear();
          titleComponent.setIcon(AllIcons.General.Information);
          titleComponent.append(myDexFiles.length == 1 ? "This dex file defines " : "These dex files define ");
          titleComponent.append(Integer.toString(result.classCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          titleComponent.append(" classes with ");
          titleComponent.append(Integer.toString(result.definedMethodCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          titleComponent.append(" methods, and references ");
          titleComponent.append(Integer.toString(result.referencedMethodCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          titleComponent.append(" methods.");
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          titleComponent.clear();
          titleComponent.setIcon(AllIcons.General.Error);
          titleComponent.append("Error parsing dex file: " + t.getMessage());
        }
      }, EdtExecutorService.getInstance());
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLoadingPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLoadingPanel;
  }

  @NotNull
  @Override
  public String getName() {
    return "Dex Viewer";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    for (Path file : myDexFiles) {
      if (!Files.isRegularFile(file)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposable);
  }

  @Nullable
  public ProguardMappings getProguardMappings() {
    return myProguardMappings;
  }

  public boolean isDeobfuscateNames() {
    return myDeobfuscateNames;
  }

  @Nullable
  ListenableFuture<DexReferences> getDexReferences() {
    if (myDexReferences == null) {
      ListeningExecutorService pooledThreadExecutor = MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);
      ListenableFuture<DexBackedDexFile[]> dexFileFuture = pooledThreadExecutor.submit(() -> {
        DexBackedDexFile[] files = new DexBackedDexFile[myDexFiles.length];
        for (int i = 0; i < files.length; i++) {
          files[i] = DexFiles.getDexFile(myDexFiles[i]);
        }
        return files;
      });
      myDexReferences = Futures.transform(dexFileFuture, new Function<DexBackedDexFile[], DexReferences>() {
        @Override
        public DexReferences apply(@Nullable DexBackedDexFile[] inputs) {
          assert inputs != null;
          return new DexReferences(inputs);
        }
      }, pooledThreadExecutor);
    }

    return myDexReferences;
  }

  private static class DexTreeNodeRenderer extends ColoredTreeCellRenderer {

    @Nullable private ProguardMappings myMappings;

    public void setMappings(@Nullable ProguardMappings mappings) {
      myMappings = mappings;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (!(value instanceof DexElementNode)) {
        return;
      }

      DexElementNode node = (DexElementNode)value;

      if (myMappings != null && node.isSeed(myMappings.seeds, myMappings.map, true)) {
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));
      }
      else if (node.isRemoved()) {
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT | SimpleTextAttributes.STYLE_ITALIC, null));
      }
      else if (!node.isDefined()) {
        append(node.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, null));
      }
      else {
        append(node.getName());
      }

      setIcon(DexNodeIcons.forNode(node));
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
      if (value instanceof DexElementNode) {
        DexElementNode node = (DexElementNode)value;
        int count = myShowDefinedCount ? node.getMethodDefinitionsCount() : node.getMethodReferencesCount();
        if (count != 0) {
          append(Integer.toString(count));
        }
      }
    }
  }

  private static class SizeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof DexElementNode) {
        DexElementNode node = (DexElementNode)value;
        append(HumanReadableUtil.getHumanizedSize(node.getSize()));
      }
    }
  }

  private static class ShowFieldsAction extends ToggleAction implements DumbAware {
    private final Tree myTree;
    private final DexViewFilters myDexViewFilters;

    public ShowFieldsAction(@NotNull Tree tree, @NotNull DexViewFilters options) {
      super("Show fields", "Toggle between show/hide fields",
            IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Field));
      myTree = tree;
      myDexViewFilters = options;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myDexViewFilters.isShowFields();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myDexViewFilters.setShowFields(state);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
  }

  private static class ShowMethodsAction extends ToggleAction implements DumbAware {
    private final Tree myTree;
    private final DexViewFilters myDexViewFilters;

    public ShowMethodsAction(@NotNull Tree tree, @NotNull DexViewFilters options) {
      super("Show methods", "Toggle between show/hide methods",
            IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method));
      myTree = tree;
      myDexViewFilters = options;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myDexViewFilters.isShowMethods();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myDexViewFilters.setShowMethods(state);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
  }

  private static class ShowReferencedAction extends ToggleAction implements DumbAware {
    private final Tree myTree;
    private final DexViewFilters myDexViewFilters;

    public ShowReferencedAction(@NotNull Tree tree, @NotNull DexViewFilters options) {
      super("Show referenced-only nodes", "Toggle between show/hide referenced-only nodes", AllIcons.ObjectBrowser.ShowMembers);
      myTree = tree;
      myDexViewFilters = options;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      String text =
        myDexViewFilters.isShowReferencedNodes() ? "Show all referenced methods and fields" : "Show defined methods and fields";
      e.getPresentation().setText(text);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myDexViewFilters.isShowReferencedNodes();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myDexViewFilters.setShowReferencedNodes(state);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
  }

  private class ShowRemovedNodesAction extends ToggleAction implements DumbAware {
    private final Tree myTree;
    private final DexViewFilters myDexViewFilters;

    public ShowRemovedNodesAction(@NotNull Tree tree, @NotNull DexViewFilters options) {
      super("Show removed nodes", "Toggle between show/hide nodes removed by Proguard", AllIcons.ObjectBrowser.CompactEmptyPackages);
      myTree = tree;
      myDexViewFilters = options;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myDexViewFilters.isShowRemovedNodes();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myDexViewFilters.setShowRemovedNodes(state);
      ((DefaultTreeModel)myTree.getModel()).reload();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myProguardMappings != null && myProguardMappings.usage != null);
    }
  }

  private class DeobfuscateNodesAction extends ToggleAction implements DumbAware {
    public DeobfuscateNodesAction() {
      super("Deobfuscate names", "Deobfuscate names using Proguard mapping", AllIcons.ObjectBrowser.AbbreviatePackageNames);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myDeobfuscateNames;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myDeobfuscateNames = state;
      initDex();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myProguardMappings != null && myProguardMappings.map != null);
    }
  }

  private class LoadProguardAction extends AnAction implements DumbAware {
    public LoadProguardAction() {
      super("Load Proguard mappings...", null, EmptyIcon.ICON_0);
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      selectProguardMapping();
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myProguardMappings != null) {
        e.getPresentation().setText("Change Proguard mappings...");
      }
      else {
        e.getPresentation().setText("Load Proguard mappings...");
      }
    }
  }
}

