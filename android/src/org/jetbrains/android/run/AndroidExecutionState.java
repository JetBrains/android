package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidExecutionState {
  @Nullable
  Collection<IDevice> getDevices();

  @Nullable
  ConsoleView getConsoleView();

  @NotNull
  AndroidRunConfigurationBase getConfiguration();
}
