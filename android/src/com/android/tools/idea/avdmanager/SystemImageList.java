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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.sdklib.repositoryv2.targets.SystemImage.*;

/**
 * Displays a list of system images currently installed and allows selection of one
 */
public class SystemImageList extends JPanel implements ListSelectionListener {
  private final TableView<SystemImageDescription> myTable = new TableView<SystemImageDescription>();
  private final Set<SystemImageSelectionListener> myListeners = Sets.newHashSet();
  private SystemImageListModel myModel;
  private SystemImageDescription myLastSelectedImage;

  private static final Map<Abi, Integer> DEFAULT_ABI_SORT_ORDER = new ContainerUtil.ImmutableMapBuilder<Abi, Integer>()
    .put(Abi.MIPS64, 0)
    .put(Abi.MIPS, 1)
    .put(Abi.ARM64_V8A, 2)
    .put(Abi.ARMEABI, 3)
    .put(Abi.ARMEABI_V7A, 4)
    .put(Abi.X86_64, 5)
    .put(Abi.X86, 6)
    .build();

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through {@link #addSelectionListener(SystemImageSelectionListener)}
   */
  public interface SystemImageSelectionListener {
    void onSystemImageSelected(@Nullable SystemImageDescription systemImage);
  }

  public SystemImageList() {
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
    JButton installLatestVersionButton = new JButton("Install Latest Version...");
    installLatestVersionButton.addActionListener(new ActionListener() {  // TODO(jbakermalone): actually show this button in the ui
      @Override
      public void actionPerformed(ActionEvent e) {
        installForDevice();
      }
    });
    myTable.getSelectionModel().addListSelectionListener(this);
    myTable.getEmptyText().setText("No System Images available. Are you connected to the internet?");
  }

  public void setModel(@NotNull SystemImageListModel model) {
    myModel = model;
    myTable.setModelAndUpdateColumns(model);
  }

  public void setRowFilter(@NotNull RowFilter<ListTableModel<SystemImageDescription>, Integer> filter) {
    TableRowSorter<ListTableModel<SystemImageDescription>> sorter =
      (TableRowSorter<ListTableModel<SystemImageDescription>>)myTable.getRowSorter();
    sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
    sorter.setRowFilter(filter);
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

  public void addSelectionListener(SystemImageSelectionListener listener) {
    myListeners.add(listener);
  }

  private static boolean isBetter(@NotNull SystemImageDescription image, @Nullable SystemImageDescription bestSoFar) {
    return bestSoFar == null || 0 < ComparisonChain.start()
      .compareTrueFirst(image.isRemote(), bestSoFar.isRemote())
      .compare(abiRank(image), abiRank(bestSoFar))
      .compare(image.getVersion(), bestSoFar.getVersion())
      .compareFalseFirst(image.getTag().equals(GOOGLE_APIS_TAG), bestSoFar.getTag().equals(GOOGLE_APIS_TAG))
      .result();
  }

  private static int abiRank(@NotNull SystemImageDescription image) {
    Abi abi = Abi.getEnum(image.getAbiType());
    if (abi != null && DEFAULT_ABI_SORT_ORDER.containsKey(abi)) {
      return DEFAULT_ABI_SORT_ORDER.get(abi);
    }
    else {
      return -1;
    }
  }

  public void setSelectedImage(@Nullable SystemImageDescription selectedImage) {
    myLastSelectedImage = selectedImage;
    updateSelection(selectedImage);
  }

  private void updateSelection(@Nullable SystemImageDescription selectedImage) {
    if (selectedImage != null) {
      myTable.setSelection(ImmutableSet.of(selectedImage));
    } else {
      myTable.clearSelection();
    }
  }

  public void makeListCurrent() {
    notifySelectionChange();
  }

  /**
   * Restore the selection to the last selected system image.
   * If the last selected system image cannot be found choose the best image in the list.
   * @param partlyDownloaded if true we are restoring after the local images has been reloaded but not the remote.
   *                         When this is the case do NOT fallback to the best image if the last selection could not be found,
   *                         instead wait for the remote images and keep looking for the current last selected system image.
   * @param defaultSystemImage System image to use if a previous image was not already selected
   */
  public void restoreSelection(boolean partlyDownloaded, @Nullable SystemImageDescription defaultSystemImage) {
    SystemImageDescription best = null;
    SystemImageDescription toFind = myLastSelectedImage != null ? myLastSelectedImage : defaultSystemImage;
    for (int index = 0; index < myTable.getRowCount(); index++) {
      SystemImageDescription desc = myModel.getRowValue(myTable.convertRowIndexToModel(index));
      if (desc.equals(toFind)) {
        best = desc;
        break;
      }
      if (!partlyDownloaded && isBetter(desc, best)) {
        best = desc;
      }
    }
    updateSelection(best);
    myLastSelectedImage = partlyDownloaded ? toFind : best;
  }

  private void installForDevice() {
    int apiLevel = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    List<String> requestedPackages = Lists.newArrayListWithCapacity(3);
    requestedPackages.add(DetailsTypes.getSysImgPath(null, new AndroidVersion(apiLevel, null), DEFAULT_TAG, Abi.X86.toString()));
    requestedPackages.add(DetailsTypes.getSysImgPath(null, new AndroidVersion(apiLevel, null), WEAR_TAG, Abi.X86.toString()));
    requestedPackages.add(DetailsTypes.getSysImgPath(null, new AndroidVersion(apiLevel, null), TV_TAG, Abi.X86.toString()));
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(this, requestedPackages);
    if (dialog != null) {
      dialog.show();
      myModel.refreshImages(true);
    }
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   */
  @Override
  public void valueChanged(@Nullable ListSelectionEvent event) {
    if (event == null || event.getValueIsAdjusting() || myModel.isUpdating()) {
      return;
    }
    myLastSelectedImage = myTable.getSelectedObject();
    notifySelectionChange();
  }

  private void notifySelectionChange() {
    for (SystemImageSelectionListener listener : myListeners) {
      listener.onSystemImageSelected(myLastSelectedImage);
    }
  }
}
