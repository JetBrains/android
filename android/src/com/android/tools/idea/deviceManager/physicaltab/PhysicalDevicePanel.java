/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.physicaltab;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevicePanel extends JBPanel<PhysicalDevicePanel> implements Disposable {
  private final @NotNull Function<@NotNull PhysicalDeviceTableModel, @NotNull Disposable> myNewPhysicalDeviceChangeListener;
  private @Nullable JTable myTable;

  PhysicalDevicePanel(@Nullable Project project) {
    this(PhysicalDeviceChangeListener::new, new PhysicalDeviceAsyncSupplier(project), EdtExecutorService.getInstance());
  }

  @VisibleForTesting
  PhysicalDevicePanel(@NotNull Function<@NotNull PhysicalDeviceTableModel, @NotNull Disposable> newPhysicalDeviceChangeListener,
                      @NotNull PhysicalDeviceAsyncSupplier supplier,
                      @NotNull Executor executor) {
    super(new BorderLayout());
    myNewPhysicalDeviceChangeListener = newPhysicalDeviceChangeListener;

    add(new JBTable(new PhysicalDeviceTableModel()).getTableHeader(), BorderLayout.NORTH);
    add(new JBLabel("No physical devices added. Connect a device via USB cable.", SwingConstants.CENTER), BorderLayout.CENTER);

    Futures.addCallback(supplier.get(), new AddNewTable(this), executor);
  }

  private static final class AddNewTable implements FutureCallback<@Nullable List<@NotNull PhysicalDevice>> {
    private final @NotNull PhysicalDevicePanel myPanel;

    private AddNewTable(@NotNull PhysicalDevicePanel panel) {
      myPanel = panel;
    }

    @Override
    public void onSuccess(@Nullable List<@NotNull PhysicalDevice> devices) {
      assert devices != null;
      PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

      Disposer.register(myPanel, myPanel.myNewPhysicalDeviceChangeListener.apply(model));

      JTable table = new JBTable(model);

      myPanel.myTable = table;

      myPanel.removeAll();
      myPanel.add(new JBScrollPane(table));
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      Logger.getInstance(PhysicalDevicePanel.class).warn(throwable);
    }
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  @NotNull Object getData() {
    assert myTable != null;

    return IntStream.range(0, myTable.getRowCount())
      .mapToObj(this::getRowAt)
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  private @NotNull Object getRowAt(int viewRowIndex) {
    assert myTable != null;

    return IntStream.range(0, myTable.getColumnCount())
      .mapToObj(viewColumnIndex -> myTable.getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }
}
