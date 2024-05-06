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
package com.android.tools.idea.run.activity;

import static com.android.tools.idea.model.AndroidManifestIndexQueryUtils.queryActivitiesFromManifestIndex;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.ActivitiesAndAliases;
import com.android.tools.idea.model.AndroidManifestIndex;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Function;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpecificActivityLocator extends ActivityLocator {
  @NotNull
  private final AndroidFacet myFacet;
  @Nullable
  private final String myActivityName;

  @NotNull
  private final GlobalSearchScope mySearchScope;

  public SpecificActivityLocator(@NotNull AndroidFacet facet, @Nullable String activityName, @NotNull GlobalSearchScope searchScope) {
    myFacet = facet;
    myActivityName = activityName;
    mySearchScope = searchScope;
  }

  public SpecificActivityLocator(@NotNull AndroidFacet facet, @Nullable String activityName) {
    this(facet, activityName, GlobalSearchScope.projectScope(facet.getModule().getProject()));
  }

  @NotNull
  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) {
    assert myActivityName != null; // validated by validate
    return myActivityName;
  }

  /**
   * Try to query from {@link AndroidManifestIndex}.
   * @throws ActivityLocatorException if the specified activity is invalid
   */
  @Override
  public void validate() throws ActivityLocatorException {
    if (myActivityName == null || myActivityName.isEmpty()) {
      throw new ActivityLocatorException(AndroidBundle.message("activity.class.not.specified.error"));
    }

    if (doesPackageContainMavenProperty(myFacet)) {
      return;
    }

    Module module = myFacet.getModule();
    Project project = module.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));

    if (activityClass == null) {
      throw new ActivityLocatorException(AndroidBundle.message("cant.find.activity.class.error"));
    }

    PsiClass specifiedActivityClass = JavaExecutionUtil.findMainClass(project, myActivityName, mySearchScope);
    validateBasedOnManifestIndex(activityClass, specifiedActivityClass);
  }

  private void validateBasedOnManifestIndex(@NotNull PsiClass activityClass, @Nullable PsiClass specifiedActivityClass)
    throws ActivityLocatorException {
    if (DumbService.isDumb(myFacet.getModule().getProject())) {
      return;
    }

    ActivitiesAndAliases activityWrappers = queryActivitiesFromManifestIndex(myFacet);

    DefaultActivityLocator.ActivityWrapper specifiedActivity;
    if (specifiedActivityClass == null || !specifiedActivityClass.isInheritor(activityClass, true)) {
      specifiedActivity = activityWrappers.findAliasByName(myActivityName);
      if (specifiedActivity == null) {
        throw new ActivityLocatorException(AndroidBundle.message("not.activity.subclass.error", myActivityName));
      }
    }
    else {
      // check whether activity is declared in the manifest
      String qualifiedActivityName = ActivityLocatorUtils.getQualifiedActivityName(specifiedActivityClass);
      specifiedActivity = activityWrappers.findActivityByName(qualifiedActivityName);
      if (specifiedActivity == null) {
        throw new ActivityLocatorException(AndroidBundle.message("activity.not.declared.in.manifest", specifiedActivityClass.getName()));
      }
    }

    // if the activity is not explicitly exported, and it doesn't have an intent filter, then it cannot be launched
    if (!specifiedActivity.isLogicallyExported()) {
      throw new ActivityLocatorException(AndroidBundle.message("specific.activity.not.launchable.error"));
    }
  }

  private static boolean doesPackageContainMavenProperty(@NotNull AndroidFacet facet) {
    final Manifest manifest = Manifest.getMainManifest(facet);

    if (manifest == null) {
      return false;
    }
    final String aPackage = ApplicationManager.getApplication().runReadAction(
      (Computable<String>)() -> manifest.getPackage().getStringValue()
    );
    return aPackage != null && aPackage.contains("${");
  }
}