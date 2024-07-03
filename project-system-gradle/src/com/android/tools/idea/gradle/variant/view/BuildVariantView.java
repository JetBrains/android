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

import static com.android.tools.idea.projectsystem.gradle.AndroidIconProviderProjectGradleToken.getModuleIcon;
import static com.intellij.ui.TableUtil.scrollSelectionToVisible;
import static com.intellij.util.ui.JBUI.scale;
import static com.intellij.util.ui.UIUtil.getTableFocusCellHighlightBorder;
import static com.intellij.util.ui.UIUtil.getToolTipBackground;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.intellij.CommonBundle;
import com.android.tools.idea.gradle.variant.conflict.ConflictSetKt;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;

/**
 * The contents of the "Build Variants" tool window.
 */
public class BuildVariantView {
  private static final int MODULE_COLUMN_INDEX = 0;
  private static final int VARIANT_COLUMN_INDEX = 1;
  private static final int ABI_COLUMN_INDEX = 2;

  public static final Color CONFLICT_CELL_BACKGROUND = MessageType.WARNING.getPopupBackground();

  private final Project myProject;

  private JPanel myToolWindowPanel;
  private JBTable myVariantsTable;
  private JPanel myNotificationPanel;
  private JButton myImportDefaultsButton;

  @NotNull
  private final List<Conflict> myConflicts = new ArrayList<>();

  @NotNull
  public static BuildVariantView getInstance(@NotNull Project project) {
    return project.getService(BuildVariantView.class);
  }

  private BuildVariantView(@NotNull Project project) {
    myProject = project;
    ((JComponent)myVariantsTable.getParent().getParent()).setBorder(JBUI.Borders.empty());
  }

  private void createUIComponents() {
    myVariantsTable = new BuildVariantTable();
    TableSpeedSearch.installOn(myVariantsTable);
    myNotificationPanel = new NotificationPanel();
    myNotificationPanel.setVisible(false);

    myImportDefaultsButton = new JButton();
    myImportDefaultsButton.setToolTipText(
        "Resets variant selection to the default variants (automatically selected if not specified in Gradle build files)");
    myImportDefaultsButton.addActionListener(
      event -> BuildVariantUpdater.requestGradleSync(myProject, null, true));
  }

  /**
   * Creates the contents of the "Build Variants" tool window.
   *
   * @param toolWindow the tool window whose contents will be created.
   */
  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    ContentFactory contentFactory = ContentFactory.getInstance();
    Content content = contentFactory.createContent(myToolWindowPanel, "", false);
    toolWindow.getContentManager().addContent(content);
  }

  private void updateContents() {
    getVariantsTable().setBuildVariantTableModel(BuildVariantTableModel.create(myProject));
  }

  private void projectImportStarted() {
    getVariantsTable().setLoading();
  }

  private void projectImportFinished() {
    ConflictSet.findConflicts(myProject).showSelectionConflicts();
  }

  @NotNull
  private BuildVariantTable getVariantsTable() {
    return (BuildVariantTable)myVariantsTable;
  }

  public void updateContents(@NotNull List<Conflict> conflicts) {
    myNotificationPanel.setVisible(!conflicts.isEmpty());
    ((NotificationPanel)myNotificationPanel).myCurrentConflictIndex = -1;
    myConflicts.clear();
    myConflicts.addAll(conflicts);
    updateContents();
  }

  public void selectModule(int row) {
    select(row, MODULE_COLUMN_INDEX);
  }

  public void select(int row, int col) {
    myVariantsTable.getSelectionModel().setSelectionInterval(row, row);
    myVariantsTable.getColumnModel().getSelectionModel().setSelectionInterval(col, col);
    scrollSelectionToVisible(myVariantsTable);
    myVariantsTable.requestFocusInWindow();
  }

  public void findAndSelectVariantEditor(@NotNull Module module) {
    findAndSelect(module, VARIANT_COLUMN_INDEX);
  }

  private void findAndSelect(@NotNull Module module, int columnIndex) {
    int rowCount = myVariantsTable.getRowCount();
    for (int row = 0; row < rowCount; row++) {
      if (module.equals(myVariantsTable.getValueAt(row, MODULE_COLUMN_INDEX))) {
        select(row, columnIndex);
        break;
      }
    }
  }

  private class NotificationPanel extends JPanel {
    int myCurrentConflictIndex = -1;

    NotificationPanel() {
      super(new BorderLayout());
      Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
      setBackground(color == null ? getToolTipBackground() : color);
      setBorder(JBUI.Borders.empty(1, 15)); // Same as EditorNotificationPanel
      setPreferredSize(new Dimension(-1, scale(24)));

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

    private int nextConflictModule(Function<Integer, Integer> getNextIndex) {
      int index = getNextIndex.apply(myCurrentConflictIndex);
      for(int i = 0; i < getVariantsTable().getRowCount(); i++) {
        if(getVariantsTable().hasConflict(index)) {
          return index;
        }
        index = getNextIndex.apply(index);
      }
      return myCurrentConflictIndex;
    }

    private int findConflictModuleForward() {
      return nextConflictModule(index -> (index + 1) % getVariantsTable().getRowCount());
    }

    private int findConflictModuleBackwards() {
      return nextConflictModule(index -> index <= 0 ? getVariantsTable().getRowCount() - 1 : index - 1);
    }

    private void navigateConflicts(boolean forward) {
      int conflictCount = myConflicts.size();
      if (conflictCount == 0) {
        return;
      }
      myCurrentConflictIndex = forward ? findConflictModuleForward() : findConflictModuleBackwards();
      selectModule(myCurrentConflictIndex);
    }
  }

  /**
   * Represents the contents of the Build Variant table in the panel.
   */
  private class BuildVariantTable extends JBTable {
    // If true, then a "loading" label is displayed instead of the table rows. This prevents the user from making changes while the IDE
    // is working to apply a previous selection (e.g., sync).
    private boolean myLoading;

    // Since the modules cells are not editable, they share a single common editor.
    private final ModuleTableCell myModuleCellEditor = new ModuleTableCell();

    // The renderers for the module, build variant, and ABI cells.
    private final ModuleTableCell myModuleCellRenderer = new ModuleTableCell();
    private final VariantsCellRenderer myVariantsCellRenderer = new VariantsCellRenderer();
    private final AbisCellRenderer myAbisCellRenderer = new AbisCellRenderer();

    BuildVariantTable() {
      super(BuildVariantTableModel.createEmpty());
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          int column = getSelectedColumn();
          int row = getSelectedRow();

          // Map the F2 button to enter edit mode when the variant and ABI cells are selected.
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
      TableSpeedSearch.installOn(this);
    }

    /**
     * @param row the row for which a conflict will be searched
     * @return the conflict for the provided row, if exists.
     */
    @NotNull
    ImmutableList<Conflict> findConflict(int row) {
      Object module = getValueAt(row, MODULE_COLUMN_INDEX);
      if (!(module instanceof Module)) {
        return ImmutableList.of();
      }
      var conflicts = myConflicts.stream()
        .filter(conflict -> conflict.hasAffectedModule((Module) module))
        .collect(Collectors.toList());

      return new ImmutableList.Builder<Conflict>().addAll(conflicts).build();
    }

    boolean hasConflict(int row) {
      return !findConflict(row).isEmpty();
    }

    @Nullable
    Module findModule(int row) {
      Object module = getValueAt(row, MODULE_COLUMN_INDEX);
      return module instanceof Module ? (Module) module : null;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return true;
    }

    void updateLoadingStatus(boolean loading) {
      myLoading = loading;
      setPaintBusy(myLoading);
      String text = myLoading ? CommonBundle.getLoadingTreeNodeText() : "Nothing to Show";
      getEmptyText().setText(text);
    }

    void setLoading() {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), () -> {
        updateLoadingStatus(true);
        setModel(BuildVariantTableModel.createEmpty());
      });
    }

    private void setBuildVariantTableModel(@NotNull BuildVariantTableModel model) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), () -> {
        updateLoadingStatus(false);
        setModel(model);
      });
    }

    @Nullable
    private DefaultCellEditor createCellEditor(int row, BuildVariantTableRow tableRow) {
      BuildVariantItem[] items = tableRow.buildVariantsAsArray();
      if (items == null) return null;
      BuildVariantItem selected = tableRow.variantItem();

      ComboBox<BuildVariantItem> editor = new ComboBox<>(items);
      editor.setSelectedItem(selected);

      editor.setBorder(JBUI.Borders.empty());
      editor.addItemListener(e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          BuildVariantItem selectedVariant = (BuildVariantItem)e.getItem();
          BuildVariantUpdater.getInstance(myProject).updateSelectedBuildVariant(tableRow.getModule(), selectedVariant.getBuildVariantName());
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
      return defaultCellEditor;
    }

    @Nullable
    private DefaultCellEditor createAbiCellEditor(int row, BuildVariantTableRow tableRow) {
      AbiItem[] items = tableRow.abisAsArray();
      if (items == null) return null;
      AbiItem selected = tableRow.abiItem();

      ComboBox<AbiItem> editor = new ComboBox<>(items);
      if (selected != null) {
        editor.setSelectedItem(selected);
      }

      editor.setBorder(JBUI.Borders.empty());
      editor.addItemListener(e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          AbiItem selectedAbi = (AbiItem)e.getItem();
          BuildVariantUpdater.getInstance(myProject).updateSelectedAbi(tableRow.getModule(), selectedAbi.getAbiName());
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
      return defaultCellEditor;
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
      BuildVariantTableRow tableRow = ((BuildVariantTableModel)getModel()).getRows().get(row);
      if ((column == VARIANT_COLUMN_INDEX)) {
        return createCellEditor(row, tableRow);
      }
      if ((column == ABI_COLUMN_INDEX)) {
        return createAbiCellEditor(row, tableRow);
      }
      return myModuleCellEditor;
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
        ImmutableList<Conflict> conflictFound = ((BuildVariantTable)table).findConflict(row);
        boolean hasConflicts = !conflictFound.isEmpty();
        if (hasConflicts) {
          background = CONFLICT_CELL_BACKGROUND;
        }
        component.setBackground(background);
        component.setFont(StartupUiUtil.getLabelFont());
        Module module = ((BuildVariantTable)table).findModule(row);
        String toolTip = hasConflicts && module != null ? ConflictSetKt.variantConflictMessage(module, conflictFound)
                                      : variantsCellHelpTooltipText;
        component.setToolTipText(toolTip);

        // add some padding to table cells. It is hard to read text of combo box.
        component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(), JBUI.Borders.empty(3, 2, 4, 2)));
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
        component.setFont(StartupUiUtil.getLabelFont());
        // Build variant conflicts do not change the tooltip of ABI cells.
        component.setToolTipText(abisCellHelpTooltipText);

        // add some padding to table cells. It is hard to read text of combo box.
        component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(), JBUI.Borders.empty(3, 2, 4, 2)));

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

    @NotNull
    private ImmutableList<Conflict> myConflicts = ImmutableList.of();

    private JPanel myPanel;
    private JLabel myModuleNameLabel;
    private JPanel myButtonsPanel;

    private Module myValue;

    ModuleTableCell() {
      myModuleNameLabel = new JLabel();
      myModuleNameLabel.setOpaque(false);
      myButtonsPanel = new JPanel();
      myButtonsPanel.setOpaque(false);

      myPanel = new JPanel(new BorderLayout()) {
        @Override
        public String getToolTipText(MouseEvent e) {
          String toolTip = getToolTipTextIfUnderX(myModuleNameLabel, e.getX());
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

      String moduleName = null;
      Icon moduleIcon = null;
      boolean isAndriodGradleModule = false;
      Module module = null;
      if (value != null) {
        module = (Module) value;
        myValue = module;
        if (!module.isDisposed()) {
          String modulePath = GradleProjectResolverUtil.getGradleIdentityPathOrNull(module);
          // Note: modulePath should never be null here.
          moduleName = modulePath != null ? modulePath : module.getName();
          moduleIcon = getModuleIcon(module);
          isAndriodGradleModule = GradleAndroidModel.get(module) != null;
        }
      }
      myModuleNameLabel.setForeground(AdtUiUtils.DEFAULT_FONT_COLOR);
      myModuleNameLabel.setFont(StartupUiUtil.getLabelFont());
      myModuleNameLabel.setText(moduleName == null ? "" : moduleName);
      myModuleNameLabel.setIcon(moduleIcon);

      Color background = isSelected ? table.getSelectionBackground() : table.getBackground();

      if (isAndriodGradleModule) {
        myConflicts = ((BuildVariantTable)table).findConflict(row);
        boolean hasConflicts = !myConflicts.isEmpty();

        myModuleNameLabel.setToolTipText(hasConflicts ? ConflictSetKt.variantConflictMessage(module, myConflicts) : null);
        if (hasConflicts) {
          background = CONFLICT_CELL_BACKGROUND;
        }
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

  static class SyncListener implements GradleSyncListenerWithRoot {
    @Override
    public void syncStarted(@NotNull Project project, @SystemIndependent String rootProjectPath) {
      BuildVariantView.getInstance(project).projectImportStarted();
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage, @SystemIndependent String rootProjectPath) {
      BuildVariantView.getInstance(project).projectImportFinished();
    }

    @Override
    public void syncSucceeded(@NotNull Project project, @SystemIndependent String rootProjectPath) {
      BuildVariantView.getInstance(project).projectImportFinished();
    }

    @Override
    public void syncSkipped(@NotNull Project project) {
      BuildVariantView.getInstance(project).projectImportFinished();
    }

    @Override
    public void syncCancelled(@NotNull Project project, @SystemIndependent String rootProjectPath) {
      BuildVariantView.getInstance(project).getVariantsTable().updateLoadingStatus(false);
    }
  }
}
