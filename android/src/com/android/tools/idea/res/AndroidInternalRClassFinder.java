/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.android.augment.AndroidInternalRClass;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An element finder which finds the Android internal R class.
 */
public class AndroidInternalRClassFinder extends PsiElementFinder {
  public static final String INTERNAL_PACKAGE_QNAME = "com.android.internal";
  public static final String INTERNAL_R_CLASS_QNAME = INTERNAL_PACKAGE_QNAME + ".R";
  private final Object myLock = new Object();

  private final Map<Sdk, SoftReference<PsiClass>> myInternalRClasses = new HashMap<>();

  public AndroidInternalRClassFinder(@NotNull Project project) {
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(
      ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
        @Override
        public void jdkRemoved(@NotNull final Sdk sdk) {
          synchronized (myLock) {
            myInternalRClasses.remove(sdk);
          }
        }
      });
  }

  private void processInternalRClasses(@NotNull Project project, @NotNull GlobalSearchScope scope, Processor<PsiClass> processor) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      AndroidPlatform platform = sdk == null ? null : AndroidPlatforms.getInstance(sdk);
      PsiClass internalRClass = platform == null ? null : getOrCreateInternalRClass(project, sdk, platform);
      if (internalRClass != null && scope.contains(internalRClass.getContainingFile().getViewProvider().getVirtualFile())) {
        if (!processor.process(internalRClass)) {
          return;
        }
      }
    }
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass[] classes = findClasses(qualifiedName, scope);
    return classes.length == 0 ? null : classes[0];
  }

  private PsiClass getOrCreateInternalRClass(Project project, Sdk sdk, AndroidPlatform platform) {
    synchronized (myLock) {
      PsiClass internalRClass = SoftReference.dereference(myInternalRClasses.get(sdk));

      if (internalRClass == null) {
        internalRClass = new AndroidInternalRClass(PsiManager.getInstance(project), platform, sdk);
        myInternalRClasses.put(sdk, new SoftReference<>(internalRClass));
      }
      return internalRClass;
    }
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    if (project == null || !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (!INTERNAL_R_CLASS_QNAME.equals(qualifiedName)) {
      return PsiClass.EMPTY_ARRAY;
    }

    CommonProcessors.CollectUniquesProcessor<PsiClass> processor = new CommonProcessors.CollectUniquesProcessor<>();
    processInternalRClasses(project, scope, processor);
    Collection<PsiClass> results = processor.getResults();
    return results.isEmpty() ? PsiClass.EMPTY_ARRAY : results.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    if (INTERNAL_PACKAGE_QNAME.equals(psiPackage.getQualifiedName())) {
      return Collections.singleton("R");
    }
    return Collections.emptySet();
  }
}
