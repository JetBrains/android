package org.jetbrains.android.database;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidRemoteDbInfo {
  private final String myDeviceId;
  private final String myPackageName;
  private final String myDatabaseName;
  private final boolean myExternal;

  public AndroidRemoteDbInfo(@NotNull String deviceId, @NotNull String packageName, @NotNull String databaseName, boolean external) {
    myDeviceId = deviceId;
    myPackageName = packageName;
    myDatabaseName = databaseName;
    myExternal = external;
  }

  @NotNull
  public String getDeviceId() {
    return myDeviceId;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public String getDatabaseName() {
    return myDatabaseName;
  }

  public boolean isExternal() {
    return myExternal;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidRemoteDbInfo info = (AndroidRemoteDbInfo)o;

    if (myExternal != info.myExternal) return false;
    if (!myDatabaseName.equals(info.myDatabaseName)) return false;
    if (!myDeviceId.equals(info.myDeviceId)) return false;
    if (!myPackageName.equals(info.myPackageName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDeviceId.hashCode();
    result = 31 * result + myPackageName.hashCode();
    result = 31 * result + myDatabaseName.hashCode();
    result = 31 * result + (myExternal ? 1 : 0);
    return result;
  }
}
