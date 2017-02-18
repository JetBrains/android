package org.jetbrains.android.database;

import com.intellij.openapi.components.ApplicationComponentAdapter;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDataSourceApplicationComponent implements ApplicationComponentAdapter {
  @Override
  public void initComponent() {
    AndroidRemoteDataBaseManager.getInstance().processRemovedProjects();
  }
}
