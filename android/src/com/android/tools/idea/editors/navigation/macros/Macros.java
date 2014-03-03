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

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

public class Macros {
  private static final String TEMPLATES_PACKAGE = /*"com.android.templates.".replace('.', '/')*/ "";
  private static final String GENERAL_TEMPLATES = TEMPLATES_PACKAGE + "GeneralTemplates";
  private static final String LISTENER_TEMPLATES = TEMPLATES_PACKAGE + "InstallListenerTemplates";
  private static final String ACCESS_TEMPLATES = TEMPLATES_PACKAGE + "AccessTemplates";
  private static final String LAUNCH_ACTIVITY_TEMPLATES = TEMPLATES_PACKAGE + "LaunchActivityTemplates";

  public final PsiMethod defineAssignment;

  public final MultiMatch createIntent;
  public final MultiMatch installClickAndCallMacro;
  public final MultiMatch installItemClickAndCallMacro;
  public final MultiMatch installMenuItemOnGetMenuItemAndLaunchActivityMacro;
  public final MultiMatch defineInnerClassToLaunchActivityMacro;
  public final MultiMatch findViewById;
  private static Map<Project, Macros> ourProjectToMacros = new IdentityHashMap<Project, Macros>();
  private final Project myProject;

  private static PsiMethod[] getMethodsByName(String templateName, String methodName, Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = facade.getElementFactory();
    ClassLoader classLoader = Macros.class.getClassLoader();
    try {
      InputStream inputStream = classLoader.getResourceAsStream("/navigationTemplates/" + templateName + ".java.template");
      try {
        int available = inputStream.available();
        byte[] buffer = new byte[available];
        assert available == inputStream.read(buffer);
        String text = new String(buffer);
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        String body = text.substring(start + 1, end);
        PsiClass psiClass = factory.createClassFromText(body, null); //todo consider providing a context
        return psiClass.findMethodsByName(methodName, false);
      }
      finally {
        inputStream.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Macros getInstance(Project project) {
    Macros result = ourProjectToMacros.get(project);
    if (result == null) {
      ourProjectToMacros.put(project, result = new Macros(project));
    }
    return result;
  }

  public MultiMatch createMacro(String methodDefinition) {
    return MultiMatch.create(myProject, methodDefinition);
  }

  private Macros(Project project) {
    myProject = project;
    defineAssignment = getMethodsByName(GENERAL_TEMPLATES, "defineAssignment", project)[0];
    PsiMethod defineInnerClassMacro = getMethodsByName(GENERAL_TEMPLATES, "defineInnerClass", project)[0];

    PsiMethod installClickMacro = getMethodsByName(LISTENER_TEMPLATES, "installClickListener", project)[0];
    PsiMethod installMenuItemClickMacro = getMethodsByName(LISTENER_TEMPLATES, "installMenuItemClick", project)[0];
    PsiMethod installItemClickMacro = getMethodsByName(LISTENER_TEMPLATES, "installItemClickListener", project)[0];

    PsiMethod getMenuItemMacro = getMethodsByName(ACCESS_TEMPLATES, "getMenuItem", project)[0];

    PsiMethod launchActivityMacro = getMethodsByName(LAUNCH_ACTIVITY_TEMPLATES, "launchActivity", project)[0];
    PsiMethod launchActivityMacro2 = getMethodsByName(LAUNCH_ACTIVITY_TEMPLATES, "launchActivity", project)[1];

    createIntent = createMacro("void macro(Context context, Class activityClass) { new Intent(context, activityClass); }");
    installClickAndCallMacro = new MultiMatch(installClickMacro);
    installItemClickAndCallMacro = new MultiMatch(installItemClickMacro);

    findViewById = createMacro("void findViewById(int $id) { findViewById(R.id.$id);}");

    installMenuItemOnGetMenuItemAndLaunchActivityMacro = new MultiMatch(installMenuItemClickMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$menuItem", getMenuItemMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$f", launchActivityMacro);

    defineInnerClassToLaunchActivityMacro = new MultiMatch(defineInnerClassMacro);
    defineInnerClassToLaunchActivityMacro.addSubMacro("$f", launchActivityMacro2);
  }
}
