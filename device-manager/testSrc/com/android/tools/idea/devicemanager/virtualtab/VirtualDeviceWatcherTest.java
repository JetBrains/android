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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.testFramework.ApplicationRule;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceWatcherTest {
  @Rule
  public final TestRule myRule = new ApplicationRule();

  @Test
  public void applicationActivatedLoadsAvds() throws Exception {
    // Arrange
    AvdInfo avd = Mockito.mock(AvdInfo.class);

    AvdManager manager = Mockito.mock(AvdManager.class);
    Mockito.when(manager.getAllAvds()).thenReturn(new AvdInfo[]{avd});

    VirtualDeviceWatcherListener listener = Mockito.mock(VirtualDeviceWatcherListener.class);
    CountDownLatch latch = new CountDownLatch(1);

    VirtualDeviceWatcher watcher = new VirtualDeviceWatcher(() -> Optional.of(manager));
    watcher.addVirtualDeviceWatcherListener(new CountDownLatchVirtualDeviceWatcherListener(listener, latch));

    IdeFrame frame = Mockito.mock(IdeFrame.class);

    // Act
    watcher.applicationActivated(frame);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(listener).virtualDevicesChanged(new VirtualDeviceWatcherEvent(watcher, List.of(avd)));
  }

  private static final class CountDownLatchVirtualDeviceWatcherListener implements VirtualDeviceWatcherListener {
    private final @NotNull VirtualDeviceWatcherListener myDelegate;
    private final @NotNull CountDownLatch myLatch;

    private CountDownLatchVirtualDeviceWatcherListener(@NotNull VirtualDeviceWatcherListener delegate, @NotNull CountDownLatch latch) {
      myDelegate = delegate;
      myLatch = latch;
    }

    @Override
    public void virtualDevicesChanged(@NotNull VirtualDeviceWatcherEvent event) {
      myDelegate.virtualDevicesChanged(event);
      myLatch.countDown();
    }
  }
}
