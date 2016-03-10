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
package com.android.tools.idea.monitor.profilerclient;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.monitor.profilerclient.MessageHeader.*;

public class ProfilerClient implements DeviceContext.DeviceSelectionListener, Disposable {
  private static final int LOCAL_PROFILER_CLIENT_PORT = 46623;
  private static final int DESTINATION_PORT = 5044;

  private static final Logger LOG = Logger.getInstance(ProfilerClient.class.getCanonicalName());

  @Nullable private Project myProject;
  @Nullable private IDevice myLastDevice;
  @Nullable private Client myLastClient;
  @Nullable private ClientProcess myClientProcess;
  @Nullable private CountDownLatch myClientProcessLatch;

  public ProfilerClient(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    myProject = project;
    deviceContext.addListener(this, project);
    Disposer.register(project, this);
  }

  @Override
  public void dispose() {
    try {
      stopClientProcess();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public synchronized void deviceSelected(@Nullable IDevice device) {
    if (myLastDevice != null && (device != myLastDevice || myLastDevice.getState() == IDevice.DeviceState.DISCONNECTED)) {
      try {
        // Attempt to remove the port forward.
        myLastDevice.removeForward(LOCAL_PROFILER_CLIENT_PORT, DESTINATION_PORT);
      }
      catch (TimeoutException e) {
        LOG.info("Timed out while attempting to remove port forward", e);
      }
      catch (AdbCommandRejectedException e) {
        LOG.info("ADB command error out while attempting to remove port forward", e);
      }
      catch (IOException e) {
        LOG.info("IO error while attempting to remove port forward", e);
      }
    }
    myLastDevice = null;

    if (device != null) {
      try {
        device.createForward(LOCAL_PROFILER_CLIENT_PORT, DESTINATION_PORT);
        myLastDevice = device;
      }
      catch (TimeoutException e) {
        LOG.error("Timed out while attempting to create port forward", e);
      }
      catch (AdbCommandRejectedException e) {
        LOG.error("ADB command error while attempting to create port forward", e);
      }
      catch (IOException e) {
        LOG.error("IO error while attempting to create port forward", e);
      }
    }
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    // Ignored. We're not interested in device state changes.
  }

  @Override
  public synchronized void clientSelected(@Nullable final Client c) {
    //TODO add functionality to detect dead but lingering clients
    if (c == myLastClient) {
      return;
    }

    try {
      stopClientProcess();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    myLastClient = c;
    if (c == null) {
      return;
    }

    myClientProcessLatch = new CountDownLatch(1);
    myClientProcess = new ClientProcess(myClientProcessLatch);
    myClientProcess.start();
  }

  private synchronized void stopClientProcess() throws InterruptedException {
    if (myClientProcess != null) {
      assert myClientProcessLatch != null;
      myClientProcessLatch.countDown();
      myClientProcess.interrupt();
      try {
        myClientProcess.join();
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
      finally {
        myClientProcess = null;
      }
    }
  }

  private static class ClientProcess extends Thread {
    private enum ConnectionState {
      ERR_TERMINATE_CONNECTION,
      UNINITIALIZED,
      DISCONNECTED,
      CONNECTED,
      OPERATIONAL
    }

    private static final short DEFAULT_ID = (short)0;
    private static final short NO_REMAINING_CHUNKS = (short)0;

    private static final byte PROTOCOL_MESSAGE_TYPE = (byte)0;
    private static final short HANDSHAKE_SUBTYPE = (short)0;
    private static final short HEARTBEAT_SUBTYPE = (short)1;

    private static final byte NETWORK_TYPE = (byte)3;
    private static final short DATA_SUBTYPE = (short)0;

    private static final MessageHeader EXPECTED_HANDSHAKE_RESPONSE =
      new MessageHeader(MESSAGE_HEADER_LENGTH + 1, DEFAULT_ID, NO_REMAINING_CHUNKS, RESPONSE_FLAG, PROTOCOL_MESSAGE_TYPE,
                        HANDSHAKE_SUBTYPE); // +1 to length for the response from the server.

    private static final int PROFILER_VERSION = 0;

    private static final long INITIALIZE_CONNECTION_RETRY_TIME_MS = 500L;
    private static final long HANDSHAKE_WAIT_TIME_MS = 100L;

    private static final long TIME_BETWEEN_PINGS_NS = TimeUnit.NANOSECONDS.convert(2L, TimeUnit.SECONDS);
    private static final long HANDSHAKE_TIMEOUT_NS = TimeUnit.NANOSECONDS.convert(60L, TimeUnit.SECONDS);
    private static final long FRAME_TIME_60_FPS_NS = TimeUnit.NANOSECONDS.convert(1L, TimeUnit.SECONDS) / 60;

    @NotNull private CountDownLatch myCountDownLatch;
    @NotNull private ByteBuffer myOutputBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN);
    @NotNull private ByteBuffer myInputBuffer = ByteBuffer.allocateDirect(16 * 1024).order(ByteOrder.LITTLE_ENDIAN);
    private SocketChannel mySocketChannel;

    private ConnectionState myConnectionState = ConnectionState.UNINITIALIZED;
    private short myCurrentHeartbeatId = 0;
    private short myLastHeartbeatId = myCurrentHeartbeatId;

    private ClientProcess(@NotNull CountDownLatch latch) {
      super("ProfilerClient.ClientProcess");
      myCountDownLatch = latch;
    }

    @Override
    public void run() {
      LOG.info("Starting ProfilerClient.ClientProcess");
      try {
        while (myCountDownLatch.getCount() > 0) {
          try {
            initializeConnection();
            processMessages();
          }
          catch (UnknownHostException e) {
            LOG.error(e);
            break;
          }
          catch (IOException e) {
            LOG.error(e);
          }
          catch (InterruptedException e) {
            LOG.info("Connection interrupted", e);
            Thread.currentThread().interrupt();
            break;
          }
          catch (RuntimeException e) {
            LOG.error("Error in ProfilerClient.ClientProcess", e);
            break;
          }
          finally {
            closeConnection();
          }
        }
      }
      finally {
        myConnectionState = ConnectionState.UNINITIALIZED;
        LOG.info("Exiting ProfilerClient.ClientProcess");
      }
    }

    private void flushOutputBuffer() throws IOException {
      myOutputBuffer.flip();
      while (myOutputBuffer.remaining() > 0) {
        mySocketChannel.write(myOutputBuffer);
      }
      myOutputBuffer.clear();
    }

    private void initializeChannel() throws IOException {
      closeConnection();

      mySocketChannel = SocketChannel.open();
      mySocketChannel.socket().setReuseAddress(true);
      mySocketChannel.socket().setTcpNoDelay(true);
      mySocketChannel.configureBlocking(false);

      myConnectionState = ConnectionState.DISCONNECTED;
    }

    private boolean connect(@NotNull InetSocketAddress addr) throws InterruptedException {
      try {
        if (!mySocketChannel.isConnectionPending()) {
          // Ignore the return value and just call finishConnect() on the SocketChannel.
          // Let the caller perform the appropriate wait before calling ProfilerClient.connect() again.
          mySocketChannel.connect(addr);
        }
        if (mySocketChannel.finishConnect()) {
          myConnectionState = ConnectionState.CONNECTED;
          return true;
        }
      }
      catch (IOException e) {
        myConnectionState = ConnectionState.ERR_TERMINATE_CONNECTION;
        LOG.error("Could not create connection to server. Exiting process.", e);
      }
      catch (RuntimeException e) {
        LOG.error(e);
      }
      return false;
    }

    private void performHandshake() throws InterruptedException {
      try {
        myOutputBuffer.clear();
        myInputBuffer.clear();

        if (initiateHandshake()) {
          if (verifyHandshake()) {
            myConnectionState = ConnectionState.OPERATIONAL;
            LOG.info("Handshake completed");
          }
          else {
            myConnectionState = ConnectionState.ERR_TERMINATE_CONNECTION;
            LOG.info("Version mismatch between client and server");
          }
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    private boolean initiateHandshake() {
      assert myOutputBuffer.position() == 0;
      writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH + 4, DEFAULT_ID, NO_REMAINING_CHUNKS, NO_FLAGS, PROTOCOL_MESSAGE_TYPE,
                    HANDSHAKE_SUBTYPE)
        .putInt(PROFILER_VERSION);

      try {
        flushOutputBuffer();
        LOG.info("Sent handshake");
      }
      catch (IOException e) {
        myConnectionState = ConnectionState.DISCONNECTED;
        return false;
      }
      catch (NotYetConnectedException e) {
        myConnectionState = ConnectionState.DISCONNECTED;
        return false;
      }
      return true;
    }

    private boolean verifyHandshake() throws IOException, InterruptedException {
      LOG.info("Verifying handshake");
      long startTime = System.nanoTime();
      while (System.nanoTime() < startTime + HANDSHAKE_TIMEOUT_NS) {
        try {
          mySocketChannel.read(myInputBuffer);
        }
        catch (SocketTimeoutException e) {
          continue;
        }
        catch (IOException e) {
          myConnectionState = ConnectionState.UNINITIALIZED;
          throw e;
        }
        myInputBuffer.flip();
        if (myInputBuffer.remaining() >= EXPECTED_HANDSHAKE_RESPONSE.length) {
          boolean valid = EXPECTED_HANDSHAKE_RESPONSE.equals(new MessageHeader(myInputBuffer)) && myInputBuffer.get() == 0;
          myInputBuffer.compact();
          return valid;
        }
        myInputBuffer.compact();

        myCountDownLatch.await(HANDSHAKE_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
      }

      LOG.info("Timed out waiting for handshake.");
      myConnectionState = ConnectionState.UNINITIALIZED;
      throw new IOException("Timed out waiting for handshake.");
    }

    private void initializeConnection() throws IOException, InterruptedException {
      LOG.info("Initializing Connection");
      assert myConnectionState == ConnectionState.UNINITIALIZED;

      InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("localhost"), LOCAL_PROFILER_CLIENT_PORT);
      myLastHeartbeatId = myCurrentHeartbeatId = 0;

      while (myCountDownLatch.getCount() > 0) {
        switch (myConnectionState) {
          case UNINITIALIZED:
            initializeChannel();
            // Fall through.
          case DISCONNECTED:
            if (!connect(addr)) {
              break;
            }
            // Fall through.
          case CONNECTED:
            performHandshake();
            if (myConnectionState == ConnectionState.OPERATIONAL) {
              return;
            }
            break;
          case OPERATIONAL:
            return;
          default:
            throw new RuntimeException("Connection error. Terminating connection.");
        }

        //noinspection BusyWait
        myCountDownLatch.await(INITIALIZE_CONNECTION_RETRY_TIME_MS, TimeUnit.MILLISECONDS);
      }
    }

    private void respondToHeartbeat(short id) throws IOException {
      writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH, id, NO_REMAINING_CHUNKS, RESPONSE_FLAG, PROTOCOL_MESSAGE_TYPE,
                    HEARTBEAT_SUBTYPE);
      flushOutputBuffer();
    }

    private boolean pumpHeartbeat() {
      writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH, myCurrentHeartbeatId, NO_REMAINING_CHUNKS, NO_FLAGS, PROTOCOL_MESSAGE_TYPE,
                    HEARTBEAT_SUBTYPE);
      myCurrentHeartbeatId++;

      try {
        flushOutputBuffer();
      }
      catch (IOException e) {
        return false;
      }
      return true;
    }

    private void processClientMessage(@NotNull MessageHeader header, @NotNull ByteBuffer input) throws IOException {
      assert header.type == 0;
      switch (header.subType) {
        case HEARTBEAT_SUBTYPE:
          if ((header.flags & RESPONSE_FLAG) != 0) {
            assert header.id == myLastHeartbeatId;
            myLastHeartbeatId = myCurrentHeartbeatId;
          }
          else {
            respondToHeartbeat(header.id);
          }
          break;
        default:
          // ignore for now.
          break;
      }
    }

    private void dispatchMessage(@NotNull MessageHeader header) throws IOException {
      switch (header.type) {
        case PROTOCOL_MESSAGE_TYPE:
          processClientMessage(header, myInputBuffer);
          break;
        case NETWORK_TYPE:
          processNetworkMessage(header, myInputBuffer);
          break;
        default:
          // Process messages to different client components.
          LOG.error(String.format("Unexpected header type %1$d", header.type));
          break;
      }
    }

    private void processNetworkMessage(@NotNull MessageHeader header, @NotNull ByteBuffer input) throws IOException {
      switch (header.subType) {
        case DATA_SUBTYPE:
          long time = input.getLong();
          long txBytes = input.getLong();
          long rxBytes = input.getLong();
          short networkType = input.getShort();
          byte highPowerState = input.get();
          break;
        default:
          LOG.error(String.format("Unexpected network subtype %1$d", header.subType));
          break;
      }
    }

    private void processMessages() throws IOException {
      long lastHeartbeatTime = System.nanoTime();

      while (myCountDownLatch.getCount() > 0) {
        long startTime = System.nanoTime();

        if (startTime - lastHeartbeatTime >= TIME_BETWEEN_PINGS_NS) {
          if (myCurrentHeartbeatId != myLastHeartbeatId) {
            break; // Client timed out, so re-establish connection.
          }
          pumpHeartbeat();
        }

        mySocketChannel.read(myInputBuffer);
        myInputBuffer.flip();
        while (myInputBuffer.remaining() >= MESSAGE_HEADER_LENGTH) {
          myInputBuffer.mark();
          MessageHeader header = new MessageHeader(myInputBuffer);
          if (myInputBuffer.remaining() < header.length - MESSAGE_HEADER_LENGTH) {
            myInputBuffer.reset();
            break;
          }

          dispatchMessage(header);

          myInputBuffer.reset();
          myInputBuffer.position(myInputBuffer.position() + header.length);

          lastHeartbeatTime = System.nanoTime();
        }
        myInputBuffer.compact();

        try {
          long sleepTime = FRAME_TIME_60_FPS_NS - (System.nanoTime() - startTime);
          if (sleepTime > 0) {
            myCountDownLatch.await(sleepTime, TimeUnit.NANOSECONDS);
          }
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    private void closeConnection() {
      if (mySocketChannel != null) {
        try {
          mySocketChannel.close();
        }
        catch (IOException ignored) {
        }
        mySocketChannel = null;
        myConnectionState = ConnectionState.UNINITIALIZED;
      }
      myInputBuffer.clear();
      myOutputBuffer.clear();
    }
  }
}
