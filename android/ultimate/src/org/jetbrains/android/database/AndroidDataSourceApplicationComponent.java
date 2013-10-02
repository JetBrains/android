package org.jetbrains.android.database;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDataSourceApplicationComponent implements ApplicationComponent {
  @Override
  public void initComponent() {
    AndroidRemoteDataBaseManager.getInstance().processRemovedProjects();
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "AndroidDataSourceApplicationComponent";
  }
}
