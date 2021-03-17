// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.database;

import static com.intellij.database.view.DatabaseContextFun.getSelectedElements;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService;
import com.android.tools.idea.explorer.fs.DeviceFileEntry;
import com.intellij.CommonBundle;
import com.intellij.database.psi.DbDataSource;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class AndroidUploadDatabaseAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AndroidUploadDatabaseAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    e.getPresentation().setVisible(project != null && !getSelectedAndroidDataSources(e).isEmpty());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    assert project != null;
    final List<AndroidDataSource> dataSources = getSelectedAndroidDataSources(e);

    final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(project);

    if (debugBridge == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("cannot.connect.to.adb.error"), CommonBundle.getErrorTitle());
      return;
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(project, AndroidBundle.message("android.db.uploading.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (final AndroidDataSource dataSource : dataSources) {
          indicator.setText("Uploading " + dataSource.getName());

          synchronized (AndroidDbUtil.DB_SYNC_LOCK) {
            uploadDatabase(project, dataSource, indicator, debugBridge);
          }
          indicator.checkCanceled();
        }
      }
    });
  }

  @NotNull
  private static List<AndroidDataSource> getSelectedAndroidDataSources(AnActionEvent e) {
    final Set<DbDataSource> dataSourceElements = getSelectedElements(e.getDataContext(), DbDataSource.class);

    if (dataSourceElements.isEmpty()) {
      return Collections.emptyList();
    }
    final List<AndroidDataSource> androidDataSources = new ArrayList<>();

    for (DbDataSource element : dataSourceElements) {
      final Object delegate = element.getDelegate();

      if (delegate instanceof AndroidDataSource) {
        androidDataSources.add((AndroidDataSource)delegate);
      }
    }
    return androidDataSources;
  }

  private static void uploadDatabase(@NotNull Project project,
                                     @NotNull AndroidDataSource dataSource,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull AndroidDebugBridge debugBridge) {
    final Path localDbFilePath = Paths.get(dataSource.buildLocalDbFileOsPath());
    final AndroidDbErrorReporter errorReporter = new AndroidDbErrorReporter(project, dataSource, true);
    final AndroidDbConnectionInfo dbConnectionInfo = AndroidDbUtil.checkDataSource(dataSource, debugBridge, errorReporter);

    if (dbConnectionInfo == null) {
      return;
    }
    final IDevice device = dbConnectionInfo.getDevice();
    final String packageName = dbConnectionInfo.getPackageName();
    final String dbName = dbConnectionInfo.getDbName();
    final String deviceId = AndroidDbUtil.getDeviceId(device);
    final boolean external = dbConnectionInfo.isExternal();

    if (!Files.isRegularFile(localDbFilePath) || deviceId == null) {
      return;
    }

    String databaseRemoteDirPath = AndroidDbUtil.getDatabaseRemoteDirPath(packageName, external);
    String databaseRemoteFilePath = AndroidDbUtil.getDatabaseRemoteFilePath(packageName, dbName, external);
    AdbDeviceFileSystemService adbService = AdbDeviceFileSystemService.getInstance(project);
    AdbDeviceFileSystem fileSystem = new AdbDeviceFileSystem(adbService, device);

    try {
      progress.setIndeterminate(false);
      DeviceFileEntry remoteDbDir = fileSystem.getEntry(databaseRemoteDirPath).get();
      remoteDbDir.uploadFile(localDbFilePath, AndroidDbUtil.wrapAsFileTransferProgress(progress)).get();

      DeviceFileEntry remoteDbFile = fileSystem.getEntry(databaseRemoteFilePath).get();
      final String dbFingerprint = AndroidDbUtil.getFingerprint(remoteDbFile);

      AndroidRemoteDataBaseManager.MyDatabaseInfo dbInfo = AndroidRemoteDataBaseManager.
        getInstance().getDatabaseInfo(deviceId, packageName, dbName, external);

      if (dbInfo == null) {
        dbInfo = new AndroidRemoteDataBaseManager.MyDatabaseInfo();
      }
      dbInfo.fingerprint = dbFingerprint;
      AndroidRemoteDataBaseManager.getInstance().setDatabaseInfo(deviceId, packageName, dbName, dbInfo, external);
    }
    catch (Exception e) {
      String message = "Failed to upload local file " + localDbFilePath.toString() +
                       " to device " + deviceId +
                       ", remote path: " + databaseRemoteFilePath +
                       ", reason: " + e.getMessage();

      errorReporter.reportError(message);
      LOG.warn(message, e);
    }
  }
}
