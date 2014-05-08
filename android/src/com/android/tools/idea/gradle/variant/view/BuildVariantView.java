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
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The contents of the "Build Variants" tool window.
 */
public class BuildVariantView {
  private static final Object[] TABLE_COLUMN_NAMES = new Object[]{"Module", "Build Variant"};

  private static final int MODULE_COLUMN_INDEX = 0;
  private static final int VARIANT_COLUMN_INDEX = 1;

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
    Arrays.sort(modules, ModulesAlphaComparator.INSTANCE);
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
    String name = module.getName();
    int rowCount = myVariantsTable.getRowCount();
    for (int row = 0; row < rowCount; row++) {
      if (name.equals(myVariantsTable.getValueAt(row, MODULE_COLUMN_INDEX))) {
        myVariantsTable.getSelectionModel().setSelectionInterval(row, row);
        myVariantsTable.getColumnModel().getSelectionModel().setSelectionInterval(MODULE_COLUMN_INDEX, MODULE_COLUMN_INDEX);
        TableUtil.scrollSelectionToVisible(myVariantsTable);
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

    BuildVariantTable() {
      super(new BuildVariantTableModel(Collections.<Object[]>emptyList()));
      setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Object newValue = value;
          Icon icon = null;
          if (newValue instanceof Module) {
            Module module = (Module)newValue;
            newValue = module.getName();
            if (column == MODULE_COLUMN_INDEX) {
              icon = GradleUtil.getModuleIcon(module);
            }
          }

          Component c = super.getTableCellRendererComponent(table, newValue, isSelected, hasFocus, row, column);

          if (c instanceof JLabel) {
            ((JLabel)c).setIcon(icon);
          }

          if (c instanceof JComponent) {
            JComponent component = (JComponent)c;
            Conflict conflictFound = null;
            for (Conflict conflict : myConflicts) {
              Object moduleName = table.getValueAt(row, MODULE_COLUMN_INDEX);
              if (conflict.getSource().getName().equals(moduleName)) {
                conflictFound = conflict;
                break;
              }
            }

            Color background = isSelected ? BuildVariantTable.this.getSelectionBackground() : BuildVariantTable.this.getBackground();
            if (conflictFound != null) {
              background = MessageType.ERROR.getPopupBackground();
            }
            component.setBackground(background);

            String toolTip = conflictFound != null? conflictFound.toString() : null;
            component.setToolTipText(toolTip);

            // add some padding to table cells. It is hard to read text of combo box.
            component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(), BorderFactory.createEmptyBorder(3, 2, 4, 2)));
          }

          return c;
        }
      });
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
    public TableCellEditor getCellEditor(int row, int column) {
      if (column == VARIANT_COLUMN_INDEX && row >= 0 && row < myCellEditors.size()) {
        return myCellEditors.get(row);
      }
      return super.getCellEditor(row, column);
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

    @Override
    public boolean isCellEditable(int row, int column) {
      // Only the "variant" column can be editable.
      return column == VARIANT_COLUMN_INDEX;
    }
  }
}
