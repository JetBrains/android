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

import static org.junit.Assert.assertEquals;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDevicePanelTest {
  private PhysicalDevicePanel myPanel;
  private Disposable myParent;
  private PhysicalTabPersistentStateComponent myComponent;
  private Disposable myListener;

  private PhysicalDevice myOnlinePixel3;
  private PhysicalDeviceAsyncSupplier mySupplier;

  private Executor myExecutor;

  @Before
  public void mockParent() {
    myParent = Mockito.mock(Disposable.class);
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
    myOnlinePixel3 = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .setLastOnlineTime(Instant.parse("2021-03-24T22:38:05.890570Z"))
      .setOnline(true)
      .build();

    mySupplier = Mockito.mock(PhysicalDeviceAsyncSupplier.class);
    Mockito.when(mySupplier.get()).thenReturn(Futures.immediateFuture(Collections.singletonList(myOnlinePixel3)));
  }

  @Before
  public void initExecutor() {
    myExecutor = MoreExecutors.directExecutor();
  }

  @After
  public void disposeOfParent() {
    Disposer.dispose(myParent);
  }

  @Test
  public void newPhysicalDevicePanel() {
    // Act
    myPanel = new PhysicalDevicePanel(myParent, () -> myComponent, model -> myListener, mySupplier, myExecutor);

    // Assert
    assertEquals(Collections.singletonList(Arrays.asList(myOnlinePixel3, "API", "Type", "Actions")), myPanel.getData());
  }

  @Test
  public void newPhysicalDevicePanelPersistentStateComponentSuppliesDevice() {
    // Arrange
    PhysicalDevice offlinePixel5 = new PhysicalDevice.Builder()
      .setSerialNumber("0A071FDD4003ZG")
      .build();

    myComponent.set(Collections.singletonList(offlinePixel5));

    // Act
    myPanel = new PhysicalDevicePanel(myParent, () -> myComponent, model -> myListener, mySupplier, myExecutor);

    // Assert
    Object data = Arrays.asList(
      Arrays.asList(myOnlinePixel3, "API", "Type", "Actions"),
      Arrays.asList(offlinePixel5, "API", "Type", "Actions"));

    assertEquals(data, myPanel.getData());
  }
}
