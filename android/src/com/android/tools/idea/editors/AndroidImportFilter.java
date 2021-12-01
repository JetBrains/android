/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors;

import com.android.annotations.concurrency.AnyThread;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.CLASS_R;
import static com.android.SdkConstants.CLASS_R_PREFIX;

public class AndroidImportFilter extends ImportFilter {
  /** Never import android.R, or inner classes of application R or android.R classes */
  @Override
  @AnyThread
  public boolean shouldUseFullyQualifiedName(@NotNull PsiFile targetFile, @NotNull String classQualifiedName) {
    if (classQualifiedName.equals(CLASS_R) || classQualifiedName.startsWith(CLASS_R_PREFIX)
        // Data binding: prevent local, generated BR class references during sync or builds (when they
        // temporarily don't exist) to auto-import this BR class for users who have enabled auto-import:
        || classQualifiedName.equals("android.databinding.tool.util.GenerationalClassUtil.ExtensionFilter.BR")) {
      return true;
    }

    int index = classQualifiedName.lastIndexOf('.');
    if (index == classQualifiedName.length() - 2 && classQualifiedName.charAt(index + 1) == 'R') {
      String pkg = getApplicationPackage(targetFile);
      if (pkg != null) {
        // If it's not the application resource class, it's some library resource class; in that case, use
        // fully qualified name
        return !isResourceClassReference(classQualifiedName, pkg + ".R");
      }
    } else if (index > 2 && classQualifiedName.charAt(index - 1) == 'R' && classQualifiedName.charAt(index - 2) == '.') {
      // Inner classes of the R class should *always* use fully qualified imports, but make sure this really does
      // look like an R inner class by making sure it is exactly <something>.R.<resourceType>, e.g.
      //  foo.bar.R.string and foo.bar.R.layout, but not my.weird.R.pkg
      return classQualifiedName.startsWith(CLASS_R) || ResourceType.fromClassName(classQualifiedName.substring(index + 1)) != null;
    }

    return false;
  }

  @Nullable
  private static String getApplicationPackage(@NotNull PsiFile targetFile) {
    AndroidFacet facet = AndroidFacet.getInstance(targetFile);
    if (facet != null) {
      // We need the manifest package here, not the Gradle effective package (which can vary by flavor and build type)
      return ProjectSystemUtil.getModuleSystem(facet).getPackageName();
    }

    return null;
  }

  private static boolean isResourceClassReference(String reference, String resourceClass) {
    return reference.equals(resourceClass) || reference.startsWith(resourceClass + ".");
  }
}
