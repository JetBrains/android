package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.android.tools.idea.explorer.fs.FileTransferProgress;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidDbUtil {

  public static final Object DB_SYNC_LOCK = new Object();

  private static final String DEVICE_ID_EMULATOR_PREFIX = "EMULATOR_";
  private static final String DEVICE_ID_SERIAL_NUMBER_PREFIX = "SERIAL_NUMBER_";

  private AndroidDbUtil() {
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

  @NotNull
  public static String getInternalDatabasesRemoteDirPath(@NotNull String packageName) {
    return "/data/data/" + packageName + "/databases";
  }

  @NotNull
  private static String getExternalDatabasesRemoteDirPath(@NotNull String packageName) {
    return "$EXTERNAL_STORAGE/Android/data/" + packageName;
  }

  @NotNull
  public static String getDatabaseRemoteFilePath(@NotNull String packageName, @NotNull String dbName, boolean external) {
    dbName = StringUtil.trimStart(dbName, "/");
    return getDatabaseRemoteDirPath(packageName, external) + "/" + dbName;
  }

  @NotNull
  public static String getDatabaseRemoteDirPath(@NotNull String packageName, boolean external) {
    if (external) {
      return getExternalDatabasesRemoteDirPath(packageName);
    }
    else {
      return getInternalDatabasesRemoteDirPath(packageName);
    }
  }

  @NonNls
  public static String getFingerprint(DeviceFileEntry file) {
    return file.getLastModifiedDate().getText() + ":" + file.getSize();
  }

  @NotNull
  static FileTransferProgress wrapAsFileTransferProgress(@NotNull ProgressIndicator progressIndicator) {
    return new FileTransferProgress() {
      @Override
      public void progress(long currentBytes, long totalBytes) {
        progressIndicator.setFraction(((double)currentBytes) / totalBytes);
      }

      @Override
      public boolean isCancelled() {
        return progressIndicator.isCanceled();
      }
    };
  }
}
