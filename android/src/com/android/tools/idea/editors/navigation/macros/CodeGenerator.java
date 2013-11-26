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
import com.android.tools.idea.editors.navigation.Utilities;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class CodeGenerator {
  public final Module module;
  public final NavigationModel navigationModel;
  private final Macros macros;

  public CodeGenerator(NavigationModel navigationModel, Module module) {
    this.navigationModel = navigationModel;
    this.module = module;
    this.macros = new Macros(module); // todo - share this with the analysis implementation
  }


  /*
  Map<String, PsiElement> bindings = new HashMap<String, PsiElement>();
  bindings.put("$menuItem", factory.createIdentifier("hello"));
  bindings.put("$f", factory.createIdentifier("goodbye"));
  bindings.put("$consume", factory.createExpressionFromText("true", body));
  PsiElement newCode = Instantiation.instantiate(installMenuItemClick, bindings);
  */


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

  public void implementTransition(final Transition transition) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    final PsiElementFactory factory = facade.getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(module.getProject());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        State sourceState = transition.getSource().getState();
        State destinationState = transition.getDestination().getState();
        if (sourceState instanceof MenuState && destinationState instanceof ActivityState) {
          MenuState menuState = (MenuState)sourceState;
          ActivityState activityState = (ActivityState)destinationState;
          ActivityState originatingActivity = getAssociatedActivityState(menuState);
          PsiClass psiClass = Utilities.getPsiClass(module, originatingActivity.getClassName()); // todo what if no class is defined?
          @NotNull PsiMethod template = factory.createMethodFromText("public boolean onPrepareOptionsMenu(Menu menu){}", psiClass);
          PsiMethod method = psiClass.findMethodBySignature(template, false); // todo generate if it's not already there
          String parameterName = method.getParameterList().getParameters()[0].getName();
          PsiCodeBlock body = method.getBody();
          PsiStatement[] statements = body.getStatements();
          PsiStatement lastStatement = statements[statements.length - 1];
          MultiMatch macro = macros.installMenuItemOnGetMenuItemAndLaunchActivityMacro;
          MultiMatch.Bindings<String> bindings = new MultiMatch.Bindings<String>();
          bindings.put("$consume", "true");
          bindings.put("$menuItem", "$menu", parameterName);
          bindings.put("$menuItem", "$id", "R.id." + transition.getSource().getViewName());
          bindings.put("$f", "context", originatingActivity.getClassName() + ".this");
          bindings.put("$f", "activityClass", activityState.getClassName() + ".class");
          String newCode = macro.instantiate(bindings);
          PsiStatement newStatement = factory.createStatementFromText(newCode + ";", body);
          body.addBefore(newStatement, lastStatement);
          codeStyleManager.reformat(method);
        }
        if (sourceState instanceof ActivityState && destinationState instanceof MenuState) {
          ActivityState activityState = (ActivityState)sourceState;
          MenuState menuState = (MenuState)destinationState;
          PsiClass psiClass = Utilities.getPsiClass(module, activityState.getClassName()); // todo what if no class is defined?
          PsiMethod template = factory.createMethodFromText("public boolean onCreateOptionsMenu(Menu menu){}", psiClass);
          PsiMethod method = psiClass.findMethodBySignature(template, false); // todo generate if it's not already there
          String parameterName = method.getParameterList().getParameters()[0].getName();
          PsiCodeBlock body = method.getBody();
          PsiStatement[] statements = body.getStatements();
          PsiStatement lastStatement = statements[statements.length - 1];
          String newStatementText = "getMenuInflater().inflate(R.menu.$XmlResourceName, $parameterName);";
          newStatementText = newStatementText.replace("$XmlResourceName", menuState.getXmlResourceName());
          newStatementText = newStatementText.replace("$parameterName", parameterName);
          PsiStatement newStatement = factory.createStatementFromText(newStatementText, body);
          body.addBefore(newStatement, lastStatement);
          codeStyleManager.reformat(method);
        }
      }
    });
  }
}
