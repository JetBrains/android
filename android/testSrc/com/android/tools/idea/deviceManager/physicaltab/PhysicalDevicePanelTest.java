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
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDevicePanelTest {
  private @Nullable PhysicalDevicePanel myPanel;

  @After
  public void disposeOfPanel() {
    assert myPanel != null;
    Disposer.dispose(myPanel);
  }

  @Test
  public void newPhysicalDevicePanel() {
    // Arrange
    Supplier<Disposable> newPhysicalDeviceChangeListener = () -> Mockito.mock(Disposable.class);

    PhysicalDevice device = new PhysicalDevice("86UX00F4R");

    PhysicalDeviceAsyncSupplier supplier = Mockito.mock(PhysicalDeviceAsyncSupplier.class);
    Mockito.when(supplier.get()).thenReturn(Futures.immediateFuture(Collections.singletonList(device)));

    Executor executor = MoreExecutors.directExecutor();

    // Act
    myPanel = new PhysicalDevicePanel(newPhysicalDeviceChangeListener, supplier, executor);

    // Assert
    assertEquals(Collections.singletonList(Arrays.asList(device, "API", "Type", "Actions")), myPanel.getData());
  }
}
