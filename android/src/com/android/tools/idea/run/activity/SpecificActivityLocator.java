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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

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

    PsiClass c = JavaExecutionUtil.findMainClass(project, myActivityName, GlobalSearchScope.projectScope(project));
    Element element;
    if (c == null || !c.isInheritor(activityClass, true)) {
      element = MergedManifest.get(module).findActivityAlias(myActivityName);
      if (element == null) {
        throw new ActivityLocatorException(AndroidBundle.message("not.activity.subclass.error", myActivityName));
      }
    }
    else {
      // check whether activity is declared in the manifest
      element = MergedManifest.get(module).findActivity(ActivityLocatorUtils.getQualifiedActivityName(c));
      if (element == null) {
        throw new ActivityLocatorException(AndroidBundle.message("activity.not.declared.in.manifest", c.getName()));
      }
    }

    DefaultActivityLocator.ActivityWrapper activity = DefaultActivityLocator.ActivityWrapper.get(element);
    Boolean exported = activity.getExported();

    // if the activity is not explicitly exported, and it doesn't have an intent filter, then it cannot be launched
    if (!Boolean.TRUE.equals(exported) && !activity.hasIntentFilter()) {
      throw new ActivityLocatorException(AndroidBundle.message("specific.activity.not.launchable.error"));
    }
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
