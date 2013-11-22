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

public class CodeGenerator {
  public final Module module;
  public final NavigationModel navigationModel;
  private final Macros macros;

  public CodeGenerator(NavigationModel navigationModel, Module module) {
    this.navigationModel = navigationModel;
    this.module = module;
    this.macros = new Macros(module); // todo - share this with the analysis implementation
  }

  public void implementTransition(final Transition transition) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    final PsiElementFactory factory = facade.getElementFactory();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        State sourceState = transition.getSource().getState();
        State destinationState = transition.getDestination().getState();
        if (sourceState instanceof MenuState && destinationState instanceof ActivityState) {
          MenuState menuState = (MenuState)sourceState;
          ActivityState activityState = (ActivityState)destinationState;
          ActivityState originatingActivity = getAssociatedActivityState(menuState);
          PsiClass psiClass = Utilities.getPsiClass(module, originatingActivity.getClassName());
          PsiMethod[] onPrepareOptionsMenuMethods = psiClass.findMethodsByName("onPrepareOptionsMenu", false);
          if (onPrepareOptionsMenuMethods.length == 1) {
            PsiMethod onPrepareOptionsMenuMethod = onPrepareOptionsMenuMethods[0];
            PsiParameter[] parameters = onPrepareOptionsMenuMethod.getParameterList().getParameters();
            if (parameters.length == 1) {
              PsiCodeBlock body = onPrepareOptionsMenuMethod.getBody();
              PsiStatement[] statements = body.getStatements();
              PsiStatement lastStatement = statements[statements.length - 1];
              MultiMatch installMenuItemOnGetMenuItemAndLaunchActivityMacro = macros.installMenuItemOnGetMenuItemAndLaunchActivityMacro;

            /*
            Map<String, PsiElement> bindings = new HashMap<String, PsiElement>();
            bindings.put("$menuItem", factory.createIdentifier("hello"));
            bindings.put("$f", factory.createIdentifier("goodbye"));
            bindings.put("$consume", factory.createExpressionFromText("true", body));
            PsiElement newCode = Instantiation.instantiate(installMenuItemClick, bindings);
            */

              MultiMatch.Bindings2 bindings = new MultiMatch.Bindings2();
              bindings.put("$consume", "true");
              bindings.put("$menuItem", "$menu", parameters[0].getName());
              bindings.put("$menuItem", "$id", "R.id." + transition.getSource().getViewName());
              bindings.put("$f", "context", originatingActivity.getClassName() + ".this");
              bindings.put("$f", "activityClass", activityState.getClassName() + ".class");

              String newCode = installMenuItemOnGetMenuItemAndLaunchActivityMacro.instantiate(bindings);
              PsiStatement newStatement = factory.createStatementFromText(newCode + ";", body);

              body.addBefore(newStatement, lastStatement);
            }
          }
        }
      }
    });
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
}
