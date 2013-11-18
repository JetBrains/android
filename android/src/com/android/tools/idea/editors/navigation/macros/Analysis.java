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

import com.android.navigation.*;
import com.android.tools.idea.editors.navigation.NavigationEditor;
import com.android.tools.idea.editors.navigation.NavigationEditorPanel;
import com.android.tools.idea.editors.navigation.Utilities;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.navigation.Utilities.getPropertyName;
import static com.android.tools.idea.editors.navigation.Utilities.getMethodsByName;

public class Analysis {
  @Nullable
  public static String getInnerText(String s) {
    if (s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    assert false;
    return null;
  }

  @Nullable
  private static FragmentEntry findFragmentByTag(Collection<FragmentEntry> l, String tag) {
    for (FragmentEntry fragment : l) {
      if (tag.equals(fragment.tag)) {
        return fragment;
      }
    }
    return null;
  }

  public static NavigationModel deriveAndAddTransitions(final NavigationModel model, Project project, VirtualFile file) {
    final Module module = Utilities.getModule(project, file);
    final Map<String, ActivityState> activities = getActivities(model);
    final Map<String, MenuState> menus = getMenus(model);

    PsiMethod methodCallMacro = getMethodsByName(module, "com.android.templates.GeneralTemplates", "call")[0];
    final PsiMethod defineAssignment = getMethodsByName(module, "com.android.templates.GeneralTemplates", "defineAssignment")[0];
    PsiMethod defineInnerClassMacro = getMethodsByName(module, "com.android.templates.GeneralTemplates", "defineInnerClass")[0];

    PsiMethod installMenuItemClickMacro =
      getMethodsByName(module, "com.android.templates.InstallListenerTemplates", "installMenuItemClick")[0];
    PsiMethod installItemClickMacro =
      getMethodsByName(module, "com.android.templates.InstallListenerTemplates", "installItemClickListener")[0];

    PsiMethod getMenuItemMacro = getMethodsByName(module, "com.android.templates.MenuAccessTemplates", "getMenuItem")[0];
    PsiMethod launchActivityMacro = getMethodsByName(module, "com.android.templates.LaunchActivityTemplates", "launchActivity")[0];
    PsiMethod launchActivityMacro2 = getMethodsByName(module, "com.android.templates.LaunchActivityTemplates", "launchActivity")[1];

    final MultiMatch installItemClickAndCallMacro = new MultiMatch(installItemClickMacro);
    installItemClickAndCallMacro.addSubMacro("$f", methodCallMacro);

    final MultiMatch installMenuItemOnGetMenuItemAndLaunchActivityMacro = new MultiMatch(installMenuItemClickMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$menuItem", getMenuItemMacro);
    installMenuItemOnGetMenuItemAndLaunchActivityMacro.addSubMacro("$f", launchActivityMacro);

    final MultiMatch defineInnerClassToLaunchActivityMacro = new MultiMatch(defineInnerClassMacro);
    defineInnerClassToLaunchActivityMacro.addSubMacro("$f", launchActivityMacro2);

    final Collection<ActivityState> activityStates = activities.values();

    if (NavigationEditor.DEBUG) System.out.println("activityStates = " + activityStates);

    for (ActivityState state : activityStates) {
      String className = state.getClassName();
      final ActivityState finalState = state;

      if (NavigationEditor.DEBUG) System.out.println("className = " + className);

      // Look for menu inflation
      {
        PsiJavaCodeReferenceElement menu = Utilities.getReferenceElement(module, className, "onCreateOptionsMenu");
        if (menu != null) {
          MenuState menuState = menus.get(menu.getLastChild().getText());
          addTransition(model, new Transition("click", new Locator(state), new Locator(menuState)));

          // Look for menu bindings
          {
            PsiMethod[] methods = getMethodsByName(module, className, "onPrepareOptionsMenu");
            if (methods.length == 1) {
              PsiMethod onPrepareOptionsMenuMethod = methods[0];
              PsiStatement[] statements = onPrepareOptionsMenuMethod.getBody().getStatements();
              if (NavigationEditor.DEBUG) System.out.println("statements = " + Arrays.toString(statements));
              for (PsiStatement s : statements) {
                MultiMatch.Result multiMatchResult = installMenuItemOnGetMenuItemAndLaunchActivityMacro.match(s.getFirstChild());
                if (multiMatchResult != null) {
                  Map<String, Map<String, PsiElement>> subBindings = multiMatchResult.subBindings;
                  ActivityState activityState =
                    activities.get(getQualifiedName(subBindings.get("$f").get("activityClass").getFirstChild()));
                  if (activityState != null) {
                    String menuItemName = subBindings.get("$menuItem").get("$id").getLastChild()
                      .getText(); // e.g. $id=PsiReferenceExpression:R.id.action_account
                    addTransition(model, new Transition("click", Locator.of(menuState, menuItemName), new Locator(activityState)));
                  }
                }
              }
            }
          }
        }
      }

      // Examine fragments associated with this activity
      String xmlFileName = NavigationEditorPanel.getXMLFileName(module, state);
      XmlFile psiFile = (XmlFile)NavigationEditorPanel.getPsiFile(false, xmlFileName, file, project);
      final List<FragmentEntry> fragments = getFragmentEntries(psiFile);

      for (FragmentEntry fragment : fragments) {
        PsiClass fragmentClass = Utilities.getPsiClass(module, fragment.className);
        PsiMethod[] methods = getMethodsByName(module, fragment.className, "installListeners");
        if (methods.length == 1) {
          PsiMethod installListenersMethod = methods[0];
          PsiStatement[] statements = installListenersMethod.getBody().getStatements();
          for (PsiStatement s : statements) {
            MultiMatch.Result multiMatchResult = installItemClickAndCallMacro.match(s.getFirstChild());
            if (multiMatchResult != null) {
              final Map<String, PsiElement> bindings = multiMatchResult.bindings;
              final Map<String, Map<String, PsiElement>> subBindings = multiMatchResult.subBindings;
              final PsiElement $target = subBindings.get("$f").get("$target");
              fragmentClass.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                  //if (DEBUG) System.out.println("$target = " + $target);
                  //if (DEBUG) System.out.println("expression = " + expression);
                  if (expression.getLExpression().getText().equals($target.getText())) {
                    PsiExpression rExpression = expression.getRExpression();
                    if (NavigationEditor.DEBUG) System.out.println("expression.getRExpression() = " + rExpression);
                    Map<String, PsiElement> bindings3 = Unifier.match(defineAssignment, rExpression);
                    if (bindings3 != null) {
                      if (NavigationEditor.DEBUG) System.out.println("bindings3 = " + bindings3);
                      PsiElement fragmentLiteral = bindings3.get("$fragmentName");
                      if (fragmentLiteral instanceof PsiLiteralExpression) {
                        String fragmentTag = getInnerText(fragmentLiteral.getText());
                        FragmentEntry fragment = findFragmentByTag(fragments, fragmentTag);
                        if (fragment != null) {
                          addTransition(model, new Transition("click", Locator.of(finalState, null),
                                                              Locator.of(finalState, fragment.tag))); // e.g. "messageFragment"
                          return;
                        }
                      }
                    }
                    MultiMatch.Result multiMatchResult1 = defineInnerClassToLaunchActivityMacro.match(rExpression);
                    if (multiMatchResult1 != null) {
                      PsiElement activityClass = multiMatchResult1.subBindings.get("$f").get("activityClass").getFirstChild();
                      State toState = activities.get(getQualifiedName(activityClass));
                      if (NavigationEditor.DEBUG) System.out.println("toState = " + toState);
                      if (toState != null) {
                        PsiElement $listView = bindings.get("$listView");
                        String viewName = $listView == null ? null : getPropertyName(removeTrailingParens($listView.getText()));
                        addTransition(model, new Transition("click", Locator.of(finalState, viewName), new Locator(toState)));
                      }
                    }
                  }
                }
              });
            }
          }
        }
      }
    }
    return model;
  }

  private static List<FragmentEntry> getFragmentEntries(XmlFile psiFile) {
    final List<FragmentEntry> fragments = new ArrayList<FragmentEntry>();
    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (tag.getName().equals("fragment")) {
          String fragmentTag = tag.getAttributeValue("android:tag");
          String fragmentClassName = tag.getAttributeValue("android:name");
          if (NavigationEditor.DEBUG) System.out.println("fragmentClassName = " + fragmentClassName);
          fragments.add(new FragmentEntry(fragmentTag, fragmentClassName));
        }
      }
    });
    return fragments;
  }

  private static String removeTrailingParens(String text) {
    return text.endsWith("()") ? text.substring(0, text.length() - 2) : text;
  }

  private static boolean addTransition(NavigationModel model, Transition transition) {
    if (NavigationEditor.DEBUG) System.out.println("Adding transition: " + transition);
    return model.add(transition);
  }

  @Nullable
  private static String getQualifiedName(PsiElement element) {
    if (element instanceof PsiTypeElement) {
      PsiTypeElement psiTypeElement = (PsiTypeElement)element;
      PsiType type1 = psiTypeElement.getType();
      if (type1 instanceof PsiClassReferenceType) {
        PsiClassReferenceType type = (PsiClassReferenceType)type1;
        PsiClass resolve = type.resolve();
        if (resolve != null) {
          return resolve.getQualifiedName();
        }
      }
    }
    return null;
  }

  private static Map<String, MenuState> getMenus(NavigationModel model) {
    Map<String, MenuState> menus = new HashMap<String, MenuState>();
    for (State state : model.getStates()) {
      if (state instanceof MenuState) {
        MenuState menuState = (MenuState)state;
        menus.put(state.getXmlResourceName(), menuState);
      }
    }
    return menus;
  }

  private static Map<String, ActivityState> getActivities(NavigationModel model) {
    Map<String, ActivityState> activities = new HashMap<String, ActivityState>();
    for (State state : model.getStates()) {
      if (state instanceof ActivityState) {
        ActivityState activityState = (ActivityState)state;
        activities.put(state.getClassName(), activityState);
      }
    }
    return activities;
  }
}
