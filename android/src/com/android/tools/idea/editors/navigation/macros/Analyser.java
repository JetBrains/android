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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.NavigationView;
import com.android.tools.idea.editors.navigation.Utilities;
import com.android.tools.idea.model.ManifestInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.navigation.Utilities.getPropertyName;
import static com.android.tools.idea.editors.navigation.Utilities.getPsiClass;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Analyser {
  private static final Logger LOG = Logger.getInstance("#" + Analyser.class.getName());
  private static final String[] ID_PREFIXES = {"@+id/", "@android:id/"};
  private static final boolean DEBUG = false;

  private final Project myProject;
  private final Module myModule;
  private final Macros myMacros;

  public Analyser(Project project, Module module) {
    myProject = project;
    myModule = module;
    myMacros = Macros.getInstance(myModule.getProject());
  }

  @Nullable
  private static String qualifyClassNameIfNecessary(String packageName, String className) {
    if (className == null) {
      return null;
    }
    else {
      return className.startsWith(".") ? packageName + className : className;
    }
  }

  @Nullable
  public static String unQuote(String s) {
    if (s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length() - 1);
    }
    assert false;
    return null;
  }

  public static void commit(Project project, @Nullable PsiFile file) {
    if (file == null) {
      return;
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document1 = documentManager.getDocument(file);
    if (DEBUG) {
      Document document2 = documentManager.getCachedDocument(file);
      if (!document1.getText().equals(document2.getText())) {
        System.out.println("document2 = " + document2);
      }
    }
    if (document1 != null) {
      documentManager.commitDocument(document1);
    }
  }

  private static Set<String> readManifestFile(Project project) {
    Set<String> result = new HashSet<String>();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) == null) {
        continue;
      }

      ManifestInfo manifestInfo = ManifestInfo.get(module, false);
      String packageName = manifestInfo.getPackage();
      List<Activity> activities = manifestInfo.getActivities();
      for (Activity activity : activities) {
        AndroidAttributeValue<PsiClass> activityClass = activity.getActivityClass();
        String className = activityClass.getRawText();
        String qualifiedName = qualifyClassNameIfNecessary(packageName, className);
        result.add(qualifiedName);
      }
    }
    return result;
  }

  private static ActivityState getActivityState(String className, Map<String, ActivityState> classNameToActivityState) {
    ActivityState result = classNameToActivityState.get(className);
    if (result == null) {
      classNameToActivityState.put(className, result = new ActivityState(className));
    }
    return result;
  }

  private static MenuState getMenuState(String menuName, Map<String, MenuState> menuNameToMenuState) {
    MenuState result = menuNameToMenuState.get(menuName);
    if (result == null) {
      menuNameToMenuState.put(menuName, result = new MenuState(menuName));
    }
    return result;
  }

  public static abstract class Evaluator {
    public static final Evaluator TRUE_OR_FALSE = new Evaluator() {
      @Nullable
      @Override
      public Object evaluate(@Nullable PsiExpression expression) {
        return null;
      }
    };

    public abstract Object evaluate(@Nullable PsiExpression expression);
  }

  public <T extends PsiVariable> Map<T, Object> searchForVariableAssignments(@Nullable PsiElement input,
                                                                             final Class<T> variableType,
                                                                             final Evaluator evaluator) {
    final Map<T, Object> result = new HashMap<T, Object>();
    search(input,
           evaluator,
           myMacros.createMacro("void assign(Object $lhs, Object $rhs) { $lhs = $rhs; }"),
           new Processor() {
             @Override
             public void process(MultiMatch.Bindings<PsiElement> exp) {
               PsiElement lExpression = exp.get("$lhs");
               PsiElement rExpression = exp.get("$rhs");
               if (lExpression instanceof PsiReferenceExpression && rExpression instanceof PsiExpression) {
                 PsiReferenceExpression ref = (PsiReferenceExpression)lExpression;
                 PsiElement resolvedValue = ref.resolve();
                 if (variableType.isInstance(resolvedValue)) {
                   result.put((T)resolvedValue, evaluator.evaluate((PsiExpression)rExpression));
                 }
               }
             }
           });
    return result;
  }

  public Map<PsiLocalVariable, Object> searchForVariableBindings(@Nullable PsiElement input,
                                                                 final Evaluator evaluator) {
    final Map<PsiLocalVariable, Object> result = new HashMap<PsiLocalVariable, Object>();
    if (input == null) {
      return result;
    }
    input.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitDeclarationStatement(PsiDeclarationStatement statement) {
        super.visitDeclarationStatement(statement);
        PsiElement[] declaredElements = statement.getDeclaredElements();
        for (PsiElement element : declaredElements) {
          if (element instanceof PsiLocalVariable) {
            PsiLocalVariable local = (PsiLocalVariable)element;
            PsiElement rExpression = local.getInitializer();
            result.put(local, evaluator.evaluate((PsiExpression)rExpression));
          }
        }
      }
    });
    return result;
  }

  private Map<PsiField, Object> getFieldAssignmentsInOnCreate(PsiClass activityClass, Evaluator evaluator) {
    if (activityClass == null) {
      return Collections.emptyMap();
    }
    PsiMethod method = Utilities.findMethodBySignature(activityClass, "void onCreate(Bundle bundle)");
    if (method == null) {
      return Collections.emptyMap();
    }
    return searchForVariableAssignments(method.getBody(), PsiField.class, evaluator);
  }

  private Map<PsiLocalVariable, Object> getLocalVariableBindingsInOnViewCreated(PsiClass activityClass, Evaluator evaluator) {
    if (activityClass == null) {
      return Collections.emptyMap();
    }
    PsiMethod method = Utilities.findMethodBySignature(activityClass, "void onViewCreated(View view, Bundle bundle)");
    if (method == null) {
      return Collections.emptyMap();
    }
    return searchForVariableBindings(method.getBody(), evaluator);
  }

  private Evaluator getEvaluator(final Set<String> ids,
                                 final Set<String> tags,
                                 final Map<PsiField, Object> fieldValues,
                                 final Map<PsiLocalVariable, Object> localVariableValues) {
    return new Evaluator() {
      @Nullable
      @Override
      public Object evaluate(@Nullable PsiExpression exp) {
        if (exp == null) { // todo does this actually make sense?
          return null;
        }
        if (exp instanceof PsiLiteral) {
          PsiLiteral literal = (PsiLiteral)exp;
          return literal.getValue();
        }
        MultiMatch.Bindings<PsiElement> match1 = myMacros.findViewById.match(exp);
        if (match1 != null) {
          String id = match1.bindings.get("$id").getText();
          return ids.contains(id) ? new Object() : null;
        }
        MultiMatch.Bindings<PsiElement> match2 = myMacros.findFragmentByTag.match(exp);
        if (match2 != null) {
          String tag = unQuote(match2.bindings.get("$tag").getText());
          return tags.contains(tag) ? new Object() : null;
        }
        if (exp instanceof PsiTypeCastExpression) {
          PsiTypeCastExpression castExp = (PsiTypeCastExpression)exp;
          return evaluate(castExp.getOperand());
        }
        if (exp instanceof PsiBinaryExpression) {
          PsiBinaryExpression binExp = (PsiBinaryExpression)exp;
          IElementType op = binExp.getOperationSign().getTokenType();
          Object lhs = evaluate(binExp.getLOperand());
          Object rhs = evaluate(binExp.getROperand());
          if (op == JavaTokenType.EQEQ) {
            return lhs == rhs;
          }
          if (op == JavaTokenType.NE) {
            return lhs != rhs;
          }
        }
        if (exp instanceof PsiReferenceExpression) {
          PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression)exp;
          PsiElement resolved = psiReferenceExpression.resolve();
          if (resolved instanceof PsiField) {
            PsiField field = (PsiField)resolved;
            return fieldValues.get(field);
          }
          else {
            if (resolved instanceof PsiLocalVariable) {
              PsiLocalVariable localVariable = (PsiLocalVariable)resolved;
              return localVariableValues.get(localVariable);
            }
          }
        }
        return null;
      }
    };
  }

  @Nullable
  private static String getQualifiedName(@Nullable PsiClass psiClass) {
    return psiClass == null ? null : psiClass.getQualifiedName();
  }

  private Evaluator getEvaluator(Configuration configuration,
                                 PsiClass activityClass,
                                 PsiClass activityOrFragmentClass,
                                 boolean isActivity) {
    Set<String> tags = getTags(getXmlFile(configuration, getQualifiedName(activityClass), true));
    Set<String> ids = getIds(getXmlFile(configuration, getQualifiedName(activityOrFragmentClass), isActivity));

    Map<PsiField, Object> noFields = Collections.emptyMap();
    Map<PsiLocalVariable, Object> noVars = Collections.emptyMap();
    Evaluator evaluator1 = getEvaluator(ids, tags, noFields, noVars);
    Map<PsiField, Object> fieldBindings =
      getFieldAssignmentsInOnCreate(activityOrFragmentClass, evaluator1);
    Evaluator evaluator2 = getEvaluator(ids, tags, fieldBindings, noVars);
    Map<PsiLocalVariable, Object> localVariableBindings =
      getLocalVariableBindingsInOnViewCreated(activityOrFragmentClass, evaluator2);
    return getEvaluator(ids, tags, fieldBindings, localVariableBindings);
  }

  @Nullable
  private XmlFile getXmlFile(Configuration configuration, @Nullable String className, boolean isActivity) {
    if (className == null) {
      return null;
    }
    String xmlFileName = getXMLFileName(myModule, className, isActivity);
    if (xmlFileName == null) {
      return null;
    }
    return (XmlFile)NavigationView.getLayoutXmlFile(false, xmlFileName, configuration, myProject);
  }

  private void deriveTransitions(final NavigationModel model,
                                 final MiniModel miniModel,
                                 final ActivityState fromActivityState,
                                 final String activityOrFragmentClassName,
                                 final Configuration configuration,
                                 final boolean isActivity) {

    if (DEBUG) System.out.println("Analyser: className = " + activityOrFragmentClassName);

    final PsiClass activityClass = Utilities.getPsiClass(myModule, fromActivityState.getClassName());
    final PsiClass activityOrFragmentClass = Utilities.getPsiClass(myModule, activityOrFragmentClassName);
    final Evaluator evaluator = getEvaluator(configuration, activityClass, activityOrFragmentClass, isActivity);

    if (activityOrFragmentClass == null) {
      // Navigation file is out-of-date and refers to classes that have been deleted. That's okay.
      LOG.info("Class " + activityOrFragmentClassName + " not found");
      return;
    }

    // Search for menu inflation

    search(activityOrFragmentClass,
           "boolean onCreateOptionsMenu(Menu menu)",
           "void macro(Object target, String id, Menu menu) { target.inflate(id, menu); }",
           new Processor() {
             @Override
             public void process(MultiMatch.Bindings<PsiElement> args) {
               String menuIdName = args.get("id").getLastChild().getText();
               final MenuState menu = getMenuState(menuIdName, miniModel.menuNameToMenuState);
               addTransition(model, new Transition("click", new Locator(fromActivityState), new Locator(menu)));
               // Search for menu item bindings
               search(activityOrFragmentClass,
                      "boolean onPrepareOptionsMenu(Menu m)",
                      myMacros.installMenuItemOnGetMenuItemAndLaunchActivityMacro,
                      new Processor() {
                        @Override
                        public void process(MultiMatch.Bindings<PsiElement> args) {
                          String className = getQualifiedName(args.get("$f", "activityClass").getFirstChild());
                          if (className != null) {
                            ActivityState activityState = getActivityState(className, miniModel.classNameToActivityState);
                            // e.g. $id=PsiReferenceExpression:R.id.action_account
                            String menuItemName = args.get("$menuItem", "$id").getLastChild().getText();
                            addTransition(model, new Transition("click", Locator.of(menu, menuItemName), new Locator(activityState)));
                          }
                        }
                      }
               );
             }
           });

    // Search for 'onClick' listeners on Buttons etc.

    search(activityOrFragmentClass,
           "void onCreate(Bundle b)",
           myMacros.installClickAndCallMacro,
           new Processor() {
             @Override
             public void process(MultiMatch.Bindings<PsiElement> args) {
               PsiElement $view = args.get("$view");
               MultiMatch.Bindings<PsiElement> bindings = myMacros.findViewById.match($view);
               if (bindings != null) {
                 String tag = bindings.get("$id").getText();
                 search(args.get("$f"), evaluator, myMacros.createIntent,
                        createProcessor(tag, miniModel.classNameToActivityState, model, fromActivityState));
               }
             }
           });

    // Search for 'onItemClick' listeners in listViews

    search(activityOrFragmentClass,
           "void onViewCreated(View v, Bundle b)",
           myMacros.installItemClickAndCallMacro,
           new Processor() {
             @Override
             public void process(MultiMatch.Bindings<PsiElement> args) {
               PsiElement $listView = args.get("$listView");
               final String viewName = $listView == null ? null : getPropertyName(removeTrailingParens($listView.getText()));
               search(args.get("$f"), evaluator, myMacros.createIntent,
                      createProcessor(viewName, miniModel.classNameToActivityState, model, fromActivityState));
             }
           });

    // Accommodate idioms from Master-Detail template

    PsiClassType superType = activityOrFragmentClass.getSuperTypes()[0];
    if (superType.getClassName().equals("ListFragment")) {
      search(activityOrFragmentClass,
             "void onListItemClick(ListView listView, View view, int position, long id)",
             "void macro(Object f) { f.$(); }", // this obscure term matches 'any method call'
             new Processor() {
               @Override
               public void process(MultiMatch.Bindings<PsiElement> args) {
                 PsiElement exp = args.get("f");
                 if (exp instanceof PsiMethodCallExpression) {
                   PsiMethodCallExpression call = (PsiMethodCallExpression)exp;
                   if (call.getFirstChild().getFirstChild().getText().equals("super")) {
                     return;
                   }
                   PsiMethod resolvedMethod = call.resolveMethod();
                   if (resolvedMethod != null) {
                     if (activityClass != null) {
                       PsiMethod implementation = activityClass.findMethodBySignature(resolvedMethod, false);
                       if (implementation != null) {
                         Evaluator evaluator = getEvaluator(configuration, activityClass, activityClass, true);
                         search(implementation.getBody(),
                                evaluator,
                                myMacros.createIntent,
                                createProcessor(/*"listView"*/null, miniModel.classNameToActivityState, model,
                                                fromActivityState));
                       }
                     }
                   }
                 }
               }
             });
    }
  }

  static class MiniModel {
    final Map<String, ActivityState> classNameToActivityState;
    final Map<String, MenuState> menuNameToMenuState;


    MiniModel(Map<String, ActivityState> classNameToActivityState, Map<String, MenuState> menuNameToMenuState) {
      this.classNameToActivityState = classNameToActivityState;
      this.menuNameToMenuState = menuNameToMenuState;
    }

    MiniModel() {
      this(new HashMap<String, ActivityState>(), new HashMap<String, MenuState>());
    }
  }

  public NavigationModel deriveAllStatesAndTransitions(NavigationModel model, Configuration configuration) {
    Set<String> activityClassNames = readManifestFile(myProject);
    if (DEBUG) {
      System.out.println("deriveAllStatesAndTransitions = " + activityClassNames);
    }
    MiniModel toDo = new MiniModel(getActivityClassNameToActivityState(activityClassNames), new HashMap<String, MenuState>());
    MiniModel next = new MiniModel();
    MiniModel done = new MiniModel();

    if (DEBUG) {
      System.out.println("activityStates = " + toDo.classNameToActivityState.values());
    }

    while (!toDo.classNameToActivityState.isEmpty()) {
      for (ActivityState sourceActivity : toDo.classNameToActivityState.values()) {
        if (done.classNameToActivityState.containsKey(sourceActivity.getClassName())) {
          continue;
        }
        model.addState(sourceActivity); // covers the case of Activities that are not part of a transition (e.g. a model with one Activity)
        deriveTransitions(model, next, sourceActivity, sourceActivity.getClassName(), configuration, true);

        // Examine fragments associated with this activity

        XmlFile layoutFile = getXmlFile(configuration, sourceActivity.getClassName(), true);
        List<FragmentEntry> fragments = getFragmentEntries(layoutFile);

        for (FragmentEntry fragment : fragments) {
          deriveTransitions(model, next, sourceActivity, fragment.className, configuration, false);
        }
      }
      done.classNameToActivityState.putAll(toDo.classNameToActivityState);
      toDo = next;
      next = new MiniModel();
    }
    return model;
  }

  private static Processor createProcessor(@Nullable final String viewName,
                                           final Map<String, ActivityState> activities,
                                           final NavigationModel model,
                                           final ActivityState fromState) {
    return new Processor() {
      @Override
      public void process(MultiMatch.Bindings<PsiElement> args) {
        PsiElement activityClass = args.get("activityClass").getFirstChild();
        String qualifiedName = getQualifiedName(activityClass);
        if (qualifiedName != null) {
          State toState = getActivityState(qualifiedName, activities);
          addTransition(model, new Transition("click", Locator.of(fromState, viewName), new Locator(toState)));
        }
      }
    };
  }

  private static List<FragmentEntry> getFragmentEntries(@Nullable XmlFile psiFile) {
    final List<FragmentEntry> result = new ArrayList<FragmentEntry>();
    if (psiFile == null) {
      return result;
    }
    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (tag.getName().equals("fragment")) {
          String fragmentTag = tag.getAttributeValue("android:tag");
          String fragmentClassName = tag.getAttributeValue("android:name");
          if (DEBUG) System.out.println("Analyser: fragmentClassName = " + fragmentClassName);
          if (fragmentClassName != null) {
            result.add(new FragmentEntry(fragmentClassName, fragmentTag));
          }
        }
      }
    });
    return result;
  }

  private static String getPrefix(String idName) {
    for(String prefix : ID_PREFIXES) {
      if (idName.startsWith(prefix)) {
          return prefix;
      }
    }
    int firstSlash = idName.indexOf('/');
    String prefix = idName.substring(0, firstSlash + 1); // prefix is "" when '/' is absent
    LOG.warn("Unrecognized prefix: " + prefix + " in " + idName);
    return prefix;
  }

  private static Set<String> getIds(@Nullable XmlFile psiFile) {
    final Set<String> result = new HashSet<String>();
    if (psiFile == null) {
      return result;
    }
    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        String id = tag.getAttributeValue("android:id");
        if (id != null) {
          result.add(id.substring(getPrefix(id).length()));
        }
      }
    });
    return result;
  }

  private static Set<String> getTags(@Nullable XmlFile psiFile) {
    final Set<String> result = new HashSet<String>();
    if (psiFile == null) {
      return result;
    }
    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        String id = tag.getAttributeValue("android:tag");
        if (id != null) {
          result.add(id);
        }
      }
    });
    return result;
  }

  private static String removeTrailingParens(String text) {
    return text.endsWith("()") ? text.substring(0, text.length() - 2) : text;
  }

  private static boolean addTransition(NavigationModel model, Transition transition) {
    if (DEBUG) System.out.println("Analyser: Adding transition: " + transition);
    return model.add(transition);
  }

  @Nullable
  private static String getQualifiedName(PsiElement element) {
    if (element instanceof PsiTypeElement) {
      PsiTypeElement psiTypeElement = (PsiTypeElement)element;
      PsiType type1 = psiTypeElement.getType();
      if (type1 instanceof PsiClassReferenceType) {
        PsiClassReferenceType type = (PsiClassReferenceType)type1;
        return getQualifiedName(type.resolve());
      }
    }
    return null;
  }

  private static Map<String, ActivityState> getActivityClassNameToActivityState(Set<String> activityClassNames) {
    Map<String, ActivityState> result = new HashMap<String, ActivityState>();
    for (String activityClassName : activityClassNames) {
      result.put(activityClassName, new ActivityState(activityClassName));
    }
    return result;
  }

  @Nullable
  public static String getXMLFileName(Module module, String controllerClassName, boolean isActivity) {
    MultiMatch.Bindings<PsiElement> exp;
    if (isActivity) {
      exp = match(module, controllerClassName, "void onCreate(Bundle bundle)",
                  "void macro(Object $R, Object $id) { setContentView($R.layout.$id); }"); // Use $R because we sometimes see e.g.: com.example.simplemail.activity.R.layout.compose_activity
    }
    else {
      exp = match(module, controllerClassName, "View onCreateView(LayoutInflater li, ViewGroup vg, Bundle b)",
                  "void macro(Object $inflater, Object $R, Object $id, Object $container) { $inflater.inflate($R.layout.$id, $container, false); }");
    }
    if (exp == null) {
      return null;
    }
    return exp.get("$id").getText();
  }

  public static abstract class Processor {
    public abstract void process(MultiMatch.Bindings<PsiElement> exp);
  }

  public static void search(@Nullable PsiElement input,
                            final Evaluator evaluator,
                            final MultiMatch matcher,
                            final Processor processor) {
    if (input != null) {
      input.accept(new JavaRecursiveElementVisitor() {
        private void visitNullableExpression(@Nullable PsiExpression expression) {
          if (expression != null) {
            visitExpression(expression);
          }
        }

        private void visitNullableStatement(@Nullable PsiStatement statement) {
          if (statement != null) {
            visitStatement(statement);
          }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
          PsiExpression condition = statement.getCondition();
          visitNullableExpression(condition);
          Object value = evaluator.evaluate(condition);

          // Both clauses below will be executed when value == BOOLEAN_UNKNOWN (null).
          if (value != Boolean.FALSE) { // True branch
            visitNullableStatement(statement.getThenBranch());
          }
          if (value != Boolean.TRUE) { // False branch
            visitNullableStatement(statement.getElseBranch());
          }
        }

        @Override
        public void visitExpression(PsiExpression expression) {
          super.visitExpression(expression);
          MultiMatch.Bindings<PsiElement> exp = matcher.match(expression);
          if (exp != null) {
            processor.process(exp);
          }
        }

        /*
        @Override
        public void visitStatement(PsiStatement statement) {
          super.visitStatement(statement);
          Map<String, PsiElement> bindings = Unifier.matchStatement(matcher.macro, statement);
          if (bindings != null) {
            processor.apply(new MultiMatch.Bindings<PsiElement>(bindings, Collections.EMPTY_MAP));
          }
        }
        */
      });
    }
  }

  public static void search(@Nullable PsiElement input, MultiMatch matcher, Processor processor) {
    search(input, Evaluator.TRUE_OR_FALSE, matcher, processor);
  }

  public static List<MultiMatch.Bindings<PsiElement>> search(@Nullable PsiElement element, final MultiMatch matcher) {
    final List<MultiMatch.Bindings<PsiElement>> results = new ArrayList<MultiMatch.Bindings<PsiElement>>();
    search(element, matcher, new Processor() {
      @Override
      public void process(MultiMatch.Bindings<PsiElement> exp) {
        results.add(exp);
      }
    });
    return results;
  }

  private static void search(PsiClass clazz, String methodSignature, MultiMatch matcher, Processor processor) {
    PsiMethod method = Utilities.findMethodBySignature(clazz, methodSignature);
    if (method == null) {
      return;
    }
    search(method.getBody(), matcher, processor);
  }

  private static void search(PsiClass clazz, String methodSignature, String matchMacro, Processor processor) {
    search(clazz, methodSignature, new MultiMatch(Utilities.createMethodFromText(clazz, matchMacro)), processor);
  }

  @Nullable
  private static MultiMatch.Bindings<PsiElement> match(PsiClass clazz, String methodSignature, String matchMacro) {
    PsiMethod method = Utilities.findMethodBySignature(clazz, methodSignature);
    if (method == null) {
      return null;
    }
    MultiMatch matcher = new MultiMatch(Utilities.createMethodFromText(clazz, matchMacro));
    List<MultiMatch.Bindings<PsiElement>> results = search(method.getBody(), matcher);
    if (results.size() != 1) {
      return null;
    }
    return results.get(0);
  }

  @Nullable
  public static MultiMatch.Bindings<PsiElement> match(Module module, String className, String methodSignature, String matchMacro) {
    PsiClass clazz = getPsiClass(module, className);
    if (clazz == null) {
      return null;
    }
    return match(clazz, methodSignature, matchMacro);
  }
}
