/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.facet;

import static com.android.tools.lint.checks.AnnotationDetector.RESTRICT_TO_ANNOTATION;

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for connecting PsiClass instances to layout view tag names
 */
public class LayoutViewClassUtils {
  // Utility class, no constructor
  private LayoutViewClassUtils() { }

  @NotNull
  public static String[] getTagNamesByClass(@NotNull final PsiClass c, final int apiLevel) {
    return ApplicationManager.getApplication().runReadAction((Computable<String[]>)() -> {
      String name = c.getName();
      if (name == null || !isViewClassVisibleAsTag(c)) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }

      String qualifiedName = c.getQualifiedName();
      if (qualifiedName == null) {
        return new String[]{name};
      }

      if (IdeResourcesUtil.isClassPackageNeeded(qualifiedName, c, apiLevel)) {
        return new String[]{qualifiedName};
      }
      return new String[]{name, qualifiedName};
    });
  }

  @Nullable
  public static PsiClass findClassByTagName(@NotNull AndroidFacet facet, @NotNull String name, @NotNull PsiClass baseClass) {
    final Module module = facet.getModule();
    final Project project = module.getProject();

    if (!name.contains(".")) {
      final PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(name, module.getModuleWithLibrariesScope());
      final int apiLevel = AndroidModuleInfo.getInstance(facet).getModuleMinApi();

      for (PsiClass aClass : classes) {
        final String qualifiedName = aClass.getQualifiedName();

        if (qualifiedName != null &&
            !IdeResourcesUtil.isClassPackageNeeded(qualifiedName, baseClass, apiLevel) &&
            InheritanceUtil.isInheritorOrSelf(aClass, baseClass, true)) {
          return aClass;
        }
      }
    }
    else {
      final PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(
        name, ProjectSystemUtil.getModuleSystem(module).getResolveScope(ScopeType.MAIN));

      for (PsiClass aClass : classes) {
        if (InheritanceUtil.isInheritorOrSelf(aClass, baseClass, true)) {
          return aClass;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass findVisibleClassByTagName(@NotNull AndroidFacet facet, @NotNull String name, @NotNull String baseClassQName) {
    PsiClass aClass = findClassByTagName(facet, name, baseClassQName);
    if (aClass != null && isViewClassVisibleAsTag(aClass)) return aClass;
    return null;
  }

  @Nullable
  public static PsiClass findClassByTagName(@NotNull AndroidFacet facet, @NotNull String name, @NotNull String baseClassQName) {
    final PsiClass baseClass = JavaPsiFacade.getInstance(facet.getModule().getProject()).findClass(
      baseClassQName, facet.getModule().getModuleWithLibrariesScope());
    return baseClass != null ? findClassByTagName(facet, name, baseClass) : null;
  }

  public static boolean isViewClassVisibleAsTag(@NotNull PsiClass aClass) {
    PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList == null) {
      return false; // not public
    }
    boolean isPublic = modifierList.hasModifierProperty(PsiModifier.PUBLIC);
    boolean isRestricted = modifierList.hasAnnotation(RESTRICT_TO_ANNOTATION.oldName()) ||
                           modifierList.hasAnnotation(RESTRICT_TO_ANNOTATION.newName());
    return isPublic && !isRestricted;
  }
}
