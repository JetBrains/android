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
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceWatcherTest {
  @Rule
  public TestRule myRule = new ApplicationRule();

  private AvdInfo myAvd;

  private AvdManager myAvdManager;
  private VirtualDeviceWatcherListener myListener;
  private CountDownLatch myLatch;
  private VirtualDeviceWatcher myWatcher;

  private IdeFrame myFrame;

  @Before
  public void initAvd() {
    myAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(myAvd.getId()).thenReturn(TestVirtualDevices.PIXEL_5_API_31_KEY.toString());
  }

  @Before
  public void initWatcher() {
    myAvdManager = Mockito.mock(AvdManager.class);

    myListener = Mockito.mock(VirtualDeviceWatcherListener.class);
    myLatch = new CountDownLatch(1);

    myWatcher = new VirtualDeviceWatcher(myAvdManager);
    myWatcher.addVirtualDeviceWatcherListener(new CountDownLatchVirtualDeviceWatcherListener(myListener, myLatch));
  }

  @Before
  public void initFrame() {
    myFrame = Mockito.mock(IdeFrame.class);
  }

  @Test
  public void addAvd() throws Exception {
    // Arrange
    Mockito.when(myAvdManager.getAllAvds())
      .thenReturn(new AvdInfo[0])
      .thenReturn(new AvdInfo[]{myAvd});

    // Act
    myWatcher.applicationDeactivated(myFrame);
    myWatcher.applicationActivated(myFrame);

    // Assert
    CountDownLatchAssert.await(myLatch);
    Mockito.verify(myListener).virtualDeviceAdded(Mockito.any());
  }

  @Test
  public void removeAvd() throws Exception {
    // Arrange
    Mockito.when(myAvdManager.getAllAvds())
      .thenReturn(new AvdInfo[]{myAvd})
      .thenReturn(new AvdInfo[0]);

    // Act
    myWatcher.applicationDeactivated(myFrame);
    myWatcher.applicationActivated(myFrame);

    // Assert
    CountDownLatchAssert.await(myLatch);
    Mockito.verify(myListener).virtualDeviceRemoved(Mockito.any());
  }

  @Test
  public void changeAvd() throws Exception {
    // Arrange
    AvdInfo changedAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(changedAvd.getId()).thenReturn(TestVirtualDevices.PIXEL_5_API_31_KEY.toString());

    Mockito.when(myAvdManager.getAllAvds())
      .thenReturn(new AvdInfo[]{myAvd})
      .thenReturn(new AvdInfo[]{changedAvd});

    // Act
    myWatcher.applicationDeactivated(myFrame);
    myWatcher.applicationActivated(myFrame);

    // Assert
    CountDownLatchAssert.await(myLatch);
    Mockito.verify(myListener).virtualDeviceChanged(Mockito.any());
  }

  private static final class CountDownLatchVirtualDeviceWatcherListener implements VirtualDeviceWatcherListener {
    private final @NotNull VirtualDeviceWatcherListener myDelegate;
    private final @NotNull CountDownLatch myLatch;

    private CountDownLatchVirtualDeviceWatcherListener(@NotNull VirtualDeviceWatcherListener delegate, @NotNull CountDownLatch latch) {
      myDelegate = delegate;
      myLatch = latch;
    }

    @Override
    public void virtualDeviceAdded(@NotNull VirtualDeviceWatcherEvent event) {
      myDelegate.virtualDeviceAdded(event);
      myLatch.countDown();
    }

    @Override
    public void virtualDeviceChanged(@NotNull VirtualDeviceWatcherEvent event) {
      myDelegate.virtualDeviceChanged(event);
      myLatch.countDown();
    }

    @Override
    public void virtualDeviceRemoved(@NotNull VirtualDeviceWatcherEvent event) {
      myDelegate.virtualDeviceRemoved(event);
      myLatch.countDown();
    }
  }
}
