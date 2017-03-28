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
  private final boolean myExternal;

  public AndroidDbConnectionInfo(@NotNull IDevice device, @NotNull String packageName, @NotNull String dbName, boolean external) {
    myDevice = device;
    myPackageName = packageName;
    myDbName = dbName;
    myExternal = external;
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

  public boolean isExternal() {
    return myExternal;
  }
}
