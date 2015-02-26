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

import com.android.sdklib.*;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.repository.packages.SystemImagePackage;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.internal.repository.sources.SdkSources;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.wizard.DialogWrapperHost;
import com.android.utils.ILogger;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.*;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.TV_TAG;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.WEAR_TAG;

/**
 * Displays a list of system images currently installed and allows selection of one
 */
public class SystemImageList extends JPanel implements ListSelectionListener {
  private final JButton myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  private final JBCheckBox myShowRemoteCheckbox = new JBCheckBox("Show downloadable system images", true);
  private final JButton myInstallLatestVersionButton = new JButton("Install Latest Version...");
  private final LocalSdk mySdk;
  private final RemoteSdk myRemoteSdk;
  private final Project myProject;
  private final JPanel myRefreshPanel = new JPanel(new FlowLayout());
  private TableView<AvdWizardConstants.SystemImageDescription> myTable = new TableView<AvdWizardConstants.SystemImageDescription>();
  private ListTableModel<AvdWizardConstants.SystemImageDescription> myModel = new ListTableModel<AvdWizardConstants.SystemImageDescription>();
  private Set<SystemImageSelectionListener> myListeners = Sets.newHashSet();
  private Predicate<AvdWizardConstants.SystemImageDescription> myFilter;
  private static final Logger LOG = Logger.getInstance(SystemImageList.class);
  private static final ILogger ILOG = new LogWrapper(LOG) {
    @Override
    public void error(Throwable t, String errorFormat, Object... args) {
      LOG.error(String.format(errorFormat, args), t);
    }
  };

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through {@link #addSelectionListener(SystemImageSelectionListener)}
   */
  public interface SystemImageSelectionListener {
    void onSystemImageSelected(@Nullable AvdWizardConstants.SystemImageDescription systemImage);
  }

  public SystemImageList(@Nullable Project project) {
    myProject = project;
    AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (androidSdkData == null) {
      throw new RuntimeException("No SDK Found");
    }
    mySdk = androidSdkData.getLocalSdk();
    myRemoteSdk = androidSdkData.getRemoteSdk();
    myModel.setColumnInfos(ourColumnInfos);
    myModel.setSortable(true);
    myTable.setModelAndUpdateColumns(myModel);
    ListSelectionModel selectionModel =
      new DefaultListSelectionModel() {
        @Override
        public void setSelectionInterval(int index0, int index1) {
          super.setSelectionInterval(index0, index1);
          TableCellEditor editor = myTable.getCellEditor();
          if (editor != null) {
            editor.cancelCellEditing();
          }
          myTable.repaint();
          possiblySwitchEditors(index0, 0);
        }
      };
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setSelectionModel(selectionModel);

    myTable.setRowSelectionAllowed(true);
    myTable.addMouseListener(editorListener);
    myTable.addMouseMotionListener(editorListener);
    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    JPanel southPanel = new JPanel(new BorderLayout());
    JPanel refreshMessageAndButton = new JPanel(new FlowLayout());
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon("refresh images");
    JLabel refreshingLabel = new JLabel("Refreshing...");
    refreshingLabel.setForeground(JBColor.GRAY);
    myRefreshPanel.add(refreshIcon);
    myRefreshPanel.add(refreshingLabel);
    myRefreshPanel.setVisible(false);
    refreshMessageAndButton.add(myRefreshPanel);
    refreshMessageAndButton.add(myRefreshButton);
    southPanel.add(refreshMessageAndButton, BorderLayout.EAST);
    southPanel.add(myShowRemoteCheckbox, BorderLayout.WEST);
    myShowRemoteCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myModel.fireTableDataChanged();
      }
    });

    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refreshImages(true);
      }
    });
    myInstallLatestVersionButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        installForDevice();
      }
    });
    add(southPanel, BorderLayout.SOUTH);
    myTable.getSelectionModel().addListSelectionListener(this);
    TableRowSorter<ListTableModel<AvdWizardConstants.SystemImageDescription>> sorter =
      new TableRowSorter<ListTableModel<AvdWizardConstants.SystemImageDescription>>(myModel) {
        @Override
        public Comparator<?> getComparator(int column) {
          if (column == 1) {
            // API levels: Sort numerically, but the column is of type String.class since
            // it can contain preview codenames as well
            return new ApiLevelComparator();
          }
          // We could consider sorting

          return super.getComparator(column);
        }
      };
    sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
    sorter.setRowFilter(new RowFilter<ListTableModel<AvdWizardConstants.SystemImageDescription>, Integer>() {
      @Override
      public boolean include(Entry<? extends ListTableModel<AvdWizardConstants.SystemImageDescription>, ? extends Integer> entry) {
        return !myModel.getRowValue(entry.getIdentifier()).isRemote() || myShowRemoteCheckbox.isSelected();
      }
    });
    myTable.setRowSorter(sorter);
  }

  private final MouseAdapter editorListener = new MouseAdapter() {
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
  };

  private void possiblySwitchEditors(MouseEvent e) {
    Point p = e.getPoint();
    int row = myTable.rowAtPoint(p);
    int col = myTable.columnAtPoint(p);
    possiblySwitchEditors(row, col);
  }

  private void possiblySwitchEditors(int row, int col) {
    if (row != myTable.getEditingRow() || col != myTable.getEditingColumn()) {
      if (row != -1 && col != -1 && myTable.isCellEditable(row, col)) {
        myTable.editCellAt(row, col);
      }
    }
  }

  public void refreshImages(final boolean forceRefresh) {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Fetching available system image list") {
      @Override
      public void run(@NotNull ProgressIndicator progressIndicator) {
        refreshImagesBackground(forceRefresh);
      }
    });
  }

  static class ImageFingerprint {
    IdDisplay tag;
    String abiType;
    AndroidVersion version;

    @Override
    public int hashCode() {
      return ((tag == null ? 0 : tag.hashCode()) * 37 + abiType.hashCode()) * 37 + version.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ImageFingerprint)) {
        return false;
      }
      ImageFingerprint other = (ImageFingerprint)obj;
      return tag.equals(other.tag) && abiType.equals(other.abiType) && version.equals(other.version);
    }
  }

  public void refreshImagesBackground(boolean forceRefresh) {
    // Should not be run on the AWT thread
    assert !ApplicationManager.getApplication().isDispatchThread();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myRefreshPanel.setVisible(true);
        myRefreshButton.setEnabled(false);
      }
    });
    if (forceRefresh) {
      mySdk.clearLocalPkg(PkgType.PKG_ALL);
    }

    List<AvdWizardConstants.SystemImageDescription> items = getLocalImages();
    // Update list in the UI immediately with the locally available system images
    updateListModel(items);

    // Then perform the network call which may take a long time (e.g. 5 seconds or more);
    // we'll merge in the results of the remotely-available images and update the UI a second
    // time when this is done.
    items.addAll(getRemoteImages(getLocalFingerprints(items)));
    updateListModel(items);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myRefreshPanel.setVisible(false);
        myRefreshButton.setEnabled(true);
      }
    });
  }

  private List<AvdWizardConstants.SystemImageDescription> getRemoteImages(Set<ImageFingerprint> seen) {
    List<AvdWizardConstants.SystemImageDescription> items = Lists.newArrayList();
    SdkSources sources = myRemoteSdk.fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, ILOG);
    Multimap<PkgType, RemotePkgInfo> packages = myRemoteSdk.fetch(sources, ILOG);

    if (packages.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
        public void run() {
          myShowRemoteCheckbox.setEnabled(false);
          myShowRemoteCheckbox.setSelected(false);
        }
      });
    }
    else {
      for (SdkSource source : sources.getAllSources()) {
        com.android.sdklib.internal.repository.packages.Package[] sourcePackages = source.getPackages();
        if (sourcePackages == null) {
          continue;
        }
        for (com.android.sdklib.internal.repository.packages.Package pack : sourcePackages) {
          if (!(pack instanceof SystemImagePackage)) {
            continue;
          }
          AvdWizardConstants.SystemImageDescription desc = new AvdWizardConstants.SystemImageDescription(pack);
          ImageFingerprint probe = new ImageFingerprint();
          probe.version = desc.getVersion();
          probe.tag = desc.getTag();
          probe.abiType = desc.getAbiType();
          // If we don't have a filter or this image passes the filter
          if (!seen.contains(probe) && (myFilter == null || myFilter.apply(desc))) {
            items.add(desc);
          }
        }
      }
    }
    return items;
  }

  public void refreshLocalImagesSynchronously() {
    myModel.setItems(getLocalImages());
  }

  private Set<ImageFingerprint> getLocalFingerprints(List<AvdWizardConstants.SystemImageDescription> images) {
    Set<ImageFingerprint> fingerprints = Sets.newHashSet();
    for (AvdWizardConstants.SystemImageDescription image : images) {
      // If we don't have a filter or this image passes the filter
      if (myFilter == null || myFilter.apply(image)) {
        ImageFingerprint si = new ImageFingerprint();
        si.tag = image.getTag();
        si.abiType = image.getAbiType();

        si.version = image.getTarget().getVersion();
        fingerprints.add(si);
      }
    }
    return fingerprints;
  }

  private List<AvdWizardConstants.SystemImageDescription> getLocalImages() {
    List<AvdWizardConstants.SystemImageDescription> items = Lists.newArrayList();
    List<IAndroidTarget> targets = Lists.newArrayList(mySdk.getTargets());

    for (IAndroidTarget target : targets) {
      ISystemImage[] systemImages = target.getSystemImages();
      if (systemImages != null) {
        for (ISystemImage image : systemImages) {
          // If we don't have a filter or this image passes the filter
          AvdWizardConstants.SystemImageDescription desc = new AvdWizardConstants.SystemImageDescription(target, image);
          if (myFilter == null || myFilter.apply(desc)) {
            items.add(desc);
          }
        }
      }
    }
    return items;
  }

  /**
   * Shows the given items. May be called from the background thread but will ensure
   * that the updates are applied in the UI thread.
   */
  private void updateListModel(@NotNull final List<AvdWizardConstants.SystemImageDescription> items) {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        AvdWizardConstants.SystemImageDescription selected = myTable.getSelectedObject();
        myModel.setItems(items);
        if (selected == null || !items.contains(selected)) {
          selectDefaultImage();
        }
        else {
          setSelectedImage(selected);
        }
      }
    };
    UIUtil.invokeLaterIfNeeded(r);
  }

  public void setFilter(Predicate<AvdWizardConstants.SystemImageDescription> filter) {
    myFilter = filter;
  }

  public void addSelectionListener(SystemImageSelectionListener listener) {
    myListeners.add(listener);
  }

  public void selectDefaultImage() {
    AndroidVersion maxVersion = null;
    AvdWizardConstants.SystemImageDescription best = null;
    for (AvdWizardConstants.SystemImageDescription desc : myModel.getItems()) {
      if (!desc.isRemote() && ((maxVersion == null || desc.getVersion().compareTo(maxVersion) > 0) || (desc.getVersion().equals(maxVersion) && desc.getAbiType().equals(Abi.X86.getCpuArch())))) {
        best = desc;
        maxVersion = best.getVersion();
      }
    }
    setSelectedImage(best);
  }

  public void setSelectedImage(@Nullable AvdWizardConstants.SystemImageDescription selectedImage) {
    if (selectedImage != null) {
      for (AvdWizardConstants.SystemImageDescription listItem : myModel.getItems()) {
        if (selectedImage.getVersion().equals(listItem.getVersion()) &&
            selectedImage.getAbiType().equals(listItem.getAbiType())) {
          myTable.setSelection(ImmutableSet.of(listItem));
          return;
        }
      }
    } else {
      myTable.clearSelection();
    }
  }

  private void installForDevice() {
    int apiLevel = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    List<IPkgDesc> requestedPackages = Lists.newArrayListWithCapacity(3);
    requestedPackages.add(PkgDesc.Builder.newSysImg(new AndroidVersion(apiLevel, null), SystemImage.DEFAULT_TAG,
                                                    Abi.X86.toString(), new MajorRevision(1)).create());
    requestedPackages.add(PkgDesc.Builder.newSysImg(new AndroidVersion(apiLevel, null), WEAR_TAG,
                                                    Abi.X86.toString(), new MajorRevision(1)).create());
    requestedPackages.add(PkgDesc.Builder.newSysImg(new AndroidVersion(apiLevel, null), TV_TAG,
                                                    Abi.X86.toString(), new MajorRevision(1)).create());
    SdkQuickfixWizard sdkQuickfixWizard = new SdkQuickfixWizard(null, null, requestedPackages);
    sdkQuickfixWizard.init();
    sdkQuickfixWizard.show();
    refreshImages(true);
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   * @param e
   */
  @Override
  public void valueChanged(ListSelectionEvent e) {
    AvdWizardConstants.SystemImageDescription selected = myTable.getSelectedObject();
    if (selected != null && selected.isRemote()) {
      selected = null;
    }
    for (SystemImageSelectionListener listener : myListeners) {
      listener.onSystemImageSelected(selected);
    }
  }

  /**
   * List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
   * the cell value in that column for a given row item.
   */
  private final ColumnInfo[] ourColumnInfos = new ColumnInfo[] {
    new SystemImageColumnInfo("Release Name") {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        String codeName = SdkVersionInfo.getCodeName(systemImage.getVersion().getApiLevel());
        String maybeDeprecated = systemImage.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API ?
                                 " (Deprecated)" : "";
        return codeName == null ? "Unknown" : codeName + maybeDeprecated;
      }
    },
    new SystemImageColumnInfo("API Level", 100) {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        return systemImage.getVersion().getApiString();
      }

      @Nullable
      @Override
      public Comparator<AvdWizardConstants.SystemImageDescription> getComparator() {
        return new Comparator<AvdWizardConstants.SystemImageDescription>() {
          @Override
          public int compare(AvdWizardConstants.SystemImageDescription o1, AvdWizardConstants.SystemImageDescription o2) {
            return o1.getVersion().getApiLevel() - o2.getVersion().getApiLevel();
          }
        };
      }
    },
    new SystemImageColumnInfo("ABI", 100) {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        return systemImage.getAbiType();
      }
    },
    new SystemImageColumnInfo("Target") {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        IdDisplay tag = systemImage.getTag();
        String name = systemImage.getName();
        return tag == null || tag.equals(SystemImage.DEFAULT_TAG) ? name : String.format("%1$s - %2$s", name, tag);
      }
    },
  };

  /**
   * This class extends {@link com.intellij.util.ui.ColumnInfo} in order to pull a string value from a given {@link com.android.sdklib.internal.avd.AvdInfo}.
   * This is the column info used for most of our table, including the Name, Resolution, and API level columns.
   * It uses the text field renderer ({@link #myRenderer}) and allows for sorting by the lexicographical value
   * of the string displayed by the {@link com.intellij.ui.components.JBLabel} rendered as the cell component. An explicit width may be used
   * by calling the overloaded constructor, otherwise the column will auto-scale to fill available space.
   */
  public abstract class SystemImageColumnInfo extends ColumnInfo<AvdWizardConstants.SystemImageDescription, String> {
    private final Border myBorder = IdeBorderFactory.createEmptyBorder(10, 10, 10, 10);

    private final int myWidth;

    public SystemImageColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    public SystemImageColumnInfo(@NotNull String name) {
      this(name, -1);
    }

    @Override
    public boolean isCellEditable(AvdWizardConstants.SystemImageDescription systemImageDescription) {
      return systemImageDescription.isRemote();
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(AvdWizardConstants.SystemImageDescription o) {
      return new SystemImageDescriptionRenderer(o);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(final AvdWizardConstants.SystemImageDescription o) {
      return new SystemImageDescriptionRenderer(o);
    }

    private class SystemImageDescriptionRenderer extends AbstractTableCellEditor implements TableCellRenderer {
      private AvdWizardConstants.SystemImageDescription image;

      SystemImageDescriptionRenderer(AvdWizardConstants.SystemImageDescription o) {
        image = o;
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (isSelected) {
          if (image.isRemote()) {
            panel.setBackground(UIUtil.getListUnfocusedSelectionBackground());
          } else {
            panel.setBackground(table.getSelectionBackground());
          }
          panel.setForeground(table.getSelectionForeground());
          panel.setOpaque(true);
        }
        else {
          panel.setBackground(table.getBackground());
          panel.setForeground(table.getForeground());
          panel.setOpaque(true);
        }
        JBLabel label = new JBLabel((String)value);
        Font labelFont = UIUtil.getLabelFont();
        if (column == 0) {
          label.setFont(labelFont.deriveFont(Font.BOLD));
        }
        if (image.isRemote()) {
          Font font = labelFont.deriveFont(label.getFont().getStyle() | Font.ITALIC);
          label.setFont(font);
          label.setForeground(UIUtil.getLabelDisabledForeground());
          // on OS X the actual text width isn't computed correctly. Compensating for that..
          if (!label.getText().isEmpty()) {
            int fontMetricsWidth = label.getFontMetrics(label.getFont()).stringWidth(label.getText());
            TextLayout l = new TextLayout(label.getText(), label.getFont(), label.getFontMetrics(label.getFont()).getFontRenderContext());
            int offset = (int)Math.ceil(l.getBounds().getWidth()) - fontMetricsWidth;
            if (offset > 0) {
              label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, offset));
            }
          }
          panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
              if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
                downloadImage(image);
              }
            }
          });
        }
        panel.add(label);
        if (image.isRemote() && column == 0) {
          final JBLabel link = new JBLabel("Download");
          link.setBackground(table.getBackground());
          link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          link.setForeground(JBColor.BLUE);
          Font font = link.getFont();
          if (isSelected) {
            Map<TextAttribute, Integer> attrs = Maps.newHashMap();
            attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            font = font.deriveFont(attrs);
          }
          link.setFont(font);
          link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              downloadImage(image);
            }
          });
          panel.add(link);
        }
        return panel;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        return getTableCellRendererComponent(table, value, isSelected, false, row, column);
      }

      @Override
      public Object getCellEditorValue() {
        return null;
      }

      @Override
      public boolean isCellEditable(EventObject e) {
        return true;
      }

    }

    private void downloadImage(AvdWizardConstants.SystemImageDescription image) {
      IPkgDesc remote = image.getRemotePackage().getPkgDesc();
      IPkgDesc request = null;
      if (remote.getType().equals(PkgType.PKG_SYS_IMAGE)) {
        request =
          PkgDesc.Builder.newSysImg(remote.getAndroidVersion(), remote.getTag(), remote.getPath(), remote.getMajorRevision())
            .create();
      }
      else if (remote.getType().equals(PkgType.PKG_ADDON_SYS_IMAGE)) {
        request = PkgDesc.Builder.newAddonSysImg(image.getVersion(), remote.getVendor(), image.getTag(), image.getAbiType(),
                                                 (MajorRevision)image.getRemotePackage().getRevision()).create();
      }
      List<IPkgDesc> requestedPackages = Lists.newArrayList(request);
      SdkQuickfixWizard sdkQuickfixWizard = new SdkQuickfixWizard(null, null, requestedPackages,
                                                                  new DialogWrapperHost(null, DialogWrapper.IdeModalityType.PROJECT));
      sdkQuickfixWizard.init();
      sdkQuickfixWizard.show();
      refreshImages(true);
    }

    @Nullable
    @Override
    public Comparator<AvdWizardConstants.SystemImageDescription> getComparator() {
      return new Comparator<AvdWizardConstants.SystemImageDescription>() {
        @Override
        public int compare(AvdWizardConstants.SystemImageDescription o1, AvdWizardConstants.SystemImageDescription o2) {
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

}
