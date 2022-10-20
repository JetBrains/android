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

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

  private static final String SEARCH_RESULTS = "Search Results";
  private static final String PHONE_TYPE = "Phone";
  private static final String TABLET_TYPE = "Tablet";

  private static final String DEFAULT_PHONE = "Pixel 2";
  private static final String DEFAULT_TABLET = "Pixel C";
  private static final String DEFAULT_WEAR = "Wear OS Square";
  private static final String DEFAULT_TV = "Android TV (1080p)";
  private static final String DEFAULT_AUTOMOTIVE = "Automotive (1024p landscape)";
  private static final String DEFAULT_DESKTOP = "Medium Desktop";
  private static final String TV = "TV";
  private static final String WEAR = "Wear OS";
  private static final String AUTOMOTIVE = "Automotive";
  private static final String DESKTOP = "Desktop";
  private static final List<String> CATEGORY_ORDER = ImmutableList.of(PHONE_TYPE, TABLET_TYPE, WEAR, DESKTOP, TV, AUTOMOTIVE);

  private Map<String, List<Device>> myDeviceCategoryMap = Maps.newHashMap();
  private static final Map<String, Device> myDefaultCategoryDeviceMap = Maps.newHashMap();

  private static final DecimalFormat ourDecimalFormat = new DecimalFormat(".##");
  private final ListTableModel<Device> myModel = new ListTableModel<>();
  private TableView<Device> myTable;
  private final ListTableModel<String> myCategoryModel = new ListTableModel<>();
  private TableView<String> myCategoryList;
  private JButton myCreateProfileButton;
  private JButton myImportProfileButton;
  private JButton myRefreshButton;
  private JPanel myPanel;
  private SearchTextField mySearchTextField;
  private List<DeviceDefinitionSelectionListener> myListeners = new ArrayList<>();
  private List<DeviceCategorySelectionListener> myCategoryListeners = new ArrayList<>();
  private List<Device> myDevices;
  private Device myDefaultDevice;

  public DeviceDefinitionList() {
    super(new BorderLayout());
    // List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
    // the cell value in that column for a given row item.
    ColumnInfo[] columnInfos = new ColumnInfo[]{new DeviceColumnInfo("Name") {
      @NonNull
      @Override
      public String valueOf(Device device) {
        return device.getDisplayName();
      }

      @NonNull
      @Override
      public String getPreferredStringValue() {
        // Long string so that preferred column width is set appropriately
        return "4.65\" 720 (Galaxy Nexus)";
      }

      @NonNull
      @Override
      public Comparator<Device> getComparator() {
        return (o1, o2) -> {
          String name1 = valueOf(o1);
          String name2 = valueOf(o2);
          if (name1 == name2) {
            return 0;
          }
          if (name1.isEmpty() || name2.isEmpty()) {
            return -1;
          }
          char firstChar1 = name1.charAt(0);
          char firstChar2 = name2.charAt(0);
          // Prefer letters to anything else
          if (Character.isLetter(firstChar1) && !Character.isLetter(firstChar2)) {
            return 1;
          }
          else if (Character.isLetter(firstChar2) && !Character.isLetter(firstChar1)) {
            return -1;
          }
          // Fall back to string comparison
          return name1.compareTo(name2);
        };
      }
    }, new PlayStoreColumnInfo("Play Store") {
    }, new DeviceColumnInfo("Size") {

      @NonNull
      @Override
      public String valueOf(Device device) {
        return getDiagonalSize(device);
      }
      @NonNull
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
    }, new DeviceColumnInfo("Resolution") {
      @NonNull
      @Override
      public String valueOf(Device device) {
        return getDimensionString(device);
      }

      @NonNull
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
    }, new DeviceColumnInfo("Density") {
      @NonNull
      @Override
      public String valueOf(Device device) {
        return getDensityString(device);
      }
    }};
    myModel.setColumnInfos(columnInfos);
    myModel.setSortable(true);
    refreshDeviceProfiles();
    setDefaultDevices();
    myTable.setModelAndUpdateColumns(myModel);
    myTable.getRowSorter().toggleSortOrder(0);
    myTable.getRowSorter().toggleSortOrder(0);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setRowSelectionAllowed(true);

    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refreshDeviceProfiles();
      }
    });
    myTable.getSelectionModel().addListSelectionListener(this);
    // The singular column that serves as the header for our category list
    ColumnInfo[] categoryInfo = new ColumnInfo[]{new ColumnInfo<String, String>("Category") {
      @Nullable
      @Override
      public String valueOf(String category) {
        return category;
      }

      @NonNull
      @Override
      public TableCellRenderer getRenderer(String s) {
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
    myDefaultDevice = updateDefaultDevice(PHONE_TYPE, DEFAULT_PHONE);
    updateDefaultDevice(TABLET_TYPE, DEFAULT_TABLET);
    updateDefaultDevice(TV, DEFAULT_TV);
    updateDefaultDevice(WEAR, DEFAULT_WEAR);
    updateDefaultDevice(AUTOMOTIVE, DEFAULT_AUTOMOTIVE);
    updateDefaultDevice(DESKTOP, DEFAULT_DESKTOP);
  }

  private Device updateDefaultDevice(String type, String deviceDisplayName) {
    List<Device> devices = myDeviceCategoryMap.get(type);
    if (devices != null) {
      for (Device d : devices) {
        if (d.getDisplayName().equals(deviceDisplayName)) {
          myDefaultCategoryDeviceMap.put(type, d);
          return d;
        }
      }
    }
    return null;
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
      setCategory(myCategoryList.getSelectedObject());
    } else if (e.getSource().equals(myTable.getSelectionModel())){
      onSelectionSet(myTable.getSelectedObject());
    }
  }

  public void addSelectionListener(@NotNull DeviceDefinitionSelectionListener listener) {
    myListeners.add(listener);
  }

  public void addCategoryListener(@NotNull DeviceCategorySelectionListener listener) {
    myCategoryListeners.add(listener);
  }

  public void removeSelectionListener(@NotNull DeviceDefinitionSelectionListener listener) {
    myListeners.remove(listener);
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
      String category = getCategory(device);
      for (Device listItem : myModel.getItems()) {
        if (listItem.getId().equals(device.getId())) {
          myTable.setSelection(ImmutableSet.of(listItem));
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
      myDefaultCategoryDeviceMap.put(getCategory(selectedObject), selectedObject);
    }
    for (DeviceDefinitionSelectionListener listener : myListeners) {
      listener.onDeviceSelectionChanged(selectedObject);
    }
  }

  /**
   * Update our list to display the given category.
   */
  public void setCategory(@Nullable String selectedCategory) {
    if (myDeviceCategoryMap.containsKey(selectedCategory)) {
      List<Device> newItems = myDeviceCategoryMap.get(selectedCategory);
      if (!myModel.getItems().equals(newItems)) {
        myModel.setItems(newItems);
        setSelectedDevice(myDefaultCategoryDeviceMap.get(selectedCategory));
        notifyCategoryListeners(selectedCategory, newItems);
      }
    }
    else if (Objects.equals(selectedCategory, SEARCH_RESULTS)) {
      updateSearchResults(mySearchTextField.getText());
    }
  }

  private void notifyCategoryListeners(@Nullable String selectedCategory, @Nullable List<Device> items) {
    for (DeviceCategorySelectionListener listener : myCategoryListeners) {
      listener.onCategorySelectionChanged(selectedCategory, items);
    }
  }

  private void refreshDeviceProfiles() {
    myDevices = DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevices();
    myDeviceCategoryMap.clear();
    for (Device d : myDevices) {
      if (d.getIsDeprecated()) {
        continue;
      }
      String category = getCategory(d);
      if (!myDeviceCategoryMap.containsKey(category)) {
        myDeviceCategoryMap.put(category, new ArrayList<>(1));
      }
      myDeviceCategoryMap.get(category).add(d);
    }
    ArrayList<String> categories = Lists.newArrayList(myDeviceCategoryMap.keySet());
    categories.sort(Comparator.comparing(category -> {
      int index = CATEGORY_ORDER.indexOf(category);
      return index >= 0 ? index : Integer.MAX_VALUE;
    }));
    Collection<String> selection = myCategoryList.getSelection();
    myCategoryModel.setItems(categories);
    myCategoryList.setSelection(selection);
  }

  /**
   * @return the category of the specified device. One of:
   * Automotive TV, Wear, Tablet, and Phone, or Other if the category
   * cannot be determined.
   */
  @VisibleForTesting
  public static String getCategory(@NotNull Device d) {
    if (HardwareConfigHelper.isAutomotive(d)) {
      return AUTOMOTIVE;
    } else if (HardwareConfigHelper.isDesktop(d)) {
      return DESKTOP;
    } else if (HardwareConfigHelper.isTv(d) || hasTvSizedScreen(d)) {
      return TV;
    } else if (HardwareConfigHelper.isWear(d)) {
      return WEAR;
    } else if (isTablet(d)) {
      return TABLET_TYPE;
    } else {
      return PHONE_TYPE;
    }
  }

  /*
   * A mobile device is considered a tablet if its screen is at least
   * {@link #MINIMUM_TABLET_SIZE} and the screen is not foldable.
   */
  @VisibleForTesting
  public static boolean isTablet(@NotNull Device d) {
    return (d.getDefaultHardware().getScreen().getDiagonalLength() >= Device.MINIMUM_TABLET_SIZE
            && !d.getDefaultHardware().getScreen().isFoldable());
  }

  private static boolean hasTvSizedScreen(@NotNull Device d) {
    return d.getDefaultHardware().getScreen().getDiagonalLength() >= Device.MINIMUM_TV_SIZE;
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
    } else if (!myCategoryModel.getItem(myCategoryModel.getRowCount() - 1).equals(SEARCH_RESULTS)) {
      myCategoryModel.addRow(SEARCH_RESULTS);
      myCategoryList.setSelection(ImmutableSet.of(SEARCH_RESULTS));
    }

    List<Device> items = Lists.newArrayList(Iterables.filter(myDevices,
                                                             (input) -> input.getDisplayName().toLowerCase(Locale.getDefault())
                                                               .contains(searchString.toLowerCase(Locale.getDefault()))));
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
  private final TableCellRenderer myRenderer = new TableCellRenderer() {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JBLabel label = new JBLabel((String)value);
      label.setBorder(myBorder);
      if (table.getSelectedRow() == row) {
        label.setBackground(table.getSelectionBackground());
        label.setForeground(table.getSelectionForeground());
        label.setOpaque(true);
      }
      return label;
    }
  };

  private abstract class DeviceColumnInfo extends ColumnInfo<Device, String> {
    private final int myWidth;

    @Nullable
    @Override
    public Comparator<Device> getComparator() {
      return (o1, o2) -> {
          if (o1 == null || valueOf(o1) == null) {
            return -1;
          }
          else if (o2 == null || valueOf(o2) == null) {
            return 1;
          }
          else {
            //noinspection ConstantConditions
            return valueOf(o1).compareTo(valueOf(o2));
          }
      };
    }

    DeviceColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    DeviceColumnInfo(String name) {
      this(name, -1);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(Device device) {
      return myRenderer;
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }
  }

  private static class PlayStoreColumnInfo extends ColumnInfo<Device, Icon> {

    public static final Icon highlightedPlayStoreIcon = ColoredIconGenerator.INSTANCE.generateWhiteIcon(StudioIcons.Avd.DEVICE_PLAY_STORE);

    private static final TableCellRenderer ourIconRenderer = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
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
            label.setIcon(highlightedPlayStoreIcon);
          }
        }
        return label;
      }
    };

    PlayStoreColumnInfo(String name) {
      super(name);
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
    public Icon valueOf(@NonNull Device device) {
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
