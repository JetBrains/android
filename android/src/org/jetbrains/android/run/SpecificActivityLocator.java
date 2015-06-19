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
package org.jetbrains.android.run;

import com.android.tools.idea.model.ManifestInfo;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Application;
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
  protected String getActivityName() {
    assert myActivityName != null; // validated by validate
    return myActivityName;
  }

  @Override
  public void validate(@NotNull AndroidFacet facet) throws ActivityLocatorException {
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

    if (AndroidRunConfiguration.doesPackageContainMavenProperty(myFacet)) {
      return;
    }

    // check whether activity is declared in the manifest
    List<Activity> activities = ManifestInfo.get(module, true).getActivities();
    Activity activity = AndroidDomUtil.getActivityDomElementByClass(activities, c);
    if (activity != null) {
      return;
    }

    Module libModule = null;
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      final Module depModule = depFacet.getModule();
      activities = ManifestInfo.get(depModule, true).getActivities();
      activity = AndroidDomUtil.getActivityDomElementByClass(activities, c);

      if (activity != null) {
        libModule = depModule;
        break;
      }
    }
    if (activity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("activity.not.declared.in.manifest", c.getName()));
    }
    else if (!myFacet.getProperties().ENABLE_MANIFEST_MERGING) {
      throw new ActivityLocatorException(
        AndroidBundle.message("activity.declared.but.manifest.merging.disabled", c.getName(), libModule.getName(), module.getName()));
    }
  }

  @Nullable
  private static ActivityAlias findActivityAlias(@NotNull AndroidFacet facet, @NotNull String name) {
    ActivityAlias alias = doFindActivityAlias(facet, name);

    if (alias != null) {
      return alias;
    }
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      alias = doFindActivityAlias(depFacet, name);

      if (alias != null) {
        return alias;
      }
    }
    return null;
  }

  @Nullable
  private static ActivityAlias doFindActivityAlias(@NotNull AndroidFacet facet, @NotNull String name) {
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return null;
    }
    final Application application = manifest.getApplication();

    if (application == null) {
      return null;
    }
    final String aPackage = manifest.getPackage().getStringValue();

    for (ActivityAlias activityAlias : application.getActivityAliass()) {
      final String alias = activityAlias.getName().getStringValue();

      if (alias != null && alias.length() > 0 && name.endsWith(alias)) {
        String prefix = name.substring(0, name.length() - alias.length());

        if (prefix.endsWith(".")) {
          prefix = prefix.substring(0, prefix.length() - 1);
        }

        if (prefix.length() == 0 || prefix.equals(aPackage)) {
          return activityAlias;
        }
      }
    }
    return null;
  }
}
