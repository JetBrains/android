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

import com.android.tools.idea.editors.navigation.Utilities;
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
  private static final String DEFINE_ASSIGNMENT =
    "void macro(String $fragmentName, Void $messageController) {" +
    "    getFragment($fragmentName, $messageController);" +
    "}";

  private static final String DEFINE_INNER_CLASS =
    "void macro(Class $Interface, Void $method, Class $Type, Object $arg, final Statement $f) {" +
    "    new $Interface() {" +
    "        public void $method($Type $arg) {" +
    "            $f.$();" +
    "        }" +
    "    };" +
    "}";

  private static final String INSTALL_CLICK_LISTENER =
    "void macro(View $view, Statement $f) {" +
    "    $view.setOnClickListener(new View.OnClickListener() {" +
    "        @Override" +
    "        public void onClick(View view) {" +
    "            $f.$();" +
    "        }" +
    "    });" +
    "}";

  private static final String INSTALL_MENU_ITEM_CLICK =
    "void macro(MenuItem $menuItem, final Statement $f, final boolean $consume) {" +
    "    $menuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {" +
    "        @Override" +
    "        public boolean onMenuItemClick(MenuItem menuItem) {" +
    "            $f.$();" +
    "            return $consume;" +
    "        }" +
    "    });" +
    "}";

  private static final String INSTALL_ITEM_CLICK_LISTENER =
    "void macro(ListView $listView, final Statement $f) {" +
    "    $listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {" +
    "        @Override" +
    "        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {" +
    "            $f.$$();" +
    "        }" +
    "    });" +
    "}";

  private static final String GET_MENU_ITEM =
    "void macro(Menu $menu, int $id) {" +
    "    $menu.findItem($id);" +
    "}";

  private static final String LAUNCH_ACTIVITY =
    "void macro(Context context, Class activityClass) {" +
    "    context.startActivity(new Intent(context, activityClass));" +
    "}";

  private static final String LAUNCH_ACTIVITY_WITH_ARG =
    "<T extends Serializable> void macro(Context context, Class activityClass, String name, T value) {" +
    "    context.startActivity(new Intent(context, activityClass).putExtra(name, value));" +
    "}";

  public final PsiMethod defineAssignment;

  public final MultiMatch createIntent;
  public final MultiMatch installClickAndCallMacro;
  public final MultiMatch installItemClickAndCallMacro;
  public final MultiMatch installMenuItemOnGetMenuItemAndLaunchActivityMacro;
  public final MultiMatch defineInnerClassToLaunchActivityMacro;
  public final MultiMatch findViewById;
  public final MultiMatch findFragmentByTag;
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

  private PsiMethod getMethodFromText(String definition) {
    return Utilities.createMethodFromText(myProject, definition);
  }

  private Macros(Project project) {
    myProject = project;
    defineAssignment = getMethodFromText(DEFINE_ASSIGNMENT);
    PsiMethod defineInnerClassMacro = getMethodFromText(DEFINE_INNER_CLASS);

    PsiMethod installClickMacro = getMethodFromText(INSTALL_CLICK_LISTENER);
    PsiMethod installMenuItemClickMacro = getMethodFromText(INSTALL_MENU_ITEM_CLICK);
    PsiMethod installItemClickMacro = getMethodFromText(INSTALL_ITEM_CLICK_LISTENER);

    PsiMethod getMenuItemMacro = getMethodFromText(GET_MENU_ITEM);

    PsiMethod launchActivityMacro = getMethodFromText(LAUNCH_ACTIVITY);
    PsiMethod launchActivityMacro2 = getMethodFromText(LAUNCH_ACTIVITY_WITH_ARG);

    createIntent = createMacro("void macro(Context context, Class activityClass) { new Intent(context, activityClass); }");
    installClickAndCallMacro = new MultiMatch(installClickMacro);
    installItemClickAndCallMacro = new MultiMatch(installItemClickMacro);

    findViewById = createMacro("void findViewById(int $id) { findViewById(R.id.$id);}");
    findFragmentByTag = createMacro("void findViewById(void $fragmentManager, int $tag) { $fragmentManager.findFragmentByTag($tag);}");

    installMenuItemOnGetMenuItemAndLaunchActivityMacro = new MultiMatch(installMenuItemClickMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$menuItem", getMenuItemMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$f", launchActivityMacro);

    defineInnerClassToLaunchActivityMacro = new MultiMatch(defineInnerClassMacro);
    defineInnerClassToLaunchActivityMacro.addSubMacro("$f", launchActivityMacro2);
  }
}
