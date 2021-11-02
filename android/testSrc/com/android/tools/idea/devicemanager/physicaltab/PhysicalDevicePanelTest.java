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

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService;
import com.android.tools.idea.adb.wireless.WiFiPairingController;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevicePanel.SetDevices;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.PopUpMenuValue;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDevicePanelTest {
  private PhysicalDevicePanel myPanel;
  private Project myProject;
  private Disposable myParent;
  private PairDevicesUsingWiFiService myService;
  private PhysicalTabPersistentStateComponent myComponent;
  private Disposable myListener;
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
  public void initComponent() {
    myComponent = new PhysicalTabPersistentStateComponent();
  }

  @Before
  public void mockListener() {
    myListener = Mockito.mock(Disposable.class);
  }

  @Before
  public void mockSupplier() {
    List<PhysicalDevice> devices = Collections.singletonList(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3);

    mySupplier = Mockito.mock(PhysicalDeviceAsyncSupplier.class);
    Mockito.when(mySupplier.get()).thenReturn(Futures.immediateFuture(devices));
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
                                      () -> myComponent,
                                      model -> myListener,
                                      panel -> new PhysicalDeviceTable(panel, new PhysicalDeviceTableModel()),
                                      mySupplier,
                                      this::newSetDevices);

    // Assert
    CountDownLatchAssert.await(myLatch, Duration.ofMillis(128));

    Object data = Collections.singletonList(Arrays.asList(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3,
                                                          "31",
                                                          "USB",
                                                          ActivateDeviceFileExplorerWindowValue.INSTANCE,
                                                          RemoveValue.INSTANCE,
                                                          PopUpMenuValue.INSTANCE));

    assertEquals(data, myPanel.getTable().getData());
  }

  @Test
  public void newPhysicalDevicePanelPersistentStateComponentSuppliesDevice() throws InterruptedException {
    // Arrange
    myComponent.set(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_5));

    // Act
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      () -> myComponent,
                                      model -> myListener,
                                      panel -> new PhysicalDeviceTable(panel, new PhysicalDeviceTableModel()),
                                      mySupplier,
                                      this::newSetDevices);

    // Assert
    CountDownLatchAssert.await(myLatch, Duration.ofMillis(128));

    Object data = Arrays.asList(Arrays.asList(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3,
                                              "31",
                                              "USB",
                                              ActivateDeviceFileExplorerWindowValue.INSTANCE,
                                              RemoveValue.INSTANCE,
                                              PopUpMenuValue.INSTANCE),
                                Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_5,
                                              "30",
                                              "",
                                              ActivateDeviceFileExplorerWindowValue.INSTANCE,
                                              RemoveValue.INSTANCE,
                                              PopUpMenuValue.INSTANCE));

    assertEquals(data, myPanel.getTable().getData());
  }

  private @NotNull FutureCallback<@Nullable List<@NotNull PhysicalDevice>> newSetDevices(@NotNull PhysicalDevicePanel panel) {
    return new CountDownLatchFutureCallback<>(new SetDevices(panel), myLatch);
  }

  @Test
  public void initPairUsingWiFiButtonFeatureIsntEnabled() {
    // Act
    myPanel = new PhysicalDevicePanel(myProject,
                                      myParent,
                                      project -> myService,
                                      () -> myComponent,
                                      model -> myListener,
                                      PhysicalDeviceTable::new,
                                      mySupplier,
                                      SetDevices::new);

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
                                      () -> myComponent,
                                      model -> myListener,
                                      PhysicalDeviceTable::new,
                                      mySupplier,
                                      SetDevices::new);

    AbstractButton button = myPanel.getPairUsingWiFiButton();
    assert button != null;

    button.doClick();

    // Assert
    assertEquals("Pair using Wi-Fi", button.getText());
    Mockito.verify(controller).showDialog();
  }
}
