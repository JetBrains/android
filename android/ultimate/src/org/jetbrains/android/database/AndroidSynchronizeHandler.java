package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.intellij.CommonBundle;
import com.intellij.javaee.dataSource.SynchronizeHandler;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.persistence.database.psi.DbDataSourceElement;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSynchronizeHandler extends SynchronizeHandler {

  @Override
  public void synchronizationStarted(@NotNull final Project project,
                                     @NotNull Collection<DbDataSourceElement> elements) {
    final List<AndroidDataSource> dataSourcesToSync = new ArrayList<AndroidDataSource>();

    for (DbDataSourceElement element : elements) {
      final Object delegate = element.getDelegate();

      if (delegate instanceof AndroidDataSource) {
        dataSourcesToSync.add((AndroidDataSource)delegate);
      }
    }

    if (dataSourcesToSync.isEmpty()) {
      return;
    }
    doSynchronize(project, dataSourcesToSync);
  }

  public static void doSynchronize(@NotNull final Project project, @NotNull final List<AndroidDataSource> dataSourcesToSync) {
    final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(project);

    if (debugBridge == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("cannot.connect.to.adb.error"), CommonBundle.getErrorTitle());
      return;
    }
    ProgressManager.getInstance().run(new Task.Modal(project, AndroidBundle.message("android.db.downloading.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {

        for (final AndroidDataSource dataSource : dataSourcesToSync) {
          indicator.setText("Downloading '" + dataSource.getName() + "'");

          synchronized (AndroidDbUtil.DB_SYNC_LOCK) {
            doSynchronizeDataSource(project, dataSource, indicator, debugBridge);
          }
          indicator.checkCanceled();
        }
      }
    });
  }

  private static void doSynchronizeDataSource(@NotNull Project project,
                                              @NotNull AndroidDataSource dataSource,
                                              @NotNull ProgressIndicator progressIndicator,
                                              @NotNull AndroidDebugBridge debugBridge) {
    final AndroidDbErrorReporterImpl errorReporter = new AndroidDbErrorReporterImpl(project, dataSource, false);
    final AndroidDbConnectionInfo dbConnectionInfo = AndroidDbUtil.checkDataSource(dataSource, debugBridge, errorReporter);

    if (dbConnectionInfo == null) {
      return;
    }
    final IDevice device = dbConnectionInfo.getDevice();
    final String deviceSerialNumber = device.getSerialNumber();
    final String packageName = dbConnectionInfo.getPackageName();
    final String dbName = dbConnectionInfo.getDbName();

    final Long modificationTime = AndroidDbUtil.getModificationTime(device, packageName, dbName, errorReporter, progressIndicator);
    progressIndicator.checkCanceled();

    if (modificationTime == null) {
      return;
    }
    final AndroidRemoteDataBaseManager remoteDbManager = AndroidRemoteDataBaseManager.getInstance();
    AndroidRemoteDataBaseManager.MyDatabaseInfo info = remoteDbManager.getDatabaseInfo(deviceSerialNumber, packageName, dbName);

    if (info == null) {
      info = new AndroidRemoteDataBaseManager.MyDatabaseInfo();
    }
    progressIndicator.checkCanceled();

    final File localDbFile = new File(dataSource.buildLocalDbFileOsPath());
    info.referringProjects.add(FileUtil.toCanonicalPath(project.getBasePath()));

    if (!localDbFile.exists() || !modificationTime.equals(info.modificationTime)) {
      if (AndroidDbUtil.downloadDatabase(device, packageName, dbName, localDbFile, progressIndicator, errorReporter)) {
        info.modificationTime = modificationTime;
        remoteDbManager.setDatabaseInfo(deviceSerialNumber, packageName, dbName, info);
      }
    }
  }
}
