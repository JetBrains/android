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
package com.android.tools.idea.ui.resourcechooser;

import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.strings.FontUtil;
import com.android.tools.idea.editors.strings.StringResourceEditorProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.ui.resourcechooser.ResourceChooserItem.DEFAULT_FOLDER_NAME;

/**
 * Panel which shows table resources such as strings, dimensions, etc.
 */
public class ResourceTablePanel implements HyperlinkListener {
  private final ChooseResourceDialog myDialog;
  private HyperlinkLabel myEditTranslationsLink;
  private JBLabel myNameLabel;
  private JBTable myTable;
  private JPanel myPanel;
  private JBScrollPane myScrollPane;

  public ResourceTablePanel(@NotNull ChooseResourceDialog dialog) {
    myDialog = dialog;
    myTable.setTableHeader(null);
    myTable.setBackground(UIUtil.getLabelBackground());
    myTable.setBorder(BorderFactory.createEmptyBorder());
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myScrollPane.getViewport().setBorder(null);
    myPanel.setPreferredSize(JBUI.size(400, 400));
    myTable.setName("valueTable"); // for tests

    TableSpeedSearch speedSearch = new TableSpeedSearch(myTable);
    speedSearch.setClearSearchOnNavigateNoMatch(true);
  }

  private void createUIComponents() {
    myEditTranslationsLink = new HyperlinkLabel("EDIT TRANSLATIONS");
    myEditTranslationsLink.addHyperlinkListener(this);
    myEditTranslationsLink.setVisible(false);
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  public void select(@Nullable ResourceChooserItem item) {
    if (item != null) {
      myTable.setModel(new ResourceTableModel(item));
      // Pick a font that can display the various translations
      TableColumn valueColumn = myTable.getColumnModel().getColumn(1);
      valueColumn.setCellRenderer(new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (column == 1) {
            String s = value.toString();
            component.setFont(FontUtil.getFontAbleToDisplay(s, table.getFont()));
          }
          else {
            component.setFont(table.getFont());
          }
          return component;
        }
      });
      myNameLabel.setText(item.getName());
      myTable.setRowHeight(ChooseResourceDialog.TABLE_CELL_HEIGHT);
      myEditTranslationsLink.setVisible(item.getType() == ResourceType.STRING && !item.isFramework());
    } else {
      myNameLabel.setText("");
      myTable.setModel(new DefaultTableModel(0, 2));
      myEditTranslationsLink.setVisible(false);
    }
  }

  // --- Implements HyperlinkListener ---

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    //myDialog.doCancelAction();
    myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
    StringResourceEditorProvider.openEditor(myDialog.getModule());
  }

  private static class ResourceTableModel extends AbstractTableModel {
    private final List<Pair<FolderConfiguration,String>> myPairs;
    private final ResourceChooserItem myItem;

    public ResourceTableModel(@NotNull ResourceChooserItem item) {
      myItem = item;
      myPairs = item.getQualifiersAndValues();
    }

    @Override
    public int getRowCount() {
      return myPairs.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Pair<FolderConfiguration, String> pair = myPairs.get(rowIndex);
      if (columnIndex == 1) {
        return pair.getSecond();
      }
      assert columnIndex == 0 : columnIndex;

      FolderConfiguration configuration = pair.getFirst();
      if (configuration.isDefault()) {
        return DEFAULT_FOLDER_NAME;
      }

      if (myItem.getType() == ResourceType.STRING) {
        // Show language name (and region)
        LocaleQualifier locale = configuration.getLocaleQualifier();
        if (locale != null) {
          if (locale.hasLanguage()) {
            String language = LocaleManager.getLanguageName(locale.getLanguage());
            if (language != null) {
              if (locale.hasRegion()) {
                assert locale.getRegion() != null;
                String region = LocaleManager.getRegionName(locale.getRegion());
                if (region != null) {
                  return language + ", " + region;
                }
              }
              return language;
            }
          }
        }
      }

      String qualifierString = configuration.getQualifierString();
      if (qualifierString.isEmpty()) {
        qualifierString = DEFAULT_FOLDER_NAME;
      }
      return qualifierString;
    }
  }
}
