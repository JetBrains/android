package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A target chooser for selecting a connected USB device (any non-emulator).
 */
public class UsbDeviceTargetChooser implements TargetChooser {
  @NotNull private final AndroidFacet myFacet;
  private final boolean mySupportMultipleDevices;

  public UsbDeviceTargetChooser(@NotNull AndroidFacet facet, boolean supportMultipleDevices) {
    myFacet = facet;
    mySupportMultipleDevices = supportMultipleDevices;
  }

  @Override
  public boolean matchesDevice(@NotNull IDevice device) {
    return !device.isEmulator();
  }

  @Nullable
  @Override
  public DeviceTarget getTarget() {
    Collection<IDevice> runningDevices = DeviceSelectionUtils
      .chooseRunningDevice(myFacet, new TargetDeviceFilter(this), mySupportMultipleDevices);
    if (runningDevices == null) {
      // The user canceled.
      return null;
    }
    return DeviceTarget.forDevices(runningDevices);
  }
}
