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

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ModuleTypeComparator;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflict;

/**
 * The contents of the "Build Variants" tool window.
 */
public class BuildVariantView {
  private static final Object[] TABLE_COLUMN_NAMES = new Object[]{"Module", "Build Variant"};

  private static final int MODULE_COLUMN_INDEX = 0;
  private static final int VARIANT_COLUMN_INDEX = 1;

  private static final Color CONFLICT_CELL_BACKGROUND = MessageType.ERROR.getPopupBackground();

  private final Project myProject;
  private BuildVariantUpdater myUpdater;

  private JPanel myToolWindowPanel;
  private JBTable myVariantsTable;
  private JPanel myErrorPanel;

  private final List<BuildVariantSelectionChangeListener> myBuildVariantSelectionChangeListeners = Lists.newArrayList();
  private final List<Conflict> myConflicts = Lists.newArrayList();

  public BuildVariantView(@NotNull Project project) {
    myProject = project;
    myUpdater = new BuildVariantUpdater();
  }

  @VisibleForTesting
  void setUpdater(@NotNull BuildVariantUpdater updater) {
    myUpdater = updater;
  }

  @NotNull
  public static BuildVariantView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildVariantView.class);
  }

  public void addListener(@NotNull BuildVariantSelectionChangeListener listener) {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      myBuildVariantSelectionChangeListeners.add(listener);
    }
  }

  public void removeListener(@NotNull BuildVariantSelectionChangeListener listener) {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      myBuildVariantSelectionChangeListeners.remove(listener);
    }
  }

  private void createUIComponents() {
    myVariantsTable = new BuildVariantTable();
    new TableSpeedSearch(myVariantsTable);
    myErrorPanel = new JPanel() {
      @Override
      public Color getBackground() {
        // Same color as the editor notification panel (EditorComposite.TopBottomPanel.)
        Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
        return color == null ? JBColor.GRAY : color;
      }
    };
    myErrorPanel.setLayout(new BoxLayout(myErrorPanel, BoxLayout.Y_AXIS));
  }

  public void projectImportStarted() {
    getVariantsTable().setLoading(true);
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
    if (GradleSyncState.getInstance(myProject).isSyncInProgress()) {
      projectImportStarted();
      return;
    }

    final List<Object[]> rows = Lists.newArrayList();
    final List<BuildVariantItem[]> variantNamesPerRow = Lists.newArrayList();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] modules = moduleManager.getModules();
    Arrays.sort(modules, ModuleTypeComparator.INSTANCE);
    for (Module module : modules) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet == null || !androidFacet.isGradleProject()) {
        continue;
      }
      JpsAndroidModuleProperties facetProperties = androidFacet.getProperties();
      String variantName = facetProperties.SELECTED_BUILD_VARIANT;

      BuildVariantItem[] variantNames = getVariantNames(module);
      if (variantNames != null) {
        // If we got here IdeaAndroidProject is *not* null.
        IdeaAndroidProject androidProject = getAndroidProject(module);
        assert androidProject != null;
        variantName = androidProject.getSelectedVariant().getName();
        variantNamesPerRow.add(variantNames);
      }

      if (variantName != null) {
        Object[] row = {module, variantName};
        rows.add(row);
      }
    }
    Runnable setModelTask = new Runnable() {
      @Override
      public void run() {
        getVariantsTable().setModel(rows, variantNamesPerRow);
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      setModelTask.run();
    }
    else {
      application.invokeLater(setModelTask);
    }
  }

  @NotNull
  private BuildVariantTable getVariantsTable() {
    return (BuildVariantTable)myVariantsTable;
  }

  @Nullable
  private static BuildVariantItem[] getVariantNames(@NotNull Module module) {
    IdeaAndroidProject androidProject = getAndroidProject(module);
    if (androidProject == null) {
      return null;
    }
    Collection<String> variantNames = androidProject.getVariantNames();
    BuildVariantItem[] items = new BuildVariantItem[variantNames.size()];
    int i = 0;
    for (String name : variantNames) {
      items[i++] = new BuildVariantItem(module.getName(), name);
    }
    Arrays.sort(items);
    return items;
  }

  @Nullable
  private static IdeaAndroidProject getAndroidProject(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    return androidFacet != null ? androidFacet.getIdeaAndroidProject() : null;
  }

  public void updateContents(@NotNull List<Conflict> conflicts) {
    myErrorPanel.removeAll();
    myConflicts.clear();

    if (!conflicts.isEmpty()) {
      EditorNotificationPanel notification = new EditorNotificationPanel();
      notification.setText("Variant selection conflicts found.");
      notification.createActionLabel("See conflicts", new Runnable() {
        @Override
        public void run() {
          ProjectSyncMessages.getInstance(myProject).activateView();
        }
      });
      myErrorPanel.add(notification);
    }

    myConflicts.addAll(conflicts);
    updateContents();
  }

  public void selectAndScrollTo(@NotNull Module module) {
    int rowCount = myVariantsTable.getRowCount();
    for (int row = 0; row < rowCount; row++) {
      if (module.equals(myVariantsTable.getValueAt(row, MODULE_COLUMN_INDEX))) {
        myVariantsTable.getSelectionModel().setSelectionInterval(row, row);
        myVariantsTable.getColumnModel().getSelectionModel().setSelectionInterval(MODULE_COLUMN_INDEX, MODULE_COLUMN_INDEX);
        TableUtil.scrollSelectionToVisible(myVariantsTable);
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
     *
     * @param facets the facets affected by the variant selection.
     */
    void buildVariantSelected(@NotNull List<AndroidFacet> facets);
  }

  private static class BuildVariantItem implements Comparable<BuildVariantItem> {
    @NotNull final String myModuleName;
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

  private class BuildVariantTable extends JBTable {
    private boolean myLoading;
    private final List<TableCellEditor> myCellEditors = Lists.newArrayList();

    private final ModuleTableCell myModuleCellRenderer = new ModuleTableCell();
    private final ModuleTableCell myModuleCellEditor = new ModuleTableCell();
    private final VariantsCellRenderer myVariantsCellRenderer = new VariantsCellRenderer();

    BuildVariantTable() {
      super(new BuildVariantTableModel(Collections.<Object[]>emptyList()));
    }

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
      String text = myLoading ? "Loading..." : "Nothing to Show";
      getEmptyText().setText(text);
    }

    private void clearContents() {
      setModel(new BuildVariantTableModel(Collections.<Object[]>emptyList()));
      myCellEditors.clear();
    }

    void setModel(@NotNull List<Object[]> rows, @NotNull List<BuildVariantItem[]> variantNamesPerRow) {
      setLoading(false);
      if (rows.isEmpty()) {
        // This is most likely an old-style (pre-Gradle) Android project. Just leave the table empty.
        setModel(new BuildVariantTableModel(rows));
        return;
      }

      boolean hasVariants = !variantNamesPerRow.isEmpty();
      List<Object[]> content = hasVariants ? rows : Collections.<Object[]>emptyList();

      setModel(new BuildVariantTableModel(content));
      addBuildVariants(variantNamesPerRow);
    }

    private void addBuildVariants(@NotNull List<BuildVariantItem[]> variantNamesPerRow) {
      for (int row = 0; row < variantNamesPerRow.size(); row++) {
        BuildVariantItem[] items = variantNamesPerRow.get(row);
        BuildVariantItem selected = null;
        for (BuildVariantItem item : items) {
          if (item.hasBuildVariantName(getValueAt(row, VARIANT_COLUMN_INDEX))) {
            selected = item;
            break;
          }
        }

        ComboBox editor = new ComboBox(items);
        if (selected != null) {
          editor.setSelectedItem(selected);
        }

        editor.setBorder(BorderFactory.createEmptyBorder());
        editor.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              BuildVariantItem selected = (BuildVariantItem)e.getItem();
              buildVariantSelected(selected.myModuleName, selected.myBuildVariantName);
            }
          }
        });
        myCellEditors.add(new DefaultCellEditor(editor));
      }
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
      return column == MODULE_COLUMN_INDEX ? myModuleCellRenderer : myVariantsCellRenderer;
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
      if (column == VARIANT_COLUMN_INDEX && row >= 0 && row < myCellEditors.size()) {
        return myCellEditors.get(row);
      }
      return myModuleCellEditor;
    }
  }

  public void selectVariant(@NotNull Module module, @NotNull String variantName) {
    buildVariantSelected(module.getName(), variantName);
  }

  private void buildVariantSelected(@NotNull String moduleName, @NotNull String variantName) {
    final List<AndroidFacet> facets = myUpdater.updateModule(myProject, moduleName, variantName);
    if (facets.isEmpty()) {
      return;
    }
    Runnable invokeListenersTask = new Runnable() {
      @Override
      public void run() {
        updateContents();
        for (BuildVariantSelectionChangeListener listener : myBuildVariantSelectionChangeListeners) {
          listener.buildVariantSelected(facets);
        }
      }
    };

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      invokeListenersTask.run();
    }
    else {
      application.invokeLater(invokeListenersTask);
    }
  }

  private static class BuildVariantTableModel extends DefaultTableModel {
    BuildVariantTableModel(List<Object[]> rows) {
      super(rows.toArray(new Object[rows.size()][TABLE_COLUMN_NAMES.length]), TABLE_COLUMN_NAMES);
    }
  }

  private static class VariantsCellRenderer extends DefaultTableCellRenderer {
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

        String toolTip = conflictFound != null ? conflictFound.toString() : null;
        component.setToolTipText(toolTip);

        // add some padding to table cells. It is hard to read text of combo box.
        component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(), BorderFactory.createEmptyBorder(3, 2, 4, 2)));
      }

      return c;
    }
  }

  private static class ModuleTableCell extends AbstractTableCellEditor implements TableCellRenderer {
    private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    @Nullable private Conflict myConflict;

    private JPanel myPanel;
    private JLabel myModuleNameLabel;
    private JPanel myButtonsPanel;
    private JButton myInfoButton;
    private JButton myFixButton;

    private Object myValue;

    ModuleTableCell() {
      myModuleNameLabel = new JLabel();
      myModuleNameLabel.setOpaque(false);

      myInfoButton = createButton(AllIcons.General.BalloonInformation);
      myInfoButton.setToolTipText("More info");
      myInfoButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myValue instanceof Module) {
            Module module = (Module)myValue;
            ModuleVariantsInfoDialog dialog = new ModuleVariantsInfoDialog(module);
            dialog.show();
          }
        }
      });

      myFixButton = createButton(AllIcons.Actions.QuickfixBulb);
      myFixButton.setToolTipText("Fix problem");
      myFixButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myConflict != null) {
            Project project = myConflict.getSource().getProject();
            boolean solved = solveSelectionConflict(myConflict);
            if (solved) {
              ConflictSet conflicts = ConflictSet.findConflicts(project);
              conflicts.showSelectionConflicts();
            }
          }
          stopCellEditing();
        }
      });

      myButtonsPanel = new JPanel();
      myButtonsPanel.setOpaque(false);
      myButtonsPanel.add(myInfoButton);
      myButtonsPanel.add(myFixButton);

      myPanel = new JPanel(new BorderLayout()) {
        @Override
        public String getToolTipText(MouseEvent e) {
          String toolTip = getToolTipTextIfUnderX(myModuleNameLabel, e.getX());
          if (toolTip != null) {
            return toolTip;
          }
          int x = e.getX() - myButtonsPanel.getX();
          toolTip = getToolTipTextIfUnderX(myInfoButton, x);
          if (toolTip != null) {
            return toolTip;
          }
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
      if (value instanceof Module) {
        Module module = (Module)value;
        if (!module.isDisposed()) {
          moduleName = module.getName();
          moduleIcon = GradleUtil.getModuleIcon(module);
        }
      }

      myModuleNameLabel.setText(moduleName == null ? "" : moduleName);
      myModuleNameLabel.setIcon(moduleIcon);

      myConflict = ((BuildVariantTable)table).findConflict(row);

      myModuleNameLabel.setToolTipText(myConflict != null ? myConflict.toString() : null);
      myFixButton.setVisible(myConflict != null);

      Color background = isSelected ? table.getSelectionBackground() : table.getBackground();
      if (myConflict != null) {
        background = CONFLICT_CELL_BACKGROUND;
      }
      myPanel.setBackground(background);

      Border border = hasFocus ? UIUtil.getTableFocusCellHighlightBorder() : EMPTY_BORDER;
      myPanel.setBorder(border);
    }

    @Override
    public Object getCellEditorValue() {
      return myValue;
    }
  }
}
