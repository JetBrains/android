// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.database;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.intellij.CommonBundle;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.view.DatabaseContextFun;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.io.DigestUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.database.view.DatabaseView.getSelectedElements;

public class AndroidUploadDatabaseAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidUploadDatabaseAction");

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
    final Set<DbDataSource> dataSourceElements = DatabaseContextFun.getSelectedDbElementsOfClass(e.getDataContext(), DbDataSource.class);

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
                                     @NotNull ProgressIndicator indicator,
                                     @NotNull AndroidDebugBridge debugBridge) {
    final String localDbFilePath = dataSource.buildLocalDbFileOsPath();
    final AndroidDbErrorReporterImpl errorReporter = new AndroidDbErrorReporterImpl(project, dataSource, true);
    final AndroidDbConnectionInfo dbConnectionInfo = AndroidDbUtil.checkDataSource(dataSource, debugBridge, errorReporter);

    if (dbConnectionInfo == null) {
      return;
    }
    final IDevice device = dbConnectionInfo.getDevice();
    final String packageName = dbConnectionInfo.getPackageName();
    final String dbName = dbConnectionInfo.getDbName();
    final String localFileMd5Hash = getLocalFileMd5Hash(new File(localDbFilePath));
    final String deviceId = AndroidDbUtil.getDeviceId(device);
    final boolean external = dbConnectionInfo.isExternal();

    if (localFileMd5Hash == null || deviceId == null) {
      return;
    }
    if (AndroidDbUtil.uploadDatabase(device, packageName, dbName, external, localDbFilePath, indicator, errorReporter)) {
      final Long modificationTime = AndroidDbUtil.getModificationTime(device, packageName, dbName, external, errorReporter, indicator);

      if (modificationTime != null) {
        AndroidRemoteDataBaseManager.MyDatabaseInfo dbInfo = AndroidRemoteDataBaseManager.
          getInstance().getDatabaseInfo(deviceId, packageName, dbName, external);

        if (dbInfo == null) {
          dbInfo = new AndroidRemoteDataBaseManager.MyDatabaseInfo();
        }
        dbInfo.modificationTime = modificationTime;
        AndroidRemoteDataBaseManager.getInstance().setDatabaseInfo(deviceId, packageName, dbName, dbInfo, external);
      }
    }
  }

  @Nullable
  private static String getLocalFileMd5Hash(@NotNull File file) {
    try {
      final byte[] buffer = new byte[1024];
      final MessageDigest md5 = DigestUtil.md5();
      final InputStream fis = new BufferedInputStream(new FileInputStream(file));
      try {
        int read;

        while ((read = fis.read(buffer)) > 0) {
          md5.update(buffer, 0, read);
        }
      }
      finally {
        fis.close();
      }
      final StringBuilder builder = new StringBuilder();

      for (byte b : md5.digest()) {
        builder.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
      }
      return builder.toString();
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }
}
