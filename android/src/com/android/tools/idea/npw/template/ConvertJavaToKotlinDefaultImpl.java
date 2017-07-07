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
package com.android.tools.idea.npw.template;

import com.android.annotations.NonNull;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of ConvertJavaToKotlinProvider that uses reflection.
 */
public class ConvertJavaToKotlinDefaultImpl implements ConvertJavaToKotlinProvider {

  @NonNull
  @Override
  public String getKotlinVersion() {
    return "1.1.3-2";
  }

  @Override
  public List<PsiFile> convertToKotlin(@NotNull Project project, @NotNull List<PsiJavaFile> psiJavaFiles) {
    try {
      PluginId kotlinPluginId = PluginId.findId("org.jetbrains.kotlin");
      if (kotlinPluginId == null) {
        return Collections.emptyList();
      }

      IdeaPluginDescriptor kotlinPlugin = ObjectUtils.notNull(PluginManager.getPlugin(kotlinPluginId));
      ClassLoader pluginClassLoader = kotlinPlugin.getPluginClassLoader();

      Class<?> java2KotlinActionClass =
        Class.forName("org.jetbrains.kotlin.idea.actions.JavaToKotlinAction", true, pluginClassLoader);
      final Object companion = java2KotlinActionClass.getDeclaredField("Companion").get(null);

      Class<?> java2KotlinActionCompanionClass =
        Class.forName("org.jetbrains.kotlin.idea.actions.JavaToKotlinAction$Companion", true, pluginClassLoader);

      final Method convert2KotlinMethod =
        java2KotlinActionCompanionClass.getMethod("convertFiles", List.class, Project.class, Boolean.TYPE);

      return (List<PsiFile>) convert2KotlinMethod.invoke(companion, psiJavaFiles, project, true);

    }
    catch (Exception e) {
      Logger.getInstance(getClass()).warn(e);
    }
    return Collections.emptyList();
  }

  @Override
  public void configureKotlin(@NotNull Project project) {

  }
}
