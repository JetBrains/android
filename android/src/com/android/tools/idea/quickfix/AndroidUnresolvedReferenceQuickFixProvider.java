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
package com.android.tools.idea.quickfix;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.jarFinder.FindJarFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

public class AndroidUnresolvedReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {

  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement reference, @NotNull QuickFixActionRegistrar registrar) {
    Module contextModule = findModuleForPsiElement(reference);
    if (contextModule == null) {
      return;
    }

    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(contextModule);
    if (gradleFacet == null) {
      return;
    }

    GradleBuildFile gradleBuildFile = GradleBuildFile.get(contextModule);
    if (gradleBuildFile == null) {
      return;
    }

    PsiFile contextFile = reference.getContainingFile();
    if (contextFile == null) {
      return;
    }

    // Since this is a gradle android project, we need to unregister:
    //  "add module dependency fix",
    //  "add junit to module quick fix",
    //  "add library to module quick fix",
    //  "add jar from web quick fix",
    // since those quick fixes would make the iml file and the gradle file out of sync.
    registrar.unregister(new Condition<IntentionAction>() {
      @Override
      public boolean value(IntentionAction intentionAction) {
        return intentionAction instanceof OrderEntryFix || intentionAction instanceof FindJarFix;
      }
    });

    // Currently our API doesn't address the case that gradle.build file does not exist at the module folder, so just skip for now.
    if (getGradleBuildFile(contextModule) == null) {
      return;
    }

    // TODO implement a quickfix that could properly "add junit dependency", "add library dependency" to the gradle file.

    PsiElement psiElement = reference.getElement();
    String referenceName = reference.getRangeInElement().substring(psiElement.getText());
    Project project = psiElement.getProject();

    // Check if we could fix it by introduce gradle dependency.
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    List<PsiClass> allowedDependencies = filterAllowedDependencies(psiElement, classes);

    if (!allowedDependencies.isEmpty()) {
      classes = allowedDependencies.toArray(new PsiClass[allowedDependencies.size()]);
      registrar.register(new AddGradleProjectDependencyFix(contextModule, contextFile.getVirtualFile(), classes, reference));
    }
  }

  // Duplicated from com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix.filterAllowedDependencies
  @NotNull
  private static List<PsiClass> filterAllowedDependencies(@NotNull PsiElement element, @NotNull PsiClass[] classes) {
    DependencyValidationManager dependencyValidationManager = DependencyValidationManager.getInstance(element.getProject());
    PsiFile fromFile = element.getContainingFile();
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (dependencyValidationManager.getViolatorDependencyRule(fromFile, psiClass.getContainingFile()) == null) {
        result.add(psiClass);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
