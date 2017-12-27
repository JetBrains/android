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

import com.android.ddmlib.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MockDdmlibDevice {
  @NotNull private static final Logger LOGGER = Logger.getInstance(MockDdmlibDevice.class);

  @NotNull private final IDevice myDevice;
  @NotNull private final MockSyncService myMockSyncService;
  @NotNull private String myName = "[GenericMockDevice]";
  @NotNull private String mySerialNumber = "1234";
  @NotNull private IDevice.DeviceState myState = IDevice.DeviceState.ONLINE;
  @NotNull private TestShellCommands myShellCommands = new TestShellCommands();
  @NotNull private Map<String, Long> myRemoteFiles = new HashMap<>();
  @NotNull private Map<String, Long> myRemoteRestrictedAccessFiles = new HashMap<>();

  public MockDdmlibDevice() throws Exception {
    myMockSyncService = new MockSyncService();

    myDevice = mock(IDevice.class);

    doAnswer(invocation -> getState()).when(myDevice).getState();
    doAnswer(invocation -> getName()).when(myDevice).getName();
    doAnswer(invocation -> getSerialNumber()).when(myDevice).getSerialNumber();
    doAnswer(invocation -> myMockSyncService.mySyncService).when(myDevice).getSyncService();

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

  public MockDdmlibDevice addRemoteFile(@NotNull String path, long size) {
    myRemoteFiles.put(path, size);
    return this;
  }

  public MockDdmlibDevice addRemoteRestrictedAccessFile(@NotNull String path, long size) {
    myRemoteRestrictedAccessFiles.put(path, size);
    return this;
  }

  public class MockSyncService {
    @NotNull private final SyncService mySyncService;

    public MockSyncService() throws Exception {
      mySyncService = mock(SyncService.class);

      doAnswer(invocation -> {
        String local = invocation.getArgument(0);
        String remote = invocation.getArgument(1);
        SyncService.ISyncProgressMonitor monitor = invocation.getArgument(2);
        pushFile(local, remote, monitor);
        return null;
      }).when(mySyncService).pushFile(anyString(), anyString(), any());

      doAnswer(invocation -> {
        String remote = invocation.getArgument(0);
        String local = invocation.getArgument(1);
        SyncService.ISyncProgressMonitor monitor = invocation.getArgument(2);
        pullFile(remote, local, monitor);
        return null;
      }).when(mySyncService).pullFile(anyString(), anyString(), any());

      doAnswer(invocation -> {
        FileListingService.FileEntry remote = invocation.getArgument(0);
        String local = invocation.getArgument(1);
        SyncService.ISyncProgressMonitor monitor = invocation.getArgument(2);
        pullFile(remote.getFullPath(), local, monitor);
        return null;
      }).when(mySyncService).pullFile(any(FileListingService.FileEntry.class), anyString(), any());
    }

    private void pushFile(String local, String remote, SyncService.ISyncProgressMonitor monitor)
      throws SyncException, IOException, TimeoutException {
      LOGGER.info(String.format("pushFile: \"%s\" -> \"%s\"", local, remote));
      // Pushing to system protected files is not allowed
      if (myRemoteRestrictedAccessFiles.containsKey(remote)) {
        throw new SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR);
      }
      long size = new File(local).length();
      monitor.start(0);
      int chunkSize = 1024;
      for (long i = 0; i < size; i += chunkSize) {
        monitor.advance(chunkSize);
      }
      monitor.stop();
      addRemoteFile(remote, size);
    }

    private void pullFile(String remote, String local, SyncService.ISyncProgressMonitor monitor)
      throws TimeoutException, IOException, SyncException {
      LOGGER.info(String.format("pullFile: \"%s\" -> \"%s\"", remote, local));
      // Pulling system protected files returns a specific error
      if (myRemoteRestrictedAccessFiles.containsKey(remote)) {
        throw new SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR);
      }
      if(!myRemoteFiles.containsKey(remote)) {
        throw new SyncException(SyncException.SyncError.NO_REMOTE_OBJECT);
      }
      long size = myRemoteFiles.get(remote);
      monitor.start((int)size);
      int chunkSize = 1024;
      byte[] chunk = new byte[chunkSize];
      File localFile = new File(local);
      OutputStream outputStream = Files.newOutputStream(localFile.toPath());
      for (long i = 0; i < size; i += chunkSize) {
        int actualSize = (int)Math.min(chunkSize, size - i);
        outputStream.write(chunk, 0, actualSize);
        monitor.advance(actualSize);
      }
      monitor.advance(chunkSize);
      monitor.stop();
    }
  }
}