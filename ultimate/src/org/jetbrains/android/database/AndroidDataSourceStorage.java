package org.jetbrains.android.database;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidDataSourceStorage",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  })
public class AndroidDataSourceStorage implements PersistentStateComponent<AndroidDataSourceStorage.State> {
  private final List<AndroidDataSource> myDataSources = ContainerUtil.createLockFreeCopyOnWriteList();

  public static AndroidDataSourceStorage getInstance(Project project) {
    return ServiceManager.getService(project, AndroidDataSourceStorage.class);
  }

  public State getState() {
    State state = new State();
    for (AndroidDataSource dataSource : myDataSources) {
      state.myDataSources.add(dataSource.buildFullState());
    }
    return state;
  }

  public void loadState(State state) {
    myDataSources.clear();
    for (AndroidDataSource.State dataSourceState : state.myDataSources) {
      AndroidDataSource o = new AndroidDataSource();
      o.loadState(dataSourceState);
      addDataSource(o);
    }
    for (AndroidDataSource o : myDataSources) {
      o.ensureDriverConfigured();
    }
  }

  public List<AndroidDataSource> getDataSources() {
    return new ArrayList<AndroidDataSource>(myDataSources);
  }

  public void removeDataSource(AndroidDataSource dataSource) {
    myDataSources.remove(dataSource);
  }

  public void addDataSource(AndroidDataSource dataSource) {
    dataSource.init();
    myDataSources.add(dataSource);
  }

  public static class State {
    @Tag("data-sources")
    @AbstractCollection(surroundWithTag = false)
    public List<AndroidDataSource.State> myDataSources = new ArrayList<AndroidDataSource.State>();
  }
}
