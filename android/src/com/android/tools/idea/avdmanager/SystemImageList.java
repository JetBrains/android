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
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.Set;
import java.util.List;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.TV_TAG;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.WEAR_TAG;

/**
 * Displays a list of system images currently installed and allows selection of one
 */
public class SystemImageList extends JPanel implements ListSelectionListener {
  private final JButton myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  private final JButton myInstallLatestVersionButton = new JButton("Install Latest Version...");
  private final LocalSdk mySdk;
  private TableView<AvdWizardConstants.SystemImageDescription> myTable = new TableView<AvdWizardConstants.SystemImageDescription>();
  private ListTableModel<AvdWizardConstants.SystemImageDescription> myModel = new ListTableModel<AvdWizardConstants.SystemImageDescription>();
  private Set<SystemImageSelectionListener> myListeners = Sets.newHashSet();
  private Predicate<ISystemImage> myFilter;


  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through {@link #addSelectionListener(SystemImageSelectionListener)}
   */
  public interface SystemImageSelectionListener {
    void onSystemImageSelected(@Nullable AvdWizardConstants.SystemImageDescription systemImage);
  }

  public SystemImageList() {
    AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (androidSdkData == null) {
      throw new RuntimeException("No SDK Found");
    }
    mySdk = androidSdkData.getLocalSdk();
    myModel.setColumnInfos(ourColumnInfos);
    myModel.setSortable(true);
    refreshImages(true);
    myTable.setModelAndUpdateColumns(myModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setRowSelectionAllowed(true);
    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    southPanel.add(myRefreshButton);
    southPanel.add(myInstallLatestVersionButton);
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
    myTable.getRowSorter().toggleSortOrder(2);
    myTable.getRowSorter().toggleSortOrder(2);
  }

  public void refreshImages(boolean forceRefresh) {
    if (forceRefresh) {
      mySdk.clearLocalPkg(PkgType.PKG_ALL);
    }
    List<IAndroidTarget> targets = Lists.newArrayList(mySdk.getTargets());
    List<AvdWizardConstants.SystemImageDescription> items = Lists.newArrayList();
    for (IAndroidTarget target : targets) {
      ISystemImage[] systemImages = target.getSystemImages();
      if (systemImages != null) {
        for (ISystemImage image : systemImages) {
          // If we don't have a filter or this image passes the filter
          if (myFilter == null || myFilter.apply(image)) {
            items.add(new AvdWizardConstants.SystemImageDescription(target, image));
          }
        }
      }
    }
    myModel.setItems(items);
  }

  public void setFilter(Predicate<ISystemImage> filter) {
    myFilter = filter;
  }

  public void addSelectionListener(SystemImageSelectionListener listener) {
    myListeners.add(listener);
  }

  public void removeSelectionListener(SystemImageSelectionListener listener) {
    myListeners.remove(listener);
  }

  public void setSelectedImage(@Nullable AvdWizardConstants.SystemImageDescription selectedImage) {
    if (selectedImage != null) {
      for (AvdWizardConstants.SystemImageDescription listItem : myModel.getItems()) {
        if (selectedImage.target.getVersion().equals(listItem.target.getVersion()) &&
            selectedImage.systemImage.getAbiType().equals(listItem.systemImage.getAbiType())) {
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
    for (SystemImageSelectionListener listener : myListeners) {
      listener.onSystemImageSelected(selected);
    }
  }

  /**
   * List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
   * the cell value in that column for a given row item.
   */
  private static final ColumnInfo[] ourColumnInfos = new ColumnInfo[] {
    new SystemImageColumnInfo("Release Name") {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        String codeName = SdkVersionInfo.getCodeName(systemImage.target.getVersion().getApiLevel());
        String maybeDeprecated = systemImage.target.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API ?
                                 " (Deprecated)" : "";
        return codeName == null ? "Unknown" : codeName + maybeDeprecated;
      }
    },
    new SystemImageColumnInfo("API Level", 100) {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        return systemImage.target.getVersion().getApiString();
      }

      @Nullable
      @Override
      public Comparator<AvdWizardConstants.SystemImageDescription> getComparator() {
        return new Comparator<AvdWizardConstants.SystemImageDescription>() {
          @Override
          public int compare(AvdWizardConstants.SystemImageDescription o1, AvdWizardConstants.SystemImageDescription o2) {
            return o1.target.getVersion().getApiLevel() - o2.target.getVersion().getApiLevel();
          }
        };
      }
    },
    new SystemImageColumnInfo("ABI", 100) {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        return systemImage.systemImage.getAbiType();
      }
    },
    new SystemImageColumnInfo("Target") {
      @Nullable
      @Override
      public String valueOf(AvdWizardConstants.SystemImageDescription systemImage) {
        return systemImage.target.getFullName();
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
  public abstract static class SystemImageColumnInfo extends ColumnInfo<AvdWizardConstants.SystemImageDescription, String> {
    private final Border myBorder = IdeBorderFactory.createEmptyBorder(10, 10, 10, 10);

    private final int myWidth;

    public SystemImageColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    public SystemImageColumnInfo(@NotNull String name) {
      this(name, -1);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(AvdWizardConstants.SystemImageDescription o) {
      return new TableCellRenderer() {
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
