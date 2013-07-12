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

import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
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
import java.util.List;

/**
 * The contents of the "Build Variants" tool window.
 */
public class BuildVariantView {
  private static final Object[] TABLE_COLUMN_NAMES = new Object[]{"Module", "Build Variant"};
  private static final int VARIANT_COLUMN_INDEX = 1;

  private final Project myProject;
  private BuildVariantUpdater myUpdater;

  private JPanel myToolWindowPanel;
  private JBTable myVariantsTable;

  private final List<BuildVariantSelectionChangeListener> myBuildVariantSelectionChangeListeners = Lists.newArrayList();

  public BuildVariantView(@NotNull Project project) {
    myProject = project;
    myUpdater = new BuildVariantUpdater();
  }

  @VisibleForTesting
  void setUpdater(@NotNull BuildVariantUpdater updater) {
    myUpdater = updater;
  }

  public static BuildVariantView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildVariantView.class);
  }

  public void addListener(@NotNull BuildVariantSelectionChangeListener listener) {
    synchronized (this) {
      myBuildVariantSelectionChangeListeners.add(listener);
    }
  }

  public void removeListener(@NotNull BuildVariantSelectionChangeListener listener) {
    synchronized (this) {
      myBuildVariantSelectionChangeListeners.remove(listener);
    }
  }

  private void createUIComponents() {
    myVariantsTable = new BuildVariantTable();
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
    if (GradleImportNotificationListener.isProjectImportInProgress()) {
      projectImportStarted();
      return;
    }

    final List<String[]> rows = Lists.newArrayList();
    final List<BuildVariantItem[]> variantNamesPerRow = Lists.newArrayList();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = Facets.getFirstFacetOfType(module, AndroidFacet.ID);
      if (androidFacet == null) {
        continue;
      }
      if (Facets.getFirstFacetOfType(module, AndroidGradleFacet.TYPE_ID) == null) {
        // If the module does not have an Android-Gradle facet, just skip it.
        continue;
      }
      JpsAndroidModuleProperties facetProperties = androidFacet.getConfiguration().getState();
      String variantName = facetProperties.SELECTED_BUILD_VARIANT;

      BuildVariantItem[] variantNames = getVariantNames(module);
      if (variantNames != null) {
        // If we got here IdeaAndroidProject is *not* null.
        variantName = getAndroidProject(module).getSelectedVariant().getName();
        variantNamesPerRow.add(variantNames);
      }

      if (variantName != null) {
        String[] row = {module.getName(), variantName};
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
    } else {
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
    AndroidFacet androidFacet = Facets.getFirstFacetOfType(module, AndroidFacet.ID);
    return androidFacet != null ? androidFacet.getIdeaAndroidProject() : null;
  }

  public interface BuildVariantSelectionChangeListener {
    /**
     * Indicates that a user selected a build variant from the "Build Variants" tool window.
     * <p/>
     * This notification occurs:
     * <ul>
     *  <li>after the user selected a build variant from the drop-down</li>
     *  <li>project structure has been updated according to selected build variant</li>
     * </ul>
     * <p/>
     * This listener will not be invoked if the project structure update fails.
     *
     * @param facet the Android facet containing the selected build variant.
     */
    void buildVariantSelected(@NotNull AndroidFacet facet);
  }

  private static class BuildVariantItem implements Comparable<BuildVariantItem> {
    @NotNull final String myModuleName;
    @NotNull final String myBuildVariantName;

    BuildVariantItem(@NotNull String moduleName, @NotNull String buildVariantName) {
      myModuleName = moduleName;
      myBuildVariantName = buildVariantName;
    }

    @Override
    public int compareTo(BuildVariantItem o) {
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
      setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (c instanceof JComponent) {
            // add some padding to table cells. It is hard to read text of combo box.
            ((JComponent)c).setBorder(BorderFactory.createEmptyBorder(4, 3, 5, 3));
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
      List<String[]> content = ImmutableList.of();
      setModel(new BuildVariantTableModel(content));
      myCellEditors.clear();
    }

    void setModel(@NotNull List<String[]> rows, @NotNull List<BuildVariantItem[]> variantNamesPerRow) {
      setLoading(false);
      if (rows.isEmpty()) {
        // This is most likely an old-style (pre-Gradle) Android project. Just leave the table empty.
        setModel(new BuildVariantTableModel(rows));
        return;
      }

      boolean hasVariants = !variantNamesPerRow.isEmpty();
      List<String[]> content = ImmutableList.of();
      if (hasVariants) {
        content = rows;
      }

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

        JComboBox editor = new JComboBox(items);
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
      if (column == 1 && row >= 0 && row < myCellEditors.size()) {
        return myCellEditors.get(row);
      }
      return super.getCellEditor(row, column);
    }
  }

  @VisibleForTesting
  void buildVariantSelected(@NotNull String moduleName, @NotNull String variantName) {
    final AndroidFacet updatedFacet = myUpdater.updateModule(myProject, moduleName, variantName);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (updatedFacet != null) {
          for (BuildVariantSelectionChangeListener listener : myBuildVariantSelectionChangeListeners) {
            listener.buildVariantSelected(updatedFacet);
          }
        }
      }
    });
  }

  private static class BuildVariantTableModel extends DefaultTableModel {
    BuildVariantTableModel(List<String[]> rows) {
      super(rows.toArray(new Object[rows.size()][TABLE_COLUMN_NAMES.length]), TABLE_COLUMN_NAMES);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      // Only the "variant" column can be editable.
      return column == VARIANT_COLUMN_INDEX;
    }
  }
}
