/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.util.JavaDocViewer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static com.android.SdkConstants.*;

public class ComponentHelpAction extends AnAction {
  private final Project myProject;
  private final Supplier<String> myTagNameSupplier;

  public ComponentHelpAction(@NotNull Project project,
                             @NotNull Supplier<String> tagNameSupplier) {
    myProject = project;
    myTagNameSupplier = tagNameSupplier;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    String tagName = myTagNameSupplier.get();
    if (tagName == null) {
      return;
    }
    PsiClass psiClass = findClassOfTagName(tagName);
    if (psiClass == null) {
      return;
    }
    JavaDocViewer.getInstance().showExternalJavaDoc(psiClass, event.getDataContext());
  }

  @Nullable
  private PsiClass findClassOfTagName(@NotNull String tagName) {
    if (tagName.indexOf('.') != -1) {
      return findClassByClassName(tagName);
    }
    PsiClass psiClass = findClassByClassName(ANDROID_WIDGET_PREFIX + tagName);
    if (psiClass != null) {
      return psiClass;
    }
    psiClass = findClassByClassName(ANDROID_VIEW_PKG + tagName);
    if (psiClass != null) {
      return psiClass;
    }
    return findClassByClassName(ANDROID_WEBKIT_PKG + tagName);
  }

  @Nullable
  private PsiClass findClassByClassName(@NotNull String fullyQualifiedClassName) {
    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(myProject);
    return javaFacade.findClass(fullyQualifiedClassName, GlobalSearchScope.allScope(myProject));
  }
}
