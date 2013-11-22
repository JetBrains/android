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
package com.android.tools.idea.editors.navigation.macros;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiMethod;

import static com.android.tools.idea.editors.navigation.Utilities.getMethodsByName;

public class Macros {
  public final PsiMethod defineAssignment;

  public final MultiMatch installItemClickAndCallMacro;
  public final MultiMatch installMenuItemOnGetMenuItemAndLaunchActivityMacro;
  public final MultiMatch defineInnerClassToLaunchActivityMacro;

  public Macros(Module module) {
    PsiMethod methodCallMacro = getMethodsByName(module, "com.android.templates.GeneralTemplates", "call")[0];
    defineAssignment = getMethodsByName(module, "com.android.templates.GeneralTemplates", "defineAssignment")[0];
    PsiMethod defineInnerClassMacro = getMethodsByName(module, "com.android.templates.GeneralTemplates", "defineInnerClass")[0];

    PsiMethod installMenuItemClickMacro =
      getMethodsByName(module, "com.android.templates.InstallListenerTemplates", "installMenuItemClick")[0];
    PsiMethod installItemClickMacro =
      getMethodsByName(module, "com.android.templates.InstallListenerTemplates", "installItemClickListener")[0];

    PsiMethod getMenuItemMacro = getMethodsByName(module, "com.android.templates.MenuAccessTemplates", "getMenuItem")[0];
    PsiMethod launchActivityMacro = getMethodsByName(module, "com.android.templates.LaunchActivityTemplates", "launchActivity")[0];
    PsiMethod launchActivityMacro2 = getMethodsByName(module, "com.android.templates.LaunchActivityTemplates", "launchActivity")[1];

    installItemClickAndCallMacro = new MultiMatch(installItemClickMacro);
    installItemClickAndCallMacro.addSubMacro("$f", methodCallMacro);

    installMenuItemOnGetMenuItemAndLaunchActivityMacro = new MultiMatch(installMenuItemClickMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$menuItem", getMenuItemMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$f", launchActivityMacro);

    defineInnerClassToLaunchActivityMacro = new MultiMatch(defineInnerClassMacro);
    defineInnerClassToLaunchActivityMacro.addSubMacro("$f", launchActivityMacro2);
  }
}
