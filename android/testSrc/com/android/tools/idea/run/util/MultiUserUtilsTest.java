/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.util;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Charsets;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

public class MultiUserUtilsTest {
  @Test
  public void userIdExtractionFromAmFlags() {
    assertEquals(MultiUserUtils.PRIMARY_USERID, MultiUserUtils.getUserIdFromAmParameters(""));
    assertEquals(MultiUserUtils.PRIMARY_USERID, MultiUserUtils.getUserIdFromAmParameters(" --user"));
    assertEquals(MultiUserUtils.PRIMARY_USERID, MultiUserUtils.getUserIdFromAmParameters(" --user   0"));
    assertEquals(20, MultiUserUtils.getUserIdFromAmParameters(" --user  20"));
  }

  @Test
  public void getCurrentUser() throws Exception {
    IDevice device = createCurrentUserMockDevice("10");
    assertFalse(MultiUserUtils.isCurrentUserThePrimaryUser(device, 200, TimeUnit.MILLISECONDS, true));

    device = createCurrentUserMockDevice("0");
    assertTrue(MultiUserUtils.isCurrentUserThePrimaryUser(device, 200, TimeUnit.MILLISECONDS, false));
  }

  @Test
  public void hasMultipleUsers() throws Exception {
   String[] pmOutput = new String[]{
      "Users:",
      "              UserInfo{0:User0:13} running",
      "              UserInfo{11:Sample Managed Profile:30} running"
    };

    IDevice device = createListUsersMockDevice(pmOutput);
    assertTrue(MultiUserUtils.hasMultipleUsers(device, 200, TimeUnit.MILLISECONDS, false));

    pmOutput = new String[]{
      "Users:",
      "              UserInfo{0:User0:13} running"
    };

    device = createListUsersMockDevice(pmOutput);
    assertFalse(MultiUserUtils.hasMultipleUsers(device, 200, TimeUnit.MILLISECONDS, true));

    // Some versions of the Android (e.g. emulator running x86, API 19) have this additional error message
    pmOutput = new String[]{
      "WARNING: linker: libdvm.so has text relocations. This is wasting memory and is a security risk. Please fix.",
      "Users:",
      "\tUserInfo{0:Owner:13}"
    };

    device = createListUsersMockDevice(pmOutput);
    assertFalse(MultiUserUtils.hasMultipleUsers(device, 200, TimeUnit.MILLISECONDS, true));
  }

  @NotNull
  private static IDevice createCurrentUserMockDevice(@NotNull String amCurrentUserOutput) throws Exception {
    IDevice device = mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(23, null));

    doAnswer(invocation -> {
      String cmd = (String)invocation.getArguments()[0];
      if (!cmd.equals("am get-current-user")) {
        fail(String.format("This mock device only supports the '%s' shell command)", cmd));
      }

      byte[] bytes = amCurrentUserOutput.getBytes(Charsets.UTF_8);

      IShellOutputReceiver receiver = (IShellOutputReceiver)invocation.getArguments()[1];
      receiver.addOutput(bytes, 0, bytes.length);
      receiver.flush();

      return null;
    }).when(device).executeShellCommand(anyString(), any(IShellOutputReceiver.class));

    return device;
  }

  @NotNull
  private static IDevice createListUsersMockDevice(@NotNull String[] pmListUsersOutput) throws Exception {
    IDevice device = mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(23, null));

    doAnswer(invocation -> {
      String cmd = (String)invocation.getArguments()[0];
      if (!cmd.equals("pm list users")) {
        fail(String.format("This mock device only supports the '%s' shell command)", cmd));
      }

      MultiUserUtils.PmListUserReceiver receiver = (MultiUserUtils.PmListUserReceiver)invocation.getArguments()[1];
      receiver.processNewLines(pmListUsersOutput);
      return null;
    }).when(device).executeShellCommand(anyString(), any(IShellOutputReceiver.class), anyLong(), any(TimeUnit.class));

    return device;
  }
}
