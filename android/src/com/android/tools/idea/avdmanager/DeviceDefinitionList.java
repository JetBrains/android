/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Device;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lists the available device definitions by category
 */
public class DeviceDefinitionList extends JPanel implements ListSelectionListener, DocumentListener, DeviceUiAction.DeviceProvider {
  private static final int NAME_MODEL_COLUMN_INDEX = 0;
  private static final String SEARCH_RESULTS = "Search Results";
  private static final DecimalFormat ourDecimalFormat = new DecimalFormat(".##");

  private Multimap<Category, Device> myCategoryToDefinitionMultimap;
  private final ListTableModel<Device> myModel = new ListTableModel<>();
  private TableView<Device> myTable;

  /**
   * myCategoryModel usually contains all the {@link Category Categories.} When the user searches for definitions, it contains
   * {@link #SEARCH_RESULTS} as well.
   */
  private final ListTableModel<Object> myCategoryModel = new ListTableModel<>();
  private TableView<Object> myCategoryList;
  private JButton myCreateProfileButton;
  private JButton myImportProfileButton;
  private Map<Category, Device> myCategoryToSelectedDefinitionMap;
  private JButton myRefreshButton;
  private JPanel myPanel;
  private SearchTextField mySearchTextField;
  private final List<DeviceDefinitionSelectionListener> myListeners = new ArrayList<>();
  private final List<DeviceCategorySelectionListener> myCategoryListeners = new ArrayList<>();
  private Collection<Device> myDevices;
  private Device myDefaultDevice;

  public DeviceDefinitionList() {
    super(new BorderLayout());
    // List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
    // the cell value in that column for a given row item.
    ColumnInfo[] columns = {
      new DeviceColumnInfo("Name") {
        @NotNull
        @Override
        public String valueOf(Device device) {
          return device.getDisplayName();
        }

        @NotNull
        @Override
        public String getPreferredStringValue() {
          // Long string so that preferred column width is set appropriately
          return "4.65\" 720 (Galaxy Nexus)";
        }

        @NotNull
        @Override
        public Comparator<Device> getComparator() {
          return Comparator.comparing(Device::getDisplayName, Collator.getInstance(ULocale.ROOT));
        }
      },
      new PlayStoreColumnInfo(),
      new DeviceColumnInfo("Size") {
        @NotNull
        @Override
        public String valueOf(Device device) {
          return getDiagonalSize(device);
        }

        @NotNull
        @Override
        public Comparator<Device> getComparator() {
          return (o1, o2) -> {
            if (o1 == null) {
              return -1;
            }
            else if (o2 == null) {
              return 1;
            }
            else {
              return Double.compare(o1.getDefaultHardware().getScreen().getDiagonalLength(),
                                    o2.getDefaultHardware().getScreen().getDiagonalLength());
            }
          };
        }
      },
      new DeviceColumnInfo("Resolution") {
        @NotNull
        @Override
        public String valueOf(Device device) {
          return getDimensionString(device);
        }

        @NotNull
        @Override
        public Comparator<Device> getComparator() {
          return (o1, o2) -> {
            if (o1 == null) {
              return -1;
            }
            else if (o2 == null) {
              return 1;
            }
            else {
              Dimension d1 = o1.getScreenSize(o1.getDefaultState().getOrientation());
              Dimension d2 = o2.getScreenSize(o2.getDefaultState().getOrientation());
              if (d1 == null) {
                return -1;
              }
              else if (d2 == null) {
                return 1;
              }
              else {
                return Integer.compare(d1.width * d1.height, d2.width * d2.height);
              }
            }
          };
        }
      },
      new DeviceColumnInfo("Density") {
        @NotNull
        @Override
        public String valueOf(Device device) {
          return getDensityString(device);
        }
      }};
    myModel.setColumnInfos(columns);
    myModel.setSortable(true);
    refreshDeviceProfiles();
    setDefaultDevices();
    myTable.setModelAndUpdateColumns(myModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setRowSelectionAllowed(true);

    myRefreshButton.addActionListener(event -> refreshDeviceProfiles());
    myTable.getSelectionModel().addListSelectionListener(this);
    // The singular column that serves as the header for our category list
    ColumnInfo[] categoryInfo = {
      new ColumnInfo<Object, String>("Category") {
        @NotNull
        @Override
        public String valueOf(@NotNull Object category) {
          return category.toString();
        }

        @NotNull
        @Override
        public TableCellRenderer getRenderer(@NotNull Object category) {
          return myRenderer;
        }
      }};
    myCategoryModel.setColumnInfos(categoryInfo);
    myCategoryList.setModelAndUpdateColumns(myCategoryModel);
    myCategoryList.getSelectionModel().addListSelectionListener(this);
    mySearchTextField.addDocumentListener(this);
    add(myPanel, BorderLayout.CENTER);

    myCreateProfileButton.setAction(new CreateDeviceAction(this));
    myCreateProfileButton.setText("New Hardware Profile");
    myImportProfileButton.setAction(new ImportDevicesAction(this));
    myImportProfileButton.setText("Import Hardware Profiles");
    myTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        possiblyShowPopup(e);
      }

      @Override
      public void mousePressed(MouseEvent e) {
        possiblyShowPopup(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        possiblyShowPopup(e);
      }
    });
  }

  private void setDefaultDevices() {
    myCategoryToSelectedDefinitionMap = Arrays.stream(Category.values())
      .collect(Collectors.toMap(category -> category, this::getDefaultDefinition));

    myDefaultDevice = myCategoryToSelectedDefinitionMap.get(Category.PHONE);
  }

  @NotNull
  private Device getDefaultDefinition(@NotNull Category category) {
    var definition = category.getDefaultDefinitionName();

    return myCategoryToDefinitionMultimap.get(category).stream()
      .filter(d -> d.getDisplayName().equals(definition))
      .findFirst()
      .orElseThrow();
  }

  @NotNull
  private static JBMenuItem createMenuItem(@NotNull DeviceUiAction action) {
    JBMenuItem item = new JBMenuItem(action);
    item.setText(action.getText());
    return item;
  }

  private void possiblyShowPopup(MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }
    Point p = e.getPoint();
    int row = myTable.rowAtPoint(p);
    int col = myTable.columnAtPoint(p);
    if (row != -1 && col != -1) {
      JBPopupMenu menu = new JBPopupMenu();
      menu.add(createMenuItem(new CloneDeviceAction(this)));
      menu.add(createMenuItem(new EditDeviceAction(this)));
      menu.add(createMenuItem(new ExportDeviceAction(this)));
      menu.add(createMenuItem(new DeleteDeviceAction(this)));
      menu.show(myTable, p.x, p.y);
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (e.getSource().equals(myCategoryList.getSelectionModel())) {
      var category = myCategoryList.getSelectedObject();

      if (category != null) {
        setCategory(category);
      }
    }
    else if (e.getSource().equals(myTable.getSelectionModel())) {
      onSelectionSet(myTable.getSelectedObject());
    }
  }

  public void addSelectionListener(@NotNull DeviceDefinitionSelectionListener listener) {
    myListeners.add(listener);
  }

  public void addCategoryListener(@NotNull DeviceCategorySelectionListener listener) {
    myCategoryListeners.add(listener);
  }

  @Override
  public void selectDefaultDevice() {
    setSelectedDevice(myDefaultDevice);
  }

  @Nullable
  @Override
  public Project getProject() {
    return null;
  }

  /**
   * Set the list's selection to the given device, or clear the selection if the
   * given device is null. The category list will also select the category to which the
   * given device belongs.
   */
  public void setSelectedDevice(@Nullable Device device) {
    if (Objects.equals(device, myTable.getSelectedObject())) {
      return;
    }
    onSelectionSet(device);
    if (device != null) {
      var category = Category.valueOfDefinition(device);
      for (Device listItem : myModel.getItems()) {
        if (listItem.getId().equals(device.getId())) {
          myTable.setSelection(ImmutableSet.of(listItem));

          var viewColumnIndex = myTable.convertColumnIndexToView(NAME_MODEL_COLUMN_INDEX);
          myTable.scrollRectToVisible(myTable.getCellRect(myTable.getSelectedRow(), viewColumnIndex, true));
        }
      }
      myCategoryList.setSelection(ImmutableSet.of(category));
      setCategory(category);
    }
  }

  /**
   * Update our listeners
   */
  private void onSelectionSet(@Nullable Device selectedObject) {
    if (selectedObject != null) {
      myCategoryToSelectedDefinitionMap.put(Category.valueOfDefinition(selectedObject), selectedObject);
    }
    for (DeviceDefinitionSelectionListener listener : myListeners) {
      listener.onDeviceSelectionChanged(selectedObject);
    }
  }

  /**
   * Update our list to display the given category.
   */
  private void setCategory(@NotNull Object name) {
    if (name.equals(SEARCH_RESULTS)) {
      updateSearchResults(mySearchTextField.getText());
      return;
    }

    if (!(name instanceof Category category)) {
      throw new IllegalArgumentException(name.getClass().toString());
    }

    var definitions = List.copyOf(myCategoryToDefinitionMultimap.get(category));

    if (myModel.getItems().equals(definitions)) {
      return;
    }

    myModel.setItems(definitions);
    setSelectedDevice(myCategoryToSelectedDefinitionMap.get(category));
    notifyCategoryListeners(name.toString(), definitions);
  }

  private void notifyCategoryListeners(@Nullable String selectedCategory, @Nullable List<Device> items) {
    for (DeviceCategorySelectionListener listener : myCategoryListeners) {
      listener.onCategorySelectionChanged(selectedCategory, items);
    }
  }

  private void refreshDeviceProfiles() {
    myDevices = new DeviceSupplier().get();

    myCategoryToDefinitionMultimap = myDevices.stream()
      .collect(Multimaps.toMultimap(Category::valueOfDefinition,
                                    device -> device,
                                    MultimapBuilder.enumKeys(Category.class).treeSetValues(new NameComparator())::build));

    var selection = myCategoryList.getSelection();
    myCategoryModel.setItems(new ArrayList<>(Arrays.asList(Category.values())));
    myCategoryList.setSelection(selection);
  }

  /**
   * @return the diagonal screen size of the given device
   */
  public static String getDiagonalSize(@NotNull Device device) {
    return ourDecimalFormat.format(device.getDefaultHardware().getScreen().getDiagonalLength()) + '"';
  }

  /**
   * @return a string of the form [width]x[height] in pixel units representing the screen resolution of the given device
   */
  public static String getDimensionString(@NotNull Device device) {
    Dimension size = device.getScreenSize(device.getDefaultState().getOrientation());
    return size == null ? "Unknown Resolution" : String.format(Locale.getDefault(), "%dx%d", size.width, size.height);
  }

  /**
   * @return a string representing the density bucket of the given device
   */
  public static String getDensityString(@NotNull Device device) {
    return device.getDefaultHardware().getScreen().getPixelDensity().getResourceValue();
  }

  private void createUIComponents() {
    myCategoryList = new TableView<>();
    myTable = new TableView<>();
    myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    updateSearchResults(getText(e.getDocument()));
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    updateSearchResults(getText(e.getDocument()));
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    updateSearchResults(getText(e.getDocument()));
  }

  private static String getText(Document d) {
    try {
      return d.getText(0, d.getLength());
    }
    catch (BadLocationException e) {
      return "";
    }
  }

  /**
   * Set the "Search Results" category to the set of devices whose names match the given search string
   */
  private void updateSearchResults(@NotNull final String searchString) {
    if (searchString.isEmpty()) {
      if (myCategoryModel.getItem(myCategoryModel.getRowCount() - 1).equals(SEARCH_RESULTS)) {
        myCategoryModel.removeRow(myCategoryModel.getRowCount() - 1);
        setCategory(myCategoryList.getRow(0));
      }
      return;
    }
    else if (!myCategoryModel.getItem(myCategoryModel.getRowCount() - 1).equals(SEARCH_RESULTS)) {
      myCategoryModel.addRow(SEARCH_RESULTS);
      myCategoryList.setSelection(ImmutableSet.of(SEARCH_RESULTS));
    }

    var locale = Locale.getDefault();
    var lowercaseSearchString = searchString.toLowerCase(locale);

    var items = myDevices.stream()
      .filter(device -> device.getDisplayName().toLowerCase(locale).contains(lowercaseSearchString))
      .collect(Collectors.toList());

    myModel.setItems(items);
    notifyCategoryListeners(null, items);
  }

  @Nullable
  @Override
  public Device getDevice() {
    return myTable.getSelectedObject();
  }

  @Override
  public void setDevice(@Nullable Device device) {
    setSelectedDevice(device);
  }

  @Override
  public void refreshDevices() {
    refreshDeviceProfiles();
  }

  private final Border myBorder = JBUI.Borders.empty(10);

  /**
   * Renders a simple text field.
   */
  private final TableCellRenderer myRenderer = (table, value, isSelected, hasFocus, row, column) -> {
    var label = new JBLabel(value.toString());
    label.setBorder(myBorder);
    if (table.getSelectedRow() == row) {
      label.setBackground(table.getSelectionBackground());
      label.setForeground(table.getSelectionForeground());
      label.setOpaque(true);
    }
    return label;
  };

  private abstract class DeviceColumnInfo extends ColumnInfo<Device, String> {
    @Nullable
    @Override
    public Comparator<Device> getComparator() {
      return Comparator.comparing(this::valueOf);
    }

    DeviceColumnInfo(@NotNull String name) {
      super(name);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(Device device) {
      return myRenderer;
    }
  }

  private static class PlayStoreColumnInfo extends ColumnInfo<Device, Icon> {

    public static final Icon highlightedPlayStoreIcon = ColoredIconGenerator.generateWhiteIcon(StudioIcons.Avd.DEVICE_PLAY_STORE);

    private static final TableCellRenderer ourIconRenderer = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        Icon theIcon = (Icon)value;
        JBLabel label = new JBLabel(theIcon);
        if (theIcon != null) {
          AccessibleContextUtil.setName(label, "Play Store");
        }
        if (table.getSelectedRow() == row) {
          label.setBackground(table.getSelectionBackground());
          label.setForeground(table.getSelectionForeground());
          label.setOpaque(true);
          if (theIcon != null) {
            label.setIcon(ExperimentalUI.isNewUI() ? StudioIcons.Avd.DEVICE_PLAY_STORE : highlightedPlayStoreIcon);
          }
        }
        return label;
      }
    };

    PlayStoreColumnInfo() {
      super("Play Store");
    }

    @NotNull
    @Override
    public TableCellRenderer getRenderer(Device device) {
      return ourIconRenderer;
    }

    @Override
    public int getWidth(JTable table) {
      return -1; // Re-sizable
    }

    @Nullable
    @Override
    public Icon valueOf(@NotNull Device device) {
      return (device.hasPlayStore() ? StudioIcons.Avd.DEVICE_PLAY_STORE : null);
    }

    @NotNull
    @Override
    public Comparator<Device> getComparator() {
      return (o1, o2) -> Boolean.compare(o2.hasPlayStore(), o1.hasPlayStore());
    }
  }

  public interface DeviceDefinitionSelectionListener {
    void onDeviceSelectionChanged(@Nullable Device selectedDevice);
  }

  public interface DeviceCategorySelectionListener {
    void onCategorySelectionChanged(@Nullable String category, @Nullable List<Device> devices);
  }
}
