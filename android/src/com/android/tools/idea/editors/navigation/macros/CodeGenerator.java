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

import com.android.annotations.NonNull;
import com.android.tools.idea.editors.navigation.model.*;
import com.android.tools.idea.editors.navigation.Utilities;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import org.jetbrains.annotations.NotNull;

public class CodeGenerator {
  private static final String[] FRAMEWORK_IMPORTS = new String[]{
    "android.app.Activity",
    "android.content.Intent",
    "android.os.Bundle"
  };
  public static final String TRANSITION_ADDED = "Transition added";
  public final Module module;
  public final NavigationModel navigationModel;
  public final Listener<String> listener;

  public CodeGenerator(NavigationModel navigationModel, Module module, Listener<String> listener) {
    this.navigationModel = navigationModel;
    this.module = module;
    this.listener = listener;
  }

  private ActivityState getAssociatedActivityState(MenuState menuState) {
    for (Transition t : navigationModel.getTransitions()) {
      if (t.getDestination().getState() == menuState) {
        State state = t.getSource().getState();
        if (state instanceof ActivityState) {
          return (ActivityState)state;

        }
      }
    }
    assert false;
    return null;
  }

  private void addImports(ImportHelper importHelper, PsiJavaFile file, String[] classNames) {
    for (String className : classNames) {
      PsiClass psiClass = Utilities.getPsiClass(module, className);
      if (psiClass != null) {
        importHelper.addImport(file, psiClass);
      }
    }
  }

  private void addImportsAsNecessary(PsiClass psiClass, @NonNull String... classNames) {
    PsiJavaFile file = (PsiJavaFile)psiClass.getContainingFile();
    ImportHelper importHelper = new ImportHelper(CodeStyleSettingsManager.getSettings(module.getProject()));
    addImports(importHelper, file, FRAMEWORK_IMPORTS);
    addImports(importHelper, file, classNames);
  }

  @SuppressWarnings("UnusedParameters")
  private void notifyListeners(PsiClass psiClass) {
    listener.notify(TRANSITION_ADDED);
  }

  public void implementTransition(final Transition transition) {
    Project project = module.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = facade.getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final Macros macros = Macros.getInstance(module.getProject());

    State sourceState = transition.getSource().getState();
    State destinationState = transition.getDestination().getState();
    if (sourceState instanceof MenuState && destinationState instanceof ActivityState) {
      MenuState menuState = (MenuState)sourceState;
      final ActivityState newActivity = (ActivityState)destinationState;
      final ActivityState originatingActivity = getAssociatedActivityState(menuState);
      final PsiClass psiClass = Utilities.getPsiClass(module, originatingActivity.getClassName());
      if (psiClass != null) {
        new WriteCommandAction<Void>(project, "Add navigation transition", psiClass.getContainingFile()) {
          @Override
          protected void run(@NotNull Result<Void> result) {
            PsiMethod signature = factory.createMethodFromText("public boolean onPrepareOptionsMenu(Menu menu){ }", psiClass);
            PsiMethod method = psiClass.findMethodBySignature(signature, false);
            if (method == null) {
              method = factory.createMethodFromText(
                "@Override public boolean onPrepareOptionsMenu(Menu menu){boolean result=super.onPrepareOptionsMenu(menu);return result;}",
                psiClass);
              psiClass.add(method);
              method = psiClass.findMethodBySignature(signature, false); // the previously assigned method is not resolved somehow
              assert method != null;
            }
            String parameterName = method.getParameterList().getParameters()[0].getName();
            PsiCodeBlock body = method.getBody();
            assert body != null;
            PsiStatement[] statements = body.getStatements();
            PsiStatement lastStatement = statements[statements.length - 1];
            MultiMatch macro = macros.installMenuItemOnGetMenuItemAndLaunchActivityMacro;
            MultiMatch.Bindings<String> bindings = new MultiMatch.Bindings<String>();
            bindings.put("$consume", "true");
            bindings.put("$menuItem", "$menu", parameterName);
            bindings.put("$menuItem", "$id", "R.id." + transition.getSource().getViewName());
            bindings.put("$f", "context", originatingActivity.getClassName() + ".this");
            bindings.put("$f", "activityClass", newActivity.getClassName() + ".class");
            String newCode = macro.instantiate(bindings);
            PsiStatement newStatement = factory.createStatementFromText(newCode + ";", body);
            body.addBefore(newStatement, lastStatement);
            addImportsAsNecessary(psiClass, "android.view.Menu", "android.view.MenuItem");
            codeStyleManager.reformat(method);
            notifyListeners(psiClass);
          }
        }.execute();
      }
    }
    if (sourceState instanceof ActivityState && destinationState instanceof MenuState) {
      ActivityState activityState = (ActivityState)sourceState;
      final MenuState menuState = (MenuState)destinationState;
      final PsiClass psiClass = Utilities.getPsiClass(module, activityState.getClassName());
      if (psiClass != null) {
        new WriteCommandAction<Void>(project, "Add navigation transition", psiClass.getContainingFile()) {
          @Override
          protected void run(@NotNull Result<Void> result) {
            PsiMethod signature = factory.createMethodFromText("boolean onCreateOptionsMenu(Menu menu){}", psiClass);
            PsiMethod method = psiClass.findMethodBySignature(signature, false);
            if (method == null) {
              method = factory.createMethodFromText("@Override public boolean onCreateOptionsMenu(Menu menu) { return true;}", psiClass);
              psiClass.add(method);
              method = psiClass.findMethodBySignature(signature, false); // // the previously assigned method is not resolved somehow
              assert method != null;
            }
            String parameterName = method.getParameterList().getParameters()[0].getName();
            PsiCodeBlock body = method.getBody();
            assert body != null;
            PsiStatement[] statements = body.getStatements();
            PsiStatement lastStatement = statements[statements.length - 1];
            String newStatementText = "getMenuInflater().inflate(R.menu.$XmlResourceName, $parameterName);";
            newStatementText = newStatementText.replace("$XmlResourceName", menuState.getXmlResourceName());
            newStatementText = newStatementText.replace("$parameterName", parameterName);
            PsiStatement newStatement = factory.createStatementFromText(newStatementText, body);
            body.addBefore(newStatement, lastStatement);
            addImportsAsNecessary(psiClass, "android.view.Menu");
            codeStyleManager.reformat(method);
            notifyListeners(psiClass);
          }
        }.execute();
      }
    }
    if (sourceState instanceof ActivityState && destinationState instanceof ActivityState) {
      final ActivityState sourceActivityState = (ActivityState)sourceState;
      final ActivityState newActivity = (ActivityState)destinationState;
      final PsiClass psiClass = Utilities.getPsiClass(module, sourceActivityState.getClassName());
      if (psiClass != null) {
        new WriteCommandAction<Void>(project, "Add navigation transition", psiClass.getContainingFile()) {
          @Override
          protected void run(@NotNull Result<Void> result) {
            PsiMethod signature = factory.createMethodFromText("void onCreate(Bundle savedInstanceState){}", psiClass);
            PsiMethod method = psiClass.findMethodBySignature(signature, false);
            if (method == null) {
              method = factory.createMethodFromText("@Override " +
                                                    "public void onCreate(Bundle savedInstanceState) {" +
                                                    "super.onCreate(savedInstanceState);}", psiClass);
              psiClass.add(method);
              method = psiClass.findMethodBySignature(signature, false); // the previously assigned method is not resolved somehow
              assert method != null;
            }
            PsiCodeBlock body = method.getBody();
            assert body != null;
            PsiStatement[] statements = body.getStatements();
            PsiStatement lastStatement = statements[statements.length - 1];
            String newCode = "findViewById($id).setOnClickListener(new View.OnClickListener() { " +
                             "  @Override" +
                             "  public void onClick(View v) {" +
                             "    $context.startActivity(new Intent($context, $activityClass));" +
                             "  }" +
                             "})";
            newCode = newCode.replaceAll("\\$id", "R.id." + transition.getSource().getViewName()); // todo improve
            newCode = newCode.replaceAll("\\$context", sourceActivityState.getClassName() + ".this");
            newCode = newCode.replaceAll("\\$activityClass", newActivity.getClassName() + ".class");
            PsiStatement newStatement = factory.createStatementFromText(newCode + ";", body);
            body.addAfter(newStatement, lastStatement);
            addImportsAsNecessary(psiClass, "android.view.View");
            codeStyleManager.reformat(method);
            notifyListeners(psiClass);
          }
        }.execute();
      }
    }
  }
}