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

import com.android.annotations.VisibleForTesting;
import com.android.resources.Density;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.npw.WizardUtils;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.*;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * A UI component which lists the existing AVDs
 */
public class AvdDisplayList extends JPanel implements ListSelectionListener, AvdActionPanel.AvdRefreshProvider,
                                                      AvdUiAction.AvdInfoProvider {
  private static final Logger LOG = Logger.getInstance(AvdDisplayList.class);
  public static final String NONEMPTY = "nonempty";
  public static final String EMPTY = "empty";

  private final Project myProject;
  private final JPanel myCenterCardPanel;
  private final JPanel myNotificationPanel;
  private final AvdListDialog myDialog;

  private TableView<AvdInfo> myTable;
  private ListTableModel<AvdInfo> myModel = new ListTableModel<AvdInfo>();
  private Set<AvdSelectionListener> myListeners = Sets.newHashSet();
  private final AvdActionsColumnInfo myActionsColumnRenderer = new AvdActionsColumnInfo("Actions", 2 /* Num Visible Actions */);

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through {@link #addSelectionListener(AvdSelectionListener)}
   */
  public interface AvdSelectionListener {
    void onAvdSelected(@Nullable AvdInfo avdInfo);
  }

  public AvdDisplayList(@NotNull AvdListDialog dialog, @Nullable Project project) {
    myDialog = dialog;
    myProject = project;
    myModel.setColumnInfos(myColumnInfos);
    myModel.setSortable(true);
    myTable = new TableView<AvdInfo>();
    myTable.setModelAndUpdateColumns(myModel);
    myTable.setDefaultRenderer(Object.class, new MyRenderer(myTable.getDefaultRenderer(Object.class)));
    setLayout(new BorderLayout());
    myCenterCardPanel = new JPanel(new CardLayout());
    myNotificationPanel = new JPanel();
    myNotificationPanel.setLayout(new BoxLayout(myNotificationPanel, 1));
    JPanel nonemptyPanel = new JPanel(new BorderLayout());
    myCenterCardPanel.add(nonemptyPanel, NONEMPTY);
    nonemptyPanel.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    nonemptyPanel.add(myNotificationPanel, BorderLayout.NORTH);
    myCenterCardPanel.add(new EmptyAvdListPanel(this), EMPTY);
    add(myCenterCardPanel, BorderLayout.CENTER);
    JPanel southPanel = new JPanel(new BorderLayout());
    JButton helpButton = new JButton(AllIcons.Actions.Help);
    helpButton.putClientProperty("JButton.buttonType", "segmented-only");
    helpButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.browse("http://developer.android.com/r/studio-ui/virtualdeviceconfig.html");
      }
    });
    JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
    refreshButton.putClientProperty("JButton.buttonType", "segmented-only");
    refreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refreshAvds();
      }
    });
    JButton newButton = new JButton(new CreateAvdAction(this));
    newButton.putClientProperty("JButton.buttonType", "segmented-only");

    JPanel southEastPanel = new JPanel(new FlowLayout());
    JPanel southWestPanel = new JPanel(new FlowLayout());
    southEastPanel.add(refreshButton);
    if (UIUtil.isUnderAquaBasedLookAndFeel()) {
      southWestPanel.add(helpButton);
    }
    else {
      southEastPanel.add(helpButton);
    }
    southWestPanel.add(newButton);
    southPanel.add(southEastPanel, BorderLayout.EAST);
    southPanel.add(southWestPanel, BorderLayout.WEST);
    nonemptyPanel.add(southPanel, BorderLayout.SOUTH);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().addListSelectionListener(this);
    myTable.addMouseListener(myEditingListener);
    myTable.addMouseMotionListener(myEditingListener);
    LaunchListener launchListener = new LaunchListener();
    myTable.addMouseListener(launchListener);
    ActionMap am = myTable.getActionMap();
    am.put("selectPreviousColumnCell", new CycleAction(true));
    am.put("selectNextColumnCell", new CycleAction(false));
    am.put("deleteAvd", new DeleteAvdAction(this));
    myTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
    myTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enter");
    myTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteAvd");
    myTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteAvd");
    am.put("enter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doAction();
      }
    });
    refreshAvds();
  }

  public void addSelectionListener(AvdSelectionListener listener) {
    myListeners.add(listener);
  }

  public void removeSelectionListener(AvdSelectionListener listener) {
    myListeners.remove(listener);
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   * @param e
   */
  @Override
  public void valueChanged(ListSelectionEvent e) {
    // Required so the editor component is updated to know it's selected.
    myTable.editCellAt(myTable.getSelectedRow(), myTable.getSelectedColumn());
    AvdInfo selected = myTable.getSelectedObject();
    for (AvdSelectionListener listener : myListeners) {
      listener.onAvdSelected(selected);
    }
  }

  @Nullable
  @Override
  public AvdInfo getAvdInfo() {
    return myTable.getSelectedObject();
  }

  /**
   * Reload AVD definitions from disk and repopulate the table
   */
  @Override
  public void refreshAvds() {
    List<AvdInfo> avds = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
    myModel.setItems(avds);
    if (avds.isEmpty()) {
      ((CardLayout)myCenterCardPanel.getLayout()).show(myCenterCardPanel, EMPTY);
    } else {
      ((CardLayout)myCenterCardPanel.getLayout()).show(myCenterCardPanel, NONEMPTY);
    }
    refreshErrorCheck();
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  private final MouseAdapter myEditingListener = new MouseAdapter() {
    @Override
    public void mouseMoved(MouseEvent e) {
      possiblySwitchEditors(e);
    }
    @Override
    public void mouseEntered(MouseEvent e) {
      possiblySwitchEditors(e);
    }
    @Override
    public void mouseExited(MouseEvent e) {
      possiblySwitchEditors(e);
    }
    @Override
    public void mouseClicked(MouseEvent e) {
      possiblySwitchEditors(e);
    }
    @Override
    public void mousePressed(MouseEvent e) {
      possiblyShowPopup(e);
    }
    @Override
    public void mouseReleased(MouseEvent e) {
      possiblyShowPopup(e);
    }
  };
  private void possiblySwitchEditors(MouseEvent e) {
    Point p = e.getPoint();
    int row = myTable.rowAtPoint(p);
    int col = myTable.columnAtPoint(p);
    if (row != myTable.getEditingRow() || col != myTable.getEditingColumn()) {
      if (row != -1 && col != -1 && myTable.isCellEditable(row, col)) {
        myTable.editCellAt(row, col);
      }
    }
  }
  private void possiblyShowPopup(MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }
    Point p = e.getPoint();
    int row = myTable.rowAtPoint(p);
    int col = myTable.columnAtPoint(p);
    if (row != -1 && col != -1) {
      int lastColumn = myTable.getColumnCount() - 1;
      Component maybeActionPanel = myTable.getCellRenderer(row, lastColumn).
          getTableCellRendererComponent(myTable, myTable.getValueAt(row, lastColumn), false, true, row, lastColumn);
      if (maybeActionPanel instanceof AvdActionPanel) {
        ((AvdActionPanel)maybeActionPanel).showPopup(myTable, e);
      }
    }
  }

  /**
   * @return the device screen size of this AVD
   */
  @VisibleForTesting
  static Dimension getScreenSize(@NotNull AvdInfo info) {
    DeviceManagerConnection deviceManager = DeviceManagerConnection.getDefaultDeviceManagerConnection();
    Device device = deviceManager.getDevice(info.getDeviceName(), info.getDeviceManufacturer());
    if (device == null) {
      return null;
    }
    return device.getScreenSize(device.getDefaultState().getOrientation());
  }

  /**
   * @return the resolution of a given AVD as a string of the format [width]x[height] - [density]
   * (e.g. 1200x1920 - xhdpi) or "Unknown Resolution" if the AVD does not define a resolution.
   */
  @VisibleForTesting
  static String getResolution(@NotNull AvdInfo info) {
    DeviceManagerConnection deviceManager = DeviceManagerConnection.getDefaultDeviceManagerConnection();
    Device device = deviceManager.getDevice(info.getDeviceName(), info.getDeviceManufacturer());
    Dimension res = null;
    Density density = null;
    if (device != null) {
      res = device.getScreenSize(device.getDefaultState().getOrientation());
      density = device.getDefaultHardware().getScreen().getPixelDensity();
    }
    String resolution;
    String densityString = density == null ? "Unknown Density" : density.getResourceValue();
    if (res != null) {
      resolution = String.format(Locale.getDefault(), "%1$d \u00D7 %2$d: %3$s", res.width, res.height, densityString);
    } else {
      resolution = "Unknown Resolution";
    }
    return resolution;
  }

  /**
   * Get the device icon representing the device class of the given AVD (e.g. phone/tablet or TV)
   */
  private static Icon getIcon(@NotNull AvdInfo info) {
    String id = info.getTag().getId();
    String path;
    if (id != null && id.contains("android-")) {
      path = String.format("/icons/formfactors/%s_32.png", id.substring("android-".length()));
      return IconLoader.getIcon(path, AvdDisplayList.class);
    } else {
      return AndroidIcons.FormFactors.Mobile_32;
    }
  }

  /**
   * List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
   * the cell value in that column for a given row item.
   */
  private final ColumnInfo[] myColumnInfos = new ColumnInfo[] {
    new AvdIconColumnInfo("Type") {
      @Nullable
      @Override
      public Icon valueOf(AvdInfo avdInfo) {
        return AvdDisplayList.getIcon(avdInfo);
      }
    },
    new AvdColumnInfo("Name") {
      @Nullable
      @Override
      public String valueOf(AvdInfo info) {
        return AvdManagerConnection.getAvdDisplayName(info);
      }
    },
    new AvdColumnInfo("Resolution") {
      @Nullable
      @Override
      public String valueOf(AvdInfo avdInfo) {
        return getResolution(avdInfo);
      }

      /**
       * We override the comparator here to sort the AVDs by total number of pixels on the screen rather than the
       * default sort order (lexicographically by string representation)
       */
      @Nullable
      @Override
      public Comparator<AvdInfo> getComparator() {
        return new Comparator<AvdInfo>() {
          @Override
          public int compare(AvdInfo o1, AvdInfo o2) {
            Dimension d1 = getScreenSize(o1);
            Dimension d2 = getScreenSize(o2);
            if (d1 == d2) {
              return 0;
            } else if (d1 == null) {
              return -1;
            } else if (d2 == null) {
              return 1;
            } else {
              return d1.width * d1.height - d2.width * d2.height;
            }
          }
        };
      }
    },
    new AvdColumnInfo("API", JBUI.scale(50)) {
      @NotNull
      @Override
      public String valueOf(AvdInfo avdInfo) {
        return avdInfo.getAndroidVersion().getApiString();
      }

      /**
       * We override the comparator here to sort the API levels numerically (when possible;
       * with preview platforms codenames are compared alphabetically)
       */
      @Nullable
      @Override
      public Comparator<AvdInfo> getComparator() {
        final ApiLevelComparator comparator = new ApiLevelComparator();
        return new Comparator<AvdInfo>() {
          @Override
          public int compare(AvdInfo o1, AvdInfo o2) {
            return comparator.compare(valueOf(o1), valueOf(o2));
          }
        };
      }
    },
    new AvdColumnInfo("Target") {
      @Nullable
      @Override
      public String valueOf(AvdInfo info) {
        String result = "Android " + SdkVersionInfo.getVersionString(info.getAndroidVersion().getFeatureLevel());
        if (!info.getTag().equals(SystemImage.DEFAULT_TAG)) {
          result += " (" + info.getTag().getDisplay() + ")";
        }
        return result;
      }
    },
    new AvdColumnInfo("CPU/ABI", JBUI.scale(60)) {
      @Nullable
      @Override
      public String valueOf(AvdInfo avdInfo) {
        return avdInfo.getCpuArch();
      }
    },
    new AvdSizeColumnInfo("Size on Disk"),
    myActionsColumnRenderer,
  };

  private void refreshErrorCheck() {
    boolean refreshUI = myNotificationPanel.getComponentCount() > 0;
    myNotificationPanel.removeAll();
    AccelerationErrorCode error = AvdManagerConnection.getDefaultAvdManagerConnection().checkAcceration();
    if (error != AccelerationErrorCode.ALREADY_INSTALLED) {
      refreshUI = true;
      myNotificationPanel.add(new AccelerationErrorNotificationPanel(error, myProject, new Runnable() {
        @Override
        public void run() {
          refreshErrorCheck();
        }
      }));
    }
    if (refreshUI) {
      myNotificationPanel.revalidate();
      myNotificationPanel.repaint();
    }
  }

  /**
   * This class extends {@link ColumnInfo} in order to pull an {@link Icon} value from a given {@link AvdInfo}.
   * This is the column info used for the Type and Status columns.
   * It uses the icon field renderer ({@link #ourIconRenderer}) and does not sort by default. An explicit width may be used
   * by calling the overloaded constructor, otherwise the column will be 50px wide.
   */
  private static abstract class AvdIconColumnInfo extends ColumnInfo<AvdInfo, Icon> {
    private final int myWidth;

    /**
     * Renders an icon in a small square field
     */
    private static final TableCellRenderer ourIconRenderer = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JBLabel label = new JBLabel((Icon)value);
        if (table.getSelectedRow() == row) {
          label.setBackground(table.getSelectionBackground());
          label.setForeground(table.getSelectionForeground());
          label.setOpaque(true);
        }
        return label;
      }

    };

    public AvdIconColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    public AvdIconColumnInfo(@NotNull String name) {
      this(name, JBUI.scale(50));
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(AvdInfo o) {
      return ourIconRenderer;
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }
  }

  /**
   * This class extends {@link com.intellij.util.ui.ColumnInfo} in order to pull a string value from a given {@link com.android.sdklib.internal.avd.AvdInfo}.
   * This is the column info used for most of our table, including the Name, Resolution, and API level columns.
   * It uses the text field renderer ({@link #myRenderer}) and allows for sorting by the lexicographical value
   * of the string displayed by the {@link com.intellij.ui.components.JBLabel} rendered as the cell component. An explicit width may be used
   * by calling the overloaded constructor, otherwise the column will auto-scale to fill available space.
   */
  public abstract static class AvdColumnInfo extends ColumnInfo<AvdInfo, String> {

    private final int myWidth;

    public AvdColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    public AvdColumnInfo(@NotNull String name) {
      this(name, -1);
    }

    @Nullable
    @Override
    public Comparator<AvdInfo> getComparator() {
      return new Comparator<AvdInfo>() {
        @Override
        public int compare(AvdInfo o1, AvdInfo o2) {
          String s1 = valueOf(o1);
          String s2 = valueOf(o2);
          return Comparing.compare(s1, s2);
        }
      };
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }
  }

  private class ActionRenderer extends AbstractTableCellEditor implements TableCellRenderer {
    AvdActionPanel myComponent;
    private int myNumVisibleActions = -1;

    ActionRenderer(int numVisibleActions, AvdInfo info) {
      myNumVisibleActions = numVisibleActions;
      myComponent = new AvdActionPanel(info, myNumVisibleActions, AvdDisplayList.this);
    }

    private Component getComponent(JTable table, int row, int column) {
      if (table.getSelectedRow() == row) {
        myComponent.setBackground(table.getSelectionBackground());
        myComponent.setForeground(table.getSelectionForeground());
      } else {
        myComponent.setBackground(table.getBackground());
        myComponent.setForeground(table.getForeground());
      }
      myComponent.setFocused(table.getSelectedRow() == row && table.getSelectedColumn() == column);
      return myComponent;
    }

    public boolean cycleFocus(boolean backward) {
      return myComponent.cycleFocus(backward);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return getComponent(table, row, column);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      return getComponent(table, row, column);
    }

    @Override
    public Object getCellEditorValue() {
      return null;
    }
  }
  /**
   * Custom table cell renderer that renders an action panel for a given AVD entry
   */
  private class AvdActionsColumnInfo extends ColumnInfo<AvdInfo, AvdInfo> {
    private int myWidth;
    private int myNumVisibleActions;

    /**
     * This cell renders an action panel for both the editor component and the display component
     */
    private final Map<Object, ActionRenderer> ourActionPanelRendererEditor = Maps.newHashMap();

    public AvdActionsColumnInfo(@NotNull String name, int numVisibleActions) {
      super(name);
      myNumVisibleActions = numVisibleActions;
      myWidth = numVisibleActions == -1 ? -1 : JBUI.scale(45) * numVisibleActions + JBUI.scale(75);
    }

    public AvdActionsColumnInfo(@NotNull String name) {
      this(name, -1);
    }

    @Nullable
    @Override
    public AvdInfo valueOf(AvdInfo avdInfo) {
      return avdInfo;
    }

    /**
     * We override the comparator here so that we can sort by healthy vs not healthy AVDs
     */
    @Nullable
    @Override
    public Comparator<AvdInfo> getComparator() {
      return new Comparator<AvdInfo>() {
        @Override
        public int compare(AvdInfo o1, AvdInfo o2) {
          return o1.getStatus().compareTo(o2.getStatus());
        }
      };
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(AvdInfo avdInfo) {
      return getComponent(avdInfo);
    }

    public ActionRenderer getComponent(AvdInfo avdInfo) {
      ActionRenderer renderer = ourActionPanelRendererEditor.get(avdInfo);
      if (renderer == null) {
        renderer = new ActionRenderer(myNumVisibleActions, avdInfo);
        ourActionPanelRendererEditor.put(avdInfo, renderer);
      }
      return renderer;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(AvdInfo avdInfo) {
      return getComponent(avdInfo);
    }

    @Override
    public boolean isCellEditable(AvdInfo avdInfo) {
      return true;
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }

    public boolean cycleFocus(AvdInfo info, boolean backward) {
      return getComponent(info).cycleFocus(backward);
    }
  }

  private class AvdSizeColumnInfo extends AvdColumnInfo {

    public AvdSizeColumnInfo(@NotNull String name) {
      super(name);
    }

    @NotNull
    private Storage getSize(AvdInfo avdInfo) {
      long sizeInBytes = 0;
      if (avdInfo != null) {
        File avdDir = new File(avdInfo.getDataFolderPath());
        for (File file : WizardUtils.listFiles(avdDir)) {
          sizeInBytes += file.length();
        }
      }
      return new Storage(sizeInBytes);
    }

    @Nullable
    @Override
    public String valueOf(AvdInfo avdInfo) {
      Storage size = getSize(avdInfo);
      String unitString = "MB";
      Long value = size.getSizeAsUnit(Storage.Unit.MiB);
      if (value > 1024) {
        unitString = "GB";
        value = size.getSizeAsUnit(Storage.Unit.GiB);
      }
      return String.format(Locale.getDefault(), "%1$d %2$s", value, unitString);
    }

    @Nullable
    @Override
    public Comparator<AvdInfo> getComparator() {
      return new Comparator<AvdInfo>() {
        @Override
        public int compare(AvdInfo o1, AvdInfo o2) {
          Storage s1 = getSize(o1);
          Storage s2 = getSize(o2);
          return Comparing.compare(s1.getSize(), s2.getSize());
        }
      };
    }
  }

  private class LaunchListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        doAction();
      }
    }
  }

  private void doAction() {
    AvdInfo info = getAvdInfo();
    if (info != null) {
      if (info.getStatus() == AvdInfo.AvdStatus.OK) {
        new RunAvdAction(this).actionPerformed(null);
      } else {
        new EditAvdAction(this).actionPerformed(null);
      }
    }
  }


  private class CycleAction extends AbstractAction {
    boolean myBackward;

    CycleAction(boolean backward) {
      myBackward = backward;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      int selectedRow = myTable.getSelectedRow();
      int selectedColumn = myTable.getSelectedColumn();
      int actionsColumn = myModel.findColumn(myActionsColumnRenderer.getName());
      if (myBackward) {
        cycleBackward(selectedRow, selectedColumn, actionsColumn);
      }
      else {
        cycleForward(selectedRow, selectedColumn, actionsColumn);
      }
      selectedRow = myTable.getSelectedRow();
      if (selectedRow != -1) {
        myTable.editCellAt(selectedRow, myTable.getSelectedColumn());
      }
      repaint();
    }

    private void cycleForward(int selectedRow, int selectedColumn, int actionsColumn) {
      if (selectedColumn == actionsColumn && selectedRow == myTable.getRowCount() - 1) {
        // We're in the last cell of the table. Check whether we can cycle action buttons
        if (!myActionsColumnRenderer.cycleFocus(myTable.getSelectedObject(), false)) {
          // At the end of action buttons. Remove selection and leave table.
          myActionsColumnRenderer.getEditor(getAvdInfo()).stopCellEditing();
          myTable.removeRowSelectionInterval(selectedRow, selectedRow);
          KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
          manager.focusNextComponent(myTable);
        }
      } else if (selectedColumn != actionsColumn && selectedRow != -1) {
        // We're in the table, but not on the action column. Select the action column.
        myTable.setColumnSelectionInterval(actionsColumn, actionsColumn);
        myActionsColumnRenderer.cycleFocus(myTable.getSelectedObject(), false);
      } else if (selectedRow == -1 || !myActionsColumnRenderer.cycleFocus(myTable.getSelectedObject(), false)) {
        // We aren't in the table yet, or we are in the actions column and at the end of the focusable actions. Move to the next row
        // and select the first column
        myTable.setColumnSelectionInterval(0, 0);
        myTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
      }
    }

    private void cycleBackward(int selectedRow, int selectedColumn, int actionsColumn) {
      if (selectedColumn == 0 && selectedRow == 0) {
        // We're in the first cell of the table. Remove selection and leave table.
        myTable.removeRowSelectionInterval(selectedRow, selectedRow);
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.focusPreviousComponent();
      } else if (selectedColumn == actionsColumn && selectedRow != -1 && !myActionsColumnRenderer.cycleFocus(myTable.getSelectedObject(), true)) {
        // We're on an actions column. If we fail to cycle actions, select the first cell in the row.
        myTable.setColumnSelectionInterval(0, 0);
      } else if (selectedRow == -1 || selectedColumn != actionsColumn) {
        // We aren't in the table yet, or we're not in the actions column. Move to the previous (or last) row.
        // and select the actions column
        if (selectedRow == -1) {
          selectedRow = myTable.getRowCount();
        }
        myTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
        myTable.setColumnSelectionInterval(actionsColumn, actionsColumn);
        myActionsColumnRenderer.cycleFocus(myTable.getSelectedObject(), true);
      }
    }
  }


  /**
   * Renders a cell with borders.
   */
  private static class MyRenderer implements TableCellRenderer {
    private static final Border myBorder = IdeBorderFactory.createEmptyBorder(10, 10, 10, 10);
    TableCellRenderer myDefaultRenderer;

    MyRenderer(TableCellRenderer defaultRenderer) {
      myDefaultRenderer = defaultRenderer;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JComponent result = (JComponent)myDefaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      result.setBorder(myBorder);
      return result;
    }
  };

}
