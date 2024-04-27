package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.SyncService;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

class AndroidDbUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDbUtil");

  public static final Object DB_SYNC_LOCK = new Object();
  public static final String TEMP_REMOTE_DB_PATH = "/data/local/tmp/intellij_temp_db_file";
  public static final String TEMP_REMOTE_GET_MODIFICATION_TIME_TOOL_PATH =
    "/data/local/tmp/intellij_native_tools/get_modification_time";
  public static final long DB_COPYING_TIMEOUT_SEC = 30;
  public static final int SHELL_COMMAND_TIMEOUT_SECONDS = 2;

  private static final String DEVICE_ID_EMULATOR_PREFIX = "EMULATOR_";
  private static final String DEVICE_ID_SERIAL_NUMBER_PREFIX = "SERIAL_NUMBER_";

  private static final Pattern RUN_AS_UNKNOWN_PACKAGE_ERROR_PATTERN = Pattern.compile("run-as: Package '\\S+' is unknown");

  private AndroidDbUtil() {
  }

  public static boolean uploadDatabase(@NotNull IDevice device,
                                       @NotNull String packageName,
                                       @NotNull String dbName,
                                       boolean external,
                                       @NotNull String localDbPath,
                                       @NotNull final ProgressIndicator progressIndicator,
                                       @NotNull AndroidDbErrorReporter errorReporter) {
    try {
      final SyncService syncService = device.getSyncService();

      try {
        syncService.pushFile(localDbPath, TEMP_REMOTE_DB_PATH, new MySyncProgressMonitor(progressIndicator));
      }
      finally {
        syncService.close();
      }
      final String remoteDbPath = getDatabaseRemoteFilePath(packageName, dbName, external);
      final String remoteDbDirPath = remoteDbPath.substring(0, remoteDbPath.lastIndexOf('/'));

      MyShellOutputReceiver outputReceiver = new MyShellOutputReceiver(progressIndicator, device);
      device.executeShellCommand(getRunAsPrefix(packageName, external) +
                                 "mkdir " + remoteDbDirPath, outputReceiver,
                                 DB_COPYING_TIMEOUT_SEC, TimeUnit.SECONDS);
      String output = outputReceiver.getOutput();

      if (!output.isEmpty() && !output.startsWith("mkdir failed")) {
        errorReporter.reportError(output);
        return false;
      }
      // recreating is needed for Genymotion emulator (IDEA-114732)
      if (!external && !recreateRemoteFile(device, packageName, remoteDbPath, errorReporter, progressIndicator)) {
        return false;
      }
      outputReceiver = new MyShellOutputReceiver(progressIndicator, device);
      device.executeShellCommand(getRunAsPrefix(packageName, external) + "cat " + TEMP_REMOTE_DB_PATH + " >" + remoteDbPath,
                                 outputReceiver, DB_COPYING_TIMEOUT_SEC, TimeUnit.SECONDS);
      output = outputReceiver.getOutput();

      if (!output.isEmpty()) {
        errorReporter.reportError(output);
        return false;
      }
      progressIndicator.checkCanceled();
    }
    catch (Exception e) {
      errorReporter.reportError(e);
      return false;
    }
    return true;
  }

  @NotNull
  private static String getRunAsPrefix(@NotNull String packageName, boolean external) {
    return external ? "" : "run-as " + packageName + " ";
  }

  private static boolean recreateRemoteFile(IDevice device,
                                            String packageName,
                                            String remotePath,
                                            AndroidDbErrorReporter errorReporter,
                                            ProgressIndicator progressIndicator) throws Exception {
    MyShellOutputReceiver outputReceiver = new MyShellOutputReceiver(progressIndicator, device);
    device.executeShellCommand("run-as " + packageName + " rm " + remotePath,
                               outputReceiver, DB_COPYING_TIMEOUT_SEC, TimeUnit.SECONDS);
    String output = outputReceiver.getOutput();

    if (!output.isEmpty() && !output.startsWith("rm failed")) {
      errorReporter.reportError(output);
      return false;
    }
    outputReceiver = new MyShellOutputReceiver(progressIndicator, device);
    device.executeShellCommand("run-as " + packageName + " touch " + remotePath,
                               outputReceiver, DB_COPYING_TIMEOUT_SEC, TimeUnit.SECONDS);
    output = outputReceiver.getOutput();

    if (!output.isEmpty()) {
      errorReporter.reportError(output);
      return false;
    }
    return true;
  }

  public static boolean downloadDatabase(@NotNull IDevice device,
                                         @NotNull String packageName,
                                         @NotNull String dbName,
                                         boolean external,
                                         @NotNull File localDbFile,
                                         @NotNull final ProgressIndicator progressIndicator,
                                         @NotNull AndroidDbErrorReporter errorReporter) {
    try {
      final MyShellOutputReceiver receiver = new MyShellOutputReceiver(progressIndicator, device);
      device.executeShellCommand(getRunAsPrefix(packageName, external) + "cat " +
                                 getDatabaseRemoteFilePath(packageName, dbName, external) + " >" +
                                 TEMP_REMOTE_DB_PATH, receiver,
                                 DB_COPYING_TIMEOUT_SEC, TimeUnit.SECONDS);
      final String output = receiver.getOutput();

      if (!output.isEmpty()) {
        errorReporter.reportError(output);
        return false;
      }
      progressIndicator.checkCanceled();
      final File parent = localDbFile.getParentFile();

      if (!parent.exists()) {
        if (!parent.mkdirs()) {
          errorReporter.reportError("cannot create directory '" + parent.getPath() + "'");
          return false;
        }
      }
      final SyncService syncService = device.getSyncService();

      try {
        syncService.pullFile(TEMP_REMOTE_DB_PATH, localDbFile.getPath(), new MySyncProgressMonitor(progressIndicator));
      }
      finally {
        syncService.close();
      }
    }
    catch (Exception e) {
      errorReporter.reportError(e);
      return false;
    }
    return true;
  }

  @Nullable
  public static AndroidDbConnectionInfo checkDataSource(@NotNull AndroidDataSource dataSource,
                                                        @NotNull AndroidDebugBridge debugBridge,
                                                        @NotNull AndroidDbErrorReporter errorReporter) {
    final AndroidDataSource.State state = dataSource.getState();
    final String deviceId = state.deviceId;

    if (deviceId == null) {
      errorReporter.reportError("device is not specified");
      return null;
    }
    final IDevice device = getDeviceById(debugBridge, deviceId);

    if (device == null) {
      errorReporter.reportError("device '" + getPresentableNameFromDeviceId(deviceId) + "' is not connected");
      return null;
    }
    if (!device.isOnline()) {
      errorReporter.reportError("the device is not online");
      return null;
    }
    final String packageName = dataSource.getState().packageName;

    if (packageName == null || packageName.length() == 0) {
      errorReporter.reportError("package name is not specified");
      return null;
    }
    final String dbName = dataSource.getState().databaseName;

    if (dbName == null || dbName.length() == 0) {
      errorReporter.reportError("database name is not specified");
      return null;
    }
    return new AndroidDbConnectionInfo(device, packageName, dbName, dataSource.getState().external);
  }

  @Nullable
  private static IDevice getDeviceById(@NotNull AndroidDebugBridge debugBridge, @NotNull String deviceId) {
    for (IDevice device : debugBridge.getDevices()) {
      if (deviceId.equals(getDeviceId(device))) {
        return device;
      }
    }
    return null;
  }


  private static boolean installGetModificationTimeTool(@NotNull IDevice device,
                                                        @NotNull AndroidDbErrorReporter reporter,
                                                        @NotNull ProgressIndicator progressIndicator) {
    String abi = device.getProperty(IDevice.PROP_DEVICE_CPU_ABI);

    if (abi == null) {
      abi = "armeabi";
    }
    String pieDir = arePositionIndependentExecutablesSupported(device) ? "pie" : "non_pie";
    final String urlStr = "/native_tools/" + pieDir + "/" + abi + "/get_modification_time";
    final URL url = AndroidDbUtil.class.getResource(urlStr);

    if (url == null) {
      LOG.error("Cannot find resource " + urlStr);
      return false;
    }
    final String remoteToolPath = TEMP_REMOTE_GET_MODIFICATION_TIME_TOOL_PATH;

    if (!pushGetModificationTimeTool(device, url, reporter, progressIndicator, remoteToolPath)) {
      return false;
    }
    final String chmodResult = executeSingleCommand(device, reporter, "chmod 755 " + remoteToolPath);

    if (chmodResult == null) {
      return false;
    }
    if (!chmodResult.isEmpty()) {
      reporter.reportError(chmodResult);
      return false;
    }
    return true;
  }

  private static boolean pushGetModificationTimeTool(@NotNull IDevice device,
                                                     @NotNull URL url,
                                                     @NotNull AndroidDbErrorReporter reporter,
                                                     @NotNull ProgressIndicator progressIndicator,
                                                     @NotNull String remotePath) {
    final File toolLocalCopy;

    try {
      toolLocalCopy = FileUtil.createTempFile("android_get_modification_time_tool", "tmp");
    }
    catch (IOException e) {
      reporter.reportError(e);
      return false;
    }
    try {
      if (!copyResourceToFile(url, toolLocalCopy, reporter)) {
        return false;
      }

      try {
        final SyncService service = device.getSyncService();
        try {
          service.pushFile(toolLocalCopy.getPath(), remotePath,
                           new MySyncProgressMonitor(progressIndicator));
        }
        finally {
          service.close();
        }
      }
      catch (Exception e) {
        reporter.reportError(e);
        return false;
      }
    }
    finally {
      FileUtil.delete(toolLocalCopy);
    }
    return true;
  }

  private static boolean copyResourceToFile(@NotNull URL url, @NotNull File file, @NotNull AndroidDbErrorReporter reporter) {
    try {
      final InputStream is = new BufferedInputStream(URLUtil.openStream(url));
      final OutputStream os = new BufferedOutputStream(new FileOutputStream(file));

      try {
        FileUtil.copy(is, os);
      }
      finally {
        is.close();
        os.close();
      }
    }
    catch (IOException e) {
      reporter.reportError(e);
      return false;
    }
    return true;
  }

  @Nullable
  public static Long getModificationTime(@NotNull IDevice device,
                                         @NotNull final String packageName,
                                         @NotNull String dbName,
                                         boolean external,
                                         @NotNull AndroidDbErrorReporter errorReporter,
                                         @NotNull ProgressIndicator progressIndicator) {
    final String path = TEMP_REMOTE_GET_MODIFICATION_TIME_TOOL_PATH;
    final String lsResult = executeSingleCommand(device, errorReporter, "ls " + path);

    if (lsResult == null) {
      return null;
    }
    boolean reinstalled = false;

    if (!lsResult.equals(path)) {
      if (!installGetModificationTimeTool(device, errorReporter, progressIndicator)) {
        return null;
      }
      reinstalled = true;
    }
    Long l = doGetModificationTime(device, packageName, dbName, external, errorReporter);

    if (l != null) {
      return l;
    }
    if (!reinstalled) {
      // get_modification_time tools seems to be broken, so reinstall it for future
      installGetModificationTimeTool(device, errorReporter, progressIndicator);
    }
    return null;
  }

  @Nullable
  private static Long doGetModificationTime(@NotNull IDevice device,
                                            @NotNull String packageName,
                                            @NotNull String dbName,
                                            boolean external,
                                            @NotNull AndroidDbErrorReporter errorReporter) {
    String databaseRemoteFilePath = getDatabaseRemoteFilePath(packageName, dbName, external);
    final String command = getRunAsPrefix(packageName, external) + TEMP_REMOTE_GET_MODIFICATION_TIME_TOOL_PATH + " " + databaseRemoteFilePath;
    String s = executeSingleCommand(device, errorReporter, command);

    if (s != null && s.contains("Permission denied")) {
      s = executeSingleCommand(device, errorReporter, TEMP_REMOTE_GET_MODIFICATION_TIME_TOOL_PATH + " " + databaseRemoteFilePath);
    }

    if (s == null) {
      return null;
    }
    try {
      return Long.parseLong(s);
    }
    catch (NumberFormatException e) {
      errorReporter.reportError(s);
      return null;
    }
  }

  @Nullable
  private static String executeSingleCommand(@NotNull IDevice device,
                                             @NotNull AndroidDbErrorReporter errorReporter,
                                             @NotNull String command) {
    final MyShellOutputReceiver receiver = new MyShellOutputReceiver(null, device);

    try {
      device.executeShellCommand(command, receiver, SHELL_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      errorReporter.reportError(e);
      return null;
    }
    return receiver.getOutput();
  }

  @Nullable
  public static String getDeviceId(@NotNull IDevice device) {
    if (device.isEmulator()) {
      String avdName = device.getAvdName();
      return avdName == null ? null : DEVICE_ID_EMULATOR_PREFIX + replaceByDirAllowedName(avdName);
    }
    else {
      final String serialNumber = device.getSerialNumber();

      if (serialNumber != null && serialNumber.length() > 0) {
        return DEVICE_ID_SERIAL_NUMBER_PREFIX + replaceByDirAllowedName(serialNumber);
      }
      final String manufacturer = DevicePropertyUtil.getManufacturer(device, "");
      final String model = DevicePropertyUtil.getModel(device, "");

      if (manufacturer.length() > 0 || model.length() > 0) {
        return replaceByDirAllowedName(manufacturer + "_" + model);
      }
      return null;
    }
  }

  @NotNull
  public static String getPresentableNameFromDeviceId(@NotNull String deviceId) {
    if (deviceId.startsWith(DEVICE_ID_EMULATOR_PREFIX)) {
      return "emulator: " + deviceId.substring(DEVICE_ID_EMULATOR_PREFIX.length());
    }
    if (deviceId.startsWith(DEVICE_ID_SERIAL_NUMBER_PREFIX)) {
      return "serial: " + deviceId.substring(DEVICE_ID_SERIAL_NUMBER_PREFIX.length());
    }
    return deviceId;
  }

  @NotNull
  private static String replaceByDirAllowedName(@NotNull String s) {
    final StringBuilder builder = new StringBuilder();

    for (int i = 0, n = s.length(); i < n; i++) {
      char c = s.charAt(i);

      if (!Character.isJavaIdentifierPart(c)) {
        c = '_';
      }
      builder.append(c);
    }
    return builder.toString();
  }

  private static boolean arePositionIndependentExecutablesSupported(IDevice device) {
    try {
      return Integer.parseInt(device.getProperty(IDevice.PROP_BUILD_API_LEVEL)) >= 16;
    }
    catch (NumberFormatException e) {
      LOG.info(e);
      return false;
    }
  }

  @NotNull
  public static String getInternalDatabasesRemoteDirPath(@NotNull String packageName) {
    return "/data/data/" + packageName + "/databases";
  }

  @NotNull
  public static String getDatabaseRemoteFilePath(@NotNull String packageName, @NotNull String dbName, boolean external) {
    dbName = StringUtil.trimStart(dbName, "/");
    if (!external) {
      return getInternalDatabasesRemoteDirPath(packageName) + "/" + dbName;
    }
    return "$EXTERNAL_STORAGE/Android/data/" + packageName + "/" + dbName;
  }

  private static class MyShellOutputReceiver extends MultiLineReceiver {
    @Nullable private final ProgressIndicator myProgressIndicator;
    private final StringBuilder myOutputBuilder = new StringBuilder();
    private final boolean myAndroid43;

    MyShellOutputReceiver(@Nullable ProgressIndicator progressIndicator, @NotNull IDevice device) {
      myProgressIndicator = progressIndicator;
      myAndroid43 = "18".equals(device.getProperty(IDevice.PROP_BUILD_API_LEVEL));
    }

    @Override
    public void processNewLines(String[] lines) {
      for (String line : lines) {
        String s = line.trim();

        if (s.length() > 0) {
          LOG.debug("ADB_SHELL: " + s);
          if (myOutputBuilder.length() > 0) {
            myOutputBuilder.append('\n');
          }
          myOutputBuilder.append(s);

          if (myAndroid43 && RUN_AS_UNKNOWN_PACKAGE_ERROR_PATTERN.matcher(s).matches()) {
            myOutputBuilder.append(". \nUnfortunately database support doesn't work for Android 4.3 devices because of the bug " +
                                   "https://code.google.com/p/android/issues/detail?id=58373");
          }
        }
      }
    }

    @Override
    public boolean isCancelled() {
      return myProgressIndicator != null && myProgressIndicator.isCanceled();
    }

    @NotNull
    public String getOutput() {
      return myOutputBuilder.toString();
    }
  }

  private static class MySyncProgressMonitor implements SyncService.ISyncProgressMonitor {
    private final ProgressIndicator myProgressIndicator;

    MySyncProgressMonitor(@NotNull ProgressIndicator progressIndicator) {
      myProgressIndicator = progressIndicator;
    }

    @Override
    public void start(int totalWork) {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isCanceled() {
      return myProgressIndicator.isCanceled();
    }

    @Override
    public void startSubTask(String name) {
    }

    @Override
    public void advance(int work) {
    }
  }
}
