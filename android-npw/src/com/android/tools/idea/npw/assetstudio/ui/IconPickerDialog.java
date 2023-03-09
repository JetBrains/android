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

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_FIND;

import com.android.ide.common.vectordrawable.VdIcon;
import com.android.tools.idea.MaterialVdIconsProvider;
import com.android.tools.idea.material.icons.MaterialVdIcons;
import com.android.tools.idea.material.icons.common.MaterialIconsMetadataUrlProvider;
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog to pick a pre-configured material icon in vector format.
 */
public final class IconPickerDialog extends DialogWrapper implements DataProvider {
  private final Logger LOG = Logger.getInstance(IconPickerDialog.class);

  private static final String MATERIAL_ICONS_PREFIX = "Material Icons";
  private static final int COLUMN_NUMBER = 6;
  private static final int ICON_ROW_HEIGHT = JBUI.scale(48 + 16);
  /**
   * Approximate number of icons bundled in images/material/icons/.
   */
  private static final int EXPECTED_NUMBER_OF_ICONS = 1100;

  private MaterialVdIcons myIcons = MaterialVdIcons.EMPTY;

  /**
   * A list of all active icons (based on the currently selected category).
   */
  private final List<VdIcon> myIconList = new ArrayList<>(EXPECTED_NUMBER_OF_ICONS);
  private final List<VdIcon> myFilteredIconList = new ArrayList<>(EXPECTED_NUMBER_OF_ICONS);

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
      return myFilteredIconList.size() > index ? myFilteredIconList.get(index) : null;
    }

    @Override
    public int getRowCount() {
      return myFilteredIconList.size() / COLUMN_NUMBER + ((myFilteredIconList.size() % COLUMN_NUMBER == 0) ? 0 : 1);
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NUMBER;
    }
  };

  private final JBTable myIconTable = new JBTable(myModel);

  private JPanel myContentPanel;
  private JPanel myIconsPanel;
  @SuppressWarnings("unused") private JPanel myLicensePanel;
  private HyperlinkLabel myLicenseLabel;
  private SearchTextField mySearchField;
  private JComboBox<String> myCategoriesBox;
  private JComboBox<String> myStylesBox;

  @Nullable private VdIcon mySelectedIcon;
  @Nullable private VdIcon myIconToSelectInTable;

  private ItemListener myStylesBoxListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() == ItemEvent.DESELECTED || e.getItem() == null) {
        return;
      }
      int categoryCurrentIndex = myCategoriesBox.getSelectedIndex();
      setCategoriesBoxModel(myStylesBox.getSelectedIndex());
      if (categoryCurrentIndex >= 0 && categoryCurrentIndex < myCategoriesBox.getItemCount()) {
        myCategoriesBox.setSelectedIndex(categoryCurrentIndex);
      }
    }
  };
  private ItemListener myCategoriesBoxListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() == ItemEvent.DESELECTED || e.getItem() == null) {
        return;
      }
      updateIconList();
    }
  };

  public IconPickerDialog(@Nullable VdIcon selectedIcon) {
    this(selectedIcon, null, null);
  }

  @VisibleForTesting
  public IconPickerDialog(@Nullable VdIcon selectedIcon,
                          @Nullable MaterialIconsMetadataUrlProvider metadataUrlProvider,
                          @Nullable MaterialIconsUrlProvider iconsUrlProvider) {
    super(false);
    myIconToSelectInTable = selectedIcon;

    setTitle("Select Icon");

    // The default panel color in darcula mode is too dark given that our icons are all black.
    // We provide a lighter color for higher contrast.
    Color iconBackgroundColor = UIUtil.getListBackground();

    // For the main content area, display a grid if icons.
    myIconTable.setBackground(iconBackgroundColor);
    myIconTable.setDefaultRenderer(VdIcon.class, new IconPickerCellRenderer());
    myIconTable.setTableHeader(null);
    myIconTable.setRowHeight(ICON_ROW_HEIGHT);
    myIconTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myIconTable.setCellSelectionEnabled(true);
    myIconsPanel.add(new JBScrollPane(myIconTable));
    TableHoverListener.DEFAULT.removeFrom(myIconTable);
    myIconTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        char keyChar = e.getKeyChar();
        if (Character.isLetter(keyChar) || Character.isDigit(keyChar)) {
          mySearchField.setText(Character.toString(keyChar));
          mySearchField.requestFocus();
        }
        super.keyPressed(e);
      }
    });

    // Add license info at the bottom.
    myLicenseLabel.setHyperlinkText("These icons are available under the ", "Apache License Version 2.0", "");
    myLicenseLabel.setHyperlinkTarget("http://www.apache.org/licenses/LICENSE-2.0.txt");

    // Setup the picking interaction for the table.
    final ListSelectionModel selModel = myIconTable.getSelectionModel();
    myIconTable.getColumnModel().setColumnSelectionAllowed(true);
    myIconTable.setGridColor(iconBackgroundColor);
    myIconTable.setIntercellSpacing(JBUI.size(3, 3));
    myIconTable.setRowMargin(0);

    ListSelectionListener listener = e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      int row = myIconTable.getSelectedRow();
      int col = myIconTable.getSelectedColumn();
      VdIcon icon = row != -1 && col != -1 ? (VdIcon)myIconTable.getValueAt(row, col) : null;
      mySelectedIcon = icon;
      if (icon != null) {
        myIconToSelectInTable = icon;
      }
      setOKActionEnabled(icon != null);
    };

    selModel.addListSelectionListener(listener);
    ListSelectionModel colSelModel = myIconTable.getColumnModel().getSelectionModel();
    colSelModel.addListSelectionListener(listener);

    selModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selModel.setSelectionInterval(0, 0);
    myIconTable.setColumnSelectionInterval(0, 0);
    myIconTable.requestFocusInWindow();

    DataManager.registerDataProvider(myContentPanel, this);
    AnAction action = ActionManager.getInstance().getAction(ACTION_FIND);
    if (action != null) {
      new SearchTextField.FindAction().registerCustomShortcutSet(action.getShortcutSet(), getRootPane(), myDisposable);
    }

    init();

    myIconTable.getEmptyText().setText("Loading icons...");
    myIconTable.setPaintBusy(true);
    myStylesBox.setEnabled(false);
    myCategoriesBox.setEnabled(false);
    MaterialVdIconsProvider.loadMaterialVdIcons((materialVdIcons, status) -> {
      assert ApplicationManager.getApplication().isDispatchThread();
      myIconTable.getEmptyText().setText(StatusText.getDefaultEmptyText());
      populateIcons(materialVdIcons);
      if (status == MaterialVdIconsProvider.Status.FINISHED) {
        // Enable the combo boxes once it has finished loading all icons.
        myIconTable.setPaintBusy(false);
        myStylesBox.setEnabled(true);
        myCategoriesBox.setEnabled(true);
        myStylesBox.addItemListener(myStylesBoxListener);
        myCategoriesBox.addItemListener(myCategoriesBoxListener);
      }
      return null;
    }, metadataUrlProvider, iconsUrlProvider, getDisposable());
  }

  private void createUIComponents() {
    mySearchField = new SearchTextField(false);
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateFilter();
      }
    });
  }

  private void updateFilter() {
    String text = mySearchField.getText().trim();
    myFilteredIconList.clear();
    for (VdIcon icon : myIconList) {
      if (text.isEmpty() || StringUtil.containsIgnoreCase(icon.getDisplayName(), text)) {
        myFilteredIconList.add(icon);
      }
    }

    myModel.fireTableDataChanged();
  }

  private boolean tryToSelectIcon() {
    VdIcon iconToSelect = myIconToSelectInTable;
    myIconToSelectInTable = null;
    if (iconToSelect == null) {
      return false;
    }
    for (int r = 0; r < myIconTable.getRowCount(); r++) {
      for (int c = 0; c < myIconTable.getColumnCount(); c++) {
        VdIcon icon = (VdIcon)myIconTable.getValueAt(r, c);
        if (icon != null && icon.getDisplayName().equals(iconToSelect.getDisplayName())) {
          myIconTable.changeSelection(r, c, false, false);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Updates {@link #myIcons} from the given {@link MaterialVdIcons}
   * <p>
   * Populates {@link #myStylesBox}, {@link #myCategoriesBox}.
   * <p>
   * Updates the icon table and sets the selected icon when appropriate.
   */
  private void populateIcons(MaterialVdIcons icons) {
    boolean shouldUpdateIconList = myIcons == null || myIcons == MaterialVdIcons.EMPTY;
    myIcons = icons;
    // Set boxes, styles and categories model.
    setStylesBoxModel();
    setCategoriesBoxModel(0);
    myStylesBox.setSelectedIndex(0);
    myCategoriesBox.setSelectedIndex(0);

    if (shouldUpdateIconList) {
      updateIconList();
    }
  }

  private void setStylesBoxModel() {
    String[] stylesArray = Arrays.stream(myIcons.getStyles()).map((styleName) -> {
      if (styleName.startsWith(MATERIAL_ICONS_PREFIX)) {
        String styleShortened = styleName.substring(MATERIAL_ICONS_PREFIX.length()).trim();
        if (styleShortened.isEmpty()) {
          // The default 'Filled' style is not named as such, just "Material Icons".
          return "Filled";
        }
        return styleShortened;
      }
      return styleName;
    }).toArray(String[]::new);
    myStylesBox.setModel(new DefaultComboBoxModel<String>(stylesArray));
  }

  /**
   * Sets up {@link #myCategoriesBox} for the existing categories in a given style. The categories are obtained from
   * {@link MaterialVdIcons#getCategories(String)}.
   *
   * @param styleIndex The index that corresponds to a style in the {@link MaterialVdIcons#getStyles()} array
   */
  private void setCategoriesBoxModel(int styleIndex) {
    ArrayList<String> categoriesArray = Arrays.stream(myIcons.getCategories(myIcons.getStyles()[styleIndex]))
      .map((categoryName) -> categoryName.equals("av") ? "Audio/Video" : StringUtil.capitalize(categoryName))
      .collect(Collectors.toCollection(ArrayList::new));
    categoriesArray.add(0, "All");
    myCategoriesBox.setModel(new CollectionComboBoxModel<String>(categoriesArray, null));
  }

  @Nullable
  public VdIcon getSelectedIcon() {
    return mySelectedIcon;
  }

  @VisibleForTesting
  public JTable getTable() {
    return myIconTable;
  }

  @VisibleForTesting
  public void setFilter(String text) {
    mySearchField.setText(text);
  }

  /**
   * Updates displayed icons based on the currently selected style and category while applying the search filter.
   */
  private void updateIconList() {
    myIconList.clear();
    String style = myIcons.getStyles()[myStylesBox.getSelectedIndex()];
    Object categoryItem = myCategoriesBox.getSelectedItem();
    if (categoryItem instanceof String && categoryItem.equals("All")) {
      myIconList.addAll(Arrays.asList(myIcons.getAllIcons(style)));
    }
    else {
      String category = myIcons.getCategories(style)[myCategoriesBox.getSelectedIndex() - 1];
      myIconList.addAll(Arrays.asList(myIcons.getIcons(style, category)));
    }

    myIconTable.getColumnModel().setColumnSelectionAllowed(true);

    updateFilter();

    // Pick the left upper corner one as the default selected one.
    if (!tryToSelectIcon()) {
      myIconTable.setColumnSelectionInterval(0, 0);
      myIconTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  @Override
  @NotNull
  public JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  @Nullable
  public Object getData(@NotNull String dataId) {
    return SearchTextField.KEY.is(dataId) ? mySearchField : null;
  }
}
