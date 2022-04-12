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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.DeviceTables;
import com.android.tools.idea.devicemanager.IconButton;
import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.android.tools.idea.devicemanager.virtualtab.LaunchInEmulatorButtonTableCellEditor.SetEnabled;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.LaunchInEmulatorValue;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import icons.StudioIcons;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class LaunchInEmulatorButtonTableCellEditorTest {
  private final @NotNull AvdManagerConnection myConnection = Mockito.mock(AvdManagerConnection.class);
  private final @NotNull AvdInfo myAvd = Mockito.mock(AvdInfo.class);

  private IconButtonTableCellEditor myEditor;

  @Test
  public void onSuccessDeviceIsOnline() throws InterruptedException {
    // Arrange
    Mockito.when(myConnection.stopAvdAsync(myAvd)).thenReturn(Futures.immediateVoidFuture());
    CountDownLatch latch = new CountDownLatch(1);
    CellEditorListener listener = Mockito.mock(CellEditorListener.class);

    myEditor = new LaunchInEmulatorButtonTableCellEditor(null, () -> myConnection, (editor) -> newSetEnabled(editor, latch));
    myEditor.addCellEditorListener(listener);

    JTable table = DeviceTables.mock(TestVirtualDevices.onlinePixel5Api31(myAvd));
    myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    AbstractButton button = myEditor.getButton();

    // Act
    button.doClick();

    // Assert
    CountDownLatchAssert.await(latch);

    assertTrue(button.isEnabled());
    Mockito.verify(listener).editingCanceled(myEditor.getChangeEvent());
  }

  @Test
  public void onSuccess() throws InterruptedException {
    // Arrange
    Mockito.when(myConnection.startAvd(null, myAvd)).thenReturn(Futures.immediateFuture(Mockito.mock(IDevice.class)));
    CountDownLatch latch = new CountDownLatch(1);
    CellEditorListener listener = Mockito.mock(CellEditorListener.class);
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);

    myEditor = new LaunchInEmulatorButtonTableCellEditor(null, () -> myConnection, (editor) -> newSetEnabled(editor, latch));
    myEditor.addCellEditorListener(listener);

    JTable table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(myAvd));
    myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    AbstractButton button = myEditor.getButton();

    // Act
    button.doClick();

    // Assert
    CountDownLatchAssert.await(latch);

    assertTrue(button.isEnabled());
    Mockito.verify(listener).editingCanceled(myEditor.getChangeEvent());
  }

  private static @NotNull FutureCallback<@Nullable Object> newSetEnabled(@NotNull LaunchInEmulatorButtonTableCellEditor editor,
                                                                         @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(new SetEnabled(editor), latch);
  }

  @Test
  public void getTableCellEditorComponentDeviceIsOnline() {
    // Arrange
    myEditor = new LaunchInEmulatorButtonTableCellEditor(null);
    JTable table = DeviceTables.mock(TestVirtualDevices.onlinePixel5Api31(myAvd));

    // Act
    Object component = myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    // Assert
    IconButton button = (IconButton)component;

    assertEquals(Optional.of(StudioIcons.Avd.STOP), button.getDefaultIcon());
    assertTrue(button.isEnabled());
    assertEquals("Stop the emulator running this AVD", button.getToolTipText());
  }

  @Test
  public void getTableCellEditorComponentStatusDoesntEqualOk() {
    // Arrange
    myEditor = new LaunchInEmulatorButtonTableCellEditor(null);
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.ERROR_PROPERTIES);

    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_5_API_31"))
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0")
      .setCpuArchitecture("arm")
      .setAndroidVersion(new AndroidVersion(31))
      .setAvdInfo(myAvd)
      .build();

    JTable table = DeviceTables.mock(device);

    // Act
    Object component = myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    // Assert
    IconButton button = (IconButton)component;

    assertEquals(Optional.of(StudioIcons.Avd.RUN), button.getDefaultIcon());
    assertFalse(button.isEnabled());
    assertEquals("Launch this AVD in the emulator", button.getToolTipText());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    myEditor = new LaunchInEmulatorButtonTableCellEditor(null);
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);
    JTable table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(myAvd));

    // Act
    Object component = myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    // Assert
    IconButton button = (IconButton)component;

    assertEquals(Optional.of(StudioIcons.Avd.RUN), button.getDefaultIcon());
    assertTrue(button.isEnabled());
    assertEquals("Launch this AVD in the emulator", button.getToolTipText());
  }
}
