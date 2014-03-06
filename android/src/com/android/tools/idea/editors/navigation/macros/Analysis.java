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
import com.android.tools.idea.editors.navigation.NavigationView;
import com.android.tools.idea.editors.navigation.Utilities;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.navigation.Utilities.getPropertyName;
import static com.android.tools.idea.editors.navigation.Utilities.getMethodBySignature;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Analysis {
  private static final Logger LOG = Logger.getInstance("#" + Analysis.class.getName());

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
    final Macros macros = Macros.getInstance(module.getProject());

    final Collection<ActivityState> activityStates = activities.values();

    if (NavigationEditor.DEBUG) System.out.println("activityStates = " + activityStates);

    for (ActivityState state : activityStates) {
      String className = state.getClassName();
      final ActivityState finalState = state;

      if (NavigationEditor.DEBUG) System.out.println("className = " + className);

      PsiClass psiClass = Utilities.getPsiClass(module, className);

      if (psiClass == null) {
        // Navigation file is out-of-date and refers to classes that have been deleted. That's okay.
        LOG.info("Class " + className + " not found");
        continue;
      }

      // Look for menu inflation
      {
        PsiJavaCodeReferenceElement menu = Utilities.getReferenceElement(module, className, "onCreateOptionsMenu");
        if (menu != null) {
          MenuState menuState = menus.get(menu.getLastChild().getText());
          addTransition(model, new Transition("click", new Locator(state), new Locator(menuState)));

          // Look for menu bindings
          {
            PsiMethod onPrepareOptionsMenuMethod = getMethodBySignature(psiClass, "public boolean onPrepareOptionsMenu(Menu m)");
            PsiStatement[] statements = onPrepareOptionsMenuMethod.getBody().getStatements();
            if (NavigationEditor.DEBUG) System.out.println("statements = " + Arrays.toString(statements));
            for (PsiStatement s : statements) {
              MultiMatch.Bindings<PsiElement> result = macros.installMenuItemOnGetMenuItemAndLaunchActivityMacro.match(s.getFirstChild());
              if (result != null) {
                ActivityState activityState = activities.get(getQualifiedName(result.get("$f", "activityClass").getFirstChild()));
                if (activityState != null) {
                  // e.g. $id=PsiReferenceExpression:R.id.action_account
                  String menuItemName = result.get("$menuItem", "$id").getLastChild().getText();
                  addTransition(model, new Transition("click", Locator.of(menuState, menuItemName), new Locator(activityState)));
                }
              }
            }
          }
        }
      }

      // Examine fragments associated with this activity
      String xmlFileName = NavigationView.getXMLFileName(module, state);
      XmlFile psiFile = (XmlFile)NavigationView.getPsiFile(false, xmlFileName, file, project);
      final List<FragmentEntry> fragments = getFragmentEntries(psiFile);

      for (FragmentEntry fragment : fragments) {
        PsiMethod installListenersMethod = getMethodBySignature(module, fragment.className, "public void onViewCreated(View v, Bundle b)");
        PsiStatement[] statements = installListenersMethod.getBody().getStatements();
        for (PsiStatement s : statements) {
          final MultiMatch.Bindings<PsiElement> multiMatchResult = macros.installItemClickAndCallMacro.match(s.getFirstChild());
          if (multiMatchResult != null) {
            multiMatchResult.get("$f").accept(new JavaRecursiveElementVisitor() {
              @Override
              public void visitNewExpression(PsiNewExpression expression) {
                MultiMatch.Bindings<PsiElement> exp = macros.createIntentMacro.match(expression);
                PsiElement activityClass = exp.get("activityClass").getFirstChild();
                State toState = activities.get(getQualifiedName(activityClass));
                if (toState != null) {
                  PsiElement $listView = multiMatchResult.get("$listView");
                  String viewName = $listView == null ? null : getPropertyName(removeTrailingParens($listView.getText()));
                  addTransition(model, new Transition("click", Locator.of(finalState, viewName), new Locator(toState)));
                }
              }
            });
          }
        }
      }
    }
    return model;
  }

  private static List<FragmentEntry> getFragmentEntries(@NotNull XmlFile psiFile) {
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
