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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.repository.io.recursiveSize
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.AvdInfo
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.table.TableView
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executor
import java.util.function.Supplier
import javax.swing.table.AbstractTableModel

/**
 * A value in the Size on Disk column in the Android Virtual Device Manager table. The primary purpose of this class is to encapsulate the
 * calculation of the virtual device size on a background thread. The future will notify the table when the calculation is done.
 */
internal class SizeOnDisk @JvmOverloads constructor(
  device: AvdInfo,
  table: TableView<AvdInfo>,
  futureSupplier: Supplier<out ListenableFuture<Long?>> = Supplier<ListenableFuture<Long?>> { getSize(Paths.get(device.dataFolderPath)) },
  executor: Executor = EdtExecutorService.getInstance()
) : Comparable<SizeOnDisk> {
  /**
   * The actual string shown in the column
   */
  private var string = "Calculating..."
  private var value: Long
  private val future: ListenableFuture<Long?>

  @VisibleForTesting
  constructor(
    device: AvdInfo, table: TableView<AvdInfo>, future: ListenableFuture<Long?>
  ) : this(device, table, Supplier<ListenableFuture<Long?>> { future }, MoreExecutors.directExecutor())

  private fun addCallback(executor: Executor, callback: FutureCallback<Long?>) {
    Futures.addCallback(future, callback, executor)
  }

  override fun toString(): String = string

  override fun compareTo(other: SizeOnDisk): Int = value.compareTo(other.value)

  companion object {
    @VisibleForTesting
    val MODEL_COLUMN_INDEX = 7
    private fun getSize(path: Path): ListenableFuture<Long?> {
      return MoreExecutors.listeningDecorator(
        AppExecutorUtil.getAppExecutorService()).submit<Long?> { path.recursiveSize() }
    }

    @VisibleForTesting
    fun toString(storage: Storage): String {
      var value = storage.getPreciseSizeAsUnit(Storage.Unit.MiB)
      var unitSymbol: Any = "MB"
      if (value >= 1024) {
        value = storage.getPreciseSizeAsUnit(Storage.Unit.GiB)
        unitSymbol = "GB"
      }
      return String.format(if (value > 9.94) "%.0f %s" else "%.1f %s", value, unitSymbol)
    }
  }

  init {
    value = -1
    future = futureSupplier.get()
    addCallback(executor, object : FutureCallback<Long?> {
      override fun onSuccess(value: Long?) {
        assert(value != null)
        val storage = Storage(value!!)
        string = toString(storage)
        this@SizeOnDisk.value = storage.size
        (table.model as AbstractTableModel).fireTableCellUpdated(table.items.indexOf(device), MODEL_COLUMN_INDEX)
      }

      override fun onFailure(throwable: Throwable) {
        logger<SizeOnDisk>().warn(throwable)
        string = "Failed to calculate"
        value = Long.MAX_VALUE
        (table.model as AbstractTableModel).fireTableCellUpdated(table.items.indexOf(device), MODEL_COLUMN_INDEX)
      }
    })
  }
}