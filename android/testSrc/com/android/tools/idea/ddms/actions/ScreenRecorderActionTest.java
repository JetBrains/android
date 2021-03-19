/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ddms.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.ScreenRecorderOptions;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class ScreenRecorderActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private DeviceContext myContext;
  private Features myFeatures;

  private Presentation myPresentation;
  private AnActionEvent myEvent;

  @Before
  public void mockContext() {
    myContext = Mockito.mock(DeviceContext.class);
  }

  @Before
  public void mockFeatures() {
    myFeatures = Mockito.mock(Features.class);
  }

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Test
  public void updateIsntEnabled() {
    new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures).update(myEvent);

    assertFalse(myPresentation.isEnabled());
    assertEquals("Screen Record", myPresentation.getText());
  }

  @Test
  public void updateDeviceIsWatch() {
    IDevice device = Mockito.mock(IDevice.class);
    Mockito.when(device.isOnline()).thenReturn(true);

    Mockito.when(myContext.getSelectedDevice()).thenReturn(device);
    Mockito.when(myFeatures.watch(device)).thenReturn(true);

    new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures).update(myEvent);

    assertFalse(myPresentation.isEnabled());
    assertEquals("Screen Record Is Unavailable for Wear OS", myPresentation.getText());
  }

  @Test
  public void updateDeviceDoesntHaveScreenRecord() {
    IDevice device = Mockito.mock(IDevice.class);
    Mockito.when(device.isOnline()).thenReturn(true);

    Mockito.when(myContext.getSelectedDevice()).thenReturn(device);

    new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures).update(myEvent);

    assertFalse(myPresentation.isEnabled());
    assertEquals("Screen Record", myPresentation.getText());
  }

  @Test
  public void updateDeviceHasScreenRecord() {
    IDevice device = Mockito.mock(IDevice.class);
    Mockito.when(device.isOnline()).thenReturn(true);

    Mockito.when(myContext.getSelectedDevice()).thenReturn(device);
    Mockito.when(myFeatures.screenRecord(device)).thenReturn(true);

    new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures).update(myEvent);

    assertTrue(myPresentation.isEnabled());
    assertEquals("Screen Record", myPresentation.getText());
  }

  @Test
  public void getTemporaryVideoPathDeviceDoesntHaveScreenRecord() {
    ScreenRecorderAction action = new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures);
    IDevice device = Mockito.mock(IDevice.class);
    AvdManager manager = Mockito.mock(AvdManager.class);

    Object path = action.getTemporaryVideoPathForVirtualDevice(device, manager);

    assertNull(path);
  }

  @Test
  public void getTemporaryVideoPathForVirtualDeviceVirtualDeviceIsNull() {
    ScreenRecorderAction action = new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures);
    IDevice device = Mockito.mock(IDevice.class);
    AvdManager manager = Mockito.mock(AvdManager.class);

    Mockito.when(myFeatures.screenRecord(device)).thenReturn(true);

    Object path = action.getTemporaryVideoPathForVirtualDevice(device, manager);

    assertNull(path);
  }

  @Test
  public void getTemporaryVideoPathForVirtualDevice() {
    ScreenRecorderAction action = new ScreenRecorderAction(myRule.getProject(), myContext, myFeatures);
    IDevice device = Mockito.mock(IDevice.class);
    AvdManager manager = Mockito.mock(AvdManager.class);

    AvdInfo virtualDevice = new AvdInfo(
      "Pixel_2_XL_API_28",
      new File("/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_28.ini"),
      "/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_28.avd",
      Mockito.mock(ISystemImage.class),
      null);

    Mockito.when(myFeatures.screenRecord(device)).thenReturn(true);
    Mockito.when(manager.getAvd(device.getAvdName(), true)).thenReturn(virtualDevice);

    Object path = action.getTemporaryVideoPathForVirtualDevice(device, manager);

    assertEquals(Paths.get("/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_28.avd/tmp.webm"), path);
  }

  @Test
  public void getEmulatorScreenRecorderOptions() {
    Path path = Jimfs.newFileSystem().getPath("sdcard", "1.mp4");
    String expectedPath = path.toString();
    assert expectedPath.equals("sdcard" + File.separator + "1.mp4");

    ScreenRecorderOptions options = new ScreenRecorderOptions.Builder()
      .setBitRate(6)
      .setSize(600, 400)
      .build();

    assertEquals("--size 600x400 --bit-rate 6000000 " + expectedPath,
                 ScreenRecorderTask.getEmulatorScreenRecorderOptions(path, options));

    options = new ScreenRecorderOptions.Builder()
      .setTimeLimit(100, TimeUnit.SECONDS)
      .build();

    assertEquals("--time-limit 100 " + expectedPath, ScreenRecorderTask.getEmulatorScreenRecorderOptions(path, options));

    options = new ScreenRecorderOptions.Builder()
      .setTimeLimit(4, TimeUnit.MINUTES)
      .build();

    assertEquals("--time-limit 180 " + expectedPath, ScreenRecorderTask.getEmulatorScreenRecorderOptions(path, options));
  }
}
