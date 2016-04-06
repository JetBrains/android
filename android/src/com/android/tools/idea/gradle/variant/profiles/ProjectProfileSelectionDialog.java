/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.profiles;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.ui.tree.TreeUtil;
import icons.AndroidIcons;
import org.jetbrains.android.util.BooleanCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.text.Collator;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getDirectLibraryDependencies;

public class ProjectProfileSelectionDialog extends DialogWrapper {
  private static final SimpleTextAttributes UNRESOLVED_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor());

  @NotNull private final Project myProject;
  @NotNull private final List<Conflict> myConflicts;

  @NotNull private final JPanel myPanel;

  private CheckboxTreeView myProjectStructureTree;
  private ConflictsTable myConflictsTable;
  private CheckboxTreeView myConflictTree;
  private DetailsComponent myConflictDetails;

  public ProjectProfileSelectionDialog(@NotNull Project project, @NotNull List<Conflict> conflicts) {
    super(project);
    myProject = project;
    myConflicts = conflicts;

    for (Conflict conflict : conflicts) {
      conflict.refreshStatus();
    }

    myPanel = new JPanel(new BorderLayout());

    Splitter splitter = new Splitter(false, .35f);
    splitter.setHonorComponentsMinimumSize(true);

    myPanel.add(splitter, BorderLayout.CENTER);

    splitter.setFirstComponent(createProjectStructurePanel());
    splitter.setSecondComponent(createConflictsPanel());

    init();

    myProjectStructureTree.expandAll();
  }

  @NotNull
  private JComponent createProjectStructurePanel() {
    createProjectStructureTree();

    DetailsComponent details = new DetailsComponent();
    details.setText("Project Structure");
    details.setContent(createTreePanel(myProjectStructureTree));

    removeEmptyBorder(details);
    return details.getComponent();
  }

  private void createProjectStructureTree() {
    CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          ColoredTreeCellRenderer textRenderer = getTextRenderer();
          if (data instanceof ModuleTreeElement) {
            ModuleTreeElement moduleElement = (ModuleTreeElement)data;
            textRenderer.append(moduleElement.myModule.getName());

            if (!moduleElement.myConflicts.isEmpty()) {
              boolean allResolved = true;
              for (Conflict conflict : moduleElement.myConflicts) {
                if (!conflict.isResolved()) {
                  allResolved = false;
                  break;
                }
              }
              SimpleTextAttributes attributes = allResolved ? UNRESOLVED_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
              textRenderer.append(" ");
              textRenderer.append(myConflicts.size() == 1 ? "[Conflict]" : "[Conflicts]", attributes);
            }

            textRenderer.setIcon(AllIcons.Actions.Module);
          }
          else if (data instanceof String) {
            textRenderer.append((String)data, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
            textRenderer.setIcon(AndroidIcons.Variant);
          }
          else if (data instanceof DependencyTreeElement) {
            DependencyTreeElement dependency = (DependencyTreeElement)data;
            textRenderer.append(dependency.myModule.getName());

            if (!StringUtil.isEmpty(dependency.myVariant)) {
              textRenderer.append(" (" + dependency.myVariant + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }

            Icon icon = dependency.myConflict != null ? AllIcons.RunConfigurations.TestFailed : AllIcons.RunConfigurations.TestPassed;
            textRenderer.setIcon(icon);
          }
        }
      }
    };

    CheckedTreeNode rootNode = new FilterAwareCheckedTreeNode(null);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] modules = moduleManager.getModules();
    Arrays.sort(modules, ModulesAlphaComparator.INSTANCE);

    Map<String, Module> modulesByGradlePath = Maps.newHashMap();

    for (Module module : modules) {
      String gradlePath = GradleUtil.getGradlePath(module);

      if (StringUtil.isEmpty(gradlePath)) {
        // The top-level module representing the project usually does not have a Gradle path.
        // We always want to include it, therefore we don't give users a chance to uncheck it in the "Project Structure" pane.
        continue;
      }

      modulesByGradlePath.put(gradlePath, module);

      ModuleTreeElement moduleElement = new ModuleTreeElement(module);
      CheckedTreeNode moduleNode = new FilterAwareCheckedTreeNode(moduleElement);
      rootNode.add(moduleNode);

      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel == null) {
        continue;
      }

      Multimap<String, DependencyTreeElement> dependenciesByVariant = HashMultimap.create();

      for (Variant variant : androidModel.getAndroidProject().getVariants()) {
        for (AndroidLibrary library : getDirectLibraryDependencies(variant, androidModel)) {
          gradlePath = library.getProject();
          if (gradlePath == null) {
            continue;
          }
          Module dependency = modulesByGradlePath.get(gradlePath);
          if (dependency == null) {
            dependency = GradleUtil.findModuleByGradlePath(myProject, gradlePath);
          }
          if (dependency == null) {
            continue;
          }

          Conflict conflict = getConflict(dependency);
          modulesByGradlePath.put(gradlePath, dependency);

          DependencyTreeElement dependencyElement =
            new DependencyTreeElement(dependency, gradlePath, library.getProjectVariant(), conflict);
          dependenciesByVariant.put(variant.getName(), dependencyElement);
        }
      }

      List<String> variantNames = Lists.newArrayList(dependenciesByVariant.keySet());
      Collections.sort(variantNames);

      List<String> consolidatedVariants = Lists.newArrayList();
      List<String> variantsToSkip = Lists.newArrayList();

      int variantCount = variantNames.size();
      for (int i = 0; i < variantCount; i++) {
        String variant1 = variantNames.get(i);
        if (variantsToSkip.contains(variant1)) {
          continue;
        }

        Collection<DependencyTreeElement> set1 = dependenciesByVariant.get(variant1);
        for (int j = i + 1; j < variantCount; j++) {
          String variant2 = variantNames.get(j);
          Collection<DependencyTreeElement> set2 = dependenciesByVariant.get(variant2);

          if (set1.equals(set2)) {
            variantsToSkip.add(variant2);
            if (!consolidatedVariants.contains(variant1)) {
              consolidatedVariants.add(variant1);
            }
            consolidatedVariants.add(variant2);
          }
        }

        String variantName = variant1;
        if (!consolidatedVariants.isEmpty()) {
          variantName = Joiner.on(", ").join(consolidatedVariants);
        }

        DefaultMutableTreeNode variantNode = new DefaultMutableTreeNode(variantName);
        moduleNode.add(variantNode);

        List<DependencyTreeElement> dependencyElements = Lists.newArrayList(set1);
        Collections.sort(dependencyElements);

        for (DependencyTreeElement dependencyElement : dependencyElements) {
          if (dependencyElement.myConflict != null) {
            moduleElement.addConflict(dependencyElement.myConflict);
          }
          variantNode.add(new DefaultMutableTreeNode(dependencyElement));
        }

        consolidatedVariants.clear();
      }
    }

    myProjectStructureTree = new CheckboxTreeView(renderer, rootNode) {
      @Override
      protected void onNodeStateChanged(@NotNull CheckedTreeNode node) {
        Module module = null;
        Object data = node.getUserObject();
        if (data instanceof ModuleTreeElement) {
          module = ((ModuleTreeElement)data).myModule;
        }

        if (module == null) {
          return;
        }

        boolean updated = false;

        Enumeration variantNodes = myConflictTree.myRoot.children();
        while (variantNodes.hasMoreElements()) {
          Object child = variantNodes.nextElement();
          if (!(child instanceof CheckedTreeNode)) {
            continue;
          }
          CheckedTreeNode variantNode = (CheckedTreeNode)child;

          Enumeration moduleNodes = variantNode.children();
          while (moduleNodes.hasMoreElements()) {
            child = moduleNodes.nextElement();
            if (!(child instanceof CheckedTreeNode)) {
              continue;
            }
            CheckedTreeNode moduleNode = (CheckedTreeNode)child;
            data = moduleNode.getUserObject();
            if (!(data instanceof Conflict.AffectedModule)) {
              continue;
            }
            Conflict.AffectedModule affected = (Conflict.AffectedModule)data;
            boolean checked = node.isChecked();
            if (module.equals(affected.getTarget()) && moduleNode.isChecked() != checked) {
              affected.setSelected(checked);
              moduleNode.setChecked(checked);
              updated = true;
            }
          }
        }

        if (updated) {
          repaintAll();
        }
      }
    };

    myProjectStructureTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myProjectStructureTree.setRootVisible(false);
  }

  @Nullable
  private Conflict getConflict(@NotNull Module source) {
    for (Conflict conflict : myConflicts) {
      if (source.equals(conflict.getSource())) {
        return conflict;
      }
    }
    return null;
  }

  @NotNull
  private JComponent createConflictsPanel() {
    createConflictTree();

    myConflictDetails = new DetailsComponent();
    myConflictDetails.setText("Conflict Detail");
    myConflictDetails.setContent(createTreePanel(myConflictTree));
    removeEmptyBorder(myConflictDetails);

    createConflictsTable();

    myConflictsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        showConflictDetail();
      }
    });
    Splitter splitter = new Splitter(true, .25f);
    splitter.setHonorComponentsMinimumSize(true);

    DetailsComponent details = new DetailsComponent();
    details.setText("Variant Selection Conflicts");
    details.setContent(ScrollPaneFactory.createScrollPane(myConflictsTable));
    removeEmptyBorder(details);
    splitter.setFirstComponent(details.getComponent());

    splitter.setSecondComponent(myConflictDetails.getComponent());

    return splitter;
  }

  @NotNull static JPanel createTreePanel(@NotNull CheckboxTreeView tree) {
    JPanel treePanel = new JPanel(new BorderLayout());

    DefaultActionGroup group = new DefaultActionGroup();
    CommonActionsManager actions = CommonActionsManager.getInstance();
    group.addAll(actions.createExpandAllAction(tree, treePanel), actions.createCollapseAllAction(tree, treePanel));

    ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("", group, true);
    JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(actionToolBar.getComponent(), BorderLayout.CENTER);
    buttonsPanel.setBorder(new SideBorder(JBColor.border(), SideBorder.TOP | SideBorder.LEFT | SideBorder.RIGHT, 1));

    treePanel.add(buttonsPanel, BorderLayout.NORTH);
    treePanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);

    return treePanel;
  }

  private static void removeEmptyBorder(@NotNull DetailsComponent details) {
    JComponent gutter = details.getContentGutter();
    for (Component child : gutter.getComponents()) {
      if (child instanceof Wrapper) {
        ((Wrapper)child).setBorder(null);
      }
    }
  }

  private void createConflictsTable() {
    ConflictsTableModel tableModel = new ConflictsTableModel(myConflicts, new Function<List<ConflictTableRow>, Void>() {
      @Override
      public Void fun(List<ConflictTableRow> rows) {
        filterProjectStructure(rows);
        return null;
      }
    });
    myConflictsTable = new ConflictsTable(tableModel);
    if (!tableModel.myRows.isEmpty()) {
      showConflictDetail();
    }
  }

  private void showConflictDetail() {
    myConflictTree.myRoot.removeAllChildren();
    int selectedIndex = myConflictsTable.getSelectedRow();

    ConflictsTableModel tableModel = (ConflictsTableModel)myConflictsTable.getModel();
    ConflictTableRow row = tableModel.myRows.get(selectedIndex);

    Conflict conflict = row.myConflict;
    myConflictDetails.setText("Conflict Detail: " + conflict.getSource().getName());

    List<String> variants = Lists.newArrayList(conflict.getVariants());
    Collections.sort(variants);

    for (String variant : variants) {
      CheckedTreeNode variantNode = new CheckedTreeNode(variant);
      myConflictTree.myRoot.add(variantNode);

      for (Conflict.AffectedModule module : conflict.getModulesExpectingVariant(variant)) {
        CheckedTreeNode moduleNode = new CheckedTreeNode(module);
        variantNode.add(moduleNode);
      }
    }

    myConflictTree.reload();
    myConflictTree.expandAll();
  }

  private void filterProjectStructure(@NotNull List<ConflictTableRow> rows) {
    List<Module> selectedConflictSources = Lists.newArrayList();
    for (ConflictTableRow row : rows) {
      if (row.myFilter) {
        selectedConflictSources.add(row.myConflict.getSource());
      }
    }

    Enumeration moduleNodes = myProjectStructureTree.myRoot.children();
    while (moduleNodes.hasMoreElements()) {
      boolean show = false;

      Object child = moduleNodes.nextElement();
      if (!(child instanceof FilterAwareCheckedTreeNode)) {
        continue;
      }
      FilterAwareCheckedTreeNode moduleNode = (FilterAwareCheckedTreeNode)child;
      Object data = moduleNode.getUserObject();
      if (!(data instanceof ModuleTreeElement)) {
        continue;
      }
      ModuleTreeElement moduleElement = (ModuleTreeElement)data;

      if (selectedConflictSources.isEmpty()) {
        show = true;
      }
      else {
        // We show the modules that depend on any of the selected conflict sources.
        for (Conflict conflict : moduleElement.myConflicts) {
          if (selectedConflictSources.contains(conflict.getSource())) {
            show = true;
            break;
          }
        }
        // We show the conflict sources as well.
        if (!show && selectedConflictSources.contains(moduleElement.myModule)) {
          show = true;
        }
      }

      moduleNode.myVisible = show;
    }

    myProjectStructureTree.reload();
    myProjectStructureTree.expandAll();
  }

  private void createConflictTree() {
    CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          ColoredTreeCellRenderer textRenderer = getTextRenderer();
          if (data instanceof Conflict.AffectedModule) {
            textRenderer.append(((Conflict.AffectedModule)data).getTarget().getName());
            textRenderer.setIcon(AllIcons.Actions.Module);
          }
          else if (data instanceof String) {
            textRenderer.append((String)data, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
            textRenderer.setIcon(AndroidIcons.Variant);
          }
        }
      }
    };

    FilterAwareCheckedTreeNode root = new FilterAwareCheckedTreeNode(null);
    myConflictTree = new CheckboxTreeView(renderer, root) {
      @Override
      protected void onNodeStateChanged(@NotNull CheckedTreeNode node) {
        Object data = node.getUserObject();
        if (!(data instanceof Conflict.AffectedModule)) {
          return;
        }
        Conflict.AffectedModule affected = (Conflict.AffectedModule)data;
        Module module = affected.getTarget();

        Enumeration moduleNodes = myProjectStructureTree.myRoot.children();
        while (moduleNodes.hasMoreElements()) {
          Object child = moduleNodes.nextElement();

          if (!(child instanceof CheckedTreeNode)) {
            continue;
          }

          CheckedTreeNode moduleNode = (CheckedTreeNode)child;
          data = moduleNode.getUserObject();

          if (data instanceof ModuleTreeElement) {
            ModuleTreeElement moduleElement = (ModuleTreeElement)data;
            boolean checked = node.isChecked();
            if (module.equals(moduleElement.myModule) && moduleNode.isChecked() != checked) {
              moduleNode.setChecked(checked);
              affected.setSelected(checked);
              repaintAll();
              break;
            }
          }
        }
      }
    };
  }

  private void repaintAll() {
    myConflictsTable.repaint();
    myConflictTree.repaint();
    myProjectStructureTree.repaint();
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private static class ConflictsTable extends JBTable {
    ConflictsTable(@NotNull ConflictsTableModel model) {
      super(model);
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      if (!model.myRows.isEmpty()) {
        // select first row by default.
        getSelectionModel().setSelectionInterval(0, 0);
      }

      TableColumn filterColumn = getColumnByIndex(ConflictsTableModel.FILTER_COLUMN);
      filterColumn.setCellEditor(new BooleanTableCellEditor());
      setUpBooleanColumn(filterColumn, true);

      TableColumn resolvedColumn = getColumnByIndex(ConflictsTableModel.RESOLVED_COLUMN);
      setUpBooleanColumn(resolvedColumn, false);
    }

    private static void setUpBooleanColumn(@NotNull TableColumn column, boolean editable) {
      TableCellRenderer renderer = editable ? new BooleanTableCellRenderer() : new BooleanCellRenderer();
      column.setCellRenderer(renderer);
      column.setMaxWidth(50);
    }

    @NotNull
    private TableColumn getColumnByIndex(int index) {
      return getColumn(getColumnName(index));
    }
  }

  private static class ConflictsTableModel extends DefaultTableModel {
    static final Object[] COLUMN_NAMES = { "Filter", "Source", "Resolved" };

    static final int FILTER_COLUMN = 0;
    static final int SOURCE_COLUMN = 1;
    static final int RESOLVED_COLUMN = 2;

    final List<ConflictTableRow> myRows = Lists.newArrayList();
    final Function<List<ConflictTableRow>, Void> myFilterFunction;

    ConflictsTableModel(@NotNull List<Conflict> conflicts, Function<List<ConflictTableRow>, Void> filterFunction) {
      super(COLUMN_NAMES, conflicts.size());
      myFilterFunction = filterFunction;
      for (Conflict conflict : conflicts) {
        myRows.add(new ConflictTableRow(conflict));
      }
      Collections.sort(myRows);
    }

    @Override
    public Object getValueAt(int row, int column) {
      ConflictTableRow tableRow = myRows.get(row);
      switch (column) {
        case FILTER_COLUMN:
          return tableRow.myFilter;
        case SOURCE_COLUMN:
          return tableRow.myConflict.getSource().getName();
        case RESOLVED_COLUMN:
          return tableRow.myConflict.isResolved();
      }
      throw new IllegalArgumentException(String.format("Column index '%d' is not valid", column));
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == FILTER_COLUMN;
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {
      if (column == FILTER_COLUMN && aValue instanceof Boolean) {
        ConflictTableRow conflictTableRow = myRows.get(row);
        conflictTableRow.myFilter = (Boolean)aValue;

        myFilterFunction.fun(myRows);
      }
    }
  }

  private static class ConflictTableRow implements Comparable<ConflictTableRow> {
    final Conflict myConflict;
    boolean myFilter;

    ConflictTableRow(Conflict conflict) {
      myConflict = conflict;
    }

    @Override
    public int compareTo(@NotNull ConflictTableRow other) {
      return Collator.getInstance().compare(myConflict.getSource().getName(), other.myConflict.getSource().getName());
    }
  }

  private static abstract class CheckboxTreeView extends CheckboxTree implements TreeExpander {
    @NotNull final CheckedTreeNode myRoot;

    CheckboxTreeView(@NotNull CheckboxTreeCellRenderer cellRenderer, @NotNull CheckedTreeNode root) {
      super(cellRenderer, root);
      myRoot = root;
    }

    @Override
    public void expandAll() {
      TreeUtil.expandAll(this);
    }

    @Override
    public boolean canExpand() {
      return canCollapse();
    }

    @Override
    public void collapseAll() {
      TreeUtil.collapseAll(this, 1);
    }

    @Override
    public boolean canCollapse() {
      return myRoot.getChildCount() > 0;
    }

    @Override
    public DefaultTreeModel getModel() {
      return (DefaultTreeModel)super.getModel();
    }

    void reload() {
      ((DefaultTreeModel)super.getModel()).reload();
    }
  }

  private static class FilterAwareCheckedTreeNode extends CheckedTreeNode {
    boolean myVisible = true;

    FilterAwareCheckedTreeNode(@Nullable Object userObject) {
      //noinspection ConstantConditions
      super(userObject);
    }

    @Override
    public TreeNode getChildAt(int index) {
      if (children == null) {
        // We use the same error message as super.
        throw new ArrayIndexOutOfBoundsException("node has no children");
      }
      int realIndex = -1;
      int visibleIndex = -1;
      Enumeration e = children.elements();
      while (e.hasMoreElements()) {
        Object child = e.nextElement();
        if (child instanceof FilterAwareCheckedTreeNode) {
          FilterAwareCheckedTreeNode node = (FilterAwareCheckedTreeNode)child;
          if (node.myVisible) {
            visibleIndex++;
          }
        }
        else {
          visibleIndex++;
        }
        realIndex++;
        if (visibleIndex == index) {
          return (TreeNode) children.elementAt(realIndex);
        }
      }

      throw new ArrayIndexOutOfBoundsException("index unmatched");
    }

    @Override
    public int getChildCount() {
      if (children == null) {
        return 0;
      }
      int count = 0;
      Enumeration e = children.elements();
      while (e.hasMoreElements()) {
        Object child = e.nextElement();
        if (child instanceof FilterAwareCheckedTreeNode) {
          FilterAwareCheckedTreeNode node = (FilterAwareCheckedTreeNode)child;
          if (node.myVisible) {
            count++;
          }
        }
        else {
          count++;
        }
      }
     return count;
    }
  }

  private static class ModuleTreeElement {
    @NotNull final Module myModule;
    @NotNull final List<Conflict> myConflicts = Lists.newArrayList();

    ModuleTreeElement(@NotNull Module module) {
      myModule = module;
    }

    void addConflict(@NotNull Conflict conflict) {
      myConflicts.add(conflict);
    }
  }

  private static class DependencyTreeElement implements Comparable<DependencyTreeElement> {
    @NotNull final Module myModule;
    @NotNull final String myGradlePath;

    @Nullable final String myVariant;
    @Nullable final Conflict myConflict;

    DependencyTreeElement(@NotNull Module module,
                          @NotNull String gradlePath,
                          @Nullable String variant,
                          @Nullable Conflict conflict) {
      myGradlePath = gradlePath;
      myVariant = variant;
      myModule = module;
      myConflict = conflict;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DependencyTreeElement that = (DependencyTreeElement)o;
      return Objects.equal(myGradlePath, that.myGradlePath) && Objects.equal(myVariant, that.myVariant);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myGradlePath, myVariant);
    }

    @Override
    public int compareTo(@NotNull DependencyTreeElement other) {
      return Collator.getInstance().compare(myModule.getName(), other.myModule.getName());
    }
  }
}
