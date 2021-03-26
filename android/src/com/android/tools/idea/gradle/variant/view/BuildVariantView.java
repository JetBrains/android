/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view;

import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflict;
import static com.intellij.ui.TableUtil.scrollSelectionToVisible;
import static com.intellij.util.ui.UIUtil.getTableFocusCellHighlightBorder;
import static com.intellij.util.ui.UIUtil.getToolTipBackground;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ModuleTypeComparator;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The contents of the "Build Variants" tool window.
 */
public class BuildVariantView {
  // Column headers for projects that only have Java/Kotlin code (i.e., no native code).
  private static final Object[] TABLE_COLUMN_NAMES_WITHOUT_ABI = new Object[]{"Module", "Active Build Variant"};
  // Column headers for projects that also have native code.
  private static final Object[] TABLE_COLUMN_NAMES_WITH_ABI = new Object[]{"Module", "Active Build Variant", "Active ABI"};

  private static final int MODULE_COLUMN_INDEX = 0;
  private static final int VARIANT_COLUMN_INDEX = 1;
  private static final int ABI_COLUMN_INDEX = 2;

  private static final Color CONFLICT_CELL_BACKGROUND = MessageType.ERROR.getPopupBackground();

  private final Project myProject;
  private BuildVariantUpdater myUpdater;

  private JPanel myToolWindowPanel;
  private JBTable myVariantsTable;
  private JPanel myNotificationPanel;

  private final List<Conflict> myConflicts = new ArrayList<>();

  // Indicates whether the last time the BuildVariantTable was drawn, it had the ABIs column displayed or not. The ABIs column
  // was displayed only if there was at least one module that had a native/C++ build in it. This flag is just a UI smoothness
  // optimization.
  //
  // While Gradle Sync is running, the Build Variant table is drawn without any rows, yet its headers (i.e., column names)
  // are still shown to the user. For Java-only projects, we don't want their table to temporarily show ABIs column during sync,
  // and similarly, for native/C++ projects, we don't want to hide the ABIs column during sync. Unfortunately, until Gradle sync is
  // complete, we don't know whether the current project contains any native/C++ code.
  //
  // This flag remembers whether the table had an ABIs column the last time we draw it to the screen (i.e., sync'ed it). This way,
  // we provide a consistent UI appearance to the user.
  private boolean previousModelHadAbis = false;

  @NotNull
  public static BuildVariantView getInstance(@NotNull Project project) {
    return project.getService(BuildVariantView.class);
  }

  public BuildVariantView(@NotNull Project project) {
    this(project, BuildVariantUpdater.getInstance(project));
  }

  @VisibleForTesting
  @NonInjectable
  public BuildVariantView(@NotNull Project project, @NotNull BuildVariantUpdater updater) {
    myProject = project;
    myUpdater = updater;
    myUpdater.addSelectionChangeListener(this::invokeListeners);
    ((JComponent)myVariantsTable.getParent().getParent()).setBorder(JBUI.Borders.empty());
  }

  private void createUIComponents() {
    myVariantsTable = new BuildVariantTable();
    new TableSpeedSearch(myVariantsTable);
    myNotificationPanel = new NotificationPanel();
    myNotificationPanel.setVisible(false);
  }

  /**
   * Creates the contents of the "Build Variants" tool window.
   *
   * @param toolWindow the tool window whose contents will be created.
   */
  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content content = contentFactory.createContent(myToolWindowPanel, "", false);
    toolWindow.getContentManager().addContent(content);
    updateContents();
  }

  public void updateContents() {
    List<Object[]> rows = new ArrayList<>();

    // Maps the row number to the corresponding array of build variants and abis.
    Map<Integer, BuildVariantItem[]> buildVariantsPerRow = new TreeMap<>();
    Map<Integer, AbiItem[]> abisPerRow = new TreeMap<>();

    int rowId = 0;
    for (Module module : getGradleModulesWithAndroidProjects()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      NdkFacet ndkFacet = NdkFacet.getInstance(module);
      assert androidFacet != null || ndkFacet != null; // getGradleModules() returns only relevant modules.

      String variantNameWithoutAbi;
      String abiName;

      NdkModuleModel ndkModel = ndkFacet == null ? null : getNdkModuleModelIfNotJustDummy(ndkFacet);
      if (ndkModel != null) {
        String variantNameWithAbi = ndkFacet.getConfiguration().SELECTED_BUILD_VARIANT;
        variantNameWithoutAbi = ndkModel.getVariantName(variantNameWithAbi);
        abiName = ndkModel.getAbiName(variantNameWithAbi);
      } else {
        assert androidFacet != null;  // getGradleModules() returns only relevant modules.
        variantNameWithoutAbi = androidFacet.getProperties().SELECTED_BUILD_VARIANT;
        abiName = "";
      }

      BuildVariantItem[] buildVariantItems = getBuildVariantItems(module);
      if (buildVariantItems.length > 0) {
        buildVariantsPerRow.put(rowId, buildVariantItems);
      }

      AbiItem[] abiItems = getAbiItems(module, variantNameWithoutAbi);
      if (abiItems.length > 0) {
        abisPerRow.put(rowId, abiItems);
      }

      Object[] row = {module, variantNameWithoutAbi, abiName};
      rows.add(row);

      rowId++;
    }

    Runnable setModelTask = () -> getVariantsTable().setModel(rows, buildVariantsPerRow, abisPerRow);
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      setModelTask.run();
    }
    else {
      application.invokeLater(setModelTask);
    }
  }

  public void projectImportStarted() {
    getVariantsTable().setLoading(true);
  }

  /**
   * @return All modules in the application that are either a valid android module or a valid NDK module.
   */
  @NotNull
  private List<Module> getGradleModulesWithAndroidProjects() {
    List<Module> gradleModules = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && AndroidModel.isRequired(androidFacet) && AndroidModel.get(androidFacet) != null) {
        gradleModules.add(module);
        continue;
      }
      NdkFacet ndkFacet = NdkFacet.getInstance(module);
      if (ndkFacet != null && getNdkModuleModelIfNotJustDummy(ndkFacet) != null) {
        gradleModules.add(module);
      }
    }

    if (!gradleModules.isEmpty()) {
      gradleModules.sort(ModuleTypeComparator.INSTANCE);
      return gradleModules;
    }
    return Collections.emptyList();
  }

  @NotNull
  private BuildVariantTable getVariantsTable() {
    return (BuildVariantTable)myVariantsTable;
  }

  /**
   * @param module The module whose build variants will be returned.
   * @return All possible build variants for the provided module.
   */
  @NotNull
  private static BuildVariantItem[] getBuildVariantItems(@NotNull Module module) {
    Collection<String> variantNames = getVariantNames(module);
    BuildVariantItem[] items = new BuildVariantItem[variantNames.size()];
    int i = 0;
    for (String name : variantNames) {
      items[i++] = new BuildVariantItem(module.getName(), name);
    }
    Arrays.sort(items);
    return items;
  }

  /**
   * @param module The module whose build variant names will be returned.
   * @return All possible build variant names for the provided module.
   */
  @NotNull
  private static Collection<String> getVariantNames(@NotNull Module module) {
    Set<String> buildVariantNames = new HashSet<>();

    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      buildVariantNames.addAll(androidModel.getVariantNames());
    }

    NdkModuleModel ndkModuleModel = getNdkModuleModelIfNotJustDummy(module);
    if (ndkModuleModel != null) {
      buildVariantNames.addAll(
        ContainerUtil.map(ndkModuleModel.getNdkVariantNames(), ndkVariantName -> ndkModuleModel.getVariantName(ndkVariantName)));
    }

    return buildVariantNames;
  }

  /**
   * @param module The module whose ABIs will be returned.
   * @param variantNameWithoutAbi The variant that will be used to filter irrelevant variant+ABI combinations.
   * @return All possible ABIs for the provided module, with the given build variant.
   */
  @NotNull
  private static AbiItem[] getAbiItems(@NotNull Module module, @NotNull String variantNameWithoutAbi) {
    Collection<String> variantNames = getAbiNames(module, variantNameWithoutAbi);
    AbiItem[] items = new AbiItem[variantNames.size()];
    int i = 0;
    for (String name : variantNames) {
      items[i++] = new AbiItem(module.getName(), name);
    }
    Arrays.sort(items);
    return items;
  }

  /**
   * @param module The module whose ABI names will be returned.
   * @param variantNameWithoutAbi The variant that will be used to filter irrelevant variant+ABI combinations.
   * @return All possible ABI names for the provided module, with the given build variant.
   */
  @NotNull
  private static Collection<String> getAbiNames(@NotNull Module module, @NotNull String variantNameWithoutAbi) {
    NdkModuleModel ndkModuleModel = getNdkModuleModelIfNotJustDummy(module);
    if (ndkModuleModel == null) {
      return Collections.emptyList();
    }

    Collection<String> allNdkVariants = ndkModuleModel.getNdkVariantNames();
    return allNdkVariants.stream()
      .filter((String ndkVariant) -> ndkVariant.startsWith(variantNameWithoutAbi))
      .map((String ndkVariant) -> ndkModuleModel.getAbiName(ndkVariant))
      .collect(Collectors.toList());
  }

  public void updateContents(@NotNull List<Conflict> conflicts) {
    myNotificationPanel.setVisible(!conflicts.isEmpty());
    ((NotificationPanel)myNotificationPanel).myCurrentConflictIndex = -1;
    myConflicts.clear();
    myConflicts.addAll(conflicts);
    updateContents();
  }

  public void findAndSelect(@NotNull Module module) {
    findAndSelect(module, MODULE_COLUMN_INDEX);
  }

  public void findAndSelectVariantEditor(@NotNull Module module) {
    findAndSelect(module, VARIANT_COLUMN_INDEX);
  }

  private void findAndSelect(@NotNull Module module, int columnIndex) {
    int rowCount = myVariantsTable.getRowCount();
    for (int row = 0; row < rowCount; row++) {
      if (module.equals(myVariantsTable.getValueAt(row, MODULE_COLUMN_INDEX))) {
        myVariantsTable.getSelectionModel().setSelectionInterval(row, row);
        myVariantsTable.getColumnModel().getSelectionModel().setSelectionInterval(columnIndex, columnIndex);
        scrollSelectionToVisible(myVariantsTable);
        myVariantsTable.requestFocusInWindow();
        break;
      }
    }
  }

  public interface BuildVariantSelectionChangeListener {
    /**
     * Indicates that a user selected a build variant from the "Build Variants" tool window.
     * <p/>
     * This notification occurs:
     * <ul>
     * <li>after the user selected a build variant from the drop-down</li>
     * <li>project structure has been updated according to selected build variant</li>
     * </ul>
     * <p/>
     * This listener will not be invoked if the project structure update fails.
     */
    void selectionChanged();
  }

  private class NotificationPanel extends JPanel {
    int myCurrentConflictIndex = -1;

    NotificationPanel() {
      super(new BorderLayout());
      Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
      setBackground(color == null ? getToolTipBackground() : color);
      setBorder(JBUI.Borders.empty(1, 15)); // Same as EditorNotificationPanel
      setPreferredSize(new Dimension(-1, JBUIScale.scale(24)));

      JLabel textLabel = new JLabel("Variant selection conflicts found");
      textLabel.setOpaque(false);
      add(textLabel, BorderLayout.CENTER);

      DefaultActionGroup group = new DefaultActionGroup();
      ActionManager actionManager = ActionManager.getInstance();

      AnAction nextConflictAction = new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          navigateConflicts(true);
        }
      };
      ActionUtil.copyFrom(nextConflictAction, IdeActions.ACTION_NEXT_OCCURENCE);
      group.add(nextConflictAction);

      AnAction prevConflictAction = new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          navigateConflicts(false);
        }
      };
      ActionUtil.copyFrom(prevConflictAction, IdeActions.ACTION_PREVIOUS_OCCURENCE);
      group.add(prevConflictAction);

      ActionToolbar toolbar = actionManager.createActionToolbar("AndroidBuildVariantViewNotifications", group, true);
      toolbar.setReservePlaceAutoPopupIcon(false);
      toolbar.setMinimumButtonSize(JBUI.size(23, 23)); // a little smaller than default (25 x 25)

      JComponent toolbarComponent = toolbar.getComponent();
      toolbarComponent.setBorder(null);
      toolbarComponent.setOpaque(false);
      add(toolbarComponent, BorderLayout.EAST);
    }

    private void navigateConflicts(boolean forward) {
      int conflictCount = myConflicts.size();
      if (conflictCount == 0) {
        return;
      }
      if (forward) {
        myCurrentConflictIndex++;
        if (myCurrentConflictIndex >= conflictCount) {
          myCurrentConflictIndex = 0;
        }
      }
      else {
        myCurrentConflictIndex--;
        if (myCurrentConflictIndex < 0) {
          myCurrentConflictIndex = conflictCount - 1;
        }
      }
      Conflict conflict = myConflicts.get(myCurrentConflictIndex);
      findAndSelect(conflict.getSource());
    }
  }

  /**
   * Represents a single selectable item in the Build Variant dropdown.
   */
  private static class BuildVariantItem implements Comparable<BuildVariantItem> {
    // The name of the module that this object refers to.
    @NotNull final String myModuleName;

    // The name of the build variant (without ABI, if the module is native).
    @NotNull final String myBuildVariantName;

    BuildVariantItem(@NotNull String moduleName, @NotNull String buildVariantName) {
      myModuleName = moduleName;
      myBuildVariantName = buildVariantName;
    }

    @Override
    public int compareTo(@Nullable BuildVariantItem o) {
      return o != null ? Collator.getInstance().compare(myBuildVariantName, o.myBuildVariantName) : 1;
    }

    boolean hasBuildVariantName(@Nullable Object name) {
      return myBuildVariantName.equals(name);
    }

    @Override
    public String toString() {
      return myBuildVariantName;
    }
  }

  /**
   * Represents a single selectable item in the ABI dropdown.
   */
  private static class AbiItem implements Comparable<AbiItem> {
    // The name of the native module that this object refers to.
    @NotNull final String myModuleName;

    // The name of the ABI.
    @NotNull final String myAbiName;

    AbiItem(@NotNull String moduleName, @NotNull String abiName) {
      myModuleName = moduleName;
      myAbiName = abiName;
    }

    @Override
    public int compareTo(@Nullable AbiItem o) {
      return o != null ? Collator.getInstance().compare(myAbiName, o.myAbiName) : 1;
    }

    boolean hasAbiName(@Nullable Object name) {
      return myAbiName.equals(name);
    }

    @Override
    public String toString() {
      return myAbiName;
    }
  }

  /**
   * The model to use for the Build Variant table in the panel.
   */
  private static class BuildVariantTableModel extends DefaultTableModel {
    BuildVariantTableModel(List<Object[]> rows, boolean hasAbis) {
      super(
        rows.toArray(
          new Object[rows.size()][hasAbis ? TABLE_COLUMN_NAMES_WITH_ABI.length : TABLE_COLUMN_NAMES_WITHOUT_ABI.length]),
        hasAbis ? TABLE_COLUMN_NAMES_WITH_ABI : TABLE_COLUMN_NAMES_WITHOUT_ABI);
    }
  }

  /**
   * Represents the contents of the Build Variant table in the panel.
   */
  private class BuildVariantTable extends JBTable {
    // If true, then the a "loading" label is displayed instead of the table rows. This prevents the user from making changes while the IDE
    // is working to apply a previous selection (e.g., sync).
    private boolean myLoading;

    // Since the modules cells are not editable, they share a single common editor.
    private final ModuleTableCell myModuleCellEditor = new ModuleTableCell();

    // Maps each table row to the corresponding build variant, and ABI cell editor.
    private final Map<Integer, TableCellEditor> myVariantCellEditors = new TreeMap<>();
    private final Map<Integer, TableCellEditor> myAbiCellEditors = new TreeMap<>();

    // The renderers for the module, build variant, and ABI cells.
    private final ModuleTableCell myModuleCellRenderer = new ModuleTableCell();
    private final VariantsCellRenderer myVariantsCellRenderer = new VariantsCellRenderer();
    private final AbisCellRenderer myAbisCellRenderer = new AbisCellRenderer();

    BuildVariantTable() {
      super(new BuildVariantTableModel(Collections.emptyList(), previousModelHadAbis));
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          int column = getSelectedColumn();
          int row = getSelectedRow();

          // Map the F2 button to enter edit mode when the the variant and ABI cells are selected.
          if ((column == VARIANT_COLUMN_INDEX || column == ABI_COLUMN_INDEX)
              && row >= 0 && e.getKeyCode() == KeyEvent.VK_F2 && editCellAt(row, column)) {
            Component editorComponent = getEditorComponent();
            if (editorComponent instanceof ComboBox) {
              editorComponent.requestFocusInWindow();
              ((ComboBox)editorComponent).showPopup();
            }
          }
        }
      });

      setExpandableItemsEnabled(false);
    }

    /**
     * @param row the row for which a conflict will be searched
     * @return the conflict for the provided row, if exists.
     */
    @Nullable
    Conflict findConflict(int row) {
      for (Conflict conflict : myConflicts) {
        Object module = getValueAt(row, MODULE_COLUMN_INDEX);
        if (conflict.getSource().equals(module)) {
          return conflict;
        }
      }
      return null;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    void setLoading(boolean loading) {
      myLoading = loading;
      setPaintBusy(myLoading);
      clearContents();
      String text = myLoading ? CommonBundle.getLoadingTreeNodeText() : "Nothing to Show";
      getEmptyText().setText(text);
    }

    private void clearContents() {
      setModel(new BuildVariantTableModel(Collections.emptyList(), previousModelHadAbis));
      myVariantCellEditors.clear();
      myAbiCellEditors.clear();
    }

    void setModel(@NotNull List<Object[]> rows,
                  @NotNull Map<Integer, BuildVariantItem[]> buildVariantsPerRow,
                  @NotNull Map<Integer, AbiItem[]> abisPerRow) {
      setLoading(false);
      previousModelHadAbis = !abisPerRow.isEmpty();
      if (rows.isEmpty()) {
        // This is most likely an old-style (pre-Gradle) Android project. Just leave the table empty.
        setModel(new BuildVariantTableModel(rows, previousModelHadAbis));
        return;
      }

      boolean hasVariants = !buildVariantsPerRow.isEmpty();
      List<Object[]> content = hasVariants ? rows : Collections.emptyList();

      setModel(new BuildVariantTableModel(content, previousModelHadAbis));
      addBuildVariants(buildVariantsPerRow);
      addAbiNames(abisPerRow);
    }

    /**
     * Populates the contents of the build variant column for each row.
     */
    private void addBuildVariants(@NotNull Map<Integer, BuildVariantItem[]> buildVariantsPerRow) {
      for (Map.Entry<Integer, BuildVariantItem[]> entry : buildVariantsPerRow.entrySet()) {
        int row = entry.getKey();
        BuildVariantItem[] items = entry.getValue();
        BuildVariantItem selected = null;
        for (BuildVariantItem item : items) {
          if (item.hasBuildVariantName(getValueAt(row, VARIANT_COLUMN_INDEX))) {
            selected = item;
            break;
          }
        }

        ComboBox<BuildVariantItem> editor = new ComboBox<>(items);
        if (selected != null) {
          editor.setSelectedItem(selected);
        }

        editor.setBorder(JBUI.Borders.empty());
        editor.addItemListener(e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            BuildVariantItem selectedVariant = (BuildVariantItem)e.getItem();
            getVariantsTable().setLoading(true /* Show "loading" message in the table*/ );
            if (!myUpdater.updateSelectedBuildVariant(myProject, selectedVariant.myModuleName, selectedVariant.myBuildVariantName)) {
              updateContents();
            }
          }
        });
        DefaultCellEditor defaultCellEditor = new DefaultCellEditor(editor);

        editor.addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
              defaultCellEditor.cancelCellEditing();
            }
          }
        });

        myVariantCellEditors.put(row, defaultCellEditor);
      }
    }

    /**
     * Populates the contents of the ABIs column for each row.
     */
    private void addAbiNames(@NotNull Map<Integer, AbiItem[]> abisPerRow) {
      for (Map.Entry<Integer, AbiItem[]> entry : abisPerRow.entrySet()) {
        int row = entry.getKey();
        AbiItem[] items = entry.getValue();

        AbiItem selected = null;
        for (AbiItem item : items) {
          if (item.hasAbiName(getValueAt(row, ABI_COLUMN_INDEX))) {
            selected = item;
            break;
          }
        }

        ComboBox<AbiItem> editor = new ComboBox<>(items);
        if (selected != null) {
          editor.setSelectedItem(selected);
        }

        editor.setBorder(JBUI.Borders.empty());
        editor.addItemListener(e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            AbiItem selectedAbi = (AbiItem)e.getItem();
            if (!myUpdater.updateSelectedAbi(myProject, selectedAbi.myModuleName, selectedAbi.myAbiName)) {
              updateContents();
            }
          }
        });
        DefaultCellEditor defaultCellEditor = new DefaultCellEditor(editor);

        editor.addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
              defaultCellEditor.cancelCellEditing();
            }
          }
        });

        myAbiCellEditors.put(row, defaultCellEditor);
      }
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
      if (column == MODULE_COLUMN_INDEX) {
        return myModuleCellRenderer;
      }

      if (column == VARIANT_COLUMN_INDEX) {
        return myVariantsCellRenderer;
      }

      return myAbisCellRenderer;
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
      if ((column == VARIANT_COLUMN_INDEX)) {
        return myVariantCellEditors.getOrDefault(row, null);
      }
      if ((column == ABI_COLUMN_INDEX)) {
        return myAbiCellEditors.getOrDefault(row, null);
      }
      return myModuleCellEditor;
    }
  }

  private void invokeListeners() {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      updateContents();
    }
    else {
      application.invokeLater(this::updateContents);
    }
  }

  /**
   * Determines how the cells in the Build Variants column will be displayed.
   */
  private static class VariantsCellRenderer extends DefaultTableCellRenderer {
    // Default help text that will be displayed as a tooltip on the Variants cells.
    private static final String variantsCellHelpTooltipText =
      "Determines the build variant that will be deployed to device and used by the editor";

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (c instanceof JLabel) {
        JLabel component = (JLabel)c;

        Color background = isSelected ? table.getSelectionBackground() : table.getBackground();
        Conflict conflictFound = ((BuildVariantTable)table).findConflict(row);
        if (conflictFound != null) {
          background = CONFLICT_CELL_BACKGROUND;
        }
        component.setBackground(background);

        String toolTip = conflictFound != null ? conflictFound.toString() : variantsCellHelpTooltipText;
        component.setToolTipText(toolTip);

        // add some padding to table cells. It is hard to read text of combo box.
        component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(),  JBUI.Borders.empty(3, 2, 4, 2)));
      }

      return c;
    }
  }

  /**
   * Determines how the cells in the ABIs column will be displayed.
   */
  private static class AbisCellRenderer extends DefaultTableCellRenderer {
    // Default help text that will be displayed as a tooltip on the ABI cells.
    private static final String abisCellHelpTooltipText =
      "For NDK modules, determines the ABI that will be used by the editor";

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (c instanceof JLabel) {
        JLabel component = (JLabel)c;

        // Build Variant conflicts do not change the background color of ABI cells.
        Color background = isSelected ? table.getSelectionBackground() : table.getBackground();
        component.setBackground(background);

        // Build variant conflicts do not change the tooltip of ABI cells.
        component.setToolTipText(abisCellHelpTooltipText);

        // add some padding to table cells. It is hard to read text of combo box.
        component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(),  JBUI.Borders.empty(3, 2, 4, 2)));

        if (component.getText().isEmpty()) {
          component.setText("\u2014");
        }
      }

      return c;
    }
  }

  /**
   * Determines how the cells in the Modules column will be displayed.
   */
  private static class ModuleTableCell extends AbstractTableCellEditor implements TableCellRenderer {
    private static final Border EMPTY_BORDER = JBUI.Borders.empty(1);

    @Nullable private Conflict myConflict;

    private JPanel myPanel;
    private JLabel myModuleNameLabel;
    private JPanel myButtonsPanel;
    private JButton myFixButton;

    private Object myValue;

    ModuleTableCell() {
      myModuleNameLabel = new JLabel();
      myModuleNameLabel.setOpaque(false);

      myFixButton = createButton(AllIcons.Actions.QuickfixBulb);
      myFixButton.setToolTipText("Fix problem");
      myFixButton.addActionListener(e -> {
        if (myConflict != null) {
          Project project = myConflict.getSource().getProject();
          boolean solved = solveSelectionConflict(myConflict);
          if (solved) {
            ConflictSet conflicts = ConflictSet.findConflicts(project);
            conflicts.showSelectionConflicts();
          }
        }
        stopCellEditing();
      });

      myButtonsPanel = new JPanel();
      myButtonsPanel.setOpaque(false);
      myButtonsPanel.add(myFixButton);

      myPanel = new JPanel(new BorderLayout()) {
        @Override
        public String getToolTipText(MouseEvent e) {
          String toolTip = getToolTipTextIfUnderX(myModuleNameLabel, e.getX());
          if (toolTip != null) {
            return toolTip;
          }
          int x = e.getX() - myButtonsPanel.getX();
          toolTip = getToolTipTextIfUnderX(myFixButton, x);
          if (toolTip != null) {
            return toolTip;
          }
          return super.getToolTipText(e);
        }
      };

      myPanel.add(myModuleNameLabel, BorderLayout.CENTER);
      myPanel.add(myButtonsPanel, BorderLayout.EAST);
    }

    @NotNull
    private static JButton createButton(@NotNull Icon icon) {
      JButton button = new JButton(icon);
      button.setBorder(null);
      button.setBorderPainted(false);
      button.setContentAreaFilled(false);
      return button;
    }

    @Nullable
    private static String getToolTipTextIfUnderX(@NotNull JComponent c, int x) {
      if (c.isVisible() && x >= c.getX() && x <= c.getX() + c.getWidth()) {
        return c.getToolTipText();
      }
      return null;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      setUpComponent(table, value, true, true, row);
      return myPanel;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setUpComponent(table, value, isSelected, hasFocus, row);
      return myPanel;
    }

    private void setUpComponent(@NotNull JTable table, @Nullable Object value, boolean isSelected, boolean hasFocus, int row) {
      myValue = value;

      String moduleName = null;
      Icon moduleIcon = null;
      boolean isAndriodGradleModule = false;
      if (value instanceof Module) {
        Module module = (Module)value;
        if (!module.isDisposed()) {
          moduleName = module.getName();
          moduleIcon = GradleUtil.getModuleIcon(module);
          isAndriodGradleModule = AndroidModuleModel.get(module) != null;
        }
      }

      myModuleNameLabel.setText(moduleName == null ? "" : moduleName);
      myModuleNameLabel.setIcon(moduleIcon);

      Color background = isSelected ? table.getSelectionBackground() : table.getBackground();

      if (isAndriodGradleModule) {
        myConflict = ((BuildVariantTable)table).findConflict(row);

        myModuleNameLabel.setToolTipText(myConflict != null ? myConflict.toString() : null);
        myFixButton.setVisible(myConflict != null);
        if (myConflict != null) {
          background = CONFLICT_CELL_BACKGROUND;
        }
      }
      else {
        // TODO: Consider showing dependency graph and conflict resolution options for native android modules also.
        myFixButton.setVisible(false);
      }

      myPanel.setBackground(background);

      Border border = hasFocus ? getTableFocusCellHighlightBorder() : EMPTY_BORDER;
      myPanel.setBorder(border);
    }

    @Override
    public Object getCellEditorValue() {
      return myValue;
    }
  }

  @Nullable
  private static NdkModuleModel getNdkModuleModelIfNotJustDummy(@NotNull NdkFacet ndkFacet) {
    NdkModuleModel ndkModel = NdkModuleModel.get(ndkFacet);
    String variantNameWithAbi = ndkFacet.getConfiguration().SELECTED_BUILD_VARIANT;
    if (variantNameWithAbi.equals(NdkModuleModel.DummyNdkVariant.variantNameWithAbi)) {
      // There are no valid NDK variants. Treat as if NdkModuleModel does not exist.
      return null;
    }
    return ndkModel;
  }

  @Nullable
  private static NdkModuleModel getNdkModuleModelIfNotJustDummy(@NotNull Module module) {
    NdkFacet ndkFacet = NdkFacet.getInstance(module);
    if (ndkFacet == null) {
      return null;
    }
    return getNdkModuleModelIfNotJustDummy(ndkFacet);
  }
}
