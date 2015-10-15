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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.jarFinder.FindJarFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

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

    VirtualFile classVFile = contextFile.getVirtualFile();
    if (classVFile == null) {
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

    PsiElement psiElement = reference.getElement();
    String referenceName = reference.getRangeInElement().substring(psiElement.getText());
    Project project = psiElement.getProject();

    // Check if it is a JUnit class reference.
    if ("TestCase".equals(referenceName) || isAnnotation(psiElement) && isJunitAnnotationName(referenceName, psiElement)) {
      boolean isJunit4 = !referenceName.equals("TestCase");
      String className = isJunit4 ? "org.junit." + referenceName : "junit.framework.TestCase";
      GlobalSearchScope scope = contextModule.getModuleWithDependenciesAndLibrariesScope(true);
      PsiClass found = JavaPsiFacade.getInstance(project).findClass(className, scope);
      if (found == null) {
        registrar.register(new AddGradleJUnitDependencyFix(contextModule, reference, className, isJunit4));
      }
    }

    // Check if it is a JetBrains annotation class reference.
    if (isAnnotation(psiElement) && AnnotationUtil.isJetbrainsAnnotation(referenceName)
        // Ambiguous with Android Support library; in Android projects prefer the support library
        // (which will be handled by a different quickfix)
        && !referenceName.equals(AnnotationUtil.NULLABLE_SIMPLE_NAME)) {
      String className = "org.jetbrains.annotations." + referenceName;
      GlobalSearchScope scope = contextModule.getModuleWithDependenciesAndLibrariesScope(true);
      PsiClass found = JavaPsiFacade.getInstance(project).findClass(className, scope);
      if (found == null) {
        registrar.register(new AddGradleJetbrainsAnnotationFix(contextModule, reference, className));
      }
    }

    // Check if we could fix it by introduce gradle dependency.
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    List<PsiClass> allowedDependencies = filterAllowedDependencies(psiElement, classes);

    if (!allowedDependencies.isEmpty()) {
      classes = allowedDependencies.toArray(new PsiClass[allowedDependencies.size()]);
      registrar.register(new AddGradleProjectDependencyFix(contextModule, classVFile, classes, reference));
    }

    // Check if we could fix it by introduce other library dependency.
    JavaPsiFacade facade = JavaPsiFacade.getInstance(psiElement.getProject());
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    Set<Object> librariesToAdd = Sets.newHashSet();
    for (PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) {
        continue;
      }
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) {
        continue;
      }
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        continue;
      }
      ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(contextModule).getFileIndex();
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry) {
          LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
          Library library = libraryEntry.getLibrary();
          if (library == null) {
            continue;
          }
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) {
            continue;
          }
          VirtualFile jar = files[0];
          if (jar == null || libraryEntry.isModuleLevel() && !librariesToAdd.add(jar) || !librariesToAdd.add(library)) {
            continue;
          }
          OrderEntry entryForFile = moduleFileIndex.getOrderEntryForFile(virtualFile);
          if (entryForFile != null) {
            if (!(entryForFile instanceof ExportableOrderEntry) ||
                ((ExportableOrderEntry)entryForFile).getScope() != DependencyScope.TEST ||
                ModuleRootManager.getInstance(contextModule).getFileIndex().isInTestSourceContent(classVFile)) {
              continue;
            }
          }
          registrar.register(new AddGradleLibraryDependencyFix(libraryEntry, aClass, contextModule, reference));
        }
      }
    }

  }

  // Duplicated from com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix.filterAllowedDependencies
  @NotNull
  private static List<PsiClass> filterAllowedDependencies(@NotNull PsiElement element, @NotNull PsiClass[] classes) {
    DependencyValidationManager dependencyValidationManager = DependencyValidationManager.getInstance(element.getProject());
    PsiFile fromFile = element.getContainingFile();

    List<PsiClass> result = Lists.newArrayList();
    for (PsiClass psiClass : classes) {
      if (dependencyValidationManager.getViolatorDependencyRule(fromFile, psiClass.getContainingFile()) == null) {
        result.add(psiClass);
      }
    }
    return result;
  }

  // Duplicated from com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix.isAnnotation
  private static boolean isAnnotation(@NotNull PsiElement psiElement) {
    return getParentOfType(psiElement, PsiAnnotation.class) != null && PsiUtil.isLanguageLevel5OrHigher(psiElement);
  }

  // Duplicated from com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix.isJunitAnnotationName
  private static boolean isJunitAnnotationName(@NonNls String referenceName, @NotNull PsiElement psiElement) {
    if ("Test".equals(referenceName) || "Ignore".equals(referenceName) || "RunWith".equals(referenceName) ||
        "Before".equals(referenceName) || "BeforeClass".equals(referenceName) ||
        "After".equals(referenceName) || "AfterClass".equals(referenceName)) {
      return true;
    }
    PsiElement parent = psiElement.getParent();
    if (parent != null && !(parent instanceof PsiAnnotation)) {
      PsiReference reference = parent.getReference();
      if (reference != null) {
        String referenceText = parent.getText();
        if (isJunitAnnotationName(reference.getRangeInElement().substring(referenceText), parent)) {
          int lastDot = referenceText.lastIndexOf('.');
          return lastDot > -1 && referenceText.substring(0, lastDot).equals("org.junit");
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
