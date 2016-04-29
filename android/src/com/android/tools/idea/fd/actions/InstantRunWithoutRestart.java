/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.fd.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunPushFailedException;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Action which performs an instant run, without restarting
 */
public class InstantRunWithoutRestart extends AnAction {
  public InstantRunWithoutRestart() {
    this("Perform Instant Run", AndroidIcons.RunIcons.Replay);
  }

  protected InstantRunWithoutRestart(String title, @NotNull Icon icon) {
    super(title, null, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      return;
    }
    perform(module);
  }

  private void perform(@NotNull Module module) {
    if (!InstantRunSettings.isInstantRunEnabled()) {
      return;
    }
    Project project = module.getProject();
    List<IDevice> devices = InstantRunManager.findDevices(project);
    InstantRunManager manager = InstantRunManager.get(project);
    for (IDevice device : devices) {
      if (!InstantRunGradleUtils.getIrSupportStatus(module, device.getVersion()).success) {
        continue;
      }

      if (InstantRunManager.isAppInForeground(device, module)) {
        if (InstantRunManager.buildTimestampsMatch(device, module)) {
          performUpdate(manager, device, getUpdateMode(), module, project);
        } else {
          new InstantRunUserFeedback(module).postText(
            NotificationType.INFORMATION, "Local Gradle build id doesn't match what's installed on the device; full build required"
          );
        }
        break;
      }
    }
  }

  private static void performUpdate(@NotNull InstantRunManager manager,
                                    @NotNull IDevice device,
                                    @NotNull UpdateMode updateMode,
                                    @Nullable Module module,
                                    @NotNull Project project) {
    AndroidFacet facet = InstantRunGradleUtils.findAppModule(module, project);
    if (facet != null) {
      AndroidGradleModel model = AndroidGradleModel.get(facet);
      if (model != null) {
        runGradle(manager, device, model, facet, updateMode);
      }
    }
  }

  private static void runGradle(@NotNull final InstantRunManager manager,
                                @NotNull final IDevice device,
                                @NotNull final AndroidGradleModel model,
                                @NotNull final AndroidFacet facet,
                                @NotNull final UpdateMode updateMode) {
    final Project project = facet.getModule().getProject();
    final GradleInvoker invoker = GradleInvoker.getInstance(project);

    final Ref<GradleInvoker.AfterGradleInvocationTask> reference = Ref.create();
    final GradleInvoker.AfterGradleInvocationTask task = new GradleInvoker.AfterGradleInvocationTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        // Get rid of listener. We should add more direct task listening to the GradleTasksExecutor; this
        // seems race-condition and unintentional side effect prone.
        invoker.removeAfterGradleInvocationTask(reference.get());

        // Build is done: send message to app etc

        InstantRunBuildInfo buildInfo = InstantRunGradleUtils.getBuildInfo(model);
        if (buildInfo != null) {
          try {
            manager.pushArtifacts(device, facet, updateMode, buildInfo);
          } catch (InstantRunPushFailedException e) {
            Logger.getInstance(InstantRunWithoutRestart.class).warn(e);
          }
        }
      }
    };
    reference.set(task);
    invoker.addAfterGradleInvocationTask(task);
    String taskName = InstantRunGradleUtils.getIncrementalDexTask(model, facet.getModule());
    invoker.executeTasks(Collections.singletonList(taskName));
  }

  @NotNull
  protected UpdateMode getUpdateMode() {
    return UpdateMode.HOT_SWAP;
  }
}
