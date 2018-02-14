package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.ActivityLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
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
   * @param module        The {@link Module} this task is to run within.
   * @param applicationId Identifier of the application triggering this task. May be used to determine if the application has ended and
   *                      some portion of your task needs to be cleaned up.
   * @param launchOptions A collection of options that are related to the current launch.
   */
  @Nullable
  LaunchTask getTask(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions);

  /**
   * Returns additional options to be used with "am start" command, e.g the options are used in:
   * {@link ActivityLaunchTask#getStartActivityCommand(IDevice, LaunchStatus, ConsolePrinter)}.
   *
   * @param module        The {@link Module} this task is to run within.
   * @param applicationId Identifier of the application triggering this task. May be used to determine if the application has ended and
   *                      some portion of your task needs to be cleaned up.
   * @param launchOptions A collection of options that are related to the current launch.
   * @param device        The device where this application is launching.
   */
  @NotNull
  default String getAmStartOptions(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions,
                                   @NotNull IDevice device) {
    return "";
  }
}
