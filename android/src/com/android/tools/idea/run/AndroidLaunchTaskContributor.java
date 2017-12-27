package com.android.tools.idea.run;

import com.android.tools.idea.run.tasks.LaunchTask;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to provide additional launch tasks to {@code AndroidLaunchTaskProvider}.
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
   */
  @Nullable
  LaunchTask getTask(@NotNull Module module, @NotNull String applicationId);
}
