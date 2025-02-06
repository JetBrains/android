/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.multiDexKeep;

import com.android.tools.idea.lang.multiDexKeep.psi.MultiDexKeepClassName;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.jvm.util.JvmClassUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference from {@link MultiDexKeepClassName} to the corresponding {@link PsiClass}.
 */
public class MultiDexKeepReference extends PsiReferenceBase<MultiDexKeepClassName> {

  public MultiDexKeepReference(@NotNull MultiDexKeepClassName psiElement) {
    super(psiElement, TextRange.allOf(psiElement.getText()));
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    Project project = myElement.getProject();
    String name = myElement.getText();
    if (!name.endsWith(".class")) {
      return null;
    }

    String qualifiedName =
      name
        .substring(0, name.lastIndexOf(".class"))
        .replace('/', '.')
        .replace('$', '.');
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
  }


  @NotNull
  @Override
  public Object[] getVariants() {
    Module module = ModuleUtilCore.findModuleForPsiElement(myElement);
    if (module == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    JdkOrderEntry jdkOrderEntry = ContainerUtil.findInstance(ModuleRootManager.getInstance(module).getOrderEntries(), JdkOrderEntry.class);
    if (jdkOrderEntry == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    // Classes from the project and libraries are packaged into the APK, but SDK classes are not so it makes no sense to show them.
    GlobalSearchScope scope =
      myElement.getResolveScope().intersectWith(GlobalSearchScope.notScope(new JdkScope(module.getProject(), jdkOrderEntry)));

    ArrayList<LookupElement> result = new ArrayList<>();
    AllClassesSearch.search(scope, myElement.getProject()).asIterable().forEach(psiClass -> {
      String qualifiedName = JvmClassUtil.getJvmClassName(psiClass);
      if (qualifiedName != null) {
        result.add(JavaLookupElementBuilder.forClass(psiClass, qualifiedName.replace(".", "/").concat(".class")));
      }
    });

    return result.toArray();
  }
}
