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

import com.android.resources.Density;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
  private final JButton myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  private final JPanel myCenterCardPanel;
  private final AvdListDialog myDialog;

  private TableView<AvdInfo> myTable;
  private ListTableModel<AvdInfo> myModel = new ListTableModel<AvdInfo>();
  private Set<AvdSelectionListener> myListeners = Sets.newHashSet();

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
    setLayout(new BorderLayout());
    myCenterCardPanel = new JPanel(new CardLayout());
    JPanel nonemptyPanel = new JPanel(new BorderLayout());
    myCenterCardPanel.add(nonemptyPanel, NONEMPTY);
    nonemptyPanel.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myCenterCardPanel.add(new EmptyAvdListPanel(this), EMPTY);
    add(myCenterCardPanel, BorderLayout.CENTER);
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(myRefreshButton, BorderLayout.EAST);
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refreshAvds();
      }
    });
    myRefreshButton.putClientProperty("JButton.buttonType", "segmented-only");
    CreateAvdAction createAvdAction = new CreateAvdAction(this);
    JButton newButton = new JButton(createAvdAction);
    newButton.setIcon(createAvdAction.getIcon());
    newButton.setText(createAvdAction.getText());
    newButton.putClientProperty("JButton.buttonType", "segmented-only");
    southPanel.add(newButton, BorderLayout.WEST);
    nonemptyPanel.add(southPanel, BorderLayout.SOUTH);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().addListSelectionListener(this);
    myTable.addMouseListener(myEditingListener);
    myTable.addMouseMotionListener(myEditingListener);
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
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void notifyRun() {
    if (myDialog.isShowing()) {
      myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
    }
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
   * Return the resolution of a given AVD as a string of the format [width]x[height] - [density]
   * (e.g. 1200x1920 - xhdpi) or "Unknown Resolution" if the AVD does not define a resolution.
   */
  protected static String getResolution(AvdInfo info) {
    String resolution;
    Dimension res = AvdManagerConnection.getDefaultAvdManagerConnection().getAvdResolution(info);
    Density density = AvdManagerConnection.getAvdDensity(info);
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
    if (id.contains("android-")) {
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
            AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
            Dimension d1 = connection.getAvdResolution(o1);
            Dimension d2 = connection.getAvdResolution(o2);
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
    new AvdColumnInfo("API", 50) {
      @NotNull
      @Override
      public String valueOf(AvdInfo avdInfo) {
        IAndroidTarget target = avdInfo.getTarget();
        if (target == null) {
          return "N/A";
        }
        return target.getVersion().getApiString();
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
        IAndroidTarget target = info.getTarget();
        if (target == null) {
          return "N/A";
        }
        return target.getName();
      }
    },
    new AvdColumnInfo("CPU/ABI", 60) {
      @Nullable
      @Override
      public String valueOf(AvdInfo avdInfo) {
        return avdInfo.getCpuArch();
      }
    },
    new AvdSizeColumnInfo("Size on Disk"),
    new AvdActionsColumnInfo("Actions", 2 /* Num Visible Actions */),
  };

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
      this(name, 50);
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
    private final Border myBorder = IdeBorderFactory.createEmptyBorder(10, 10, 10, 10);

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
    public TableCellRenderer getRenderer(AvdInfo o) {
      return myRenderer;
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

  private static abstract class ActionRenderer extends AbstractTableCellEditor implements TableCellRenderer {}
  /**
   * Custom table cell renderer that renders an action panel for a given AVD entry
   */
  private class AvdActionsColumnInfo extends ColumnInfo<AvdInfo, AvdInfo> {
    private int myNumVisibleActions = -1;
    private int myWidth;

    /**
     * This cell renders an action panel for both the editor component and the display component
     */
    private final ActionRenderer ourActionPanelRendererEditor = new ActionRenderer() {
      Map<Object, Component> myInfoToComponentMap = Maps.newHashMap();
      private Component getComponent(JTable table, Object value, boolean isSelected, int row) {
        Component panel;
        if (myInfoToComponentMap.containsKey(value)) {
          panel = myInfoToComponentMap.get(value);
        } else {
          panel = new AvdActionPanel((AvdInfo)value, myNumVisibleActions, AvdDisplayList.this);
          myInfoToComponentMap.put(value, panel);
        }
        if (table.getSelectedRow() == row || isSelected) {
          panel.setBackground(table.getSelectionBackground());
          panel.setForeground(table.getSelectionForeground());
        } else {
          panel.setBackground(table.getBackground());
          panel.setForeground(table.getForeground());
        }
        return panel;
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return getComponent(table, value, isSelected, row);
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        return getComponent(table, value, isSelected, row);
      }

      @Override
      public Object getCellEditorValue() {
        return null;
      }
    };

    public AvdActionsColumnInfo(@NotNull String name, int numVisibleActions) {
      super(name);
      myNumVisibleActions = numVisibleActions;
      myWidth = numVisibleActions == -1 ? -1 : 45 * numVisibleActions + 75;
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
    public TableCellRenderer getRenderer(AvdInfo o) {
      return ourActionPanelRendererEditor;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(AvdInfo avdInfo) {
      return ourActionPanelRendererEditor;
    }

    @Override
    public boolean isCellEditable(AvdInfo avdInfo) {
      return true;
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
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
        for (File file : TemplateUtils.listFiles(avdDir)) {
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
}
