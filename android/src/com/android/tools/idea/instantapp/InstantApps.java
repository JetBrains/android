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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.MergedManifest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_ATOM;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class InstantApps {
  /*
  This method will find and return a base split if one exists and is associated with the given AndroidFacet. Otherwise it returns null
   */
  @Nullable
  public static Module findInstantAppBaseSplit(@NotNull AndroidFacet facet) {
    Module baseAtomModule = null;
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    if (facet.getProjectType() == PROJECT_TYPE_INSTANTAPP && androidModuleModel != null) {
      AndroidAtom baseSplit = androidModuleModel.getMainArtifact().getDependencies().getBaseAtom();
      if (baseSplit != null) {
        ReadAction<Module> readAction = new ReadAction<Module>() {

          @Override
          protected void run(@NotNull Result<Module> result) throws Throwable {
            result.setResult(ModuleManager.getInstance(facet.getModule().getProject()).findModuleByName(baseSplit.getAtomName()));
          }
        };
        baseAtomModule = readAction.execute().getResultObject();
      }
    }
    return baseAtomModule;
  }

  /*
  This method will find and return an instant app module if one exists in the given Project. Otherwise it returns null. If the
  project contains multiple Instant Apps then it will return the first one based on the internal project ordering.
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

  /*
  Given a project that is known to contain an Instant App, this method will return the Module corresponding to the base split. In
  a project with multiple Instant Apps it will return the base split of the project returned by findInstantAppModule.
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

  /*
  Given a base split module, return the package that containst all splits in the Instant App
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

  /*
  Given a base split module, return the directory in to which intent-filters and shared resources should be inserted
   */
  @NotNull
  public static String getBaseSplitOutDir(@NotNull Module baseSplit) {
    String baseSplitModuleName = baseSplit.getName();
    // Until we have feature-modules we need to handle the "fake-split" case where we actually want to add things to the library associated
    // with the base split rather than the base split. If the module name is of type xxxsplit or xxxatom then look for xxx or xxxlib
    boolean endsWithSplit = baseSplitModuleName.endsWith("split");
    if (endsWithSplit || baseSplitModuleName.endsWith("atom")) {
      ModuleManager moduleManager = ModuleManager.getInstance(baseSplit.getProject());
      int endIndex = baseSplitModuleName.length() - (endsWithSplit ? 5 : 4);
      String realBaseModuleName = baseSplitModuleName.substring(0, endIndex);
      Module realBaseModule = moduleManager.findModuleByName(realBaseModuleName);
      if (realBaseModule != null) {
        baseSplit = realBaseModule;
      }
      else {
        realBaseModule = moduleManager.findModuleByName(realBaseModuleName + "lib");
        if (realBaseModule != null) {
          baseSplit = realBaseModule;
        }
      }
    }
    AndroidModuleModel baseSplitModel = AndroidModuleModel.get(baseSplit);
    assert baseSplitModel != null;
    return baseSplitModel.getRootDirPath().getAbsolutePath();
  }

  /* Given a target module in a project, find the Instant App split module that references the target. Returns the split module if it
  exists, the target module if the target module is a split, or null if no suitable module can be found.
   */
  @Nullable
  public static Module getContainingSplit(@NotNull Module target) {
    AndroidFacet facet = AndroidFacet.getInstance(target);
    if (facet != null && facet.getProjectType() == PROJECT_TYPE_ATOM) {
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
            .anyMatch(n -> n.equals(":" + target.getName()))) {
            return otherModule;
          }
        }
      }
    }
    return null;
  }

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
}
