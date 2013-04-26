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

import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
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
  private final BuildVariantUpdater myUpdater;

  private JPanel myToolWindowPanel;
  private JBTable myVariantsTable;

  public BuildVariantView(@NotNull Project project) {
    myProject = project;
    myUpdater = new BuildVariantUpdater();
  }

  public static BuildVariantView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildVariantView.class);
  }

  private void createUIComponents() {
    myVariantsTable = new BuildVariantTable();
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
    final List<String[]> rows = Lists.newArrayList();
    final List<BuildVariantItem[]> variantNamesPerRow = Lists.newArrayList();
    final BuildVariantTable table = (BuildVariantTable)myVariantsTable;

    table.clearBuildVariants();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = Facets.getFirstFacet(module, AndroidFacet.ID);
      if (androidFacet == null) {
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
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        table.setModel(rows, variantNamesPerRow);
      }
    });
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
    AndroidFacet androidFacet = Facets.getFirstFacet(module, AndroidFacet.ID);
    return androidFacet != null ? androidFacet.getIdeaAndroidProject() : null;
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
            Border border = UIUtil.getTableFocusCellHighlightBorder();
            ((JComponent)c).setBorder(border);
          }
          return c;
        }
      });
    }

    void clearBuildVariants() {
      myCellEditors.clear();
    }

    void setModel(@NotNull List<String[]> rows, @NotNull List<BuildVariantItem[]> variantNamesPerRow) {
      setModel(new BuildVariantTableModel(rows, !variantNamesPerRow.isEmpty()));
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
              myUpdater.updateModule(myProject, selected.myModuleName, selected.myBuildVariantName);
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

  private static class BuildVariantTableModel extends DefaultTableModel {
    // Indicates if the "variant" column is editable.
    private final boolean myEditable;

    BuildVariantTableModel(List<String[]> rows, boolean editable) {
      super(rows.toArray(new Object[rows.size()][TABLE_COLUMN_NAMES.length]), TABLE_COLUMN_NAMES);
      myEditable = editable;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      // Only the "variant" column can be editable.
      return myEditable && column == VARIANT_COLUMN_INDEX;
    }
  }
}
