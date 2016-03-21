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
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.monitor.profilerclient.MessageHeader.*;

/**
 * This handles the network connection between the host and the ProfilerServer.
 * It is package private since it should only ever be accessed by ProfilerClient.
 */
class DeviceClientChannel {
  private static final int LOCAL_PROFILER_CLIENT_PORT_RANGE_START = 46623;
  private static final Logger LOG = Logger.getInstance(DeviceClientChannel.class.getCanonicalName());
  private static final int MAX_PORT = 65536;

  private final int myPort;

  @NotNull private final Map<Client, AppClientChannel> myAppClients = new HashMap<Client, AppClientChannel>();
  @NotNull private LinkedBlockingQueue<ClientCommand> myClientCommands = new LinkedBlockingQueue<ClientCommand>();
  @NotNull private List<ProfilerClientListener> myProfilerClientListeners = new ArrayList<ProfilerClientListener>();

  @Nullable private DeviceChannelHandler myDeviceChannelHandler;
  @NotNull private IDevice myDevice;

  private DeviceClientChannel(@NotNull IDevice device, int port, @NotNull ProfilerClientListener profilerClientListener) {
    myDevice = device;
    myPort = port;
    myProfilerClientListeners.add(profilerClientListener);
  }

  /**
   * Creates a connection to the target device's ProfilerServer.
   *
   * @return an abstraction of a connection to a device, or null if the connection failed
   */
  @Nullable
  static synchronized DeviceClientChannel connect(@NotNull IDevice device, @NotNull ProfilerClientListener profilerClientListener) {
    int port = LOCAL_PROFILER_CLIENT_PORT_RANGE_START;
    for (; port < MAX_PORT; ++port) {
      try {
        device.createForward(port, "StudioProfiler", IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        break;
      }
      catch (TimeoutException e) {
        LOG.error("Timed out while attempting to create port forward", e);
        return null;
      }
      catch (AdbCommandRejectedException e) {
        LOG.error("ADB command error while attempting to create port forward", e);
      }
      catch (IOException e) {
        LOG.error("IO error while attempting to create port forward", e);
      }
    }

    if (port == MAX_PORT) {
      LOG.error("Could not establish port forward. All ports in use in valid range.");
      return null;
    }

    return new DeviceClientChannel(device, port, profilerClientListener);
  }

  public synchronized int getAppClientCount() {
    return myAppClients.size();
  }

  /**
   * Creates a connection to a target app on this device.
   *
   * @return an abstraction of a connection to an app, or null if the connection failed
   */
  @Nullable
  synchronized AppClient connect(@NotNull Client client, @NotNull ProfilerClientListener profilerClientListener) {
    AppClientChannel appClientChannel;

    if (myAppClients.containsKey(client)) {
      appClientChannel = myAppClients.get(client);
      appClientChannel.addListener(profilerClientListener);
    }
    else {
      int port = LOCAL_PROFILER_CLIENT_PORT_RANGE_START;
      for (; port < MAX_PORT; ++port) {
        try {
          myDevice.createForward(port, "StudioProfiler_pid" + client.getClientData().getPid(), IDevice.DeviceUnixSocketNamespace.ABSTRACT);
          break;
        }
        catch (TimeoutException e) {
          LOG.error("Timed out while attempting to create port forward", e);
          return null;
        }
        catch (AdbCommandRejectedException e) {
          LOG.error("ADB command error while attempting to create port forward", e);
        }
        catch (IOException e) {
          LOG.error("IO error while attempting to create port forward", e);
        }
      }

      if (port == MAX_PORT) {
        LOG.error("Could not establish port forward. All ports in use in valid range.");
        return null;
      }

      // TODO move this back to {@link DeviceClient#connect} when the Main Server is completed.
      myDeviceChannelHandler = new DeviceChannelHandler(myClientCommands, myDevice, myPort, port);
      myDeviceChannelHandler.start();

      appClientChannel = new AppClientChannel(this, client, profilerClientListener);
      myAppClients.put(client, appClientChannel);
    }

    return new AppClient(appClientChannel, profilerClientListener);
  }

  /**
   * Disconnects cleanly from the target app on this device.
   *
   * @param appClientChannel is the desired target as returned from {@link ProfilerService#connect(IDevice, ProfilerClientListener)}
   */
  synchronized void disconnect(@NotNull AppClient appClient) {
    if (!myAppClients.containsKey(appClient.getAppClientChannel().getClient())) {
      LOG.error("Caller attempted to disconnected from a device that was not connected.");
    }

    appClient.getAppClientChannel().removeListener(appClient.getProfilerClientListener());
    if (appClient.getAppClientChannel().getProfilerClientListenersCount() == 0) {
      myAppClients.remove(appClient.getAppClientChannel().getClient());
      try {
        // TODO move this back to {@link ProfilerService#disconnect} when the Main Server is completed.
        // TODO send the DeviceClientChannel a disconnect command
        assert myDeviceChannelHandler != null;
        myDeviceChannelHandler.terminate();
      }
      catch (InterruptedException ignored) {
        LOG.debug("Device connection termination interrupted");
      }
    }
  }

  @NotNull
  IDevice getDevice() {
    return myDevice;
  }

  void addListener(@NotNull ProfilerClientListener profilerClientListener) {
    myProfilerClientListeners.add(profilerClientListener);
  }

  void removeListener(@NotNull ProfilerClientListener profilerClientListener) {
    myProfilerClientListeners.remove(profilerClientListener);

    if (getProfilerClientListenersCount() == 0) {
      if (getAppClientCount() > 0) {
        LOG.error("AppClient(s) were not cleaned up prior to fully disconnecting the DeviceClient.");
      }

      try {
        assert myDeviceChannelHandler != null;
        myDeviceChannelHandler.terminate();
      }
      catch (InterruptedException ignored) {
        LOG.debug("Device connection termination interrupted.");
      }
    }
  }

  int getProfilerClientListenersCount() {
    return myProfilerClientListeners.size();
  }

  void queueCommand(@NotNull ClientCommand command) {
    myClientCommands.add(command);
  }

  private enum ConnectionState {
    ERR_TERMINATE_CONNECTION,
    UNINITIALIZED,
    DISCONNECTED,
    CONNECTED,
    OPERATIONAL
  }

  /**
   * Inner class/thread that actually performs and handles the connection to the Device.
   */
  private static class DeviceChannelHandler extends Thread {
    private static final short DEFAULT_ID = (short)0;
    private static final short NO_REMAINING_CHUNKS = (short)0;

    private static final short HANDSHAKE_SUBTYPE = (short)0;
    private static final short HEARTBEAT_SUBTYPE = (short)1;
    private static final int PROFILER_VERSION = 0;
    private static final long INITIALIZE_CONNECTION_RETRY_TIME_MS = 500L;
    private static final long HANDSHAKE_WAIT_TIME_MS = 100L;
    private final MessageHeader EXPECTED_HANDSHAKE_RESPONSE =
      new MessageHeader(MESSAGE_HEADER_LENGTH + 1, DEFAULT_ID, NO_REMAINING_CHUNKS, RESPONSE_FLAG, ProfilerComponentIds.SERVER,
                        HANDSHAKE_SUBTYPE); // +1 to length for the response from the server.
    private final long TIME_BETWEEN_PINGS_NS = TimeUnit.NANOSECONDS.convert(2L, TimeUnit.SECONDS);
    private final long PING_TIMEOUT_NS = TimeUnit.NANOSECONDS.convert(5L, TimeUnit.SECONDS);
    private final long HANDSHAKE_TIMEOUT_NS = TimeUnit.NANOSECONDS.convert(60L, TimeUnit.SECONDS);
    private final long FRAME_TIME_60_FPS_NS = TimeUnit.NANOSECONDS.convert(1L, TimeUnit.SECONDS) / 60;

    @NotNull private IDevice myDevice;

    @NotNull private CountDownLatch myCountDownLatch = new CountDownLatch(1);
    @NotNull private LinkedBlockingQueue<ClientCommand> myCommandQueue;

    private SocketChannel mySocketChannel;
    private int myPort;
    private int myHackedAppPort;
    @NotNull private ConnectionState myConnectionState = ConnectionState.UNINITIALIZED;
    @NotNull private ByteBuffer myOutputBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN);
    @NotNull private ByteBuffer myInputBuffer = ByteBuffer.allocateDirect(16 * 1024).order(ByteOrder.LITTLE_ENDIAN);

    @NotNull private TIntObjectHashMap<ProfilerClientListener> myOutstandingResponseListeners =
      new TIntObjectHashMap<ProfilerClientListener>();
    private short myMessageIdCounter = 0;
    private short myCurrentHeartbeatId = 0;
    private short myLastHeartbeatId = myCurrentHeartbeatId;

    private DeviceChannelHandler(@NotNull LinkedBlockingQueue<ClientCommand> commandQueue,
                                 @NotNull IDevice device,
                                 int port,
                                 int hackedAppPort) {
      super("DeviceClientChannel.DeviceChannelHandler");
      myCommandQueue = commandQueue;
      myDevice = device;
      myPort = port;
      myHackedAppPort = hackedAppPort;
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

    private synchronized void terminate() throws InterruptedException {
      myCountDownLatch.countDown();
      interrupt();
      try {
        join();
        myDevice.removeForward(myPort, "StudioProfiler", IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
      catch (AdbCommandRejectedException ignored) {
        LOG.debug("Command rejected while removing port forwarding.");
      }
      catch (IOException ignored) {
        LOG.debug("IO exception while removing port forwarding.");
      }
      catch (TimeoutException ignored) {
        LOG.debug("Timed out while removing port forwarding.");
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
      writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH + 4, DEFAULT_ID, NO_REMAINING_CHUNKS, NO_FLAGS, ProfilerComponentIds.SERVER,
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

      // TODO change myHackedAppPort back to myPort after Main Server is completed
      InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("localhost"), myHackedAppPort);
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
      writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH, id, NO_REMAINING_CHUNKS, RESPONSE_FLAG, ProfilerComponentIds.SERVER,
                    HEARTBEAT_SUBTYPE);
      flushOutputBuffer();
    }

    private boolean pumpHeartbeat() {
      writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH, myCurrentHeartbeatId, NO_REMAINING_CHUNKS, NO_FLAGS, ProfilerComponentIds.SERVER,
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
      // Note that server messages are processed before any listener messages, so the ID does not clash.
      if (header.type == ProfilerComponentIds.SERVER) {
        processClientMessage(header, myInputBuffer);
        return;
      }

      int bufferStartPosition = myInputBuffer.position();
      if (myOutstandingResponseListeners.containsKey(header.id)) {
        myOutstandingResponseListeners.get(header.id).onMessage(header, myInputBuffer);
        myInputBuffer.position(bufferStartPosition + header.length - MESSAGE_HEADER_LENGTH);
      }
      else {
        // TODO perhaps handle broadcasting separately so that messages are only parsed once for all listeners
        for (TIntObjectIterator<ProfilerClientListener> it = myOutstandingResponseListeners.iterator(); it.hasNext(); it.advance()) {
          it.value().onMessage(header, myInputBuffer);
          myInputBuffer.position(bufferStartPosition);
        }
      }
    }

    private void processMessages() throws IOException {
      long lastHeartbeatTime = System.nanoTime();

      while (myCountDownLatch.getCount() > 0) {
        long startTime = System.nanoTime();

        if (startTime - lastHeartbeatTime >= TIME_BETWEEN_PINGS_NS) {
          if (myCurrentHeartbeatId != myLastHeartbeatId) {
            if (startTime - lastHeartbeatTime >= PING_TIMEOUT_NS) {
              break; // Client timed out, so re-establish connection.
            }
          }
          else if (!pumpHeartbeat()) {
            break; // Socket error, most likely connection closed by peer.
          }
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

        processCommands();

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

    private void processCommands() {
      while (!myCommandQueue.isEmpty()) {
        ClientCommand command = myCommandQueue.poll();
        switch (command.commandType) {
          case SYSTEM:
            break;

          case SEND:
            short idToUse;
            short checkId = myMessageIdCounter; // Check for overflow loop around.
            while (myOutstandingResponseListeners.containsKey(myMessageIdCounter)) {
              myMessageIdCounter++;
              if (checkId == myMessageIdCounter) {
                throw new RuntimeException("Ran out of IDs to use. Did someone not release the IDs?");
              }
            }
            idToUse = myMessageIdCounter;

            if (command.waitForResponse) {
              myOutstandingResponseListeners.put(idToUse, command.listener);
            }
            writeToBuffer(myOutputBuffer, MESSAGE_HEADER_LENGTH + command.length, idToUse, (short)0, command.flags, command.type,
                          command.subType);
            if (command.data != null) {
              myOutputBuffer.put(command.data, command.offset, command.length);
            }
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
