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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
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

    String qualifiedName = name.replace('/', '.').substring(0, name.lastIndexOf(".class"));
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
  }


  @NotNull
  @Override
  public Object[] getVariants() {
    Project project = myElement.getProject();
    PsiPackage rootPackage = JavaPsiFacade.getInstance(project).findPackage("");

    ArrayList<LookupElement> classNames = new ArrayList<>();
    if (rootPackage != null) {
      collectClassNames(rootPackage, GlobalSearchScope.projectScope(project), classNames);
    }

    return classNames.toArray();
  }

  private static void collectClassNames(@NotNull PsiPackage currentPackage,
                                        @NotNull GlobalSearchScope projectScope,
                                        @NotNull ArrayList<LookupElement> classNames) {
    for (PsiClass psiClass : currentPackage.getClasses(projectScope)) {
      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName != null) {
        classNames.add(LookupElementBuilder.create(psiClass, qualifiedName.replace(".", "/").concat(".class")));
      }
    }

    for (PsiPackage childPackage : currentPackage.getSubPackages(projectScope)) {
      collectClassNames(childPackage, projectScope, classNames);
    }
  }
}
