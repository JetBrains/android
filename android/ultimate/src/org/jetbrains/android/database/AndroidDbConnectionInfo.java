package org.jetbrains.android.database;

import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class AndroidDbConnectionInfo {
  private final IDevice myDevice;
  private final String myPackageName;
  private final String myDbName;

  public AndroidDbConnectionInfo(@NotNull IDevice device, @NotNull String packageName, @NotNull String dbName) {
    myDevice = device;
    myPackageName = packageName;
    myDbName = dbName;
  }

  @NotNull
  public IDevice getDevice() {
    return myDevice;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public String getDbName() {
    return myDbName;
  }
}
