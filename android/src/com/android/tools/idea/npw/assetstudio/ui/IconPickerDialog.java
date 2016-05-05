/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.ide.common.vectordrawable.VdIcon;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Generate a dialog to pick a pre-configured material icon in vector format.
 */
public final class IconPickerDialog extends DialogWrapper {
  private static final String MATERIAL_DESIGN_ICONS_PATH = "images/material_design_icons/";
  private static final String DEFAULT_ICON_NAME = "action/ic_android_black_24dp.xml";

  // Note that "All" is a virtual category. All icons are marked with a specific category, but
  // no filtering will be done when "All" is selected. This is the reason we reference this array
  // starting from index 1.
  private static final String[] ICON_CATEGORIES =
    {"All", "Action", "Alert", "Av", "Communication", "Content", "Device", "Editor", "File", "Hardware", "Image", "Maps", "Navigation",
      "Notification", "Social", "Toggle"};
  private static final String ALL_CATEGORY = ICON_CATEGORIES[0];

  private static final int COLUMN_NUMBER = 6;
  private static final int ICON_ROW_HEIGHT = 48;
  // Suppress JBColor warning as this Color is intentionally used only for Darcula
  @SuppressWarnings("UseJBColor") public static final Color DARCULA_ICON_BACKGROUND = new Color(0x5D5D5D);

  /**
   * A mapping of all categories to their target icons.
   */
  private final Multimap<String, VdIcon> myCategoryIcons = TreeMultimap.create();

  /**
   * A list of all active icons (based on the currently selected category).
   */
  private final List<VdIcon> myIconList = new ArrayList<>();

  private final AbstractTableModel myModel = new AbstractTableModel() {

    @Override
    public String getColumnName(int column) {
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return VdIcon.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      int index = rowIndex * COLUMN_NUMBER + columnIndex;
      if (index < 0) {
        return null;
      }
      return myIconList.size() > index ? myIconList.get(index) : null;
    }

    @Override
    public int getRowCount() {
      return myIconList.size() / COLUMN_NUMBER + ((myIconList.size() % COLUMN_NUMBER == 0) ? 0 : 1);
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NUMBER;
    }
  };
  private final JBTable myIconTable = new JBTable(myModel);
  private final JBScrollPane myTablePane = new JBScrollPane(myIconTable);
  private final DefaultTableCellRenderer myTableRenderer = new DefaultTableCellRenderer() {
    @Override
    public void setValue(Object value) {
      VdIcon icon = (VdIcon)value;
      setText("");
      setToolTipText(icon != null ? icon.getName() : null);
      setIcon(icon);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (table.getValueAt(row, column) == null) {
        Component cell = super.getTableCellRendererComponent(table, value, false, false, row, column);
        cell.setFocusable(false);
        return cell;
      }
      else {
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
    }
  };

  private JPanel myContentPanel;
  private JPanel myCategoriesPanel;
  private JPanel myIconsPanel;
  @SuppressWarnings("unused") private JPanel myLicensePanel;
  private HyperlinkLabel myLicenseLabel;

  @Nullable private VdIcon mySelectedIcon = null;

  public IconPickerDialog(@Nullable VdIcon selectedIcon) {
    super(false);

    setTitle("Select Icon");
    initializeIconMap();

    // On the left hand side, add the categories chooser.
    final JBList categoryList = new JBList(ICON_CATEGORIES);
    final JBScrollPane categoryPane = new JBScrollPane(categoryList);
    myCategoriesPanel.add(categoryPane);


    // The default panel color in darcula mode is too dark given that our icons are all black. We
    // provide a lighter color for better contrast.
    Color iconBackgroundColor = UIUtil.isUnderDarcula() ? DARCULA_ICON_BACKGROUND : UIUtil.getListBackground();

    // For the main content area, display a grid if icons
    myIconTable.setBackground(iconBackgroundColor);
    myIconTable.setDefaultRenderer(VdIcon.class, myTableRenderer);
    myIconTable.setRowHeight(ICON_ROW_HEIGHT);
    myIconTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myIconTable.setCellSelectionEnabled(true);
    myIconsPanel.add(myTablePane);

    // Add license info at the bottom.
    myLicenseLabel.setHyperlinkText("These icons are available under the ", "CC-BY license", "");
    myLicenseLabel.setHyperlinkTarget("https://creativecommons.org/licenses/by/4.0/");

    // Setup the picking interaction for the table.
    final ListSelectionModel selModel = myIconTable.getSelectionModel();
    myIconTable.getColumnModel().setColumnSelectionAllowed(true);
    myIconTable.setGridColor(iconBackgroundColor);
    myIconTable.setIntercellSpacing(new Dimension(0, 0));
    myIconTable.setRowMargin(0);
    ListSelectionListener listener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        int row = myIconTable.getSelectedRow();
        int col = myIconTable.getSelectedColumn();
        VdIcon icon = (VdIcon)myModel.getValueAt(row, col);
        mySelectedIcon = icon;
        setOKActionEnabled(icon != null);
      }
    };
    selModel.addListSelectionListener(listener);
    ListSelectionModel colSelModel = myIconTable.getColumnModel().getSelectionModel();

    colSelModel.addListSelectionListener(listener);

    // Setup the picking interaction for the category list.
    categoryList.addListSelectionListener(new ListSelectionListener() {

      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateIconList((String)categoryList.getSelectedValue());
      }
    });
    categoryList.setSelectedIndex(0);

    selModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selModel.setSelectionInterval(0, 0);
    myIconTable.setColumnSelectionInterval(0, 0);
    myIconTable.requestFocusInWindow();

    if (selectedIcon != null) {
      initializeSelection(selectedIcon);
    }

    init();
  }

  @NotNull
  public static VdIcon getDefaultIcon() {
    URL url = GraphicGenerator.class.getClassLoader().getResource(MATERIAL_DESIGN_ICONS_PATH + DEFAULT_ICON_NAME);
    return new VdIcon(url);
  }

  private void initializeSelection(@NotNull VdIcon selectedIcon) {
    for (int r = 0; r < myIconTable.getRowCount(); r++) {
      for (int c = 0; c < myIconTable.getColumnCount(); c++) {
        VdIcon icon = (VdIcon)myIconTable.getValueAt(r, c);
        if (icon.getURL().equals(selectedIcon.getURL())) {
          myIconTable.changeSelection(r, c, false, false);
          return;
        }
      }
    }
  }

  private void initializeIconMap() {
    for (int i = 1; i < ICON_CATEGORIES.length; i++) {
      String categoryName = ICON_CATEGORIES[i];
      String categoryNameLowerCase = categoryName.toLowerCase(Locale.ENGLISH);
      String fullDirName = MATERIAL_DESIGN_ICONS_PATH + categoryNameLowerCase + '/';
      for (Iterator<String> iter = GraphicGenerator.getResourcesNames(fullDirName, SdkConstants.DOT_XML); iter.hasNext(); ) {
        final String iconName = iter.next();
        URL url = GraphicGenerator.class.getClassLoader().getResource(fullDirName + iconName);
        VdIcon icon = new VdIcon(url);
        myCategoryIcons.put(categoryName, icon);
      }
    }
    // Now that each category has been initialized, collect all icons into the "all" category
    myCategoryIcons.putAll(ALL_CATEGORY, myCategoryIcons.values());
  }

  @Nullable
  public VdIcon getSelectedIcon() {
    return mySelectedIcon;
  }

  private void updateIconList(@NotNull String categoryName) {
    myIconList.clear();
    assert myCategoryIcons.containsKey(categoryName);
    myIconList.addAll(myCategoryIcons.get(categoryName));
    myIconTable.getColumnModel().setColumnSelectionAllowed(true);

    myModel.fireTableDataChanged();
    // Pick the left upper corner one as the default selected one.
    myIconTable.setColumnSelectionInterval(0, 0);
    myIconTable.getSelectionModel().setSelectionInterval(0, 0);
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
