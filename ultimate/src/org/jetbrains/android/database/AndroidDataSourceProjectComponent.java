package org.jetbrains.android.database;

import com.intellij.database.autoconfig.DataSourceConfigUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDataSourceProjectComponent extends AbstractProjectComponent {


  public AndroidDataSourceProjectComponent(@NotNull Project project) {
    super(project);
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
    final Set<AndroidRemoteDbInfo> result = new HashSet<AndroidRemoteDbInfo>();

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


  @NotNull
  @Override
  public String getComponentName() {
    return "AndroidDataSourceProjectComponent";
  }
}
