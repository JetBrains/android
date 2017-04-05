package org.jetbrains.android.database;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDataSourceApplicationComponent implements ApplicationComponent {
  @Override
  public void initComponent() {
    AndroidRemoteDataBaseManager.getInstance().processRemovedProjects();
  }
}
