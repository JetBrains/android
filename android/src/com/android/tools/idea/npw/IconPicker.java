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
package com.android.tools.idea.npw;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.ide.common.vectordrawable.VdIcon;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

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
 * Generate a dialog to pick one pre-configured material icons in vector format.
 */
public class IconPicker extends JPanel {
  private static final Logger LOG = Logger.getInstance(IconPicker.class);
  private final static int COLUMN_NUMBER = 6;
  private final static String MATERIAL_DESIGN_ICONS_PATH = "images/material_design_icons/";

  private final List<VdIcon> mIconList = new ArrayList<VdIcon>();

  private VdIcon mSelectedIcon = null;

  private JBColor mIconBackground = new JBColor(UIUtil.getListBackground().getRGB(), 0x6F6F6F);

  // Note that "All" is a virtual category. All icons will be marked as a
  // specific category except "All". This is the reason when we reference this
  // array, we start from index 1.
  private final static String[] mIconCategories =
    {"All", "Action", "Alert", "Av", "Communication", "Content", "Device",
      "Editor", "File", "Hardware", "Image", "Maps", "Navigation",
      "Notification", "Social", "Toggle"};

  // This is a map from category name to a hash set of icon names.
  private final Multimap<String, VdIcon> mAllIconCategoryMap = TreeMultimap.create();

  private AbstractTableModel mModel = new AbstractTableModel() {

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
      return mIconList.size() > index ? mIconList.get(index) : null;
    }

    @Override
    public int getRowCount() {
      return mIconList.size() / COLUMN_NUMBER +
             ((mIconList.size() % COLUMN_NUMBER == 0) ? 0 : 1);
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NUMBER;
    }
  };

  private DefaultTableCellRenderer mTableRenderer = new DefaultTableCellRenderer() {
    @Override
    public void setValue(Object value) {
      if (value == null) {
        setToolTipText(null);
        setText("");
        setIcon(null);
      }
      else {
        VdIcon icon = (VdIcon)value;
        setToolTipText(icon.getName());
        setIcon(icon);
      }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      if (table.getValueAt(row, column) == null) {
        Component cell = super.getTableCellRendererComponent(table, value, false, false,
                                                             row, column);
        cell.setFocusable(false);
        return cell;
      } else {
        return  super.getTableCellRendererComponent(table, value, isSelected,
                                                    hasFocus, row, column);
      }
    }
  };

  private JBTable mTable = new JBTable(mModel);

  private JBScrollPane mTablePane = new JBScrollPane(mTable);

  /**
   * The UI layout is a border layout.
   *      ---           --------------------
   *      mCatPane   |   JBScrollPane mTablePane  |
   *                       ---------------
   *                 |    | JBTable mTable|       |
   *                       ---------------
   *                 |                           |
   *                    --------------------
   *      License Label
   */
  public IconPicker(final DialogBuilder builder) {
    super(new BorderLayout(20, 20));

    // Now on the right hand side, setup the table for icons.
    mTableRenderer.setBackground(mIconBackground);
    mTable.setDefaultRenderer(VdIcon.class, mTableRenderer);
    mTable.setRowHeight(48);
    mTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mTable.setCellSelectionEnabled(true);
    add(mTablePane);

    // On the left hand side, add the categories chooser.
    final JBList categoryList = new JBList(mIconCategories);
    final JBScrollPane mCategoryPane = new JBScrollPane(categoryList);

    JPanel p = new JPanel(new BorderLayout());
    add(p, BorderLayout.WEST);
    p.add(mCategoryPane);

    // Add license info at the bottom.
    HyperlinkLabel licenseLabel = new HyperlinkLabel();
    licenseLabel.setHyperlinkText("These icons are available under the ", "CC-BY license", "");
    licenseLabel.setHyperlinkTarget("https://creativecommons.org/licenses/by/4.0/");

    add(licenseLabel, BorderLayout.SOUTH);

    // Setup the picking interaction for the table.
    final ListSelectionModel selModel = mTable.getSelectionModel();
    mTable.getColumnModel().setColumnSelectionAllowed(true);
    mTable.setGridColor(mIconBackground);
    mTable.setIntercellSpacing(new Dimension(0, 0));
    mTable.setRowMargin(0);
    ListSelectionListener listener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        int row = mTable.getSelectedRow();
        int col = mTable.getSelectedColumn();
        VdIcon icon = (VdIcon)mModel.getValueAt(row, col);
        mSelectedIcon = icon;
        builder.setOkActionEnabled(icon != null);
      }
    };
    selModel.addListSelectionListener(listener);
    ListSelectionModel colSelModel = mTable.getColumnModel().getSelectionModel();

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
    loadInternalDrawables();

    selModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selModel.setSelectionInterval(0, 0);
    mTable.setColumnSelectionInterval(0, 0);
    mTable.requestFocusInWindow();
  }

  @Nullable
  public VdIcon getSelectIcon() {
    return mSelectedIcon;
  }

  /**
   * According to the name of the category, rebuild the IconList.
   * @param categoryName
   */
  private void updateIconList(String categoryName) {
    mIconList.clear();
    if (mAllIconCategoryMap.containsKey(categoryName)) {
      mIconList.addAll(mAllIconCategoryMap.get(categoryName));
      mTable.getColumnModel().setColumnSelectionAllowed(true);
    }
    else {
      mIconList.addAll(mAllIconCategoryMap.values());
    }
    mModel.fireTableDataChanged();
    // Pick the left upper corner one as the default selected one.
    mTable.setColumnSelectionInterval(0, 0);
    mTable.getSelectionModel().setSelectionInterval(0, 0);
  }

  /**
   * Based on the list of categories' names, build a full map: the key is the
   * category name, the value is a set of icon names.
   * At the same time, load the icons as XML files, and converted into VdIcon
   * objects. Then build a full list of such VdIcons.
   */
  private void loadInternalDrawables() {
    // Starting from 1, since 0 means "all".
    for (int i = 1; i < mIconCategories.length; i++) {
      String categoryName = mIconCategories[i];
      String categoryNameLowerCase = categoryName.toLowerCase(Locale.ENGLISH);
      String fullDirName = MATERIAL_DESIGN_ICONS_PATH + categoryNameLowerCase + '/';
      for (Iterator<String> iter = GraphicGenerator.getResourcesNames(fullDirName, SdkConstants.DOT_XML); iter.hasNext(); ) {
        final String iconName = iter.next();
        URL url = GraphicGenerator.class.getClassLoader().getResource(fullDirName + iconName);
        VdIcon icon = new VdIcon(url);
        mAllIconCategoryMap.put(categoryName, icon);
      }
    }

    mIconList.addAll(mAllIconCategoryMap.values());
    mModel.fireTableDataChanged();
  }
}
