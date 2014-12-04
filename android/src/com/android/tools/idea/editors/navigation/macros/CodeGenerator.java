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


  private WriteCommandAction<Void> createAddCodeAction(final PsiClass psiClass,
                                                       final String signatureText,
                                                       final String templateText,
                                                       final String codeToInsert,
                                                       final boolean addBeforeLastStatement,
                                                       final String... imports) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(module.getProject()).getElementFactory();
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
    final Locator source = transition.getSource();
    final String viewName = source.getViewName();
    final Locator destination = transition.getDestination();
    source.getState().accept(new State.Visitor() {
      @Override
      public void visit(final ActivityState sourceState) {
        final PsiClass psiClass = Utilities.getPsiClass(module, sourceState.getClassName());
        if (psiClass == null) {
          return;
        }
        destination.getState().accept(new State.Visitor() {
          @Override
          public void visit(ActivityState destinationState) {
            String code = "findViewById(R.id." + viewName + ").setOnClickListener(new View.OnClickListener() { " +
                          "    @Override" +
                          "    public void onClick(View v) {" +
                          "        $context.startActivity(new Intent($context, " + destinationState.getClassName() + ".class));" +
                          "    }" +
                          "});";
            code = code.replaceAll("\\$context", sourceState.getClassName() + ".this");
            createAddCodeAction(psiClass, ON_CREATE_SIGNATURE, ON_CREATE_BODY, code, false, "android.view.View").execute();
          }

          @Override
          public void visit(MenuState destinationState) {
            String code = "getMenuInflater().inflate(R.menu." + destinationState.getXmlResourceName() + ", $0);";
            createAddCodeAction(psiClass, ON_CREATE_OPTIONS_MENU_SIGNATURE, ON_CREATE_OPTIONS_MENU_BODY, code, true, "android.view.Menu")
              .execute();
          }
        });
      }

      @Override
      public void visit(final MenuState sourceState) {
        final ActivityState associatedSourceActivityState = navigationModel.findAssociatedActivityState(sourceState);
        if (associatedSourceActivityState == null) {
          return;
        }
        final PsiClass psiClass = Utilities.getPsiClass(module, associatedSourceActivityState.getClassName());
        if (psiClass == null) {
          return;
        }
        destination.getState().accept(new State.BaseVisitor() {
          @Override
          public void visit(ActivityState destinationState) {
            String code = "$0.findItem(R.id." + viewName + ").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {" +
                          "    @Override" +
                          "    public boolean onMenuItemClick(MenuItem menuItem) {" +
                          "        $context.startActivity(new Intent($context, " + destinationState.getClassName() + ".class));" +
                          "        return true;" +
                          "    }" +
                          "});";
            code = code.replaceAll("\\$context", associatedSourceActivityState.getClassName() + ".this");
            createAddCodeAction(psiClass, ON_PREPARE_OPTIONS_MENU_SIGNATURE, ON_PREPARE_OPTIONS_MENU_BODY, code, true, "android.view.Menu",
                                "android.view.MenuItem").execute();
          }
        });
      }
    });
  }
}