/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.SetAllOnline;
import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableModelTest {
  private final @NotNull AvdManagerConnection myConnection = Mockito.mock(AvdManagerConnection.class);
  private final @NotNull AvdInfo myAvd = Mockito.mock(AvdInfo.class);
  private final @NotNull Collection<@NotNull VirtualDevice> myDevices = List.of(TestVirtualDevices.pixel5Api31(myAvd));
  private final @NotNull TableModelListener myListener = Mockito.mock(TableModelListener.class);

  @Test
  public void setAllOnline() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    Mockito.when(myConnection.isAvdRunning(myAvd)).thenReturn(true);

    VirtualDeviceTableModel model = new VirtualDeviceTableModel(null,
                                                                myDevices,
                                                                (m, key) -> newSetOnline(m, key, latch),
                                                                () -> myConnection,
                                                                SetAllOnline::new,
                                                                new DeviceManagerAndroidDebugBridge(),
                                                                EmulatorConsole::getConsole,
                                                                VirtualTabMessages::showErrorDialog);

    model.addTableModelListener(myListener);

    // Act
    model.setAllOnline();

    // Assert
    CountDownLatchAssert.await(latch);

    assertEquals(List.of(TestVirtualDevices.onlinePixel5Api31(myAvd)), model.getDevices());
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 0))));
  }

  private static @NotNull FutureCallback<@NotNull Boolean> newSetOnline(@NotNull VirtualDeviceTableModel model,
                                                                        @NotNull Key key,
                                                                        @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(VirtualDeviceTableModel.newSetOnline(model, key), latch);
  }

  @Test
  public void isCellEditableCaseLaunchOrStopModelColumnIndex() {
    // Arrange
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.PIXEL_5_API_31_KEY)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setState(VirtualDevice.State.LAUNCHING)
      .setAvdInfo(myAvd)
      .build();

    TableModel model = new VirtualDeviceTableModel(null, List.of(device));

    // Act
    boolean editable = model.isCellEditable(0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    assertFalse(editable);
  }

  @Test
  public void isCellEditableCaseActivateDeviceFileExplorerWindowModelColumnIndexProjectIsNull() {
    // Arrange
    TableModel model = new VirtualDeviceTableModel(null);

    // Act
    boolean editable = model.isCellEditable(0, VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);

    // Assert
    assertFalse(editable);
  }

  @Test
  public void isCellEditableCaseActivateDeviceFileExplorerWindowModelColumnIndexDeviceIsOnline() {
    // Arrange
    TableModel model = new VirtualDeviceTableModel(Mockito.mock(Project.class), List.of(TestVirtualDevices.onlinePixel5Api31(myAvd)));

    // Act
    boolean editable = model.isCellEditable(0, VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);

    // Assert
    assertTrue(editable);
  }

  @Test
  public void isCellEditableCaseActivateDeviceFileExplorerWindowModelColumnIndex() {
    // Arrange
    TableModel model = new VirtualDeviceTableModel(Mockito.mock(Project.class), List.of(TestVirtualDevices.pixel5Api31(myAvd)));

    // Act
    boolean editable = model.isCellEditable(0, VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);

    // Assert
    assertFalse(editable);
  }

  @Test
  public void setValueAtStartAvdSucceeded() {
    // Arrange
    Mockito.when(myConnection.startAvd(null, myAvd)).thenReturn(Futures.immediateFuture(Mockito.mock(IDevice.class)));

    TableModel model = new VirtualDeviceTableModel(null,
                                                   myDevices,
                                                   VirtualDeviceTableModel::newSetOnline,
                                                   () -> myConnection,
                                                   SetAllOnline::new,
                                                   new DeviceManagerAndroidDebugBridge(),
                                                   EmulatorConsole::getConsole,
                                                   VirtualTabMessages::showErrorDialog);

    model.addTableModelListener(myListener);

    // Act
    model.setValueAt(VirtualDevice.State.LAUNCHING, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    Object device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.PIXEL_5_API_31_KEY)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setState(VirtualDevice.State.LAUNCHING)
      .setAvdInfo(myAvd)
      .build();

    assertEquals(device, model.getValueAt(0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));
  }

  @Test
  public void setValueAtStartAvdFailed() throws Exception {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    Throwable throwable = new RuntimeException();
    Mockito.when(myConnection.startAvd(null, myAvd)).thenReturn(Futures.immediateFailedFuture(throwable));

    @SuppressWarnings("unchecked")
    BiConsumer<Throwable, Project> showErrorDialog = Mockito.mock(BiConsumer.class);

    TableModel model = new VirtualDeviceTableModel(null,
                                                   myDevices,
                                                   (m, key) -> newSetOnline(m, key, latch),
                                                   () -> myConnection,
                                                   SetAllOnline::new,
                                                   new DeviceManagerAndroidDebugBridge(),
                                                   EmulatorConsole::getConsole,
                                                   showErrorDialog);

    model.addTableModelListener(myListener);

    // Act
    model.setValueAt(VirtualDevice.State.LAUNCHING, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    CountDownLatchAssert.await(latch);

    assertEquals(TestVirtualDevices.pixel5Api31(myAvd), model.getValueAt(0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));

    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 0))));

    Mockito.verify(showErrorDialog).accept(throwable, null);
  }

  @Test
  public void setValueAtStopFailed() throws Exception {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    Mockito.when(myConnection.isAvdRunning(myAvd)).thenReturn(true);

    DeviceManagerAndroidDebugBridge bridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
    Mockito.when(bridge.findDevice(null, TestVirtualDevices.PIXEL_5_API_31_KEY)).thenReturn(Futures.immediateFuture(null));

    @SuppressWarnings("unchecked")
    BiConsumer<Throwable, Project> showErrorDialog = Mockito.mock(BiConsumer.class);

    TableModel model = new VirtualDeviceTableModel(null,
                                                   List.of(TestVirtualDevices.onlinePixel5Api31(myAvd)),
                                                   (m, key) -> newSetOnline(m, key, latch),
                                                   () -> myConnection,
                                                   SetAllOnline::new,
                                                   bridge,
                                                   EmulatorConsole::getConsole,
                                                   showErrorDialog);

    model.addTableModelListener(myListener);

    // Act
    model.setValueAt(VirtualDevice.State.STOPPING, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    CountDownLatchAssert.await(latch);

    assertEquals(TestVirtualDevices.onlinePixel5Api31(myAvd), model.getValueAt(0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));

    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 0))));

    ErrorDialogException exception = new ErrorDialogException(
      "Unable to stop Pixel 5 API 31",
      "An error occurred stopping Pixel 5 API 31. To stop the device, try manually closing the Pixel 5 API 31 emulator window.");

    ArgumentMatcher<Throwable> matcher = new ErrorDialogExceptionArgumentMatcher(exception);
    Mockito.verify(showErrorDialog).accept(ArgumentMatchers.argThat(matcher), ArgumentMatchers.isNull());
  }

  private static final class ErrorDialogExceptionArgumentMatcher implements ArgumentMatcher<Throwable> {
    private final @NotNull ErrorDialogException myExpected;

    private ErrorDialogExceptionArgumentMatcher(@NotNull ErrorDialogException expected) {
      myExpected = expected;
    }

    @Override
    public boolean matches(@NotNull Throwable actual) {
      if (!(actual instanceof ErrorDialogException)) {
        return false;
      }

      ErrorDialogException exception = (ErrorDialogException)actual;
      return myExpected.getTitle().equals(exception.getTitle()) && myExpected.getMessage().equals(exception.getMessage());
    }
  }

  @Test
  public void setValueAtStopSucceeded() throws Exception {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    ListenableFuture<IDevice> future = Futures.immediateFuture(Mockito.mock(IDevice.class));

    DeviceManagerAndroidDebugBridge bridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
    Mockito.when(bridge.findDevice(null, TestVirtualDevices.PIXEL_5_API_31_KEY)).thenReturn(future);

    EmulatorConsole console = Mockito.mock(EmulatorConsole.class);

    TableModel model = new VirtualDeviceTableModel(null,
                                                   List.of(TestVirtualDevices.onlinePixel5Api31(myAvd)),
                                                   VirtualDeviceTableModel::newSetOnline,
                                                   AvdManagerConnection::getDefaultAvdManagerConnection,
                                                   m -> newSetAllOnline(m, latch),
                                                   bridge,
                                                   device -> console,
                                                   VirtualTabMessages::showErrorDialog);

    model.addTableModelListener(myListener);

    // Act
    model.setValueAt(VirtualDevice.State.STOPPING, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    CountDownLatchAssert.await(latch);

    Object device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.PIXEL_5_API_31_KEY)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setState(VirtualDevice.State.STOPPING)
      .setAvdInfo(myAvd)
      .build();

    assertEquals(device, model.getValueAt(0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));

    InOrder inOrder = Mockito.inOrder(console);

    inOrder.verify(console).kill();
    inOrder.verify(console).close();
  }

  private static @NotNull FutureCallback<@Nullable Object> newSetAllOnline(@NotNull VirtualDeviceTableModel model,
                                                                           @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(new SetAllOnline(model), latch);
  }
}
