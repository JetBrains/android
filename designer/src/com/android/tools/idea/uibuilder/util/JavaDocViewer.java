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
package com.android.tools.idea.uibuilder.util;

import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

public class JavaDocViewer extends ApplicationComponent.Adapter {

  public static JavaDocViewer getInstance() {
    return ServiceManager.getService(JavaDocViewer.class);
  }

  public void showExternalJavaDoc(@NotNull PsiClass psiClass, @NotNull DataContext context) {
    ExternalJavaDocAction.showExternalJavadoc(psiClass, null, null, context);
  }
}
