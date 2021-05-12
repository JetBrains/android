/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab.columns;

import com.android.repository.io.FileUtilKt;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.table.TableView;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A value in the Size on Disk column in the Device Manager table. The primary purpose of this class is to encapsulate the
 * calculation of the virtual device size on a background thread. The future will notify the table when the calculation is done.
 */
final class SizeOnDisk implements Comparable<SizeOnDisk> {
  @VisibleForTesting
  static final int MODEL_COLUMN_INDEX = 2;

  /**
   * The actual string shown in the column
   */
  @NotNull
  private String myString;

  private long myValue;

  @NotNull
  private final ListenableFuture<Long> myFuture;

  SizeOnDisk(@NotNull AvdInfo device, @NotNull TableView<AvdInfo> table) {
    this(device, table, () -> getSize(Paths.get(device.getDataFolderPath())), EdtExecutorService.getInstance());
  }

  @NotNull
  private static ListenableFuture<Long> getSize(@NotNull Path path) {
    return MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()).submit(() -> FileUtilKt.recursiveSize(path));
  }

  private SizeOnDisk(@NotNull AvdInfo device,
                     @NotNull TableView<AvdInfo> table,
                     @NotNull Supplier<? extends ListenableFuture<Long>> futureSupplier,
                     @NotNull Executor executor) {
    myString = "Calculating...";
    myValue = -1;

    myFuture = futureSupplier.get();

    addCallback(executor, new FutureCallback<Long>() {
      @Override
      public void onSuccess(@Nullable Long value) {
        assert value != null;
        Storage storage = new Storage(value);

        myString = SizeOnDisk.toString(storage);
        myValue = storage.getSize();

        ((AbstractTableModel)table.getModel()).fireTableCellUpdated(table.getItems().indexOf(device), MODEL_COLUMN_INDEX);
      }

      @Override
      public void onFailure(@NotNull Throwable throwable) {
        Logger.getInstance(SizeOnDisk.class).warn(throwable);

        myString = "Failed to calculate";
        myValue = Long.MAX_VALUE;

        ((AbstractTableModel)table.getModel()).fireTableCellUpdated(table.getItems().indexOf(device), MODEL_COLUMN_INDEX);
      }
    });
  }

  private void addCallback(@NotNull Executor executor, @NotNull FutureCallback<Long> callback) {
    Futures.addCallback(myFuture, callback, executor);
  }

  @NotNull
  @VisibleForTesting
  static String toString(@NotNull Storage storage) {
    double value = storage.getPreciseSizeAsUnit(Unit.MiB);
    Object unitSymbol = "MB";

    if (value >= 1024) {
      value = storage.getPreciseSizeAsUnit(Unit.GiB);
      unitSymbol = "GB";
    }

    return String.format(value > 9.94 ? "%.0f %s" : "%.1f %s", value, unitSymbol);
  }

  @NotNull
  @Override
  public String toString() {
    return myString;
  }

  @Override
  public int compareTo(@NotNull SizeOnDisk sizeOnDisk) {
    return Long.compare(myValue, sizeOnDisk.myValue);
  }
}
