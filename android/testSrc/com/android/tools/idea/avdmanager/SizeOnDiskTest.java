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
package com.android.tools.idea.avdmanager;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.devices.Storage;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ui.table.TableView;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SizeOnDiskTest {
  private AbstractTableModel myModel;
  private TableView<AvdInfo> myTable;

  private ListenableFuture<Long> myNotDoneFuture;

  @Before
  public void mockModel() {
    myModel = Mockito.mock(AbstractTableModel.class);
  }

  @Before
  public void mockTable() {
    // noinspection unchecked
    myTable = Mockito.mock(TableView.class);
  }

  @SuppressWarnings("DoNotMock")  // b/180537631
  @Before
  public void mockNotDoneFuture() {
    // noinspection unchecked
    myNotDoneFuture = Mockito.mock(ListenableFuture.class);
  }

  @Test
  public void sizeOnDisk() {
    // Arrange
    AvdInfo device = Mockito.mock(AvdInfo.class);

    // Act
    Object sizeOnDisk = new SizeOnDisk(device, myTable, myNotDoneFuture);

    // Assert
    assertEquals("Calculating...", sizeOnDisk.toString());
  }

  @Test
  public void sizeOnDiskOnSuccess() {
    // Arrange
    AvdInfo device = Mockito.mock(AvdInfo.class);

    Mockito.when(myTable.getModel()).thenReturn(myModel);
    Mockito.when(myTable.getItems()).thenReturn(Collections.singletonList(device));

    // Act
    Object sizeOnDisk = new SizeOnDisk(device, myTable, Futures.immediateFuture(537_920_595L));

    // Assert
    assertEquals("513 MB", sizeOnDisk.toString());
    Mockito.verify(myModel).fireTableCellUpdated(0, SizeOnDisk.MODEL_COLUMN_INDEX);
  }

  @Test
  public void sizeOnDiskOnFailure() {
    // Arrange
    AvdInfo device = Mockito.mock(AvdInfo.class);

    Mockito.when(myTable.getModel()).thenReturn(myModel);
    Mockito.when(myTable.getItems()).thenReturn(Collections.singletonList(device));

    // Act
    Object sizeOnDisk = new SizeOnDisk(device, myTable, Futures.immediateFailedFuture(new RuntimeException()));

    // Assert
    assertEquals("Failed to calculate", sizeOnDisk.toString());
    Mockito.verify(myModel).fireTableCellUpdated(0, SizeOnDisk.MODEL_COLUMN_INDEX);
  }

  @Test
  public void sizeOnDiskToString() {
    assertEquals("5.3 MB", SizeOnDisk.toString(new Storage((long)(5.32 * 1024), Unit.KiB)));
    assertEquals("5.4 MB", SizeOnDisk.toString(new Storage((long)(5.37 * 1024), Unit.KiB)));
    assertEquals("9.3 MB", SizeOnDisk.toString(new Storage((long)(9.3 * 1024), Unit.KiB)));
    assertEquals("10 MB", SizeOnDisk.toString(new Storage((long)(9.98 * 1024), Unit.KiB)));
    assertEquals("123 MB", SizeOnDisk.toString(new Storage((long)(123.4 * 1024), Unit.KiB)));
    assertEquals("124 MB", SizeOnDisk.toString(new Storage((long)(123.6 * 1024), Unit.KiB)));
    assertEquals("1023 MB", SizeOnDisk.toString(new Storage((long)(1023.0 * 1024), Unit.KiB)));
    assertEquals("18 GB", SizeOnDisk.toString(new Storage((long)(18.0 * 1024), Unit.MiB)));
  }

  @Test
  public void sort() {
    // Arrange
    AvdInfo device1 = Mockito.mock(AvdInfo.class);
    SizeOnDisk sizeOnDisk1 = new SizeOnDisk(device1, myTable, myNotDoneFuture);

    AvdInfo device2 = Mockito.mock(AvdInfo.class);
    SizeOnDisk sizeOnDisk2 = new SizeOnDisk(device2, myTable, Futures.immediateFuture(1_048_576L));

    AvdInfo device3 = Mockito.mock(AvdInfo.class);
    SizeOnDisk sizeOnDisk3 = new SizeOnDisk(device3, myTable, Futures.immediateFuture(2_097_152L));

    AvdInfo device4 = Mockito.mock(AvdInfo.class);
    SizeOnDisk sizeOnDisk4 = new SizeOnDisk(device4, myTable, Futures.immediateFuture(3_145_728L));

    AvdInfo device5 = Mockito.mock(AvdInfo.class);
    SizeOnDisk sizeOnDisk5 = new SizeOnDisk(device5, myTable, Futures.immediateFailedFuture(new RuntimeException()));

    List<SizeOnDisk> sizesOnDisk = Arrays.asList(sizeOnDisk4, sizeOnDisk1, sizeOnDisk5, sizeOnDisk2, sizeOnDisk3);

    // Act
    sizesOnDisk.sort(null);

    // Assert
    assertEquals(Arrays.asList(sizeOnDisk1, sizeOnDisk2, sizeOnDisk3, sizeOnDisk4, sizeOnDisk5), sizesOnDisk);
  }
}
