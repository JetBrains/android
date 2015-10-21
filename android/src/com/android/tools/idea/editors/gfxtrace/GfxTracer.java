/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace;

import com.android.ddmlib.*;
import com.android.tools.chartlib.EventData;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.tools.idea.monitor.gpu.GpuMonitorView;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;

public class GfxTracer {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTracer.class);
  @NotNull private static final String PRELOAD_LIB = "/data/local/tmp/libgapii.so";
  @NotNull private static final int GAPII_PORT = 9286;
  @NotNull private static final String GAPII_ABSTRACT_PORT = "gapii";

  @NotNull private static final Pattern ENFORCING_PATTERN = Pattern.compile("^Enforcing$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  @NotNull private static final Pattern PERMISSIVE_PATTERN = Pattern.compile("^Permissive$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  @NotNull private final IDevice myDevice;
  @NotNull final CaptureService myCaptureService;
  @NotNull final CaptureHandle myCapture;
  @NotNull final EventData myEvents;

  private volatile boolean myStopped = false;

  // Options holds the flags used to control the capture mode.
  public static class Options {
    // If true, then a framebuffer-observation will be made after every end-of-frame.
    public int myObserveFrameFrequency = 0;
    // If true, then a framebuffer-observation will be made after every draw call.
    public int myObserveDrawFrequency = 0;
  }

  public static GfxTracer launch(@NotNull final Project project,
                                 @NotNull final IDevice device,
                                 @NotNull final DeviceInfo.Package pkg,
                                 @NotNull final DeviceInfo.Activity act,
                                 @NotNull final Options options,
                                 @NotNull EventData events) {
    final GfxTracer tracer = new GfxTracer(project, device, events);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        tracer.launchAndCapture(pkg, act, options);
      }
    });
    return tracer;
  }

  public static GfxTracer listen(@NotNull final Project project,
                                 @NotNull final IDevice device,
                                 @NotNull final Options options,
                                 @NotNull EventData events) {
    final GfxTracer tracer = new GfxTracer(project, device, events);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        tracer.capture(options);
      }
    });
    return tracer;
  }

  private GfxTracer(@NotNull Project project, @NotNull IDevice device, @NotNull EventData events) {
    myCaptureService = CaptureService.getInstance(project);
    myDevice = device;
    myEvents = events;
    try {
      myCapture = myCaptureService.startCaptureFile(GfxTraceCaptureType.class);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void launchAndCapture(@NotNull final DeviceInfo.Package pkg, @NotNull final DeviceInfo.Activity act, @NotNull Options options) {
    EventData.Event event = myEvents.start(System.currentTimeMillis(), GpuMonitorView.EVENT_LAUNCH);
    try {
      String abi = pkg.myABI;
      if (abi == null) {
        // Package has no preferential ABI. Use device ABI instead.
        abi = myDevice.getAbis().get(0);
      }
      final File myGapii = GapiPaths.findTraceLibrary(abi);
      String component = pkg.myName + "/" + act.myName;
      // Switch adb to root mode, if not already
      myDevice.root();
      // turn off selinux enforce if it is on, and remember the state so we can reset it when we are done
      String enforced = captureAdbShell(myDevice, "getenforce");
      boolean wasEnforcing;
      if (ENFORCING_PATTERN.matcher(enforced).find()) {
        wasEnforcing = true;
      }
      else if (PERMISSIVE_PATTERN.matcher(enforced).find()) {
        wasEnforcing = false;
      }
      else {
        LOG.error("Unexpected getenforce result'" + enforced + "'");
        wasEnforcing = true;
      }
      if (wasEnforcing) {
        captureAdbShell(myDevice, "setenforce 0");
      }
      try {
        // The property name must have at most 31 characters and must not end with dot.
        String propName = "wrap." + pkg.myName;
        if (propName.length() > 31) {
          propName = propName.substring(0, 31);
        }
        while (propName.endsWith(".")) {
          propName = propName.substring(0, propName.length() - 1);
        }
        // push the spy down to the device
        myDevice.pushFile(myGapii.getAbsolutePath(), PRELOAD_LIB);
        // Put gapii in the library preload
        captureAdbShell(myDevice, "setprop " + propName + " LD_PRELOAD=" + PRELOAD_LIB);
        try {
          // Launch the app with the spy enabled
          captureAdbShell(myDevice, "am start -S -W -n " + component);
          event.stop(System.currentTimeMillis());
          capture(options);
        }
        finally {
          // Undo the preload wrapping
          captureAdbShell(myDevice, "setprop " + propName + " \"\"");
        }
      }
      finally {
        if (wasEnforcing) {
          captureAdbShell(myDevice, "setenforce 1");
        }
      }
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      if (event.to == -1) {
        event.stop(System.currentTimeMillis());
      }
    }
  }

  private void capture(@NotNull Options options) {
    try {
      captureFromDevice(options);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      // Hand the trace back to the capture system
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myCaptureService.finalizeCaptureFileAsynchronous(myCapture, new FutureCallback<Capture>() {
            @Override
            public void onSuccess(Capture capture) {
              capture.getFile().refresh(true, false);
              myCaptureService.notifyCaptureReady(capture);
            }

            @Override
            public void onFailure(Throwable t) {
              LOG.error(t.getMessage());
            }
          }, MoreExecutors.sameThreadExecutor());
        }
      });
    }
  }

  private void captureFromDevice(@NotNull Options options)
    throws AdbCommandRejectedException, IOException, TimeoutException, InterruptedException {
    try {
      myDevice.createForward(GAPII_PORT, GAPII_ABSTRACT_PORT, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      captureFromSocket("localhost", GAPII_PORT, options);
    }
    finally {
      myDevice.removeForward(GAPII_PORT, GAPII_ABSTRACT_PORT, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
    }
  }

  private void captureFromSocket(String host, int port, @NotNull Options options) throws IOException, InterruptedException {
    Socket socket = null;
    EventData.Event event = null;
    long total = 0;
    byte[] buffer = new byte[4096];
    try {
      // Now loop until we get a connection
      int len = 0;
      while (!myStopped) {
        if (socket == null) {
          //noinspection SocketOpenedButNotSafelyClosed
          socket = new Socket(host, port);
          socket.setSoTimeout(500);
          sendHeader(socket, options);
        }
        len = copyBlock(socket, myCapture, buffer);
        if (len > 0) {
          if (event == null) {
            event = myEvents.start(System.currentTimeMillis(), GpuMonitorView.EVENT_TRACING);
          }
          total += len;
        }
        else if (len < 0) {
          socket.close();
          socket = null;
          if (total == 0) {
            // If we have never read any data, just try again in a bit
            Thread.sleep(500);
          }
          else {
            myStopped = true;
          }
        }
      }
    }
    finally {
      if (event != null) {
        event.stop(System.currentTimeMillis());
      }
      if (socket != null) {
        socket.close();
      }
    }
  }

  private static void sendHeader(@NotNull Socket socket, @NotNull Options options) throws IOException {
    // The GAPII header version 1 is defined as:
    //
    // struct ConnectionHeader {
    //   uint8_t  mMagic[4];                     // 's', 'p', 'y', '0'
    //   uint32_t mVersion;                      // 2
    //   uint32_t  mObserveFrameFrequency;      // non-zero == enabled
    //   uint32_t  mObserveDrawFrequency; // non-zero == enabled
    // };
    //
    // All fields are encoded little-endian with no compression, regardless of
    // architecture. All changes must be kept in sync with:
    //   platform/tools/gpu/cc/gapii/connection_header.h
    OutputStream out = socket.getOutputStream();
    byte[] b = new byte[16];
    // magic
    b[0] = 's';
    b[1] = 'p';
    b[2] = 'y';
    b[3] = '0';
    // version
    b[4] = 2;
    b[5] = 0;
    b[6] = 0;
    b[7] = 0;
    // mObserveFrameFrequency
    b[8] = (byte)(options.myObserveFrameFrequency >> 0);
    b[9] = (byte)(options.myObserveFrameFrequency >> 8);
    b[10] = (byte)(options.myObserveFrameFrequency >> 16);
    b[11] = (byte)(options.myObserveFrameFrequency >> 24);
    // mObserveDrawFrequency
    b[12] = (byte)(options.myObserveDrawFrequency >> 0);
    b[13] = (byte)(options.myObserveDrawFrequency >> 8);
    b[14] = (byte)(options.myObserveDrawFrequency >> 16);
    b[15] = (byte)(options.myObserveDrawFrequency >> 24);
    out.write(b);
    out.flush();
  }

  public void stop() {
    myStopped = true;
  }

  private static int copyBlock(Socket socket, CaptureHandle capture, byte[] buffer) throws IOException {
    try {
      int len = socket.getInputStream().read(buffer);
      if (len > 0) {
        CaptureService.appendDataSynchronous(capture, buffer, 0, len);
      }
      return len;
    }
    catch (SocketTimeoutException e) {
      return 0;
    }
  }

  @NotNull
  private static String captureAdbShell(IDevice device, String command)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    device.executeShellCommand(command, receiver);
    return receiver.getOutput();
  }
}
