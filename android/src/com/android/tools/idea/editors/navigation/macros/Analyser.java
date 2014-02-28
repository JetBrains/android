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
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.navigation.Utilities.getPropertyName;
import static com.android.tools.idea.editors.navigation.Utilities.getPsiClass;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Analyser {
  private static final Logger LOG = Logger.getInstance("#" + Analyser.class.getName());
  private final Project myProject;
  private Module myModule;
  private Macros myMacros;
  private static final String ID_PREFIX = "@+id/";

  public Analyser(Project project, Module module) {
    myProject = project;
    myModule = module;
    myMacros = Macros.getInstance(myModule.getProject());
  }

  @Nullable
  private static String qualifyClassNameIfNecessary(String packageName, String className) {
    return (className == null) ? null : !className.contains(".") ? (packageName + "." + className) : className;
  }

  private static Set<String> qualifyClassNames(Set<String> classNames, String packageName) {
    Set<String> result = new HashSet<String>();
    for (String name : classNames) {
      result.add(qualifyClassNameIfNecessary(packageName, name));
    }
    return result;
  }

  private static class Results {
    String packageName;
    Set<String> activities = new HashSet<String>();
    Set<String> mainActivities = new HashSet<String>();
    Set<String> launcherActivities = new HashSet<String>();
  }

  private static Set<String> getActivityClassNamesFromManifestFile(@Nullable XmlFile manifest) {
    if (manifest == null) {
      return Collections.emptySet();
    }
    final Results results = new Results();
    manifest.accept(new XmlRecursiveElementVisitor() {
      @Nullable
      private String processFilter(XmlTag tag, String filterName, String parentName) {
        if (tag.getName().equals(parentName) && filterName.equals(tag.getAttributeValue("android:name"))) {
          XmlTag parent = (XmlTag)tag.getParent();
          if (parent.getName().equals("intent-filter")) {
            XmlTag grandparent = (XmlTag)parent.getParent();
            if (grandparent.getName().equals("activity")) {
              return grandparent.getAttributeValue("android:name");
            }
          }
        }
        return null;
      }

      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (tag.getName().equals("manifest")) {
          results.packageName = tag.getAttributeValue("package");
        }
        if (tag.getName().equals("activity")) {
          String name = tag.getAttributeValue("android:name");
          if (name != null) {
            results.activities.add(name);
          }
        }
        String mainActivity = processFilter(tag, "android.intent.action.MAIN", "action");
        if (mainActivity != null) {
          results.mainActivities.add(mainActivity);
        }
        String launcherActivity = processFilter(tag, "android.intent.category.LAUNCHER", "category");
        if (launcherActivity != null) {
          results.launcherActivities.add(launcherActivity);
        }
      }
    });
    String packageName = results.packageName;
    results.activities = qualifyClassNames(results.activities, packageName);
    results.mainActivities = qualifyClassNames(results.mainActivities, packageName);
    results.launcherActivities = qualifyClassNames(results.launcherActivities, packageName);

    Set<String> result = new HashSet<String>(results.activities);
    result.retainAll(results.mainActivities);
    result.retainAll(results.launcherActivities);
    return result;
  }

  private static Set<String> readManifestFile(Project project) {
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile manifestFile = baseDir.findFileByRelativePath("AndroidManifest.xml");
    if (manifestFile == null) {
      manifestFile = baseDir.findFileByRelativePath("app/src/main/AndroidManifest.xml");
    }
    if (manifestFile == null) {
      if (NavigationEditor.DEBUG) System.out.println("Can't find Manifest"); // todo generalize
      return Collections.emptySet();
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    XmlFile manifestPsiFile = (XmlFile)psiManager.findFile(manifestFile);
    return getActivityClassNamesFromManifestFile(manifestPsiFile);
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

  public static abstract class StaticEvaluator {
    public abstract boolean evaluate(@Nullable PsiExpression expression);
  }

  public static void searchForCallExpression2(@Nullable PsiElement element,
                                              final StaticEvaluator evaluator,
                                              final MultiMatch matcher,
                                              final Statement statement) {
    if (element != null) {
      element.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitIfStatement(PsiIfStatement statement) {
          if (evaluator.evaluate(statement.getCondition())) {
            super.visitIfStatement(statement);
          }
        }

        @Override
        public void visitCallExpression(PsiCallExpression expression) {
          super.visitCallExpression(expression);
          MultiMatch.Bindings<PsiElement> exp = matcher.match(expression);
          if (exp != null) {
            statement.apply(exp);
          }
        }
      });
    }
  }

  public static Map<PsiVariable, PsiExpression> searchForFieldAssignments(@Nullable PsiElement element, final StaticEvaluator evaluator) {
    final Map<PsiVariable, PsiExpression> result = new HashMap<PsiVariable, PsiExpression>();
    if (element != null) {
      element.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitIfStatement(PsiIfStatement statement) {
          if (evaluator.evaluate(statement.getCondition())) {
            super.visitIfStatement(statement);
          }
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
          super.visitAssignmentExpression(expression);
          PsiExpression lExpression = expression.getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression)lExpression;
            PsiElement resolve = ref.resolve();
            if (resolve instanceof PsiField) {
              result.put((PsiField)resolve, expression.getRExpression());
            }
          }
        }
      });
    }
    return result;
  }

  private static Map<PsiVariable, PsiExpression> getVariableToValueBindings(PsiClass activityClass1, StaticEvaluator evaluator) {
    PsiMethod onCreate = Utilities.findMethodBySignature(activityClass1, "void onCreate(Bundle bundle)");
    if (onCreate == null) {
      return Collections.emptyMap();
    }
    return searchForFieldAssignments(onCreate.getBody(), evaluator);
  }

  private static boolean isLiteralFor(@Nullable PsiExpression exp, @Nullable Object value) {
    if (exp instanceof PsiLiteral) {
      PsiLiteral literal = (PsiLiteral)exp;
      return literal.getValue() == value;
    }
    return false;
  }

  private StaticEvaluator getEvaluator(final Set<String> ids) {
    return new StaticEvaluator() {
      @Override
      public boolean evaluate(@Nullable PsiExpression expression) {
        if (expression instanceof PsiBinaryExpression) {
          PsiBinaryExpression exp = (PsiBinaryExpression)expression;
          if (exp.getOperationSign().getTokenType() == JavaTokenType.NE && isLiteralFor(exp.getROperand(), null)) {
            MultiMatch.Bindings<PsiElement> match = myMacros.findViewById.match(exp.getLOperand());
            if (match != null) {
              String id = match.bindings.get("$id").getText();
              if (ids.contains(id)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    };
  }

  private static StaticEvaluator getEvaluator(final Map<PsiVariable, PsiExpression> variableToValue) {
    return new StaticEvaluator() {
      @Override
      public boolean evaluate(@Nullable PsiExpression expression) {
        if (expression instanceof PsiReferenceExpression) {
          PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression)expression;
          PsiElement resolved = psiReferenceExpression.resolve();
          if (resolved instanceof PsiField) {
            PsiField psiField = (PsiField)resolved;
            return isLiteralFor(variableToValue.get(psiField), Boolean.TRUE);
          }
        }
        return false;
      }
    };
  }

  @Nullable
  private XmlFile getXmlFile(ActivityState activity, VirtualFile modelFile) {
    String xmlFileName = NavigationView.getXMLFileName(myModule, activity);
    return (XmlFile)NavigationView.getLayoutXmlFile(false, xmlFileName, modelFile, myProject);
  }

  private void deriveTransitions(final NavigationModel model,
                                 final MiniModel miniModel,
                                 final ActivityState fromActivityState,
                                 final String activityOrFragmentClassName,
                                 final VirtualFile navigationModelFile) {

    if (NavigationEditor.DEBUG) System.out.println("className = " + activityOrFragmentClassName);

    final PsiClass activityOrFragmentClass = Utilities.getPsiClass(myModule, activityOrFragmentClassName);

    if (activityOrFragmentClass == null) {
      // Navigation file is out-of-date and refers to classes that have been deleted. That's okay.
      LOG.info("Class " + activityOrFragmentClassName + " not found");
      return;
    }

    // Search for menu inflation

    search(activityOrFragmentClass,
           "boolean onCreateOptionsMenu(Menu menu)",
           "void macro(Object target, String id, Menu menu) { target.inflate(id, menu); }",
           new Statement() {
             @Override
             public void apply(MultiMatch.Bindings<PsiElement> args) {
               String menuIdName = args.get("id").getLastChild().getText();
               final MenuState menu = getMenuState(menuIdName, miniModel.menuNameToMenuState);
               addTransition(model, new Transition("click", new Locator(fromActivityState), new Locator(menu)));
               // Search for menu item bindings
               search(activityOrFragmentClass,
                      "boolean onPrepareOptionsMenu(Menu m)",
                      myMacros.installMenuItemOnGetMenuItemAndLaunchActivityMacro,
                      new Statement() {
                        @Override
                        public void apply(MultiMatch.Bindings<PsiElement> args) {
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
           new Statement() {
             @Override
             public void apply(MultiMatch.Bindings<PsiElement> args) {
               PsiElement $view = args.get("$view");
               MultiMatch.Bindings<PsiElement> bindings = myMacros.findViewById.match($view);
               if (bindings != null) {
                 String tag = bindings.get("$id").getText();
                 searchForCallExpression(args.get("$f"), myMacros.createIntent,
                                         createProcessor(tag, miniModel.classNameToActivityState, model, fromActivityState));
               }
             }
           });

    // Search for 'onItemClick' listeners is listViews

    search(activityOrFragmentClass,
           "void onViewCreated(View v, Bundle b)",
           myMacros.installItemClickAndCallMacro,
           new Statement() {
             @Override
             public void apply(MultiMatch.Bindings<PsiElement> args) {
               PsiElement $listView = args.get("$listView");
               final String viewName = $listView == null ? null : getPropertyName(removeTrailingParens($listView.getText()));
               searchForCallExpression(args.get("$f"), myMacros.createIntent,
                                       createProcessor(viewName, miniModel.classNameToActivityState, model, fromActivityState));
             }
           });

    // Accommodate idioms from Master-Detail template

    PsiClassType superType = activityOrFragmentClass.getSuperTypes()[0];
    if (superType.getClassName().equals("ListFragment")) {
      search(activityOrFragmentClass,
             "void onListItemClick(ListView listView, View view, int position, long id)",
             "void macro(Object f) { f.$(); }", // this obscure term matches 'any method call'
             new Statement() {
               @Override
               public void apply(MultiMatch.Bindings<PsiElement> args) {
                 PsiElement exp = args.get("f");
                 if (exp instanceof PsiMethodCallExpression) {
                   PsiMethodCallExpression call = (PsiMethodCallExpression)exp;
                   if (call.getFirstChild().getFirstChild().getText().equals("super")) {
                     return;
                   }
                   PsiMethod resolvedMethod = call.resolveMethod();
                   if (resolvedMethod != null) {
                     final PsiClass activityClass =
                       Utilities.getPsiClass(myModule, fromActivityState.getClassName()); // todo fix efficiency
                     if (activityClass != null) {
                       final PsiMethod implementation = activityClass.findMethodBySignature(resolvedMethod, false);
                       if (implementation != null) {
                         StaticEvaluator evaluator1 = getEvaluator(getIds(getXmlFile(fromActivityState, navigationModelFile)));
                         StaticEvaluator evaluator2 = getEvaluator(getVariableToValueBindings(activityClass, evaluator1));
                         searchForCallExpression2(implementation.getBody(),
                                                  evaluator2,
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


    MiniModel(Map<String, ActivityState> classNameToActivityState,
              Map<String, MenuState> menuNameToMenuState) {
      this.classNameToActivityState = classNameToActivityState;
      this.menuNameToMenuState = menuNameToMenuState;
    }

    MiniModel() {
      this(new HashMap<String, ActivityState>(), new HashMap<String, MenuState>());
    }
  }

  public NavigationModel deriveAllStatesAndTransitions(final NavigationModel model, final VirtualFile modelFile) {
    MiniModel toDo = new MiniModel(getActivityClassNameToActivityState(readManifestFile(myProject)), new HashMap<String, MenuState>());
    MiniModel next = new MiniModel();
    MiniModel done = new MiniModel();

    if (NavigationEditor.DEBUG) System.out.println("activityStates = " + toDo.classNameToActivityState.values());

    while (!toDo.classNameToActivityState.isEmpty()) {
      for (ActivityState sourceActivity : toDo.classNameToActivityState.values()) {
        if (done.classNameToActivityState.containsKey(sourceActivity.getClassName())) {
          continue;
        }
        model.addState(sourceActivity); // covers the case of Activities that are not part of a transition (e.g. a model with one Activity)
        deriveTransitions(model, next, sourceActivity, sourceActivity.getClassName(), modelFile);

        // Examine fragments associated with this activity

        XmlFile layoutFile = getXmlFile(sourceActivity, modelFile);
        List<FragmentEntry> fragments = getFragmentEntries(layoutFile);

        for (FragmentEntry fragment : fragments) {
          deriveTransitions(model, next, sourceActivity, fragment.className, modelFile);
        }
      }
      done.classNameToActivityState.putAll(toDo.classNameToActivityState);
      toDo = next;
      next = new MiniModel();
    }
    return model;
  }

  private static Statement createProcessor(@Nullable final String viewName,
                                           final Map<String, ActivityState> activities,
                                           final NavigationModel model,
                                           final ActivityState fromState) {
    return new Statement() {
      @Override
      public void apply(MultiMatch.Bindings<PsiElement> args) {
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
          if (NavigationEditor.DEBUG) System.out.println("fragmentClassName = " + fragmentClassName);
          if (fragmentTag != null && fragmentClassName != null) {
            result.add(new FragmentEntry(fragmentTag, fragmentClassName));
          }
        }
      }
    });
    return result;
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
          assert id.startsWith(ID_PREFIX);
          result.add(id.substring(ID_PREFIX.length()));
        }
      }
    });
    return result;
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

  private static Map<String, ActivityState> getActivityClassNameToActivityState(Set<String> activityClassNames) {
    Map<String, ActivityState> result = new HashMap<String, ActivityState>();
    for (String activityClassName : activityClassNames) {
      result.put(activityClassName, new ActivityState(activityClassName));
    }
    return result;
  }

  @Nullable
  public static String getXMLFileName(Module module, String controllerClassName) {
    MultiMatch.Bindings<PsiElement> exp =
      match(module, controllerClassName, "public void onCreate(Bundle bundle)", "void macro(String id) { setContentView(id); }");
    if (exp == null) {
      return null;
    }
    PsiElement id = exp.get("id");
    PsiElement unqualifiedXmlName = id.getLastChild();
    return unqualifiedXmlName.getText();
  }

  public static abstract class Statement {
    public abstract void apply(MultiMatch.Bindings<PsiElement> exp);
  }

  public static void searchForCallExpression(@Nullable PsiElement element, final MultiMatch matcher, final Statement statement) {
    if (element != null) {
      element.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitCallExpression(PsiCallExpression expression) {
          super.visitCallExpression(expression);
          MultiMatch.Bindings<PsiElement> exp = matcher.match(expression);
          if (exp != null) {
            statement.apply(exp);
          }
        }
      });
    }
  }

  public static List<MultiMatch.Bindings<PsiElement>> search(@Nullable PsiElement element, final MultiMatch matcher) {
    final List<MultiMatch.Bindings<PsiElement>> results = new ArrayList<MultiMatch.Bindings<PsiElement>>();
    searchForCallExpression(element, matcher, new Statement() {
      @Override
      public void apply(MultiMatch.Bindings<PsiElement> exp) {
        results.add(exp);
      }
    });
    return results;
  }

  private static void search(PsiClass clazz, String methodSignature, MultiMatch matcher, Statement statement) {
    PsiMethod method = Utilities.findMethodBySignature(clazz, methodSignature);
    if (method == null) {
      return;
    }
    searchForCallExpression(method.getBody(), matcher, statement);
  }

  private static void search(PsiClass clazz, String methodSignature, String matchMacro, Statement statement) {
    search(clazz, methodSignature, new MultiMatch(Utilities.createMethodFromText(clazz, matchMacro)), statement);
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
