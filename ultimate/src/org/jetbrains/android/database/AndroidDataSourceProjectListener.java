// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.database;

import com.intellij.database.autoconfig.DataSourceConfigUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.io.FileUtil;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidDataSourceProjectListener implements ProjectManagerListener {

  @Override
  public void projectOpened(@NotNull Project project) {
    DataSourceConfigUtil.refreshTablesInBackground(project, AndroidDataSourceStorage.getInstance(project).getDataSources());
  }

  @Override
  public void projectClosing(@NotNull Project project) {
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return;
    }
    final String basePath = FileUtil.toCanonicalPath(project.getBasePath());

    if (basePath != null) {
      final Set<AndroidRemoteDbInfo> infos = collectAllUsedDatabases(project);
      AndroidRemoteDataBaseManager.getInstance().updateDbUsagesForProject(basePath, infos);
    }
  }

  @NotNull
  private Set<AndroidRemoteDbInfo> collectAllUsedDatabases(@NotNull Project project) {
    final Set<AndroidRemoteDbInfo> result = new HashSet<>();

    for (AndroidDataSource source : AndroidDataSourceStorage.getInstance(project).getDataSources()) {
      final AndroidDataSource.State s = source.getState();
      String deviceId = s.deviceId;

      if (deviceId == null) {
        deviceId = "";
      }
      result.add(new AndroidRemoteDbInfo(deviceId, s.packageName, s.databaseName, s.external));
    }
    return result;
  }
}
