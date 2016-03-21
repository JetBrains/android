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
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.editor.ProfilerState;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;

public class GfxTracer {
  private static final long UPDATE_FREQUENCY_MS = 500;

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTracer.class);
  @NotNull private static final String PRELOAD_LIB = "/data/local/tmp/libgapii.so";
  private static final int GAPII_PORT = 9286;
  @NotNull private static final String GAPII_ABSTRACT_PORT = "gapii";
  private static final int GAPII_PROTOCOL_VERSION = 3;
  private static final int GAPII_FLAG_DISABLE_PRECOMPILED_SHADERS = 0x00000001;

  @NotNull private static final Pattern ENFORCING_PATTERN = Pattern.compile("^Enforcing$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  @NotNull private static final Pattern PERMISSIVE_PATTERN = Pattern.compile("^Permissive$|^Disabled$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  @NotNull private final IDevice myDevice;
  @NotNull final CaptureService myCaptureService;
  @NotNull final CaptureHandle myCapture;
  @NotNull final Listener myListener;

  private volatile boolean myStopped = false;


  // Options holds the flags used to control the capture mode.
  public static class Options {
    // The trace file name to output.
    public String myTraceName;
    // If non-zero, then a framebuffer-observation will be made after every N end-of-frames.
    public int myObserveFrameFrequency = 0;
    // If non-zero, then a framebuffer-observation will be made after every N draw calls.
    public int myObserveDrawFrequency = 0;
    // If true then GAPII will pretend the driver does not support precompiled shaders.
    public boolean myDisablePrecompiledShaders = false;

    /**
     * Returns the default trace {@link Options} given the {@link RunConfiguration} settings.
     */
    public static Options fromRunConfiguration(@Nullable RunConfiguration config) {
      Options options = new Options();
      if (config != null && config instanceof AndroidRunConfigurationBase) {
        ProfilerState state = ((AndroidRunConfigurationBase)config).getProfilerState();
        options.myDisablePrecompiledShaders = state.GAPID_DISABLE_PCS;
      }
      return options;
    }
  }

  /**
   * Listener is the interface used to report trace status.
   */
  public interface Listener {
    void onAction(@NotNull String name);

    void onProgress(long sizeInBytes);

    void onStopped();

    void onError(@NotNull String error);
  }

  public static GfxTracer launch(@NotNull final Project project,
                                 @NotNull final IDevice device,
                                 @NotNull final DeviceInfo.Package pkg,
                                 @NotNull final DeviceInfo.Activity act,
                                 @NotNull final Options options,
                                 @NotNull final Listener listener) {
    final GfxTracer tracer = new GfxTracer(project, device, options, listener);
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
                                 @NotNull final Listener listener) {
    final GfxTracer tracer = new GfxTracer(project, device, options, listener);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        tracer.capture(options);
      }
    });
    return tracer;
  }

  private GfxTracer(@NotNull Project project, @NotNull IDevice device, @NotNull final Options options, @NotNull Listener listener) {
    myCaptureService = CaptureService.getInstance(project);
    myDevice = device;
    myListener = listener;
    try {
      myCapture = myCaptureService.startCaptureFile(GfxTraceCaptureType.class, options.myTraceName, true);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void launchAndCapture(@NotNull final DeviceInfo.Package pkg, @NotNull final DeviceInfo.Activity act, @NotNull Options options) {
    try {
      myListener.onAction("Launching application...");
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
  }

  private void capture(@NotNull Options options) {
    boolean gotData = false;
    try {
      gotData = captureFromDevice(options);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      if (gotData) {
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
      else {
        // Discard the empty capture.
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myCaptureService.cancelCaptureFile(myCapture);
          }
        });
      }
    }
  }

  /**
   * captureFromDevice attempts to connect to graphics interceptor running on {@link #myDevice}.
   *
   * @param options the options to use for the trace.
   * @return true if some data was captured, otherwise false.
   */
  private boolean captureFromDevice(@NotNull Options options)
    throws AdbCommandRejectedException, IOException, TimeoutException, InterruptedException {
    try {
      myDevice.createForward(GAPII_PORT, GAPII_ABSTRACT_PORT, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      return captureFromSocket("localhost", GAPII_PORT, options);
    }
    finally {
      myDevice.removeForward(GAPII_PORT, GAPII_ABSTRACT_PORT, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
    }
  }

  /**
   * captureFromSocket attempts to connect to graphics interceptor on the specified address.
   *
   * @param host    the host address of the interceptor.
   * @param port    the port address of the interceptor.
   * @param options the options to use for the trace.
   * @return true if some data was captured, otherwise false.
   */
  private boolean captureFromSocket(String host, int port, @NotNull Options options) throws IOException, InterruptedException {
    myListener.onAction("Connecting to application...");
    Socket socket = null;
    long lastUpdateMS = 0;
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
          if (total == 0) {
            myListener.onAction("Tracing...");
          }
          total += len;
          long nowMS = System.currentTimeMillis();
          if (nowMS - lastUpdateMS > UPDATE_FREQUENCY_MS) {
            myListener.onProgress(total);
            lastUpdateMS = nowMS;
          }
        }
        else if (len < 0) {
          socket.close();
          socket = null;
          if (total == 0) {
            // If we have never read any data, just try again in a bit
            Thread.sleep(500);
          }
          else {
            stop();
          }
        }
      }
    }
    finally {
      if (socket != null) {
        socket.close();
      }
    }
    return total > 0;
  }

  private static void sendHeader(@NotNull Socket socket, @NotNull Options options) throws IOException {
    // The GAPII header version 3 is defined as:
    //
    // struct ConnectionHeader {
    //   uint8_t  mMagic[4];                     // 's', 'p', 'y', '0'
    //   uint32_t mVersion;                      // 3
    //   uint32_t  mObserveFrameFrequency;       // non-zero == enabled
    //   uint32_t  mObserveDrawFrequency;        // non-zero == enabled
    //   uint32_t  mFlags;                       // bitfield
    // };
    //
    // All fields are encoded little-endian with no compression, regardless of
    // architecture. All changes must be kept in sync with:
    //   platform/tools/gpu/cc/gapii/connection_header.h

    int flags = 0;
    if (options.myDisablePrecompiledShaders) {
      flags |= GAPII_FLAG_DISABLE_PRECOMPILED_SHADERS;
    }

    OutputStream out = socket.getOutputStream();
    byte[] b = new byte[20];
    // magic
    b[0] = 's';
    b[1] = 'p';
    b[2] = 'y';
    b[3] = '0';
    // version
    b[4] = (byte)(GAPII_PROTOCOL_VERSION >> 0);
    b[5] = (byte)(GAPII_PROTOCOL_VERSION >> 8);
    b[6] = (byte)(GAPII_PROTOCOL_VERSION >> 16);
    b[7] = (byte)(GAPII_PROTOCOL_VERSION >> 24);
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
    // mFlags
    b[16] = (byte)(flags >> 0);
    b[17] = (byte)(flags >> 8);
    b[18] = (byte)(flags >> 16);
    b[19] = (byte)(flags >> 24);

    out.write(b);
    out.flush();
  }

  public void stop() {
    myStopped = true;
    myListener.onStopped();
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
