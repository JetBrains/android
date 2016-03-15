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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.NavigationView;
import com.android.tools.idea.editors.navigation.NavigationEditorUtils;
import com.android.tools.idea.editors.navigation.model.*;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.MergedManifest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Analyser {
  private static final Logger LOG = Logger.getInstance(Analyser.class.getName());
  private static final String[] ID_PREFIXES = {"@+id/", "@android:id/"};
  private static final boolean DEBUG = false;
  private static final String[] ACTIVITY_SIGNATURES =
    {"void onCreate(Bundle b)", "void onCreateView(View parent, String name, Context context, AttributeSet attrs)"};
  private static final String[] FRAGMENT_SIGNATURES = {"void onCreate(Bundle b)", "void onViewCreated(View v, Bundle b)"};
  public static final String FAKE_OVERFLOW_MENU_ID = "$overflow_menu$";
  public static final String EMPTY_LAYOUT_TAG = SdkConstants.SPACE;


  private final Module myModule;
  private final Macros myMacros;

  public Analyser(Module module) {
    myModule = module;
    myMacros = Macros.getInstance(myModule.getProject());
  }

  public NavigationModel getNavigationModel(Configuration configuration) {
    return deriveAllStatesAndTransitions(configuration);
  }

  @Nullable
  private static String qualifyClassNameIfNecessary(@Nullable String packageName, String className) {
    if (className == null) {
      return null;
    }
    else {
      if (className.startsWith(".")) {
        if (packageName == null) {
          LOG.warn("Missing package name for unqualified class: " + className);
          return null;
        }
        return packageName + className;
      }
      else {
        return className;
      }
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

  private static Set<String> getActivitiesFromManifestFile(Module module) {
    Set<String> result = new HashSet<String>();
    MergedManifest manifestInfo = ManifestInfo.get(module);
    String packageName = manifestInfo.getPackage();
    List<Activity> activities = manifestInfo.getActivities();
    for (Activity activity : activities) {
      AndroidAttributeValue<PsiClass> activityClass = activity.getActivityClass();
      String className = activityClass.getRawText();
      String qualifiedName = qualifyClassNameIfNecessary(packageName, className);
      result.add(qualifiedName);
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

  private static MenuState getMenuState(String className, String menuName, Map<String, MenuState> classNameToMenuState) {
    MenuState result = classNameToMenuState.get(className);
    if (result == null) {
      classNameToMenuState.put(menuName, result = MenuState.create(className, menuName));
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
    search(input, evaluator, myMacros.createMacro("void assign(Object $lhs, Object $rhs) { $lhs = $rhs; }"), new Processor() {
      @Override
      public void process(MultiMatch.Bindings<PsiElement> exp) {
        PsiElement lExpression = exp.get("$lhs");
        PsiElement rExpression = exp.get("$rhs");
        if (lExpression instanceof PsiReferenceExpression && rExpression instanceof PsiExpression) {
          PsiReferenceExpression ref = (PsiReferenceExpression)lExpression;
          PsiElement resolvedValue = ref.resolve();
          if (variableType.isInstance(resolvedValue)) {
            //noinspection unchecked
            result.put((T)resolvedValue, evaluator.evaluate((PsiExpression)rExpression));
          }
        }
      }
    });
    return result;
  }

  public Map<PsiLocalVariable, Object> searchForVariableBindings(@Nullable PsiElement input, final Evaluator evaluator) {
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
    PsiMethod method = NavigationEditorUtils.findMethodBySignature(activityClass, "void onCreate(Bundle bundle)");
    if (method == null) {
      return Collections.emptyMap();
    }
    return searchForVariableAssignments(method.getBody(), PsiField.class, evaluator);
  }

  private Map<PsiLocalVariable, Object> getLocalVariableBindingsInOnViewCreated(PsiClass activityClass, Evaluator evaluator) {
    if (activityClass == null) {
      return Collections.emptyMap();
    }
    PsiMethod method = NavigationEditorUtils.findMethodBySignature(activityClass, "void onViewCreated(View view, Bundle bundle)");
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
        MultiMatch.Bindings<PsiElement> match1 = myMacros.getFindViewById1().match(exp);
        if (match1 != null) {
          String id = match1.bindings.get("$id").getText();
          return ids.contains(id) ? new Object() : null;
        }
        MultiMatch.Bindings<PsiElement> match2 = myMacros.getFindFragmentByTag().match(exp);
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
                                 @Nullable PsiClass activityClass,
                                 PsiClass activityOrFragmentClass,
                                 boolean isActivity) {
    Set<String> tags = getTags(getXmlFile(configuration, getQualifiedName(activityClass), true));
    Set<String> ids = getIds(getXmlFile(configuration, getQualifiedName(activityOrFragmentClass), isActivity));

    Map<PsiField, Object> noFields = Collections.emptyMap();
    Map<PsiLocalVariable, Object> noVars = Collections.emptyMap();
    Evaluator evaluator1 = getEvaluator(ids, tags, noFields, noVars);
    Map<PsiField, Object> fieldBindings = getFieldAssignmentsInOnCreate(activityOrFragmentClass, evaluator1);
    Evaluator evaluator2 = getEvaluator(ids, tags, fieldBindings, noVars);
    Map<PsiLocalVariable, Object> localVariableBindings = getLocalVariableBindingsInOnViewCreated(activityOrFragmentClass, evaluator2);
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
    return (XmlFile)NavigationView.getLayoutXmlFile(false, xmlFileName, configuration, myModule.getProject());
  }

  private static PsiLocator<String> getGetTag(final Macros macros) {
    return new PsiLocator<String>() {
      @Nullable
      @Override
      public String locateIn(MultiMatch.Bindings<PsiElement> args) {
        PsiElement view = args.get("$view");

        MultiMatch.Bindings<PsiElement> bindings1 = macros.getFindViewById1().match(view);
        if (bindings1 != null) {
          return bindings1.get("$id").getText();
        }

        MultiMatch.Bindings<PsiElement> bindings2 = macros.getFindViewById2().match(view);
        if (bindings2 != null) {
          return bindings2.get("$id").getText();
        }

        return null;
      }
    };
  }

  private static PsiLocator<String> getFindMenuItem(final Macros macros) {
    return new PsiLocator<String>() {
      @Nullable
      @Override
      public String locateIn(MultiMatch.Bindings<PsiElement> args) {
        PsiElement view = args.get("$menuItem");

        MultiMatch.Bindings<PsiElement> bindings = macros.getFindMenuItem().match(view);
        return (bindings == null) ? null : bindings.get("$id").getText();
      }
    };
  }

  private static Processor createProcessor(final NavigationModel model,
                                           final State fromState,
                                           @Nullable final String fromFragmentClassName,
                                           final Evaluator evaluator,
                                           final Map<String, ActivityState> classNameToActivityState,
                                           final String name,
                                           final MultiMatch matcher,
                                           final PsiLocator<String> locator1,
                                           final PsiLocator<PsiElement> locator2) {
    return new Processor() {
      @Override
      public void process(MultiMatch.Bindings<PsiElement> args) {
        search(args.get(name), evaluator, matcher,
               createProcessor(model, classNameToActivityState, fromState, fromFragmentClassName, constant(locator1.locateIn(args)),
                               locator2));
      }
    };
  }

  // Look for 'simple' names of classes here to cover both platform and support library derivatives.
  private static boolean isListInheritor(PsiClass c, boolean activity) {
    String baseName = activity ? "ListActivity" : "ListFragment";
      for(PsiClass s = c; s != null; s = s.getSuperClass()) {
        if (s.getName().equals(baseName)) {
          return true;
        }
      }
    return false;
  }

  private void deriveTransitions(final NavigationModel model,
                                 final MiniModel miniModel,
                                 final ActivityState activityState,
                                 @Nullable final String fragmentClassName,
                                 final Configuration configuration,
                                 final boolean isActivity) {
    boolean isFragment = !isActivity;

    final String activityClassName = activityState.getClassName();

    if (DEBUG) LOG.info("Analyser: activityClassName = " + activityClassName);
    if (DEBUG) LOG.info("Analyser: fragmentClassName = " + fragmentClassName);

    final PsiClass activityClass = NavigationEditorUtils.getPsiClass(myModule, activityClassName);
    if (activityClass == null) {
      // Either a build is underway or a navigation file is out-of-date and refers to classes that have been deleted. Give up.
      LOG.info("Class " + activityClassName + " not found");
      return;
    }
    final PsiClass fragmentClass = fragmentClassName != null ? NavigationEditorUtils.getPsiClass(myModule, fragmentClassName) : null;
    if (isFragment && fragmentClass == null) {
      // Either a build is underway or a navigation file is out-of-date and refers to classes that have been deleted. Give up.
      LOG.info("Class " + fragmentClassName + " not found");
      return;
    }

    final PsiClass activityOrFragmentClass = isActivity ? activityClass : fragmentClass;

    final Evaluator evaluator = getEvaluator(configuration, activityClass, activityOrFragmentClass, isActivity);
    final Map<String, ActivityState> classNameToActivityState = miniModel.classNameToActivityState;

    // Search for menu inflation in Activities

    if (isActivity) {
      boolean actionBarDisabled = false;
      final ResourceResolver resolver = configuration.getResourceResolver();
      if (resolver != null) {
        ResourceValue value = resolver.findItemInTheme("windowActionBar", true);
        if (value != null && "false".equalsIgnoreCase(value.getValue())) {
          actionBarDisabled = true;
        }
      }
      if (!actionBarDisabled) {
        search(activityClass, "boolean onCreateOptionsMenu(Menu menu)",
               "void macro(Object target, String id, Menu menu) { target.inflate(id, menu); }", new Processor() {
            @Override
            public void process(MultiMatch.Bindings<PsiElement> args) {
              String menuIdName = args.get("id").getLastChild().getText();
              final MenuState menu = getMenuState(activityClassName, menuIdName, miniModel.classNameToMenuToMenuState);
              addTransition(model, new Transition(Transition.PRESS, Locator.of(activityState, FAKE_OVERFLOW_MENU_ID), Locator.of(menu)));
              for (PsiClass superClass = activityClass; superClass != null; superClass = superClass.getSuperClass()) {
                // Search for menu item bindings in the style the Navigation Editor generates them
                search(superClass, "boolean onPrepareOptionsMenu(Menu m)", myMacros.getInstallMenuItemClickAndCallMacro(),
                       createProcessor(model, menu, fragmentClassName, evaluator, classNameToActivityState, "$f",
                                       myMacros.getCreateIntent(),
                                       getFindMenuItem(myMacros), CLASS_NAME_1));
                // Search for switch statement style menu item bindings
                search(superClass, "boolean onOptionsItemSelected(MenuItem item)", myMacros.getCreateIntent(),
                       createProcessor(model, classNameToActivityState, menu, fragmentClassName, constant(null), CLASS_NAME_1));
              }
            }
          });
      }
    }

    // In both Activities and Fragments, search for:
    //    'onClick' listeners in Views (Buttons etc),
    //    'OnItemClick' listeners in ListViews.
    String[] signatures = isActivity ? ACTIVITY_SIGNATURES : FRAGMENT_SIGNATURES;
    for (String signature : signatures) {
      search(activityOrFragmentClass, signature, myMacros.getInstallClickAndCallMacro(),
             createProcessor(model, activityState, fragmentClassName, evaluator, classNameToActivityState, "$f", myMacros.getCreateIntent(),
                             getGetTag(myMacros), CLASS_NAME_1));
      search(activityOrFragmentClass, signature, myMacros.getInstallItemClickAndCallMacro(),
             createProcessor(model, activityState, fragmentClassName, evaluator, classNameToActivityState, "$f", myMacros.getCreateIntent(),
                             constant(NavigationView.LIST_VIEW_ID), CLASS_NAME_1));
    }

    // Search for subclass style item click listeners in ListActivity and ListFragment derivatives
    if (isListInheritor(activityOrFragmentClass, isActivity)) {
      search(activityOrFragmentClass, "void onListItemClick(ListView l, View v, int position, long id)", myMacros.getCreateIntent(),
             createProcessor(model, classNameToActivityState, activityState, fragmentClassName, constant(NavigationView.LIST_VIEW_ID),
                             CLASS_NAME_1));
    }

    // Accommodate idioms from Master-Detail template
    if (isFragment) {
      if (isListInheritor(fragmentClass, false)) {
        search(activityOrFragmentClass, "void onListItemClick(ListView listView, View view, int position, long id)",
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
                       PsiMethod implementation = activityClass.findMethodBySignature(resolvedMethod, false);
                       if (implementation != null) {
                         Evaluator evaluator = getEvaluator(configuration, activityClass, activityClass, true);
                         search(implementation.getBody(), evaluator, myMacros.getCreateIntent(),
                                createProcessor(model, classNameToActivityState, activityState, fragmentClassName, constant(null),
                                                CLASS_NAME_1));
                       }
                     }
                   }
                 }
               });
      }
    }
  }

  static class MiniModel {
    final Map<String, ActivityState> classNameToActivityState;
    final Map<String, MenuState> classNameToMenuToMenuState;


    MiniModel(Map<String, ActivityState> classNameToActivityState, Map<String, MenuState> classNameToMenuToMenuState) {
      this.classNameToActivityState = classNameToActivityState;
      this.classNameToMenuToMenuState = classNameToMenuToMenuState;
    }

    MiniModel() {
      this(new HashMap<String, ActivityState>(), new HashMap<String, MenuState>());
    }
  }

  private NavigationModel deriveAllStatesAndTransitions(Configuration configuration) {
    NavigationModel model = new NavigationModel();
    Set<String> activityClassNames = getActivitiesFromManifestFile(myModule);
    if (DEBUG) {
      LOG.info("deriveAllStatesAndTransitions = " + activityClassNames);
    }
    MiniModel toDo = new MiniModel(getActivityClassNameToActivityState(activityClassNames), new HashMap<String, MenuState>());
    MiniModel next = new MiniModel();
    MiniModel done = new MiniModel();

    if (DEBUG) {
      LOG.info("activityStates = " + toDo.classNameToActivityState.values());
    }

    while (!toDo.classNameToActivityState.isEmpty()) {
      for (ActivityState sourceActivity : toDo.classNameToActivityState.values()) {
        if (done.classNameToActivityState.containsKey(sourceActivity.getClassName())) {
          continue;
        }
        XmlFile layoutFile = getXmlFile(configuration, sourceActivity.getClassName(), true);
        if (isEmptyLayout(layoutFile)) {
          continue;
        }
        model.addState(sourceActivity); // covers the case of Activities that are not part of a transition (e.g. a model with one Activity)
        deriveTransitions(model, next, sourceActivity, null, configuration, true);

        // Examine fragments associated with this activity

        List<FragmentEntry> fragments = getFragmentEntries(layoutFile);

        List<FragmentEntry> fragmentList = sourceActivity.getFragments();
        fragmentList.clear();
        fragmentList.addAll(fragments);

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

  /**
   * Find a specific element from the results of an expression match.
   */
  private abstract static class PsiLocator<T> {
    public abstract T locateIn(MultiMatch.Bindings<PsiElement> bindings);
  }

  private static final PsiLocator<PsiElement> CLASS_NAME_1 = new PsiLocator<PsiElement>() {
    @Override
    public PsiElement locateIn(MultiMatch.Bindings<PsiElement> args) {
      return args.get("activityClass");
    }
  };

  private static PsiLocator<String> constant(@Nullable final String constant) {
    return new PsiLocator<String>() {
      @Nullable
      @Override
      public String locateIn(MultiMatch.Bindings<PsiElement> args) {
        return constant;
      }
    };
  }

  private static Processor createProcessor(final NavigationModel model,
                                           final Map<String, ActivityState> activities,
                                           final State fromState,
                                           @Nullable final String fromFragmentClassName,
                                           final PsiLocator<String> fromViewIdLocator,
                                           final PsiLocator<PsiElement> toStateClassLocator) {
    return new Processor() {
      @Override
      public void process(MultiMatch.Bindings<PsiElement> args) {
        //if (BREAK);
        PsiElement activityClass = toStateClassLocator.locateIn(args).getFirstChild();
        String qualifiedName = getQualifiedName(activityClass);
        if (qualifiedName != null) {
          ActivityState toState = getActivityState(qualifiedName, activities);
          String viewId = fromViewIdLocator.locateIn(args);
          addTransition(model, new Transition(Transition.PRESS, Locator.of(fromState, fromFragmentClassName, viewId), Locator.of(toState)));
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
          String fragmentTag = tag.getAttributeValue("tag", SdkConstants.ANDROID_URI);
          String fragmentClassName = tag.getAttributeValue("name", SdkConstants.ANDROID_URI);
          if (DEBUG) LOG.info("Analyser: fragmentClassName = " + fragmentClassName);
          if (fragmentClassName != null) {
            result.add(new FragmentEntry(fragmentClassName, fragmentTag));
          }
        }
      }
    });
    return result;
  }

  private static boolean isEmptyLayout(@Nullable XmlFile psiFile) {
    if (psiFile == null) {
      return false;
    }
    final Ref<Boolean> result = new Ref<Boolean>(false);
    psiFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        // No call to super, we're only interested in the top level element
        if (tag.getName().equals(EMPTY_LAYOUT_TAG)) {
          result.set(true);
        }
      }
    });
    return result.get();
  }

  public static String getPrefix(@Nullable String idName) {
    if (idName == null) {
      return "";
    }
    for (String prefix : ID_PREFIXES) {
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
        String id = tag.getAttributeValue("id", SdkConstants.ANDROID_URI);
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

  private static boolean addTransition(NavigationModel model, Transition transition) {
    if (DEBUG) LOG.info("Analyser: Adding transition: " + transition);
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
  public static String getXMLFileName(Module module, String className, boolean isActivity) {
    PsiClass clazz = NavigationEditorUtils.getPsiClass(module, className);
    if (clazz == null) {
      LOG.warn("Couldn't find class: " + className);
      return null;
    }
    // Use $R because we sometimes see e.g.: com.example.simplemail.activity.R.layout.compose_activity
    String signature = isActivity ? "void onCreate(Bundle bundle)" : "View onCreateView(LayoutInflater li, ViewGroup vg, Bundle b)";
    String body = isActivity
                  ? "void macro(Object $R, Object $id) { setContentView($R.layout.$id); }"
                  : "void macro(Object $inflater, Object $R, Object $id, Object $p) { $inflater.inflate($R.layout.$id, $p, false); }";
    PsiClass stop = NavigationEditorUtils.getPsiClass(module, isActivity ? "android.app.Activity" : "android.app.Fragment");

    for (PsiClass superClass = clazz; superClass != stop && superClass != null; superClass = superClass.getSuperClass()) {
      MultiMatch.Bindings<PsiElement> exp = match(superClass, signature, body);
      if (exp != null) {
        String id = exp.get("$id").getText();
        if (id != null) {
          return id;
        }
      }
    }
    return null;
  }

  private static abstract class Processor {
    public abstract void process(MultiMatch.Bindings<PsiElement> exp);
  }

  public static void search(@Nullable PsiElement input, final Evaluator evaluator, final MultiMatch matcher, final Processor processor) {
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
    PsiMethod method = NavigationEditorUtils.findMethodBySignature(clazz, methodSignature);
    if (method == null) {
      return;
    }
    search(method.getBody(), matcher, processor);
  }

  private static void search(PsiClass clazz, String methodSignature, String matchMacro, Processor processor) {
    final PsiMethod psiMethod = NavigationEditorUtils.createMethodFromText(clazz, matchMacro);
    search(clazz, methodSignature, new MultiMatch(CodeTemplate.fromMethod(psiMethod)), processor);
  }

  @Nullable
  private static MultiMatch.Bindings<PsiElement> match(@NotNull PsiClass clazz, String methodSignature, String matchMacro) {
    PsiMethod method = NavigationEditorUtils.findMethodBySignature(clazz, methodSignature);
    if (method == null) {
      return null;
    }
    final PsiMethod psiMethod = NavigationEditorUtils.createMethodFromText(clazz, matchMacro);
    MultiMatch matcher = new MultiMatch(CodeTemplate.fromMethod(psiMethod));
    List<MultiMatch.Bindings<PsiElement>> results = search(method.getBody(), matcher);
    if (results.size() != 1) {
      return null;
    }
    return results.get(0);
  }

  @SuppressWarnings("UnusedDeclaration")
  public static List<String> findProperties(@Nullable PsiClass input) {
    if (input == null) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<String>();
    input.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        if (method.getName().startsWith("get")) {
          result.add(PropertyUtil.getPropertyName(method));
        }
      }
    });
    return result;
  }
}
