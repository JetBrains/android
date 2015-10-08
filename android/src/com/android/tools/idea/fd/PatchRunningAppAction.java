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
package com.android.tools.idea.fd;

import com.android.ddmlib.IDevice;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.InstalledPatchCache;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;


public class PatchRunningAppAction extends AnAction {
  public PatchRunningAppAction() {
    super("Instant Run: Push Changed Files To Running App Instantly", null, AndroidIcons.FastDeploy);
  }

  /**
   * Checks whether the app associated with the given module is already running on the given device
   *
   * @param device the device to check
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the app is already running and is listening for incremental updates
   */
  public static boolean isAppRunning(@NotNull IDevice device, @NotNull Module module) {
    FastDeployManager manager = FastDeployManager.get(module.getProject());
    return manager.ping(device, module);
  }

  /**
   * Checks whether the app associated with the given module is capable of being run time patched
   * (whether or not it's running). This checks whether we have a Gradle project, and if that
   * Gradle project is using a recent enough Gradle plugin with incremental support, etc. It
   * also checks whether the user has disabled instant run.
   *
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the app is using an incremental support enabled Gradle plugin
   */
  @SuppressWarnings("unused") // Pending integration with Run configuration infrastructure
  public static boolean isPatchableApp(@NotNull Module module) {
    if (!FastDeployManager.isInstantRunEnabled(module.getProject())) {
      return false;
    }

    FastDeployManager manager = FastDeployManager.get(module.getProject());
    AndroidFacet facet = manager.findAppModule(module);
    if (facet == null) {
      return false;
    }

    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model == null) {
      return false;
    }

    String version = model.getAndroidProject().getModelVersion();
    try {
      // Sigh, would be nice to have integer versions to avoid having to do this here
      FullRevision revision = FullRevision.parseRevision(version);

      // Supported in version 1.6 of the Gradle plugin and up
      return revision.getMajor() > 1 || revision.getMinor() >= 6;
    } catch (NumberFormatException ignore) {
      return false;
    }
  }

  /**
   * Performs an incremental update of the app associated with the given module on the given device
   *
   * @param device the device to apply the update to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @param forceRestart if true, force a full restart of the given app (normally false)
   */
  public static void perform(@Nullable final IDevice device, @NotNull final Module module, final boolean forceRestart) {
    if (FastDeployManager.DISPLAY_STATISTICS) {
      FastDeployManager.notifyBegin();
    }

    Project project = module.getProject();
    UpdateMode updateMode = forceRestart ? UpdateMode.COLD_SWAP : UpdateMode.HOT_SWAP;
    FastDeployManager manager = FastDeployManager.get(project);
    manager.performUpdate(device, updateMode, module);
  }

  public static boolean pushChanges(@NotNull final IDevice device, @NotNull final AndroidFacet facet) {
    FastDeployManager manager = FastDeployManager.get(facet.getModule().getProject());
    manager.pushChanges(device, UpdateMode.HOT_SWAP, facet, getLastInstalledArscTimestamp(device, facet));
    return true;
  }

  private static long getLastInstalledArscTimestamp(@NotNull IDevice device, @NotNull AndroidFacet facet) {
    String pkgName;
    try {
      pkgName = ApkProviderUtil.computePackageName(facet);
    }
    catch (ApkProvisionException e) {
      return 0;
    }

    return ServiceManager.getService(InstalledPatchCache.class).getInstalledArscTimestamp(device, pkgName);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    if (module != null) {
      if (!isPatchableApp(module)) {
        FastDeployManager.postBalloon(MessageType.ERROR, "Incremental Support not available in this project -- update Gradle plugin",
                                      module.getProject());
        return;
      }
      boolean forceRestart = (e.getModifiers() & InputEvent.CTRL_MASK) != 0;

      // Save changes first such that Gradle can pick up the changes
      ApplicationManager.getApplication().saveAll();

      perform(null, module, forceRestart);
    }
  }
}
