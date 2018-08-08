// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.database;

import com.intellij.database.autoconfig.DataSourceConfigUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class AndroidDataSourceProjectComponent implements ProjectComponent {
  @NotNull private final Project myProject;

  public AndroidDataSourceProjectComponent(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    DataSourceConfigUtil.refreshTablesInBackground(myProject, AndroidDataSourceStorage.getInstance(myProject).getDataSources());
  }

  @Override
  public void projectClosed() {
    if (!ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)) {
      return;
    }
    final String basePath = FileUtil.toCanonicalPath(myProject.getBasePath());

    if (basePath != null) {
      final Set<AndroidRemoteDbInfo> infos = collectAllUsedDatabases();
      AndroidRemoteDataBaseManager.getInstance().updateDbUsagesForProject(basePath, infos);
    }
  }

  @NotNull
  private Set<AndroidRemoteDbInfo> collectAllUsedDatabases() {
    final Set<AndroidRemoteDbInfo> result = new HashSet<>();

    for (AndroidDataSource source : AndroidDataSourceStorage.getInstance(myProject).getDataSources()) {
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
