package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.SyncService;
import com.intellij.javaee.dataSource.LoaderContext;
import com.intellij.javaee.module.view.dataSource.DataSourceUiUtil;
import com.intellij.javaee.module.view.dataSource.LocalDataSource;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.persistence.database.autoconfig.DataSourceConfigUtil;
import com.intellij.persistence.database.autoconfig.MissingDriversNotification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidDbUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDbUtil");

  public static final Object DB_SYNC_LOCK = new Object();
  public static final String TEMP_REMOTE_DB_PATH = "/data/local/tmp/intellij_temp_db_file";
  public static final long DB_COPYING_TIMEOUT_SEC = 30;
  public static final int SHELL_COMMAND_TIMEOUT_SECONDS = 2;
  private static final Pattern LS_L_OUTPUT_PATTERN = Pattern.compile(
    "^([bcdlsp-][-r][-w][-xsS][-r][-w][-xsS][-r][-w][-xstST])\\s+(\\S+)\\s+(\\S+)\\s+" +
    "([\\d\\s,]*)\\s+(\\d{4}-\\d\\d-\\d\\d)\\s+(\\d\\d:\\d\\d)\\s+(.*)$");

  private AndroidDbUtil() {
  }

  public static boolean uploadDatabase(@NotNull IDevice device,
                                       @NotNull String packageName,
                                       @NotNull String dbName,
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
      final String remoteDbPath = "/data/data/" + packageName + "/databases/" + dbName;
      final String remoteDbDirPath = remoteDbPath.substring(0, remoteDbPath.lastIndexOf('/'));

      MyShellOutputReceiver outputReceiver = new MyShellOutputReceiver(progressIndicator);
      device.executeShellCommand("run-as " + packageName + " mkdir " + remoteDbDirPath, outputReceiver,
                                 DB_COPYING_TIMEOUT_SEC, TimeUnit.SECONDS);
      String output = outputReceiver.getOutput();

      if (!output.isEmpty() && !output.startsWith("mkdir failed")) {
        errorReporter.reportError(output);
        return false;
      }
      outputReceiver = new MyShellOutputReceiver(progressIndicator);
      device.executeShellCommand("run-as " + packageName + " cp " + TEMP_REMOTE_DB_PATH + " " + remoteDbPath,
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

  public static boolean downloadDatabase(@NotNull IDevice device,
                                         @NotNull String packageName,
                                         @NotNull String dbName,
                                         @NotNull File localDbFile,
                                         @NotNull final ProgressIndicator progressIndicator,
                                         @NotNull AndroidDbErrorReporter errorReporter) {
    try {
      device.executeShellCommand("touch " + TEMP_REMOTE_DB_PATH, new MyShellOutputReceiver(progressIndicator),
                                 SHELL_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      final MyShellOutputReceiver receiver = new MyShellOutputReceiver(progressIndicator);
      device.executeShellCommand("run-as " + packageName + " cp /data/data/" + packageName + "/databases/" + dbName + " " +
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
    final String deviceSerialNumber = state.getDeviceSerialNumber();

    if (deviceSerialNumber == null) {
      errorReporter.reportError("serial number is not specified");
      return null;
    }
    final IDevice device = getDeviceBySerialNumber(debugBridge, deviceSerialNumber);

    if (device == null) {
      errorReporter.reportError("device with serial number '" + deviceSerialNumber + "' is not connected");
      return null;
    }
    if (!device.isOnline()) {
      errorReporter.reportError("the device is not online");
      return null;
    }
    final String packageName = dataSource.getState().getPackageName();

    if (packageName == null || packageName.length() == 0) {
      errorReporter.reportError("package name is not specified");
      return null;
    }
    final String dbName = dataSource.getState().getDatabaseName();

    if (dbName == null || dbName.length() == 0) {
      errorReporter.reportError("database name is not specified");
      return null;
    }
    return new AndroidDbConnectionInfo(device, packageName, dbName);
  }

  @Nullable
  private static IDevice getDeviceBySerialNumber(@NotNull AndroidDebugBridge debugBridge, @NotNull String serialNumber) {
    for (IDevice device : debugBridge.getDevices()) {
      if (serialNumber.equals(device.getSerialNumber())) {
        return device;
      }
    }
    return null;
  }

  @Nullable
  public static String getRemoteDbModificationTimeAndSize(@NotNull IDevice device,
                                                          @NotNull final String packageName,
                                                          @NotNull String dbName,
                                                          @NotNull AndroidDbErrorReporter errorReporter) {
    final String command = "run-as " + packageName + " ls -l /data/data/" + packageName + "/databases/" + dbName;
    final String result = executeSingleCommand(device, errorReporter, command);
    return result != null ? parseModificationTimeAndSizeFromLsResult(result, errorReporter) : null;
  }

  @Nullable
  static String getRemoteDbMd5Hash(@NotNull IDevice device,
                                   @NotNull final String packageName,
                                   @NotNull String dbName,
                                   @NotNull AndroidDbErrorReporter errorReporter) {
    final String command = "run-as " + packageName + " md5 /data/data/" + packageName + "/databases/" + dbName;
    final String result = executeSingleCommand(device, errorReporter, command);

    if (result == null) {
      return null;
    }
    if (result.startsWith("run-as:")) {
      errorReporter.reportError(result);
      return null;
    }
    final int idx = result.indexOf(' ');

    if (idx < 0) {
      errorReporter.reportError(result);
      return null;
    }
    final String md5Str = result.substring(0, idx);

    if (md5Str.length() != 32) {
      errorReporter.reportError(result);
      return null;
    }
    return md5Str;
  }

  @Nullable
  private static String executeSingleCommand(@NotNull IDevice device,
                                             @NotNull AndroidDbErrorReporter errorReporter,
                                             @NotNull String command) {
    final MyShellOutputReceiver receiver = new MyShellOutputReceiver(null);

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
  private static String parseModificationTimeAndSizeFromLsResult(@NotNull String s, @NotNull AndroidDbErrorReporter errorReporter) {
    final Matcher matcher = LS_L_OUTPUT_PATTERN.matcher(s);

    if (!matcher.matches()) {
      errorReporter.reportError(s);
      return null;
    }
    final String sizeStr = matcher.group(4);
    final String dateStr = matcher.group(5);
    final String timeStr = matcher.group(6);
    final String fullDateStr = dateStr + "|" + timeStr + "|" + sizeStr;

    if (sizeStr.length() == 0 || dateStr.length() == 0 || timeStr.length() == 0) {
      LOG.error("Incorrect ls output: " + fullDateStr);
      return null;
    }
    return fullDateStr;
  }

  public static void detectDriverAndRefresh(@NotNull Project project, @NotNull AndroidDataSource dataSource, boolean create) {
    if (!DataSourceConfigUtil.detectDriverClassPath(project, dataSource.getDriverClass(), dataSource.getClasspathElements())) {
      final String message =
        "<html><body><font face=\"verdana\">\n" +
        "<font size=\"3\">Android SQLite data source is " + (create ? "created" : "updated") + ", but some<br>\n" +
        "additional drivers are required for " + ApplicationNamesInfo.getInstance().getProductName() + "<br>\n" +
        "to connect to the database<br>\n" +
        "<a href=\"create\">Download drivers</a><br>\n" +
        "</font></font></body></html>";
      final MissingDriversNotification notification = new MissingDriversNotification(
        project, Collections.<LocalDataSource>singleton(dataSource),
        AndroidDbManager.NOTIFICATION_GROUP_ID, "Database Driver  Required", message);
      Notifications.Bus.notify(notification, project);
    }
    else {
      DataSourceUiUtil.refreshDatasource(project, true, true, LoaderContext.loadAll(dataSource));
    }
  }

  private static class MyShellOutputReceiver extends MultiLineReceiver {
    @Nullable private final ProgressIndicator myProgressIndicator;
    private final StringBuilder myOutputBuilder = new StringBuilder();

    public MyShellOutputReceiver(@Nullable ProgressIndicator progressIndicator) {
      myProgressIndicator = progressIndicator;
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

    public MySyncProgressMonitor(@NotNull ProgressIndicator progressIndicator) {
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
