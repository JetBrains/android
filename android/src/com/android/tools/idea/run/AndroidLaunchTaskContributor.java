package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.execution.Executor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point to provide additional launch tasks to {@code AndroidLaunchTasksProvider}.
 */
public interface AndroidLaunchTaskContributor {

  ExtensionPointName<AndroidLaunchTaskContributor> EP_NAME =
    ExtensionPointName.create("com.android.run.androidLaunchTaskContributor");

  /**
   * Returns additional options to be used with "am start" command, e.g the options are used in:
   * {@link ActivityLaunchTask#getStartActivityCommand(IDevice, LaunchStatus, com.intellij.execution.ui.ConsoleView)}.
   *
   * @param applicationId Identifier of the application triggering this task. May be used to determine if the application has ended and
   *                      some portion of your task needs to be cleaned up.
   * @param configuration A configuration that is launching.
   * @param device        The device where this application is launching.
   * @param executor      Executor
   */
  @NotNull
  default String getAmStartOptions(@NotNull String applicationId, @NotNull AndroidRunConfigurationBase configuration,
                                   @NotNull IDevice device, @NotNull Executor executor) {
    return "";
  }
}
