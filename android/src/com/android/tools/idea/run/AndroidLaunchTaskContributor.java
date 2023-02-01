package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.tasks.ActivityLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.intellij.execution.Executor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to provide additional launch tasks to {@code AndroidLaunchTasksProvider}.
 */
public interface AndroidLaunchTaskContributor {

  ExtensionPointName<AndroidLaunchTaskContributor> EP_NAME =
    ExtensionPointName.create("com.android.run.androidLaunchTaskContributor");

  /**
   * Returns the appropriate launch task, or {@code null} if no applicable task for the given {@code module} and {@code applicationId}.
   *
   * @param applicationId Identifier of the application triggering this task. May be used to determine if the application has ended and
   *                      some portion of your task needs to be cleaned up.
   * @param configuration A configuration that is launching.
   * @param device        The device where this application is launching.
   * @param executor      Executor
   */
  @Nullable
  LaunchTask getTask(@NotNull String applicationId, @NotNull AndroidRunConfigurationBase configuration,
                     @NotNull IDevice device, @NotNull Executor executor);

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
