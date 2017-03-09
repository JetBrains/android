/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.SyncService;
import org.jetbrains.annotations.NotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MockDdmlibDevice {
  @NotNull private final IDevice myDevice;
  @NotNull private String myName = "[GenericMockDevice]";
  @NotNull private String mySerialNumber = "1234";
  @NotNull private IDevice.DeviceState myState = IDevice.DeviceState.ONLINE;
  @NotNull private TestShellCommands myShellCommands = new TestShellCommands();

  public MockDdmlibDevice() throws Exception {
    SyncService syncService = mock(SyncService.class);

    myDevice = mock(IDevice.class);

    doAnswer(invocation -> getState()).when(myDevice).getState();
    doAnswer(invocation -> getName()).when(myDevice).getName();
    doAnswer(invocation -> getSerialNumber()).when(myDevice).getSerialNumber();
    doAnswer(invocation -> syncService).when(myDevice).getSyncService();

    doAnswer(invocation -> {
      String command = invocation.getArgument(0);
      IShellOutputReceiver receiver = invocation.getArgument(1);
      getShellCommands().executeShellCommand(command, receiver);
      return null;
    }).when(myDevice).executeShellCommand(any(), any());
  }

  @NotNull
  public IDevice getIDevice() {
    return myDevice;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public MockDdmlibDevice setName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public String getSerialNumber() {
    return mySerialNumber;
  }

  @NotNull
  public MockDdmlibDevice setSerialNumber(@NotNull String serialNumber) {
    mySerialNumber = serialNumber;
    return this;
  }

  @NotNull
  public TestShellCommands getShellCommands() {
    return myShellCommands;
  }

  @NotNull
  public MockDdmlibDevice setShellCommands(@NotNull TestShellCommands shellCommands) {
    myShellCommands = shellCommands;
    return this;
  }

  @NotNull
  public IDevice.DeviceState getState() {
    return myState;
  }

  @NotNull
  public MockDdmlibDevice setState(@NotNull IDevice.DeviceState state) {
    myState = state;
    return this;
  }
}
