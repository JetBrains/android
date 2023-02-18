/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Application id provider for non-Gradle projects.
 */
public class NonGradleApplicationIdProvider implements ApplicationIdProvider {
  @NotNull
  private final AndroidFacet myFacet;

  public NonGradleApplicationIdProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @Override
  @NotNull
  public String getPackageName() throws ApkProvisionException {
    return computePackageName(myFacet);
  }

  @Override
  public String getTestPackageName() throws ApkProvisionException {
    return computePackageName(myFacet);
  }

  @NotNull
  public static String computePackageName(@NotNull final AndroidFacet facet) throws ApkProvisionException {
    if (facet.getProperties().USE_CUSTOM_MANIFEST_PACKAGE) {
      return facet.getProperties().CUSTOM_MANIFEST_PACKAGE;
    }
    else {
      String pkg = ProjectSystemUtil.getModuleSystem(facet).getPackageName();
      if (pkg == null || pkg.isEmpty()) {
        throw new ApkProvisionException("[" + facet.getModule().getName() + "] Unable to obtain main package from manifest.");
      }
      return pkg;
    }
  }
}
