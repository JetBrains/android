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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.MergedManifest;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpecificActivityLocator extends ActivityLocator {
  @NotNull
  private final AndroidFacet myFacet;
  @Nullable
  private final String myActivityName;

  public SpecificActivityLocator(@NotNull AndroidFacet facet, @Nullable String activityName) {
    myFacet = facet;
    myActivityName = activityName;
  }

  @NotNull
  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) {
    assert myActivityName != null; // validated by validate
    return myActivityName;
  }

  @Override
  public void validate() throws ActivityLocatorException {
    if (myActivityName == null || myActivityName.length() == 0) {
      throw new ActivityLocatorException(AndroidBundle.message("activity.class.not.specified.error"));
    }

    Module module = myFacet.getModule();
    Project project = module.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
    if (activityClass == null) {
      throw new ActivityLocatorException(AndroidBundle.message("cant.find.activity.class.error"));
    }

    PsiClass c = JavaExecutionUtil.findMainClass(project, myActivityName, GlobalSearchScope.projectScope(project));

    if (c == null || !c.isInheritor(activityClass, true)) {
      final ActivityAlias activityAlias = findActivityAlias(myFacet, myActivityName);
      if (activityAlias == null) {
        throw new ActivityLocatorException(AndroidBundle.message("not.activity.subclass.error", myActivityName));
      }

      if (!ActivityLocatorUtils.containsLauncherIntent(activityAlias.getIntentFilters())) {
        throw new ActivityLocatorException(AndroidBundle.message("activity.not.launchable.error", AndroidUtils.LAUNCH_ACTION_NAME));
      }

      // valid activity alias
      return;
    }

    if (doesPackageContainMavenProperty(myFacet)) {
      return;
    }

    // check whether activity is declared in the manifest
    List<Activity> activities = MergedManifest.get(module).getActivities();
    Activity activity = AndroidDomUtil.getActivityDomElementByClass(activities, c);
    if (activity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("activity.not.declared.in.manifest", c.getName()));
    }
  }

  @Nullable
  private static ActivityAlias findActivityAlias(@NotNull AndroidFacet facet, @NotNull final String qualifiedName) {
    final List<ActivityAlias> aliases = MergedManifest.get(facet).getActivityAliases();

    return ApplicationManager.getApplication().runReadAction(new Computable<ActivityAlias>() {
      @Override
      public ActivityAlias compute() {
        for (ActivityAlias alias : aliases) {
          if (qualifiedName.equals(ActivityLocatorUtils.getQualifiedName(alias))) {
            return alias;
          }
        }
        return null;
      }
    });
  }

  private static boolean doesPackageContainMavenProperty(@NotNull AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return false;
    }
    final String aPackage = manifest.getPackage().getStringValue();
    return aPackage != null && aPackage.contains("${");
  }
}
