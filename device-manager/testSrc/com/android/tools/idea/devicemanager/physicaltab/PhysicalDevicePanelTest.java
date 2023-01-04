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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService;
import com.android.tools.idea.adb.wireless.WiFiPairingController;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.ConnectionType;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.android.tools.idea.devicemanager.TestTables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.DisposableRule;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDevicePanelTest {
  @Rule
  public final DisposableRule myDisposableRule = new DisposableRule();
  private PhysicalDevicePanel myPanel;
  private Project myProject;
  private Disposable myParent;
  private PairDevicesUsingWiFiService myService;
  private WearPairingManager myManager;
  private PhysicalTabPersistentStateComponent myComponent;
  private final Disposable myListener = myDisposableRule.getDisposable();
  private PhysicalDeviceAsyncSupplier mySupplier;

  private CountDownLatch myLatch;

  @Before
  public void mockProject() {
    myProject = Mockito.mock(Project.class);
  }

  @Before
  public void initParent() {
    myParent = Disposer.newDisposable("PhysicalDevicePanelTest");
  }

  @Before
  public void mockService() {
    myService = Mockito.mock(PairDevicesUsingWiFiService.class);
  }

  @Before
  public void mockManager() {
    myManager = Mockito.mock(WearPairingManager.class);
  }

  @Before
  public void initComponent() {
    myComponent = new PhysicalTabPersistentStateComponent();
  }

  @Before
  public void mockSupplier() {
    mySupplier = Mockito.mock(PhysicalDeviceAsyncSupplier.class);
    Mockito.when(mySupplier.get()).thenReturn(Futures.immediateFuture(List.of(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3)));
  }

  @Before
  public void initLatch() {
    myLatch = new CountDownLatch(1);
  }

  @After
  public void disposeOfParent() {
    Disposer.dispose(myParent);
  }

  @Test
  public void newPhysicalDevicePanel() throws InterruptedException {
    // Act
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      myManager,
                                      () -> myComponent,
                                      model -> myListener,
                                      PhysicalDeviceDetailsPanel::new,
                                      mySupplier,
                                      this::newSetDevices);

    // Assert
    CountDownLatchAssert.await(myLatch);

    Object data = List.of(List.of(DeviceType.PHONE,
                                  TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3,
                                  new AndroidVersion(31),
                                  ConnectionType.USB_SET,
                                  ActivateDeviceFileExplorerWindowValue.INSTANCE,
                                  RemoveValue.INSTANCE,
                                  PopUpMenuValue.INSTANCE));

    assertEquals(data, TestTables.getData(myPanel.getTable()));
  }

  @Test
  public void newPhysicalDevicePanelPersistentStateComponentSuppliesDevice() throws InterruptedException {
    // Arrange
    myComponent.set(List.of(TestPhysicalDevices.GOOGLE_PIXEL_5));

    // Act
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      myManager,
                                      () -> myComponent,
                                      model -> myListener,
                                      PhysicalDeviceDetailsPanel::new,
                                      mySupplier,
                                      this::newSetDevices);

    // Assert
    CountDownLatchAssert.await(myLatch);

    Object data = List.of(List.of(DeviceType.PHONE,
                                  TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3,
                                  new AndroidVersion(31),
                                  ConnectionType.USB_SET,
                                  ActivateDeviceFileExplorerWindowValue.INSTANCE,
                                  RemoveValue.INSTANCE,
                                  PopUpMenuValue.INSTANCE),
                          List.of(DeviceType.PHONE,
                                  TestPhysicalDevices.GOOGLE_PIXEL_5,
                                  new AndroidVersion(30),
                                  Set.of(),
                                  ActivateDeviceFileExplorerWindowValue.INSTANCE,
                                  RemoveValue.INSTANCE,
                                  PopUpMenuValue.INSTANCE));

    assertEquals(data, TestTables.getData(myPanel.getTable()));
  }

  private @NotNull FutureCallback<List<PhysicalDevice>> newSetDevices(@NotNull PhysicalDevicePanel panel) {
    return new CountDownLatchFutureCallback<>(PhysicalDevicePanel.newSetDevices(panel), myLatch);
  }

  @Test
  public void initPairUsingWiFiButtonFeatureIsntEnabled() {
    // Act
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      myManager,
                                      () -> myComponent,
                                      model -> myListener,
                                      PhysicalDeviceDetailsPanel::new,
                                      mySupplier,
                                      PhysicalDevicePanel::newSetDevices);

    // Assert
    assertNull(myPanel.getPairUsingWiFiButton());
  }

  @Test
  public void initPairUsingWiFiButton() {
    // Arrange
    WiFiPairingController controller = Mockito.mock(WiFiPairingController.class);

    Mockito.when(myService.isFeatureEnabled()).thenReturn(true);
    Mockito.when(myService.createPairingDialogController()).thenReturn(controller);

    // Act
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      myManager,
                                      () -> myComponent,
                                      model -> myListener,
                                      PhysicalDeviceDetailsPanel::new,
                                      mySupplier,
                                      PhysicalDevicePanel::newSetDevices);

    AbstractButton button = myPanel.getPairUsingWiFiButton();
    assert button != null;

    button.doClick();

    // Assert
    assertEquals("Pair using Wi-Fi", button.getText());
    Mockito.verify(controller).showDialog();
  }

  @Test
  public void newDetailsPanelDoesntThrowAssertionError() throws InterruptedException {
    // Arrange
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      myManager,
                                      () -> myComponent,
                                      model -> myListener,
                                      (device, project) -> newPhysicalDeviceDetailsPanel(device),
                                      mySupplier,
                                      this::newSetDevices);

    CountDownLatchAssert.await(myLatch);

    PhysicalDeviceTable table = myPanel.getTable();

    table.setRowSelectionInterval(0, 0);
    myPanel.viewDetails();

    // Act
    table.getModel().addOrSet(TestPhysicalDevices.GOOGLE_PIXEL_3);
  }

  @Test
  public void viewDetails() throws InterruptedException {
    // Arrange
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      myManager,
                                      () -> myComponent,
                                      model -> myListener,
                                      (device, project) -> newPhysicalDeviceDetailsPanel(device),
                                      mySupplier,
                                      this::newSetDevices);

    CountDownLatchAssert.await(myLatch);

    // Act
    myPanel.getTable().setRowSelectionInterval(0, 0);
    myPanel.viewDetails();

    // Assert
    assertTrue(myPanel.hasDetails());
  }

  @NotNull
  private DetailsPanel newPhysicalDeviceDetailsPanel(@NotNull PhysicalDevice device) {
    AsyncDetailsBuilder builder = Mockito.mock(AsyncDetailsBuilder.class);
    Mockito.when(builder.buildAsync()).thenReturn(Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3));

    return new PhysicalDeviceDetailsPanel(device, builder, myManager);
  }
}
