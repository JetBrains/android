package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ManualTargetChooser {
  /** Re-use the last used devices if we are configured to do so and the online devices have not changed. */
  @NotNull
  public static Collection<IDevice> getLastUsedDevices(@NotNull Project project, int runConfigId, @NotNull DeviceCount deviceCount) {
    DeviceStateAtLaunch devicesToReuse = DevicePickerStateService.getInstance(project).getDevicesUsedInLastLaunch(runConfigId);
    if (devicesToReuse == null) {
      return ImmutableList.of();
    }

    Set<IDevice> onlineDevices = getOnlineDevices(project);
    if (devicesToReuse.matchesCurrentAvailableDevices(onlineDevices)) {
      Collection<IDevice> usedDevices = devicesToReuse.filterByUsed(onlineDevices);
      if (usedDevices.size() == 1 || deviceCount.isMultiple()) {
        return usedDevices;
      }
    }
    return ImmutableList.of();
  }

  public static Set<IDevice> getOnlineDevices(@NotNull Project project) {
    AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(project);
    if (debugBridge == null) {
      return Collections.emptySet();
    }
    return Sets.newHashSet(debugBridge.getDevices());
  }
}
