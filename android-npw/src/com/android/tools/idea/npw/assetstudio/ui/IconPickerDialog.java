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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.jetbrains.annotations.TestOnly;

/**
 * A dialog to pick a pre-configured material icon in vector format.
 */
public final class IconPickerDialog extends DialogWrapper implements DataProvider {
  private static class Style {
    private final String myName;
    private final String myDisplayName;

    static final Style EMPTY = new Style("", "");

    private static String getShortenedStyleName(@NotNull String styleName) {
      String styleShortened = StringUtil.trimStart(styleName, MATERIAL_ICONS_PREFIX).trim();
      // The default 'Filled' style is not named as such, just "Material Icons".
      if (styleShortened.isEmpty()) return "Filled";
      return styleShortened;
    }

    private Style(@NotNull String styleName, @NotNull String displayName) {
      myName = styleName;
      myDisplayName = displayName;
    }

    Style(@NotNull String styleName) {
      this(styleName, getShortenedStyleName(styleName));
    }

    @Override
    public String toString() {
      return myDisplayName;
    }
  }

  private static class Category {
    private final String myName;
    private final String myDisplayName;

    /** Special category that represents "no category selected". */
    static final Category ALL = new Category("All", "All");

    private Category(@NotNull String name, @NotNull String displayName) {
      myName = name;
      myDisplayName = displayName;
    }

    Category(@NotNull String name) {
      this(name, name.equals("av") ? "Audio/Video" : StringUtil.capitalize(name));
    }

    @Override
    public String toString() {
      return myDisplayName;
    }
  }

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
  private JComboBox<Category> myCategoriesBox;
  private JComboBox<Style> myStylesBox;

  @Nullable private VdIcon mySelectedIcon;
  @Nullable private VdIcon myIconToSelectInTable;

  private final AtomicBoolean isBusy = new AtomicBoolean(false);

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

    myContentPanel.setPreferredSize(JBUI.size(800, 600));

    init();

    myIconTable.getEmptyText().setText("Loading icons...");
    myStylesBox.setVisible(false);
    myStylesBox.setName("Styles");
    myCategoriesBox.setVisible(false);
    myCategoriesBox.setName("Categories");
    ItemListener stylesBoxListener = e -> {
      if (e.getStateChange() == ItemEvent.DESELECTED || e.getItem() == null) {
        return;
      }
      int categoryCurrentIndex = myCategoriesBox.getSelectedIndex();
      setCategoriesBoxModel(myStylesBox.getSelectedIndex());
      if (categoryCurrentIndex >= 0 && categoryCurrentIndex < myCategoriesBox.getItemCount()) {
        myCategoriesBox.setSelectedIndex(categoryCurrentIndex);
      }
    };
    myStylesBox.addItemListener(stylesBoxListener);
    ItemListener categoriesBoxListener = e -> {
      if (e.getStateChange() == ItemEvent.DESELECTED || e.getItem() == null) {
        return;
      }
      updateIconList();
    };
    myCategoriesBox.addItemListener(categoriesBoxListener);
    myStylesBox.setName("Styles");
    myCategoriesBox.setName("Categories");
    refreshIconList(metadataUrlProvider, iconsUrlProvider, true);
  }

  /**
   * Refreshes the current icons list including styles and categories.
   * @param metadataUrlProvider the url provider for the metadata information
   * @param iconsUrlProvider the url provider for the icon content
   * @param isFirstRefresh true if this is the first call to refresh icon during the dialog initialization. If true, a second call to this
   *                       method might happen some time later if the download process detects icons remotely that were not present locally.
   */
  private void refreshIconList(@Nullable MaterialIconsMetadataUrlProvider metadataUrlProvider,
                               @Nullable MaterialIconsUrlProvider iconsUrlProvider,
                               boolean isFirstRefresh) {
    myIconTable.setPaintBusy(true);
    myStylesBox.setEnabled(false);
    myCategoriesBox.setEnabled(false);
    isBusy.set(true);
    MaterialVdIconsProvider.loadMaterialVdIcons((materialVdIcons, status) -> {
      assert ApplicationManager.getApplication().isDispatchThread();
      myIconTable.getEmptyText().setText(StatusText.getDefaultEmptyText());
      populateIcons(materialVdIcons);
      if (status == MaterialVdIconsProvider.Status.FINISHED) {
        myIconTable.setPaintBusy(false);
        // After everything has been updated, re-enable the dropdowns
        myStylesBox.setEnabled(true);
        myCategoriesBox.setEnabled(true);
        isBusy.set(false);
      }
      pack();
      repaint();
      return null;
    }, getDisposable(), metadataUrlProvider, iconsUrlProvider, () -> {
      if (isFirstRefresh) {
        // Now we call ourselves again with isFirstRefresh set to false, avoiding an update cycle.
        refreshIconList(metadataUrlProvider, iconsUrlProvider, false);
      }
      return null;
    });
  }

  private void createUIComponents() {
    mySearchField = new SearchTextField(
false);
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

    if (myStylesBox.getItemCount() > 0) {
      if (myStylesBox.getSelectedIndex() == -1) myStylesBox.setSelectedIndex(0);
      myStylesBox.setVisible(true);
      setCategoriesBoxModel(myStylesBox.getSelectedIndex());
    }

    if (myCategoriesBox.getItemCount() > 0) {
      if (myCategoriesBox.getSelectedIndex() == -1) myCategoriesBox.setSelectedIndex(0);
      myCategoriesBox.setVisible(true);
    }

    if (shouldUpdateIconList) {
      updateIconList();
    }
  }

  private void setStylesBoxModel() {
    Style[] stylesArray = myIcons.getStyles().stream()
      .sorted(
        Comparator
          // Promote Material Symbols to the top of the list
          .<String>comparingInt((style) -> style.startsWith("Material Symbols") ? -1 : 0)
          .thenComparing(Object::toString))
      .map(Style::new)
      .toArray(Style[]::new);
    myStylesBox.setModel(new DefaultComboBoxModel<>(stylesArray));
  }

  /**
   * Sets up {@link #myCategoriesBox} for the existing categories in a given style. The categories are obtained from
   * {@link MaterialVdIcons#getCategories(String)}.
   *
   * @param styleIndex The index that corresponds to a style in the {@link MaterialVdIcons#getStyles()} array
   */
  private void setCategoriesBoxModel(int styleIndex) {
    Style style = styleIndex < myStylesBox.getItemCount() ? myStylesBox.getModel().getElementAt(styleIndex) : Style.EMPTY;
    ArrayList<Category> categoriesArray = myIcons.getCategories(style.myName).stream()
      .sorted()
      .map(Category::new)
      .collect(Collectors.toCollection(ArrayList::new));

    boolean hasCategories = !categoriesArray.isEmpty();
    if (hasCategories) {
      categoriesArray.add(0, Category.ALL);
    }
    myCategoriesBox.setModel(new CollectionComboBoxModel<>(categoriesArray, null));
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
    Style style = (Style)myStylesBox.getSelectedItem();
    if (style != null) {
      Category categoryItem = (Category)myCategoriesBox.getSelectedItem();
      // If the category is "All" or no category has been selected yet, add all
      if (categoryItem == null || categoryItem == Category.ALL) {
        myIconList.addAll(myIcons.getAllIcons(style.myName));
      }
      else {
        myIconList.addAll(myIcons.getIcons(style.myName, categoryItem.myName));
      }
      myIconList.sort(Comparator.comparing(VdIcon::getDisplayName));
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

  @TestOnly
  boolean isBusy() {
    return isBusy.get();
  }
}
