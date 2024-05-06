package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.intellij.CommonBundle;
import com.intellij.database.SynchronizeHandler;
import com.intellij.database.psi.DbDataSource;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class AndroidSynchronizeHandler extends SynchronizeHandler {

  @Override
  public void synchronizationStarted(@NotNull final Project project,
                                     @NotNull Set<DbDataSource> elements) {
    final List<AndroidDataSource> dataSourcesToSync = new ArrayList<>();

    for (DbDataSource element : elements) {
      final Object delegate = element.getDelegate();

      if (delegate instanceof AndroidDataSource) {
        dataSourcesToSync.add((AndroidDataSource)delegate);
      }
    }

    if (dataSourcesToSync.isEmpty()) {
      return;
    }
    final Set<AndroidDataSource> syncedDataSources = doSynchronize(project, dataSourcesToSync);

    for (Iterator<DbDataSource> it = elements.iterator(); it.hasNext(); ) {
      final DbDataSource element = it.next();
      final Object delegate = element.getDelegate();

      if (delegate instanceof AndroidDataSource && !syncedDataSources.contains(delegate)) {
        it.remove();
      }
    }
  }

  @NotNull
  public static Set<AndroidDataSource> doSynchronize(@NotNull final Project project,
                                                     @NotNull final Collection<AndroidDataSource> dataSourcesToSync) {
    final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(project);

    if (debugBridge == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("cannot.connect.to.adb.error"), CommonBundle.getErrorTitle());
      return Collections.emptySet();
    }
    final Set<AndroidDataSource> syncedDataSources = Collections.synchronizedSet(new HashSet<>(dataSourcesToSync));
    final MySynchronizeDataSourcesTask task = new MySynchronizeDataSourcesTask(project, debugBridge, syncedDataSources);
    ProgressManager.getInstance().run(task);
    return syncedDataSources;
  }

  private static void doSynchronizeDataSource(@NotNull Project project,
                                              @NotNull AndroidDataSource dataSource,
                                              @NotNull ProgressIndicator progressIndicator,
                                              @NotNull AndroidDebugBridge debugBridge,
                                              @NotNull AndroidDbErrorReporter errorReporter) {
    final AndroidDbConnectionInfo dbConnectionInfo = AndroidDbUtil.checkDataSource(dataSource, debugBridge, errorReporter);

    if (dbConnectionInfo == null) {
      return;
    }
    final IDevice device = dbConnectionInfo.getDevice();
    final String deviceId = AndroidDbUtil.getDeviceId(device);

    if (deviceId == null) {
      return;
    }
    final String packageName = dbConnectionInfo.getPackageName();
    final String dbName = dbConnectionInfo.getDbName();
    final boolean external = dbConnectionInfo.isExternal();

    final Long modificationTime = AndroidDbUtil.getModificationTime(
      device, packageName, dbName, external, errorReporter, progressIndicator);
    progressIndicator.checkCanceled();

    if (modificationTime == null) {
      return;
    }
    final AndroidRemoteDataBaseManager remoteDbManager = AndroidRemoteDataBaseManager.getInstance();
    AndroidRemoteDataBaseManager.MyDatabaseInfo info =
      remoteDbManager.getDatabaseInfo(deviceId, packageName, dbName, external);

    if (info == null) {
      info = new AndroidRemoteDataBaseManager.MyDatabaseInfo();
    }
    progressIndicator.checkCanceled();

    final File localDbFile = new File(dataSource.buildLocalDbFileOsPath());
    info.referringProjects.add(FileUtil.toCanonicalPath(project.getBasePath()));

    if (!localDbFile.exists() || !modificationTime.equals(info.modificationTime)) {
      if (AndroidDbUtil.downloadDatabase(device, packageName, dbName, external, localDbFile, progressIndicator, errorReporter)) {
        info.modificationTime = modificationTime;
        remoteDbManager.setDatabaseInfo(deviceId, packageName, dbName, info, external);
      }
    }
  }

  private static class MySynchronizeDataSourcesTask extends Task.Modal {
    private final Project myProject;
    private final AndroidDebugBridge myDebugBridge;
    private final Set<AndroidDataSource> myDataSources;

    MySynchronizeDataSourcesTask(@NotNull Project project,
                                        @NotNull AndroidDebugBridge debugBridge,
                                        @NotNull Set<AndroidDataSource> dataSources) {
      super(project, AndroidBundle.message("android.db.downloading.progress.title"), true);
      myProject = project;
      myDebugBridge = debugBridge;
      myDataSources = dataSources;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      for (Iterator<AndroidDataSource> it = myDataSources.iterator(); it.hasNext(); ) {
        final AndroidDataSource dataSource = it.next();
        indicator.setText("Downloading '" + dataSource.getName() + "'");

        synchronized (AndroidDbUtil.DB_SYNC_LOCK) {
          final AndroidDbErrorReporter errorReporter = new AndroidDbErrorReporterImpl(myProject, dataSource, false);
          doSynchronizeDataSource(myProject, dataSource, indicator, myDebugBridge, errorReporter);

          if (errorReporter.hasError()) {
            it.remove();
          }
        }
        indicator.checkCanceled();
      }
    }
  }
}
