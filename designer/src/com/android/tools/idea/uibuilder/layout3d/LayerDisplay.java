package com.android.tools.idea.uibuilder.layout3d;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.*;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.PixelProbe;
import com.android.tools.pixelprobe.decoder.Decoder;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.io.File;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.pixelprobe.decoder.Decoder.Options.LAYER_METADATA_ONLY;


public class LayerDisplay {

  public static void main(String[] str) {

    try {
      DeviceBridge.initDebugBridge();
      Thread.sleep(500);
      IDevice d = DeviceBridge.getDevices()[0];
      for (IDevice device : DeviceBridge.getDevices()) {
        System.out.println(device.toString());
        System.out.println("getAvdName = "+device.getAvdName());
        System.out.println("getLanguage = "+device.getLanguage());
        System.out.println("getSerialNumber = "+device.getSerialNumber());
        System.out.println("getName = "+device.getName());
        System.out.println("getAvdName = "+device.getAvdName());
        System.out.println("getAvdName = "+device.getAvdName());
        System.out.println("getAvdName = "+device.getAvdName());
        System.out.println("getAvdName = "+device.getAvdName());
        System.out.println("getAvdName = "+device.getAvdName());
        System.out.println((device.isEmulator() ? "emulator" : "device"));
      }
      if (d != null)
      System.out.println( d.getAvdName());
      DeviceBridge.terminate();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void dump(List<Layer> layers, String str) {
    if (layers == null) {
      return;
    }
    for (Layer layer : layers) {
      System.out.println(str + layer.getBounds());
      dump(layer.getChildren(), " " + str);
    }
  }

  static class Configuration {
    public static final int DEFAULT_SERVER_PORT = 4939;
    // These codes must match the auto-generated codes in IWindowManager.java
    // See IWindowManager.aidl as well
    public static final int SERVICE_CODE_START_SERVER = 1;
    public static final int SERVICE_CODE_STOP_SERVER = 2;
    public static final int SERVICE_CODE_IS_SERVER_RUNNING = 3;
  }

  static class DeviceBridge {
    private static AndroidDebugBridge bridge;

    private static final HashMap<IDevice, Integer> devicePortMap = new HashMap<IDevice, Integer>();
    private static int nextLocalPort = Configuration.DEFAULT_SERVER_PORT;

    public static void initDebugBridge() {
      if (bridge == null) {
        AndroidDebugBridge.init(false /* debugger support */);
      }
      if (bridge == null || !bridge.isConnected()) {
        String adbLocation = System.getProperty("hierarchyviewer.adb");
        if (adbLocation != null && !adbLocation.isEmpty()) {
          adbLocation += File.separator + "adb";
        }
        else {
          adbLocation = "adb";
        }
        bridge = AndroidDebugBridge.createBridge(adbLocation, true);
      }
    }

    public static void startListenForDevices(AndroidDebugBridge.IDeviceChangeListener listener) {
      AndroidDebugBridge.addDeviceChangeListener(listener);
    }

    public static class VersionLoader {
      public static int loadServerVersion(IDevice device) {
        return loadVersion(device, "SERVER");
      }

      public static int loadProtocolVersion(IDevice device) {
        return loadVersion(device, "PROTOCOL");
      }
      private static int loadVersion(IDevice device, String command) {
        Socket socket = null;
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
          socket = new Socket();
          socket.connect(new InetSocketAddress("127.0.0.1",
                                               DeviceBridge.getDeviceLocalPort(device)));
          out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
          in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
          out.write(command);
          out.newLine();
          out.flush();
          return Integer.parseInt(in.readLine());
        } catch (Exception e) {
          // Empty
        } finally {
          try {
            if (out != null) {
              out.close();
            }
            if (in != null) {
              in.close();
            }
            if (socket != null) {
              socket.close();
            }
          } catch (IOException ex) {
            ex.printStackTrace();
          }
        }
        // Versioning of the protocol and server was added with version 2
        return 2;
      }
    }
    static  class Window {
      public static final Window FOCUSED_WINDOW = new Window("<Focused Window>", -1);
      private String title;
      private int hashCode;
      public Window(String title, int hashCode) {
        this.title = title;
        this.hashCode = hashCode;
      }
      public String getTitle() {
        return title;
      }
      public int getHashCode() {
        return hashCode;
      }
      public String encode() {
        return Integer.toHexString(hashCode);
      }
      @Override
      public String toString() {
        return title;
      }
    }
    public static IDevice[] getDevices() {
      return bridge.getDevices();
    }

    public static boolean isViewServerRunning(IDevice device) {
      initDebugBridge();
      final boolean[] result = new boolean[1];
      try {
        if (device.isOnline()) {
          device.executeShellCommand(buildIsServerRunningShellCommand(),
                                     new BooleanResultReader(result));
          if (!result[0]) {
            if (VersionLoader.loadProtocolVersion(device) > 2) {
              result[0] = true;
            }
          }
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      catch (TimeoutException e) {
        e.printStackTrace();
      }
      catch (AdbCommandRejectedException e) {
        e.printStackTrace();
      }
      catch (ShellCommandUnresponsiveException e) {
        e.printStackTrace();
      }
      return result[0];
    }

    public static boolean startViewServer(IDevice device) {
      return startViewServer(device, Configuration.DEFAULT_SERVER_PORT);
    }

    public static boolean startViewServer(IDevice device, int port) {
      initDebugBridge();
      final boolean[] result = new boolean[1];
      try {
        if (device.isOnline()) {
          device.executeShellCommand(buildStartServerShellCommand(port),
                                     new BooleanResultReader(result));
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      catch (TimeoutException e) {
        e.printStackTrace();
      }
      catch (AdbCommandRejectedException e) {
        e.printStackTrace();
      }
      catch (ShellCommandUnresponsiveException e) {
        e.printStackTrace();
      }
      return result[0];
    }

    public static boolean stopViewServer(IDevice device) {
      initDebugBridge();
      final boolean[] result = new boolean[1];
      try {
        if (device.isOnline()) {
          device.executeShellCommand(buildStopServerShellCommand(),
                                     new BooleanResultReader(result));
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      catch (TimeoutException e) {
        e.printStackTrace();
      }
      catch (AdbCommandRejectedException e) {
        e.printStackTrace();
      }
      catch (ShellCommandUnresponsiveException e) {
        e.printStackTrace();
      }
      return result[0];
    }

    public static void terminate() {
      AndroidDebugBridge.terminate();
    }

    /**
     * Sets up a just-connected device to work with the view server.
     * <p/>This starts a port forwarding between a local port and a port on the device.
     *
     * @param device
     */
    public static void setupDeviceForward(IDevice device) {
      synchronized (devicePortMap) {
        if (device.getState() == IDevice.DeviceState.ONLINE) {
          int localPort = nextLocalPort++;
          try {
            device.createForward(localPort, Configuration.DEFAULT_SERVER_PORT);
            devicePortMap.put(device, localPort);
          }
          catch (TimeoutException e) {
            Log.e("hierarchy", "Timeout setting up port forwarding for " + device);
          }
          catch (AdbCommandRejectedException e) {
            Log.e("hierarchy", String.format(
              "Adb rejected forward command for device %1$s: %2$s",
              device, e.getMessage()));
          }
          catch (IOException e) {
            Log.e("hierarchy", String.format(
              "Failed to create forward for device %1$s: %2$s",
              device, e.getMessage()));
          }
        }
      }
    }

    public static void removeDeviceForward(IDevice device) {
      synchronized (devicePortMap) {
        final Integer localPort = devicePortMap.get(device);
        if (localPort != null) {
          try {
            device.removeForward(localPort, Configuration.DEFAULT_SERVER_PORT);
            devicePortMap.remove(device);
          }
          catch (TimeoutException e) {
            Log.e("hierarchy", "Timeout removing port forwarding for " + device);
          }
          catch (AdbCommandRejectedException e) {
            Log.e("hierarchy", String.format(
              "Adb rejected remove-forward command for device %1$s: %2$s",
              device, e.getMessage()));
          }
          catch (IOException e) {
            Log.e("hierarchy", String.format(
              "Failed to remove forward for device %1$s: %2$s",
              device, e.getMessage()));
          }
        }
      }
    }

    public static int getDeviceLocalPort(IDevice device) {
      synchronized (devicePortMap) {
        Integer port = devicePortMap.get(device);
        if (port != null) {
          return port;
        }
        Log.e("hierarchy", "Missing forwarded port for " + device.getSerialNumber());
        return -1;
      }
    }

    private static String buildStartServerShellCommand(int port) {
      return String.format("service call window %d i32 %d",
                           Configuration.SERVICE_CODE_START_SERVER, port);
    }

    private static String buildStopServerShellCommand() {
      return String.format("service call window %d", Configuration.SERVICE_CODE_STOP_SERVER);
    }

    private static String buildIsServerRunningShellCommand() {
      return String.format("service call window %d",
                           Configuration.SERVICE_CODE_IS_SERVER_RUNNING);
    }

    private static class BooleanResultReader extends MultiLineReceiver {
      private final boolean[] mResult;

      public BooleanResultReader(boolean[] result) {
        mResult = result;
      }

      @Override
      public void processNewLines(String[] strings) {
        if (strings.length > 0) {
          Pattern pattern = Pattern.compile(".*?\\([0-9]{8} ([0-9]{8}).*");
          Matcher matcher = pattern.matcher(strings[0]);
          if (matcher.matches()) {
            if (Integer.parseInt(matcher.group(1)) == 1) {
              mResult[0] = true;
            }
          }
        }
      }

      @Override
      public boolean isCancelled() {
        return false;
      }
    }
  }
}
