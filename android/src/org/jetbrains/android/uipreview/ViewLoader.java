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

package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.util.Pair;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.lang.reflect.*;
import java.util.*;

import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_INCLUDE;

/**
 * Handler for loading views for the layout editor on demand, and reporting issues with class
 * loading, instance creation, etc.
 */
public class ViewLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.ViewLoader");

  @NotNull private final Module myModule;
  @NotNull private final ProjectResources myProjectResources;
  @NotNull private final Set<String> myMissingClasses = new TreeSet<String>();
  @NotNull private final Map<String, Throwable> myBrokenClasses = new HashMap<String, Throwable>();
  @NotNull private final Set<String> myClassesWithIncorrectFormat = new HashSet<String>();
  @NotNull private final Map<String, Class<?>> myLoadedClasses = new HashMap<String, Class<?>>();
  @NotNull private RenderLogger myLogger;
  @Nullable private final ClassLoader myParentClassLoader;
  @Nullable private ProjectClassLoader myProjectClassLoader;
  @Nullable private String myMissingRClassMessage;
  @Nullable private String myRClassName;
  private boolean myMissingRClass;
  private boolean myIncorrectRClassFormat;
  private boolean myHasProjectLoadedClasses;

  public ViewLoader(@NotNull LayoutLibrary layoutLib, @NotNull AndroidFacet facet, @NotNull ProjectResources projectResources,
                    @NotNull RenderLogger logger) {
    myParentClassLoader = layoutLib.getClassLoader();
    myModule = facet.getModule();
    myProjectResources = projectResources;
    myLogger = logger;
  }

  /**
   * Sets the {@link LayoutLog} logger to use for error messages during problems
   *
   * @param logger the new logger to use, or null to clear it out
   */
  public void setLogger(@Nullable RenderLogger logger) {
    myLogger = logger;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Nullable
  public Object loadView(String className, Class[] constructorSignature, Object[] constructorArgs)
    throws ClassNotFoundException {

    Class<?> aClass = myLoadedClasses.get(className);

    try {
      if (aClass != null) {
        return createNewInstance(aClass, constructorSignature, constructorArgs);
      }
      aClass = loadClass(className);

      if (aClass != null) {
        final Object viewObject = createNewInstance(aClass, constructorSignature, constructorArgs);
        myLoadedClasses.put(className, aClass);
        return viewObject;
      }
    }
    catch (LinkageError e) {
      LOG.debug(e);
      myBrokenClasses.put(className, e.getCause());
      recordError(e);
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      myBrokenClasses.put(className, e.getCause());
      recordError(e);
    }
    catch (InvocationTargetException e) {
      LOG.debug(e);

      final Throwable cause = e.getCause();
      if (cause instanceof IncompatibleClassFileFormatException) {
        myClassesWithIncorrectFormat.add(((IncompatibleClassFileFormatException)cause).getClassName());
      }
      else {
        myBrokenClasses.put(className, cause);
      }

      recordError(e);
    }
    catch (IllegalAccessException e) {
      LOG.debug(e);
      myBrokenClasses.put(className, e.getCause());
      recordError(e);
    }
    catch (InstantiationException e) {
      LOG.debug(e);
      myBrokenClasses.put(className, e.getCause());
      recordError(e);
    }
    catch (NoSuchMethodException e) {
      LOG.debug(e);
      myBrokenClasses.put(className, e.getCause());
      recordError(e);
    }
    catch (IncompatibleClassFileFormatException e) {
      myClassesWithIncorrectFormat.add(e.getClassName());
      recordError(e);
    }

    try {
      final Object o = createViewFromSuperclass(className, constructorSignature, constructorArgs);

      if (o != null) {
        return o;
      }
      return createMockView(className, constructorSignature, constructorArgs);
    }
    catch (ClassNotFoundException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (InvocationTargetException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (NoSuchMethodException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (IllegalAccessException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (InstantiationException e) {
      throw new ClassNotFoundException(className, e);
    }
    catch (NoSuchFieldException e) {
      throw new ClassNotFoundException(className, e);
    }
  }

  private void recordError(Throwable e) {
    // Find root cause to log it.
    while (e.getCause() != null) {
      e = e.getCause();
    }

    // Add the missing class to the list so that the renderer can print them later.
    myLogger.recordThrowable(e);
  }

  @Nullable
  private Class<?> loadClass(String className) throws IncompatibleClassFileFormatException {
    try {
      if (myProjectClassLoader == null) {
        myProjectClassLoader = new ProjectClassLoader(myParentClassLoader, myModule);
      }
      return myProjectClassLoader.loadClass(className);
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      if (!className.equals(VIEW_FRAGMENT)) {
        myMissingClasses.add(className);
      }
      return null;
    }
  }

  public boolean hasUnsupportedClassVersionProblem() {
    return myClassesWithIncorrectFormat.size() > 0;
  }

  @Nullable
  private Object createViewFromSuperclass(final String className, final Class[] constructorSignature, final Object[] constructorArgs) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Object>() {
      @Nullable
      @Override
      public Object compute() {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myModule.getProject());
        PsiClass psiClass = facade.findClass(className, myModule.getModuleWithDependenciesAndLibrariesScope(false));

        if (psiClass == null) {
          return null;
        }
        psiClass = psiClass.getSuperClass();
        final Set<String> visited = new HashSet<String>();

        while (psiClass != null) {
          final String qName = psiClass.getQualifiedName();

          if (qName == null ||
              !visited.add(qName) ||
              AndroidUtils.VIEW_CLASS_NAME.equals(psiClass.getQualifiedName())) {
            break;
          }

          if (!AndroidUtils.isAbstract(psiClass)) {
            try {
              Class<?> aClass = myLoadedClasses.get(qName);
              if (aClass == null) {
                aClass = myParentClassLoader.loadClass(qName);
                if (aClass != null) {
                  myLoadedClasses.put(qName, aClass);
                }
              }
              if (aClass != null) {
                final Object instance = createNewInstance(aClass, constructorSignature, constructorArgs);

                if (instance != null) {
                  return instance;
                }
              }
            }
            catch (Throwable e) {
              LOG.debug(e);
            }
          }
          psiClass = psiClass.getSuperClass();
        }
        return null;
      }
    });
  }

  private Object createMockView(String className, Class[] constructorSignature, Object[] constructorArgs)
    throws
    ClassNotFoundException,
    InvocationTargetException,
    NoSuchMethodException,
    InstantiationException,
    IllegalAccessException,
    NoSuchFieldException {

    assert myProjectClassLoader != null;
    final Class<?> mockViewClass = myProjectClassLoader.loadClass(SdkConstants.CLASS_MOCK_VIEW);
    final Object viewObject = createNewInstance(mockViewClass, constructorSignature, constructorArgs);

    final Method setTextMethod = viewObject.getClass().getMethod("setText", CharSequence.class);
    String label = getShortClassName(className);
    if (label.equals(VIEW_FRAGMENT)) {
      label = "<fragment>";
      // TODO:
      // Append "\nPick preview layout from the \"Fragment Layout\" context menu"
      // when used from the layout editor
    }
    else if (label.equals(VIEW_INCLUDE)) {
      label = "Text";
    }

    setTextMethod.invoke(viewObject, label);

    try {
      final Class<?> gravityClass = Class.forName("android.view.Gravity", true, viewObject.getClass().getClassLoader());
      final Field centerField = gravityClass.getField("CENTER");
      final int center = centerField.getInt(null);
      final Method setGravityMethod = viewObject.getClass().getMethod("setGravity", Integer.TYPE);
      setGravityMethod.invoke(viewObject, Integer.valueOf(center));
    }
    catch (ClassNotFoundException e) {
      LOG.info(e);
    }

    return viewObject;
  }

  @NotNull
  public Set<String> getClassesWithIncorrectFormat() {
    return myClassesWithIncorrectFormat;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  private static String getShortClassName(String fqcn) {
    if (fqcn.startsWith("android.")) {
      // android.foo.Name -> android...Name
      int first = fqcn.indexOf('.');
      int last = fqcn.lastIndexOf('.');
      if (last > first) {
        return fqcn.substring(0, first) + ".." + fqcn.substring(last);
      }
    }
    else {
      // com.example.p1.p2.MyClass -> com.example...MyClass
      int first = fqcn.indexOf('.');
      first = fqcn.indexOf('.', first + 1);
      int last = fqcn.lastIndexOf('.');
      if (last > first && first >= 0) {
        return fqcn.substring(0, first) + ".." + fqcn.substring(last);
      }
    }

    return fqcn;
  }

  @SuppressWarnings("ConstantConditions")
  private Object createNewInstance(Class<?> clazz, Class[] constructorSignature, Object[] constructorParameters)
    throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException, InstantiationException {
    Constructor<?> constructor = null;

    try {
      constructor = clazz.getConstructor(constructorSignature);
    }
    catch (NoSuchMethodException e) {
      // View class has 1-parameter, 2-parameter and 3-parameter constructors

      final int paramsCount = constructorSignature.length;
      if (paramsCount == 0) {
        throw e;
      }

      for (int i = 3; i >= 1; i--) {
        if (i == paramsCount) {
          continue;
        }

        final int k = paramsCount < i ? paramsCount : i;

        final Class[] sig = new Class[i];
        System.arraycopy(constructorSignature, 0, sig, 0, k);

        final Object[] params = new Object[i];
        System.arraycopy(constructorParameters, 0, params, 0, k);

        for (int j = k + 1; j <= i; j++) {
          if (j == 2) {
            sig[j - 1] = clazz.getClassLoader().loadClass("android.util.AttributeSet");
            params[j - 1] = null;
          }
          else if (j == 3) {
            // parameter 3: int defstyle
            sig[j - 1] = int.class;
            params[j - 1] = 0;
          }
        }

        constructorSignature = sig;
        constructorParameters = params;

        try {
          constructor = clazz.getConstructor(constructorSignature);
          if (constructor != null) {
            if (constructorSignature.length < 2) {
              LOG.info("wrong_constructor: Custom view " +
                       clazz.getSimpleName() +
                       " is not using the 2- or 3-argument " +
                       "View constructors; XML attributes will not work");
              myLogger.warning("wrongconstructor", //$NON-NLS-1$
                               String.format(
                                "Custom view %1$s is not using the 2- or 3-argument View constructors; XML attributes will not work",
                                clazz.getSimpleName()), null /*data*/);

            }
            break;
          }
        }
        catch (NoSuchMethodException ignored) {
        }
      }

      if (constructor == null) {
        throw e;
      }
    }

    constructor.setAccessible(true);
    return constructor.newInstance(constructorParameters);
  }

  @NotNull
  public Set<String> getMissingClasses() {
    return myMissingClasses;
  }

  public boolean hasLoadedClasses() {
    return myHasProjectLoadedClasses;
  }

  @NotNull
  public Map<String, Throwable> getBrokenClasses() {
    return myBrokenClasses;
  }

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly
   */
  public void loadAndParseRClassSilently() {
    myMissingRClassMessage = null;
    myMissingRClass = false;
    myIncorrectRClassFormat = false;
    myRClassName = null;

    try {
      final String rClassName = RenderUtil.getRClassName(myModule);

      if (rClassName == null) {
        LOG.info("loadAndParseRClass: failed to find manifest package for project %1$s");
        return;
      }
      loadAndParseRClass(rClassName);
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      myMissingRClassMessage = e.getMessage();
      myMissingRClass = true;
    }
    catch (IncompatibleClassFileFormatException e) {
      LOG.debug(e);
      myIncorrectRClassFormat = true;
      myRClassName = e.getClassName();
    }
  }

  public void loadAndParseRClass() throws ClassNotFoundException, IncompatibleClassFileFormatException {
    final String rClassName = RenderUtil.getRClassName(myModule);

    if (rClassName == null) {
      LOG.info("loadAndParseRClass: failed to find manifest package for project %1$s");
      return;
    }
    loadAndParseRClass(rClassName);
  }

  public void loadAndParseRClass(@NotNull String className) throws ClassNotFoundException, IncompatibleClassFileFormatException {
    Class<?> aClass = myLoadedClasses.get(className);
    if (aClass == null) {
      ProjectClassLoader loader = new ProjectClassLoader(null, myModule);
      aClass = loader.loadClass(className);

      if (aClass != null) {
        myLoadedClasses.put(className, aClass);
        myHasProjectLoadedClasses = true;
      }
    }

    if (aClass != null) {
      final Map<ResourceType, TObjectIntHashMap<String>> res2id =
        new EnumMap<ResourceType, TObjectIntHashMap<String>>(ResourceType.class);
      final TIntObjectHashMap<Pair<ResourceType, String>> id2res = new TIntObjectHashMap<Pair<ResourceType, String>>();
      final Map<IntArrayWrapper, String> styleableId2res = new HashMap<IntArrayWrapper, String>();

      if (parseClass(aClass, id2res, styleableId2res, res2id)) {
        myProjectResources.setCompiledResources(id2res, styleableId2res, res2id);
      }
    }
  }

  private static boolean parseClass(Class<?> rClass,
                                    TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                    Map<IntArrayWrapper, String> styleableId2Res,
                                    Map<ResourceType, TObjectIntHashMap<String>> res2id) throws ClassNotFoundException {
    try {
      final Class<?>[] nestedClasses;
      try {
        nestedClasses = rClass.getDeclaredClasses();
      }
      catch (LinkageError e) {
        final Throwable cause = e.getCause();

        if (cause instanceof ClassNotFoundException) {
          LOG.debug(e);
          throw (ClassNotFoundException)cause;
        }
        throw e;
      }
      for (Class<?> resClass : nestedClasses) {
        final ResourceType resType = ResourceType.getEnum(resClass.getSimpleName());

        if (resType != null) {
          final TObjectIntHashMap<String> resName2Id = new TObjectIntHashMap<String>();
          res2id.put(resType, resName2Id);

          for (Field field : resClass.getDeclaredFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
              final Class<?> type = field.getType();
              if (type.isArray() && type.getComponentType() == int.class) {
                styleableId2Res.put(new IntArrayWrapper((int[])field.get(null)), field.getName());
              }
              else if (type == int.class) {
                final Integer value = (Integer)field.get(null);
                id2res.put(value, Pair.of(resType, field.getName()));
                resName2Id.put(field.getName(), value);
              }
              else {
                LOG.error("Unknown field type in R class: " + type);
              }
            }
          }
        }
      }
    }
    catch (IllegalAccessException e) {
      LOG.info(e);
      return false;
    }

    return true;
  }

  // ---- Create diagnostic messages about class instantiation failures ----

  public void addDiagnostics(RenderLogger logger) {
    if (hasUnsupportedClassVersionProblem() || (myIncorrectRClassFormat && hasLoadedClasses())) {
      reportIncorrectClassFormatWarning(myRClassName, myIncorrectRClassFormat);
    }

    if (myMissingRClass && hasLoadedClasses()) {
      final StringBuilder builder = new StringBuilder();
      builder.append(myMissingRClassMessage != null && myMissingRClassMessage.length() > 0
                     ? ("Class not found error: " + myMissingRClassMessage + ".")
                     : "R class not found.")
        .append(" Try to build project");
      logger.addWarningMessage(new FixableIssueMessage(builder.toString()));
    }

    reportMissingClassesWarning(getMissingClasses());
    reportBrokenClassesWarning(getBrokenClasses());
  }

  private void reportMissingClassesWarning(@NotNull Set<String> missingClasses) {
    if (missingClasses.size() > 0) {
      final StringBuilder builder = new StringBuilder();
      builder.append("Missing classes:\n");

      for (String missingClass : missingClasses) {
        builder.append("&nbsp; &nbsp; &nbsp; &nbsp;").append(missingClass).append('\n');
      }
      builder.append("Try to build project");
      myLogger.addWarningMessage(new FixableIssueMessage(builder.toString()));
    }
  }

  private void reportBrokenClassesWarning(@NotNull Map<String, Throwable> brokenClasses) {
    if (brokenClasses.size() > 0) {
      final List<Throwable> throwables = new ArrayList<Throwable>();
      final List<String> brokenClassNames = new ArrayList<String>();

      for (Map.Entry<String, Throwable> entry : brokenClasses.entrySet()) {
        brokenClassNames.add(entry.getKey());
        throwables.add(entry.getValue());
      }
      final Throwable[] throwableArray = throwables.toArray(new Throwable[throwables.size()]);

      final StringBuilder builder = new StringBuilder();
      builder.append("Unable to initialize:\n");

      for (String className : brokenClassNames) {
        builder.append("&nbsp; &nbsp; &nbsp; &nbsp;").append(className).append('\n');
      }
      removeLastNewLineChar(builder);
      builder.append('\n');
      final FixableIssueMessage issue = new FixableIssueMessage(builder.toString(), Collections.singletonList(
        com.intellij.openapi.util.Pair.<String, Runnable>create("Details", new Runnable() {
          @Override
          public void run() {
            AndroidUtils.showStackStace(myModule.getProject(), throwableArray);
          }
        })));
      issue.addTip("Tip: Use View.isInEditMode() in your custom views to skip code when shown in the IDE");
      myLogger.addWarningMessage(issue);
    }
  }

  private void reportIncorrectClassFormatWarning(@Nullable String rClassName,
                                                 boolean incorrectRClassFormat) {
    final Project project = myModule.getProject();
    final List<Module> problemModules = getProblemModules(myModule);
    final StringBuilder builder = new StringBuilder("Preview might be incorrect: unsupported classes version");
    final List<com.intellij.openapi.util.Pair<String, Runnable>> quickFixes = new ArrayList<com.intellij.openapi.util.Pair<String, Runnable>>();

    if (problemModules.size() > 0) {
      quickFixes.add(new com.intellij.openapi.util.Pair<String, Runnable>("Rebuild project with '-target 1.6'", new Runnable() {
        @Override
        public void run() {
          final JpsJavaCompilerOptions settings = JavacConfiguration.getOptions(project, JavacConfiguration.class);
          if (settings.ADDITIONAL_OPTIONS_STRING.length() > 0) {
            settings.ADDITIONAL_OPTIONS_STRING += ' ';
          }
          settings.ADDITIONAL_OPTIONS_STRING += "-target 1.6";
          CompilerManager.getInstance(project).rebuild(null);
        }
      }));

      quickFixes.add(new com.intellij.openapi.util.Pair<String, Runnable>("Change Java SDK to 1.5/1.6", new Runnable() {
        @Override
        public void run() {
          final Set<String> sdkNames = getSdkNamesFromModules(problemModules);

          if (sdkNames.size() == 1) {
            final Sdk sdk = ProjectJdkTable.getInstance().findJdk(sdkNames.iterator().next());

            if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
              final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(project);

              if (ShowSettingsUtil.getInstance().editConfigurable(project, config, new Runnable() {
                public void run() {
                  config.select(sdk, true);
                }
              })) {
                askAndRebuild(project);
              }
              return;
            }
          }

          final String moduleToSelect = problemModules.size() > 0
                                        ? problemModules.iterator().next().getName()
                                        : null;
          if (ModulesConfigurator.showDialog(project, moduleToSelect, ClasspathEditor.NAME)) {
            askAndRebuild(project);
          }
        }
      }));

      final Set<String> classesWithIncorrectFormat = new HashSet<String>(
        getClassesWithIncorrectFormat());
      if (incorrectRClassFormat && rClassName != null) {
        classesWithIncorrectFormat.add(rClassName);
      }
      if (classesWithIncorrectFormat.size() > 0) {
        quickFixes.add(new com.intellij.openapi.util.Pair<String, Runnable>("Details", new Runnable() {
          @Override
          public void run() {
            showClassesWithIncorrectFormat(project, classesWithIncorrectFormat);
          }
        }));
      }

      builder.append("\nThe following modules are built with incompatible JDK: ");

      for (Iterator<Module> it = problemModules.iterator(); it.hasNext(); ) {
        Module problemModule = it.next();
        builder.append(problemModule.getName());
        if (it.hasNext()) {
          builder.append(", ");
        }
      }
    }

    myLogger.addErrorMessage(new FixableIssueMessage(builder.toString(), quickFixes));
  }

  private static void showClassesWithIncorrectFormat(@NotNull Project project, @NotNull Set<String> classesWithIncorrectFormat) {
    final StringBuilder builder = new StringBuilder("Classes with incompatible format:\n");

    for (Iterator<String> it = classesWithIncorrectFormat.iterator(); it.hasNext(); ) {
      builder.append("    ").append(it.next());

      if (it.hasNext()) {
        builder.append('\n');
      }
    }
    Messages.showInfoMessage(project, builder.toString(), "Unsupported class version");
  }

  private static void askAndRebuild(Project project) {
    final int r =
      Messages.showYesNoDialog(project, "You have to rebuild project to see fixed preview. Would you like to do it?",
                               "Rebuild project", Messages.getQuestionIcon());
    if (r == Messages.YES) {
      CompilerManager.getInstance(project).rebuild(null);
    }
  }

  @NotNull
  private static Set<String> getSdkNamesFromModules(@NotNull Collection<Module> modules) {
    final Set<String> result = new HashSet<String>();

    for (Module module : modules) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

      if (sdk != null) {
        result.add(sdk.getName());
      }
    }
    return result;
  }

  @NotNull
  private static List<Module> getProblemModules(@NotNull Module root) {
    final List<Module> result = new ArrayList<Module>();
    collectProblemModules(root, new HashSet<Module>(), result);
    return result;
  }

  private static void collectProblemModules(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Collection<Module> result) {
    if (!visited.add(module)) {
      return;
    }

    if (isBuiltByJdk7OrHigher(module)) {
      result.add(module);
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      collectProblemModules(depModule, visited, result);
    }
  }

  private static boolean isBuiltByJdk7OrHigher(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    if (sdk == null) {
      return false;
    }

    if (sdk.getSdkType() instanceof AndroidSdkType) {
      final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();

      if (data != null) {
        final Sdk jdk = data.getJavaSdk();

        if (jdk != null) {
          sdk = jdk;
        }
      }
    }
    return sdk.getSdkType() instanceof JavaSdk &&
           JavaSdk.getInstance().isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_7);
  }

  private static void removeLastNewLineChar(StringBuilder builder) {
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\n') {
      builder.deleteCharAt(builder.length() - 1);
    }
  }
}
