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
import com.android.tools.idea.editors.navigation.Utilities;
import com.android.tools.idea.editors.navigation.model.*;
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
  private static final String[] FRAMEWORK_IMPORTS = new String[]{"android.app.Activity", "android.content.Intent", "android.os.Bundle"};
  public static final String TRANSITION_ADDED = "Transition added";

  private static final String ON_CREATE_SIGNATURE = "void onCreate(Bundle savedInstanceState)";
  private static final String ON_CREATE_BODY = "@Override " +
                                               "public void onCreate(Bundle savedInstanceState) { " +
                                               "    super.onCreate(savedInstanceState);" +
                                               "} ";

  private static final String ON_CREATE_OPTIONS_MENU_SIGNATURE = "boolean onCreateOptionsMenu(Menu menu)";
  private static final String ON_CREATE_OPTIONS_MENU_BODY = "@Override " +
                                                            "public boolean onCreateOptionsMenu(Menu menu) { " +
                                                            "    return true;" +
                                                            "}";
  private static final String ON_PREPARE_OPTIONS_MENU_SIGNATURE = "boolean onPrepareOptionsMenu(Menu menu)";
  private static final String ON_PREPARE_OPTIONS_MENU_BODY = "@Override " +
                                                             "public boolean onPrepareOptionsMenu(Menu menu) { " +
                                                             "    boolean result = super.onPrepareOptionsMenu(menu); " +
                                                             "    return result;" +
                                                             "}";
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

  private static void addImports(Module module, ImportHelper importHelper, PsiJavaFile file, String[] classNames) {
    for (String className : classNames) {
      PsiClass psiClass = Utilities.getPsiClass(module, className);
      if (psiClass != null) {
        importHelper.addImport(file, psiClass);
      }
    }
  }

  private static void addImportsAsNecessary(Module module, PsiClass psiClass, @NonNull String... classNames) {
    PsiJavaFile file = (PsiJavaFile)psiClass.getContainingFile();
    ImportHelper importHelper = new ImportHelper(CodeStyleSettingsManager.getSettings(module.getProject()));
    addImports(module, importHelper, file, FRAMEWORK_IMPORTS);
    addImports(module, importHelper, file, classNames);
  }

  @SuppressWarnings("UnusedParameters")
  private void notifyListeners(PsiClass psiClass) {
    listener.notify(TRANSITION_ADDED);
  }

  private CodeStyleManager getCodeStyleManager() {
    return CodeStyleManager.getInstance(module.getProject());
  }

  private static String substituteArgs(PsiMethod method, String code) {
    if (code.contains("$0")) {
      code = code.replace("$0", method.getParameterList().getParameters()[0].getName());
    }
    return code;
  }


  private WriteCommandAction<Void> createAddCodeAction(final PsiElementFactory factory,
                                                       final PsiClass psiClass,
                                                       final String signatureText,
                                                       final String templateText,
                                                       final String codeToInsert,
                                                       final boolean addBeforeLastStatement,
                                                       final String... imports) {
    return new WriteCommandAction<Void>(module.getProject(), "Add navigation transition", psiClass.getContainingFile()) {
      @Override
      protected void run(@NotNull Result<Void> result) {
        PsiMethod signature = factory.createMethodFromText(signatureText + "{}", psiClass);
        PsiMethod method = psiClass.findMethodBySignature(signature, false);
        if (method == null) {
          method = factory.createMethodFromText(templateText, psiClass);
          psiClass.add(method);
          method = psiClass.findMethodBySignature(signature, false); // the previously assigned method is not resolved somehow
          assert method != null;
        }
        PsiCodeBlock body = method.getBody();
        assert body != null;
        PsiStatement[] statements = body.getStatements();
        PsiStatement lastStatement = statements[statements.length - 1];
        PsiStatement newStatement = factory.createStatementFromText(substituteArgs(method, codeToInsert), body);
        if (addBeforeLastStatement) {
          body.addBefore(newStatement, lastStatement);
        }
        else {
          body.addAfter(newStatement, lastStatement);
        }
        addImportsAsNecessary(module, psiClass, imports);
        getCodeStyleManager().reformat(method);
        notifyListeners(psiClass);
      }
    };
  }

  public void implementTransition(final Transition transition) {
    Project project = module.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = facade.getElementFactory();

    State sourceState = transition.getSource().getState();
    State destinationState = transition.getDestination().getState();
    if (sourceState instanceof MenuState && destinationState instanceof ActivityState) {
      MenuState menuState = (MenuState)sourceState;
      final ActivityState newActivity = (ActivityState)destinationState;
      final ActivityState originatingActivity = getAssociatedActivityState(menuState);
      final PsiClass psiClass = Utilities.getPsiClass(module, originatingActivity.getClassName());
      if (psiClass != null) {
        String code = "$0.findItem($id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {" +
                      "    @Override" +
                      "    public boolean onMenuItemClick(MenuItem menuItem) {" +
                      "        $context.startActivity(new Intent($context, $activityClass));" +
                      "        return true;" +
                      "    }" +
                      "});";
        code = code.replace("$id", "R.id." + transition.getSource().getViewName());
        code = code.replaceAll("\\$context", originatingActivity.getClassName() + ".this");
        code = code.replace("$activityClass", newActivity.getClassName() + ".class");
        createAddCodeAction(factory, psiClass, ON_PREPARE_OPTIONS_MENU_SIGNATURE, ON_PREPARE_OPTIONS_MENU_BODY, code, true,
                            "android.view.Menu", "android.view.MenuItem").execute();
      }
    }
    if (sourceState instanceof ActivityState && destinationState instanceof MenuState) {
      ActivityState activityState = (ActivityState)sourceState;
      final MenuState menuState = (MenuState)destinationState;
      final PsiClass psiClass = Utilities.getPsiClass(module, activityState.getClassName());
      if (psiClass != null) {
        String code = "getMenuInflater().inflate(R.menu.$XmlResourceName, $0);";
        code = code.replace("$XmlResourceName", menuState.getXmlResourceName());
        createAddCodeAction(factory, psiClass, ON_CREATE_OPTIONS_MENU_SIGNATURE, ON_CREATE_OPTIONS_MENU_BODY, code, true,
                            "android.view.Menu").execute();
      }
    }
    if (sourceState instanceof ActivityState && destinationState instanceof ActivityState) {
      ActivityState sourceActivityState = (ActivityState)sourceState;
      ActivityState newActivity = (ActivityState)destinationState;
      PsiClass psiClass = Utilities.getPsiClass(module, sourceActivityState.getClassName());
      if (psiClass != null) {
        String code = "findViewById($id).setOnClickListener(new View.OnClickListener() { " +
                      "    @Override" +
                      "    public void onClick(View v) {" +
                      "        $context.startActivity(new Intent($context, $activityClass));" +
                      "    }" +
                      "});";
        code = code.replace("$id", "R.id." + transition.getSource().getViewName());
        code = code.replaceAll("\\$context", sourceActivityState.getClassName() + ".this");
        code = code.replace("$activityClass", newActivity.getClassName() + ".class");
        createAddCodeAction(factory, psiClass, ON_CREATE_SIGNATURE, ON_CREATE_BODY, code, false, "android.view.View").execute();
      }
    }
  }
}