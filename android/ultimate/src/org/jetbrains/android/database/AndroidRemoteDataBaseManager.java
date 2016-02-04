package org.jetbrains.android.database;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
@State(name = "AndroidRemoteDataBaseManager", storages = @Storage("androidRemoteDatabases.xml"))
public class AndroidRemoteDataBaseManager implements PersistentStateComponent<AndroidRemoteDataBaseManager.State> {
  private State myState = new State();

  public synchronized static AndroidRemoteDataBaseManager getInstance() {
    return ServiceManager.getService(AndroidRemoteDataBaseManager.class);
  }

  @NotNull
  public static String buildLocalDbFileOsPath(@Nullable String deviceId,
                                              @Nullable String packageName,
                                              @Nullable String databaseName,
                                              boolean external) {
    if (deviceId == null ||
        packageName == null ||
        databaseName == null ||
        deviceId.length() == 0 ||
        packageName.length() == 0 ||
        databaseName.length() == 0) {
      return "";
    }
    return AndroidUtils.getAndroidSystemDirectoryOsPath() +
           File.separator + "databases" +
           File.separator + deviceId +
           File.separator + packageName +
           File.separator + (external ? "external" : "internal") +
           File.separator + databaseName;
  }

  public synchronized void processRemovedProjects() {
    final List<MyDbKey> keysToRemove = new ArrayList<MyDbKey>();

    for (Map.Entry<MyDbKey, MyDatabaseInfo> entry : myState.databases.entrySet()) {
      final MyDbKey key = entry.getKey();
      final MyDatabaseInfo value = entry.getValue();

      for (Iterator<String> it = value.referringProjects.iterator(); it.hasNext(); ) {
        final String projectBasePath = it.next();

        if (!new File(projectBasePath).exists()) {
          it.remove();
          keysToRemove.add(key);
        }
      }
    }
    removeUnusedDatabases(keysToRemove);
  }

  public synchronized void updateDbUsagesForProject(@NotNull String projectBasePath, @NotNull Set<AndroidRemoteDbInfo> usedDatabases) {
    final List<MyDbKey> keysToRemove = new ArrayList<MyDbKey>();

    for (Map.Entry<MyDbKey, MyDatabaseInfo> entry : myState.databases.entrySet()) {
      final MyDbKey key = entry.getKey();
      final MyDatabaseInfo value = entry.getValue();

      if (value.referringProjects.contains(projectBasePath) &&
          !usedDatabases.contains(new AndroidRemoteDbInfo(key.deviceId, key.packageName, key.databaseName, key.external))) {
        value.referringProjects.remove(projectBasePath);
        keysToRemove.add(key);
      }
    }
    removeUnusedDatabases(keysToRemove);
  }

  private synchronized void removeUnusedDatabases(@NotNull List<MyDbKey> keysToRemove) {
    final Map<MyDbKey, MyDatabaseInfo> dbMap = myState.databases;

    for (Iterator<MyDbKey> it = keysToRemove.iterator(); it.hasNext(); ) {
      final MyDbKey key = it.next();
      final MyDatabaseInfo value = dbMap.get(key);

      if (value.referringProjects.isEmpty()) {
        it.remove();
        final String localDbPath = buildLocalDbFileOsPath(key.deviceId, key.packageName, key.databaseName, key.external);
        final File localDbFile = new File(localDbPath);

        if (localDbFile.exists()) {
          FileUtil.delete(localDbFile);
        }
      }
    }
  }

  @Nullable
  public synchronized MyDatabaseInfo getDatabaseInfo(@NotNull String deviceId,
                                                     @NotNull String packageName,
                                                     @NotNull String databaseName,
                                                     boolean external) {
    return myState.databases.get(new MyDbKey(deviceId, packageName, databaseName, external));
  }

  public synchronized void setDatabaseInfo(@NotNull String deviceId,
                                           @NotNull String packageName,
                                           @NotNull String databaseName,
                                           @NotNull MyDatabaseInfo databaseInfo,
                                           boolean external) {
    myState.databases.put(new MyDbKey(deviceId, packageName, databaseName, external), databaseInfo);
  }

  @Override
  public synchronized State getState() {
    return myState;
  }

  @Override
  public synchronized void loadState(State state) {
    myState = state;
  }

  public static class State {
    @Tag("databases")
    @MapAnnotation(surroundWithTag = false)
    public Map<MyDbKey, MyDatabaseInfo> databases = new HashMap<MyDbKey, MyDatabaseInfo>();
  }

  @Tag("db-key")
  public static class MyDbKey {
    public String deviceId = "";
    public String packageName = "";
    public String databaseName = "";
    public boolean external;

    public MyDbKey(@NotNull String deviceId, @NotNull String packageName, @NotNull String databaseName, boolean external) {
      this.deviceId = deviceId;
      this.packageName = packageName;
      this.databaseName = databaseName;
      this.external = external;
    }

    public MyDbKey() {
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyDbKey key = (MyDbKey)o;

      if (external != key.external) return false;
      if (databaseName != null ? !databaseName.equals(key.databaseName) : key.databaseName != null) return false;
      if (deviceId != null ? !deviceId.equals(key.deviceId) : key.deviceId != null) return false;
      if (packageName != null ? !packageName.equals(key.packageName) : key.packageName != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = deviceId != null ? deviceId.hashCode() : 0;
      result = 31 * result + (packageName != null ? packageName.hashCode() : 0);
      result = 31 * result + (databaseName != null ? databaseName.hashCode() : 0);
      result = 31 * result + (external ? 1 : 0);
      return result;
    }
  }

  @Tag("db-info")
  public static class MyDatabaseInfo {
    public long modificationTime = 0;
    public Set<String> referringProjects = new HashSet<String>();
  }
}
