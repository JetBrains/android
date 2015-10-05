package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A process for selecting the target used in an Android run configuration launch.
 */
public interface TargetChooser {

  /**
   * @return the target to use, or null if the user cancelled (or there was an error). Null return values will end the launch quietly -
   * if an error needs to be displayed, the target chooser should surface it.
   */
  @Nullable
  DeployTarget getTarget(@NotNull ConsolePrinter printer, @NotNull DeviceCount deviceCount, boolean debug);

  boolean matchesDevice(@NotNull IDevice device);

  /** Performs any validation checks on the setup of this target chooser. */
  @NotNull
  List<ValidationError> validate();
}
