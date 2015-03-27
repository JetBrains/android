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
import com.android.tools.idea.editors.navigation.Listener;
import com.android.tools.idea.editors.navigation.NavigationEditorUtils;
import com.android.tools.idea.editors.navigation.model.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class CodeGenerator {
  private static final Logger LOG = Logger.getInstance(CodeGenerator.class.getName());
  private static final String[] FRAMEWORK_IMPORTS = new String[]{"android.content.Intent"};
  public static final String TRANSITION_ADDED = "Transition added";
  private static final String LIST_POSITION_EXTRA_NAME = "position";
  private static final boolean PREPEND_PACKAGE_NAME_TO_EXTRA_NAME = false;

  private static class Template {
    public String[] imports;
    public String signature;
    public String body;
    public boolean insertCodeBeforeLastStatement;
    public Function<Transition, String> code;

    private void installTransition(CodeGenerator codeGenerator, PsiClass psiClass, Transition transition) {
      codeGenerator.createAddCodeAction(psiClass, this, transition).execute();
    }
  }

  private static final Template SHOW_MENU = new Template() {
    {
      imports = new String[]{"android.view.Menu"};
      signature = "boolean onCreateOptionsMenu(Menu menu)";
      body = "@Override " +
             "public boolean onCreateOptionsMenu(Menu menu) { " +
             "    return true;" +
             "}";
      insertCodeBeforeLastStatement = true;
      code = new Function<Transition, String>() {
        @Override
        public String fun(Transition transition) {
          String destinationResourceName = ((MenuState)transition.getDestination().getState()).getXmlResourceName();
          return "getMenuInflater().inflate(R.menu." + destinationResourceName + ", $0);";
        }
      };
    }
  };

  private static final Template MENU_ACTION = new Template() {
    {
      imports = new String[]{"android.view.Menu", "android.view.MenuItem"};
      signature = "boolean onPrepareOptionsMenu(Menu menu)";
      body = "@Override " +
             "public boolean onPrepareOptionsMenu(Menu menu) { " +
             "    boolean result = super.onPrepareOptionsMenu(menu); " +
             "    return result;" +
             "}";
      insertCodeBeforeLastStatement = true;
      code = new Function<Transition, String>() {
        @Override
        public String fun(Transition transition) {
          Locator source = transition.getSource();
          String viewName = source.getViewId();
          String sourceClassName = source.getState().getClassName();
          String destinationClassName = transition.getDestination().getState().getClassName();
          String activity = sourceClassName + ".this";

          return "$0.findItem(R.id." + viewName + ").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {" +
                        "    @Override" +
                        "    public boolean onMenuItemClick(MenuItem menuItem) {" +
                        "        startActivity(new Intent(" + activity + ", " + destinationClassName + ".class));" +
                        "        return true;" +
                        "    }" +
                        "});";
        }
      };
    }
  };

  private static Template setOnClickListener(final boolean isFragment) {
    return new Template() {
      {
        imports = new String[]{"android.view.View", "android.os.Bundle"};
        signature = isFragment ? "void onViewCreated(View view, Bundle savedInstanceState)" : "void onCreate(Bundle savedInstanceState)";
        body = "@Override\n" +
               "public " + signature + " { " +
               "    super.onCreate(savedInstanceState);" +
               "}";
        insertCodeBeforeLastStatement = false;
        code = new Function<Transition, String>() {
          @Override
          public String fun(Transition transition) {
            Locator source = transition.getSource();
            String viewName = source.getViewId();
            String sourceClassName = source.getState().getClassName();
            Locator destination = transition.getDestination();
            String destinationClassName = destination.getState().getClassName();
            String finder = isFragment ? "view." : "";
            String activity = isFragment ? "getActivity()" : sourceClassName + ".this";

            return finder + "findViewById(R.id." + viewName + ").setOnClickListener(new View.OnClickListener() { " +
                   "    @Override" +
                   "    public void onClick(View v) {" +
                   "        startActivity(new Intent(" + activity + ", " + destinationClassName + ".class));" +
                   "    }" +
                   "});";
          }
        };
      }
    };
  }

  private static Template overrideOnItemClickInList(final boolean isFragment) {
    return new Template() {
      {
        imports = new String[]{"android.view.View", "android.widget.ListView"};
        signature = "void onListItemClick(ListView l, View v, int position, long id)";
        body = "@Override\n" +
               (isFragment ? "public" : "protected") + " " + signature + " {" +
               "    super.onListItemClick(l, v, position, id);\n" +
               "}";
        insertCodeBeforeLastStatement = false;
        code = new Function<Transition, String>() {
          @Override
          public String fun(Transition transition) {
            Locator source = transition.getSource();
            String sourceClassName = source.getState().getClassName();
            String sourcePackageName = sourceClassName.substring(0, sourceClassName.lastIndexOf('.'));
            Locator destination = transition.getDestination();
            String destinationClassName = destination.getState().getClassName();
            String activity = isFragment ? "getActivity()" : sourceClassName + ".this";

            //noinspection ConstantConditions
            return "startActivity(new Intent(" + activity + ", " + destinationClassName +
                   ".class).putExtra(\"" +
                   (PREPEND_PACKAGE_NAME_TO_EXTRA_NAME ? sourcePackageName + "." + LIST_POSITION_EXTRA_NAME : LIST_POSITION_EXTRA_NAME) +
                   "\", position));";
          }
        };
      }
    };
  }

  public final Module module;
  public final Listener<String> listener;

  public CodeGenerator(Module module, Listener<String> listener) {
    this.module = module;
    this.listener = listener;
  }

  private static void addImports(Module module, ImportHelper importHelper, PsiJavaFile file, String[] classNames) {
    for (String className : classNames) {
      PsiClass psiClass = NavigationEditorUtils.getPsiClass(module, className);
      if (psiClass != null) {
        importHelper.addImport(file, psiClass);
      } else {
        LOG.warn("Class not found: " + className);
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

  private WriteCommandAction<Void> createAddCodeAction(PsiClass psiClass, Template template, Transition t) {
    return createAddCodeAction(psiClass, template.signature, template.body, template.code.fun(t), template.insertCodeBeforeLastStatement,
                               template.imports);
  }

  public void implementTransition(final Transition transition) {
    Locator source = transition.getSource();
    State sourceState = source.getState();
    String fragmentClassName = source.getFragmentClassName();
    final boolean targetIsFragment = fragmentClassName != null;
    String targetClassName = targetIsFragment ? fragmentClassName : sourceState.getClassName();
    final PsiClass hostClass = NavigationEditorUtils.getPsiClass(module, targetClassName);
    if (hostClass == null) {
      return;
    }
    final State destinationState = transition.getDestination().getState();
    sourceState.accept(new State.Visitor() {
      @Override
      public void visit(final ActivityState sourceState) {
        destinationState.accept(new State.Visitor() {
          @Override
          public void visit(ActivityState destinationState) {
            PsiClass listClass = NavigationEditorUtils
              .getPsiClass(module, targetIsFragment ? "android.app.ListFragment" : "android.app.ListActivity");
            assert listClass != null;
            Template t =
              hostClass.isInheritor(listClass, true) ? overrideOnItemClickInList(targetIsFragment) : setOnClickListener(targetIsFragment);
            t.installTransition(CodeGenerator.this, hostClass, transition);
          }

          @Override
          public void visit(MenuState destinationState) {
            SHOW_MENU.installTransition(CodeGenerator.this, hostClass, transition);
          }
        });
      }

      @Override
      public void visit(final MenuState sourceState) {
        destinationState.accept(new State.BaseVisitor() {
          @Override
          public void visit(ActivityState destinationState) {
            MENU_ACTION.installTransition(CodeGenerator.this, hostClass, transition);
          }
        });
      }
    });
  }
}