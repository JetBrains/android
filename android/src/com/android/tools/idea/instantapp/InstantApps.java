/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import com.android.builder.model.AndroidAtom;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Library;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.model.MergedManifest;
import com.google.common.base.Splitter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_ATOM;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class InstantApps {
  private static final Pattern OLD_ATOM_NAME_PATTERN = Pattern.compile("(?:(?:atom|feature)-)(.+)");
  private static final Pattern NEW_ATOM_NAME_PATTERN = Pattern.compile("(.+)(?:atom|split)");

  /**
   * This method will find and return a base split if one exists and is associated with the given facet.
   *
   * @param facet the {@link AndroidFacet} for the Instant App application module whose base split you want to find.
   * @return The {@link Module} corresponding with the base split or {@code null} if none is found.
   */
  @Nullable
  public static Module findInstantAppBaseSplit(@NotNull AndroidFacet facet) {
    Module baseAtomModule = null;
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    if (facet.getProjectType() == PROJECT_TYPE_INSTANTAPP && androidModuleModel != null) {
      AndroidAtom baseSplit = androidModuleModel.getMainArtifact().getDependencies().getBaseAtom();
      if (baseSplit != null) {
        String project = baseSplit.getProject();
        if (project != null) {
          ReadAction<Module> readAction = new ReadAction<Module>() {

            @Override
            protected void run(@NotNull Result<Module> result) throws Throwable {
              for (Module module : ModuleManager.getInstance(facet.getModule().getProject()).getModules()) {
                GradleFacet facetToCheck = GradleFacet.getInstance(module);
                if (facetToCheck != null) {
                  GradleModuleModel gradleModuleModel = facetToCheck.getGradleModuleModel();
                  if (gradleModuleModel != null && project.equals(gradleModuleModel.getGradlePath())) {
                    result.setResult(module);
                    return;
                  }
                }
              }

              result.setResult(null);
            }
          };
          baseAtomModule = readAction.execute().getResultObject();
        }
      }
    }
    return baseAtomModule;
  }

  /**
   * This method will find and return an instant app module if one exists in the given Project. If the project contains multiple Instant
   * Apps then it will return the first one based on the internal project ordering.
   *
   * @param project the {@link Project} you want to search.
   * @return The found {@link Module} corresponding to an Instant App application module, or {@code null} if none exist.
   */
  @Nullable
  public static Module findInstantAppModule(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.getProjectType() == PROJECT_TYPE_INSTANTAPP) {
        return facet.getModule();
      }
    }
    return null;
  }

  /**
   * Given a project that is known to contain an Instant App, this method will get the base split. In a project with multiple Instant Apps
   * it will get the base split of the project returned by {@link #findInstantAppModule}.
   *
   * @param project the {@link Project} to search.
   * @return The {@link Module} corresponding to the base split.
   */
  @NotNull
  public static Module getBaseSplitInInstantApp(@NotNull Project project) {
    Module instantAppModule = findInstantAppModule(project);
    assert instantAppModule != null;
    AndroidFacet facet = AndroidFacet.getInstance(instantAppModule);
    assert facet != null;
    Module baseSplit = findInstantAppBaseSplit(facet);
    assert baseSplit != null;
    return baseSplit;
  }

  /**
   * Get the package used by all splits that are part of an Instant App.
   *
   * @param baseSplit the {@link Module} corresponding to the base split of an Instant App.
   * @return the package name as a {@link String}.
   */
  @NotNull
  public static String getInstantAppPackage(@NotNull Module baseSplit) {
    AndroidFacet baseSplitFacet = AndroidFacet.getInstance(baseSplit);
    assert baseSplitFacet != null;
    Manifest baseSplitManifest = baseSplitFacet.getManifest();
    assert baseSplitManifest != null;
    String packageValue = baseSplitManifest.getPackage().getStringValue();
    assert packageValue != null;
    return packageValue;
  }

  /**
   * Find the directory where intent-filters and shared resources should be inserted given a specified base split.
   *
   * @param baseSplit the {@link Module} corresponding to the base split.
   * @return The output directory as a {@link String}.
   */
  @NotNull
  public static String getBaseSplitOutDir(@NotNull Module baseSplit) {
    String baseSplitModuleName = baseSplit.getName();
    // TODO: Remove all this code once we have feature modules
    // Until we have feature-modules we need to handle the "fake-split" case where we actually want to add things to the library associated
    // with the base split rather than the base split. If the module name is of type xxxsplit or xxxatom or atom-xxx or feature-xxx then
    // look for xxx or xxxlib or lib-xxx
    String baseName = baseSplitModuleName;
    Matcher newMatcher = NEW_ATOM_NAME_PATTERN.matcher(baseSplitModuleName);
    if (newMatcher.matches()) {
      baseName = newMatcher.group(1);
    }
    else {
      Matcher oldMatcher = OLD_ATOM_NAME_PATTERN.matcher(baseSplitModuleName);
      if (oldMatcher.matches()) {
        baseName = oldMatcher.group(1);
      }
    }

    if (!baseName.equals(baseSplitModuleName)) {
      ModuleManager moduleManager = ModuleManager.getInstance(baseSplit.getProject());
      Module realBaseModule = moduleManager.findModuleByName(baseName);
      if (realBaseModule != null) {
        baseSplit = realBaseModule;
      }
      else {
        realBaseModule = moduleManager.findModuleByName("lib-" + baseName);
        if (realBaseModule != null) {
          baseSplit = realBaseModule;
        }
        else {
          realBaseModule = moduleManager.findModuleByName(baseName + "lib");
          if (realBaseModule != null) {
            baseSplit = realBaseModule;
          }
        }
      }
    }
    AndroidModuleModel baseSplitModel = AndroidModuleModel.get(baseSplit);
    assert baseSplitModel != null;
    return baseSplitModel.getRootDirPath().getAbsolutePath();
  }

  /**
   * Find the Instant App split module that references the target.
   *
   * @param target a {@link Module}.
   * @return Returns the referencing split module if it exists, the target module if the target module is a split, or {@code null} if no
   * suitable module can be  found.
   */
  @Nullable
  public static Module getContainingSplit(@NotNull Module target) {
    AndroidFacet facet = AndroidFacet.getInstance(target);
    if (facet != null && facet.getProjectType() == PROJECT_TYPE_ATOM) {
      return target;
    }
    GradleFacet gradleFacet = GradleFacet.getInstance(target);
    if (gradleFacet == null) {
      return target;
    }

    GradleModuleModel gradleModuleModel = gradleFacet.getGradleModuleModel();
    if (gradleModuleModel == null) {
      return target;
    }

    for (Module otherModule : ModuleManager.getInstance(target.getProject()).getModules()) {
      if (otherModule == target) {
        continue;
      }
      AndroidFacet otherFacet = AndroidFacet.getInstance(otherModule);
      if (otherFacet != null && otherFacet.getProjectType() == PROJECT_TYPE_ATOM) {
        AndroidModuleModel androidModuleModel = AndroidModuleModel.get(otherFacet);
        if (androidModuleModel != null) {
          Dependencies dependencies = androidModuleModel.getMainArtifact().getDependencies();
          if (dependencies.getLibraries()
            .stream()
            .map(Library::getProject)
            .filter(Objects::nonNull)
            .anyMatch(n -> n.equals(gradleModuleModel.getGradlePath()))) {
            return otherModule;
          }
        }
      }
    }
    return null;
  }

  /**
   * Finds the default URL to use for a modules.
   *
   * @param facet the {@link AndroidFacet} of the module.
   * @return The URL to launch the instant app as a {@link String}.
   */
  @NotNull
  public static String getDefaultInstantAppUrl(@NotNull AndroidFacet facet) {
    String defaultUrl = "<<ERROR - NO URL SET>>";
    Module baseSplit = findInstantAppBaseSplit(facet);
    if (baseSplit != null) {
      String foundUrl = new InstantAppUrlFinder(MergedManifest.get(baseSplit)).getDefaultUrl();
      defaultUrl = isEmpty(foundUrl) ? defaultUrl : foundUrl;
    }
    return defaultUrl;
  }

  public static boolean isInstantAppSdkEnabled() {
    return InstantAppSdks.getInstance().isInstantAppSdkEnabled();
  }

  @NotNull
  public static File getInstantAppSdk() throws FileNotFoundException {
    File sdk = InstantAppSdks.getInstance().getInstantAppSdk(true);
    if (sdk == null) {
      throw new FileNotFoundException("Instant App SDK couldn't be found.");
    }

    return sdk;
  }

  public static boolean isInstantAppApplicationModule(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    return model != null && model.getProjectType() == PROJECT_TYPE_INSTANTAPP;
  }

  @Nullable
  @Deprecated
  public static String getInstantAppSdkLocation() {
    try {
      return getInstantAppSdk().getCanonicalPath();
    }
    catch (IOException e) {
      // Ignore
      return null;
    }
  }

  public static boolean isPostO(IDevice device) {
    AndroidVersion version = device.getVersion();

    // Previews of O have api level 25, so comparing with #isGreaterOrEqualThan(apiLevel) doesn't work here.
    return version.compareTo(25, "O") >= 0;
  }

  public static boolean isLoggedInGoogleAccount(@NotNull IDevice device, boolean showDialog) throws Exception {
    // TODO: delete this when Google accounts are not needed anymore

    CountDownLatch latch = new CountDownLatch(1);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
    try {
      device.executeShellCommand("dumpsys account", receiver);
      latch.await(500, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      throw new Exception("Couldn't get account in device", e);
    }

    String output = receiver.getOutput();

    Iterable<String> lines = Splitter.on("\n").split(output);
    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("Account {")) {
        if (line.contains("type=com.google")) {
          return true;
        }
      }
    }

    if (showDialog) {
      ApplicationManager.getApplication().invokeLater(
        () -> Messages.showMessageDialog("Device not logged in a Google account", "Instant App", null));
    }

    return false;
  }
}
