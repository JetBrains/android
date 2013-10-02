package org.jetbrains.android.database;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidRemoteDbInfo {
  private final String myDeviceSerialNumber;
  private final String myPackageName;
  private final String myDatabaseName;

  public AndroidRemoteDbInfo(@NotNull String deviceSerialNumber, @NotNull String packageName, @NotNull String databaseName) {
    myDeviceSerialNumber = deviceSerialNumber;
    myPackageName = packageName;
    myDatabaseName = databaseName;
  }

  @NotNull
  public String getDeviceSerialNumber() {
    return myDeviceSerialNumber;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public String getDatabaseName() {
    return myDatabaseName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidRemoteDbInfo info = (AndroidRemoteDbInfo)o;

    if (!myDatabaseName.equals(info.myDatabaseName)) return false;
    if (!myDeviceSerialNumber.equals(info.myDeviceSerialNumber)) return false;
    if (!myPackageName.equals(info.myPackageName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDeviceSerialNumber.hashCode();
    result = 31 * result + myPackageName.hashCode();
    result = 31 * result + myDatabaseName.hashCode();
    return result;
  }
}
