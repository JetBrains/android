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

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.rendering.InconvertibleClassError;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderProblem;
import com.android.util.Pair;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

/**
 * Handler for loading views for the layout editor on demand, and reporting issues with class
 * loading, instance creation, etc.
 * If the project is indexing, the ViewLoader will not be able to detect the modification times
 * in the project so it will not report outdated classes.
 */
@SuppressWarnings("deprecation") // The Pair class is required by the IProjectCallback
public class ViewLoader {
  private static final Logger LOG = Logger.getInstance(ViewLoader.class);
  /** Number of instances of a custom view that are allowed to nest inside itself. */
  private static final int ALLOWED_NESTED_VIEWS = 100;

  @NotNull private final Module myModule;
  @NotNull private final Map<String, Class<?>> myLoadedClasses = Maps.newHashMap();
  /** Classes that are being loaded currently. */
  @NotNull private final Multiset<Class<?>> myLoadingClasses = HashMultiset.create(5);
  /** Classes that have been modified after compilation. */
  @NotNull private final Set<String> myRecentlyModifiedClasses = Sets.newHashSetWithExpectedSize(5);
  @Nullable private final Object myCredential;
  @NotNull private RenderLogger myLogger;
  @NotNull private final LayoutLibrary myLayoutLibrary;
  @Nullable private ModuleClassLoader myModuleClassLoader;

  public ViewLoader(@NotNull LayoutLibrary layoutLib, @NotNull AndroidFacet facet, @NotNull RenderLogger logger,
                    @Nullable Object credential) {
    myLayoutLibrary = layoutLib;
    myModule = facet.getModule();
    myLogger = logger;
    myCredential = credential;
  }

  /**
   * Sets the {@link LayoutLog} logger to use for error messages during problems
   *
   * @param logger the new logger to use, or null to clear it out
   */
  public void setLogger(@Nullable RenderLogger logger) {
    myLogger = logger;
  }

  @Nullable
  public Object loadView(String className, Class<?>[] constructorSignature, Object[] constructorArgs)
    throws ClassNotFoundException {

    Object aClass = loadClass(className, constructorSignature, constructorArgs, true);
    if (aClass != null) {
      return aClass;
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

  /**
   * Like loadView, but doesn't log  exceptions if failed and doesn't try to create a mock view.
   */
  @Nullable
  public Object loadClass(String className, Class<?>[] constructorSignature, Object[] constructorArgs) throws ClassNotFoundException {
    // RecyclerView.Adapter is an abstract class, but its instance is needed for RecyclerView to work correctly. So, when LayoutLib asks for
    // its instance, we define a new class which extends the Adapter class.
    if (RecyclerViewHelper.CN_RV_ADAPTER.equals(className)) {
      className = RecyclerViewHelper.CN_CUSTOM_ADAPTER;
      constructorSignature = ArrayUtil.EMPTY_CLASS_ARRAY;
      constructorArgs = ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    return loadClass(className, constructorSignature, constructorArgs, false);
  }

  @Nullable
  private Object loadClass(String className, Class<?>[] constructorSignature, Object[] constructorArgs, boolean isView) {
    Class<?> aClass = myLoadedClasses.get(className);

    try {
      if (aClass != null) {
        checkModified(className);
        return createNewInstance(aClass, constructorSignature, constructorArgs, isView);
      }
      aClass = loadClass(className, isView);

      if (aClass != null) {
        checkModified(className);
        if (myLoadingClasses.count(aClass) > ALLOWED_NESTED_VIEWS) {
          throw new InstantiationException(
            "The layout involves creation of " + className + " over " + ALLOWED_NESTED_VIEWS + " levels deep. Infinite recursion?");
        }
        myLoadingClasses.add(aClass);
        try {
          final Object viewObject = createNewInstance(aClass, constructorSignature, constructorArgs, isView);
          myLoadedClasses.put(className, aClass);
          return viewObject;
        }
        finally {
          myLoadingClasses.remove(aClass);
        }
      }
    }
    catch (InconvertibleClassError e) {
      myLogger.addIncorrectFormatClass(e.getClassName(), e);
    }
    catch (LinkageError e) {
      myLogger.addBrokenClass(className, e);
    }
    catch (ClassNotFoundException e) {
      myLogger.addBrokenClass(className, e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof InconvertibleClassError) {
        InconvertibleClassError error = (InconvertibleClassError)cause;
        myLogger.addIncorrectFormatClass(error.getClassName(), error);
      }
      else {
        myLogger.addBrokenClass(className, cause);
      }
    }
    catch (IllegalAccessException e) {
      myLogger.addBrokenClass(className, e);
    }
    catch (InstantiationException e) {
      myLogger.addBrokenClass(className, e);
    }
    catch (NoSuchMethodException e) {
      myLogger.addBrokenClass(className, e);
    }
    return null;
  }

  /** Checks that the given class has not been edited since the last compilation (and if it has, logs a warning to the user) */
  private void checkModified(@NotNull String fqcn) {
    if (DumbService.getInstance(myModule.getProject()).isDumb()) {
      // If the index is not ready, we can not check the modified time since it requires accessing the PSI
      return;
    }

    if (myModuleClassLoader != null && myModuleClassLoader.isSourceModified(fqcn, myCredential) && !myRecentlyModifiedClasses.contains(fqcn)) {
      myRecentlyModifiedClasses.add(fqcn);
      RenderProblem.Html problem = RenderProblem.create(WARNING);
      HtmlBuilder builder = problem.getHtmlBuilder();
      String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
      builder.addLink("The " + className + " custom view has been edited more recently than the last build: ", "Build", " the project.",
                      myLogger.getLinkManager().createCompileModuleUrl());
      myLogger.addMessage(problem);
    }
  }

  @Nullable
  public Class<?> loadClass(@NotNull String className, boolean logError) throws InconvertibleClassError {
    try {
      return getModuleClassLoader().loadClass(className);
    }
    catch (ClassNotFoundException e) {
      if (logError && !className.equals(VIEW_FRAGMENT)) {
        myLogger.addMissingClass(className);
      }
      return null;
    }
  }

  @NotNull
  private ModuleClassLoader getModuleClassLoader() {
    if (myModuleClassLoader == null) {
      // Allow creating class loaders during rendering; may be prevented by the RenderSecurityManager
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        myModuleClassLoader = ModuleClassLoader.get(myLayoutLibrary, myModule);
      } finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }

    return myModuleClassLoader;
  }

  @Nullable
  private Object createViewFromSuperclass(final String className, final Class<?>[] constructorSignature, final Object[] constructorArgs) {
    // Creating views from the superclass calls into PSI which may need
    // I/O access (for example when it consults the Java class index
    // and that index needs to be lazily updated.)
    // We run most of the method as a safe region, but we exit the
    // safe region before calling {@link #createNewInstance} (which can
    // call user code), and enter it again upon return.
    final Ref<Boolean> token = new Ref<Boolean>();
    token.set(RenderSecurityManager.enterSafeRegion(myCredential));
    try {
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
                if (aClass == null && myLayoutLibrary.getClassLoader() != null) {
                  aClass = myLayoutLibrary.getClassLoader().loadClass(qName);
                  if (aClass != null) {
                    myLoadedClasses.put(qName, aClass);
                  }
                }
                if (aClass != null) {
                  try {
                    RenderSecurityManager.exitSafeRegion(token.get());
                    return createNewInstance(aClass, constructorSignature, constructorArgs, true);
                  } finally {
                    token.set(RenderSecurityManager.enterSafeRegion(myCredential));
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
    } finally {
      RenderSecurityManager.exitSafeRegion(token.get());
    }
  }

  private Object createMockView(String className, Class<?>[] constructorSignature, Object[] constructorArgs)
    throws
    ClassNotFoundException,
    InvocationTargetException,
    NoSuchMethodException,
    InstantiationException,
    IllegalAccessException,
    NoSuchFieldException {

    final Class<?> mockViewClass = getModuleClassLoader().loadClass(CLASS_MOCK_VIEW);
    final Object viewObject = createNewInstance(mockViewClass, constructorSignature, constructorArgs, true);

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
  private Object createNewInstance(Class<?> clazz, Class<?>[] constructorSignature, Object[] constructorParameters, boolean isView)
    throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException, InstantiationException {
    Constructor<?> constructor = null;

    try {
      constructor = clazz.getConstructor(constructorSignature);
    }
    catch (NoSuchMethodException e) {
      if (!isView) {
        throw e;
      }

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
            sig[j - 1] = clazz.getClassLoader().loadClass(CLASS_ATTRIBUTE_SET);
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

  @Nullable
  private static String getRClassName(@NotNull final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          return null;
        }

        final Manifest manifest = facet.getManifest();
        if (manifest == null) {
          return null;
        }

        final String packageName = manifest.getPackage().getValue();
        return packageName == null ? null : packageName + '.' + R_CLASS;
      }
    });
  }

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly
   */
  public void loadAndParseRClassSilently() {
    final String rClassName = getRClassName(myModule);
    try {
      if (rClassName == null) {
        LOG.info(String.format("loadAndParseRClass: failed to find manifest package for project %1$s", myModule.getProject().getName()));
        return;
      }
      myLogger.setResourceClass(rClassName);
      loadAndParseRClass(rClassName);
    }
    catch (ClassNotFoundException e) {
      myLogger.setMissingResourceClass(true);
    }
    catch (NoClassDefFoundError e) {
      // ClassNotFoundException is thrown when no R class was found. But if the R class was found, but not the inner classes (like R$id or
      // R$styleable), NoClassDefFoundError is thrown. This is likely because R class was generated by AarResourceClassGenerator but the
      // inner classes weren't needed and hence not generated.
      myLogger.setMissingResourceClass(true);
    }
    catch (InconvertibleClassError e) {
      assert rClassName != null;
      myLogger.addIncorrectFormatClass(rClassName, e);
    }
  }

  private void loadAndParseRClass(@NotNull String className) throws ClassNotFoundException, InconvertibleClassError {
    Class<?> aClass = myLoadedClasses.get(className);
    if (aClass == null) {
      final ModuleClassLoader moduleClassLoader = getModuleClassLoader();
      final boolean isClassLoaded = moduleClassLoader.isClassLoaded(className);
      aClass = moduleClassLoader.loadClass(className);

      if (!isClassLoaded && aClass != null) {
        // This is the first time we've found the resources. The dynamic R classes generated for aar libraries are now stale and must be
        // regenerated. Clear the ModuleClassLoader and reload the R class.
        myLoadedClasses.clear();
        ModuleClassLoader.clearCache(myModule);
        myModuleClassLoader = null;
        aClass = getModuleClassLoader().loadClass(className);
      }
      if (aClass != null) {
        myLoadedClasses.put(className, aClass);
        myLogger.setHasLoadedClasses(true);
      }
    }

    if (aClass != null) {
      final Map<ResourceType, TObjectIntHashMap<String>> res2id =
        new EnumMap<ResourceType, TObjectIntHashMap<String>>(ResourceType.class);
      final TIntObjectHashMap<Pair<ResourceType, String>> id2res = new TIntObjectHashMap<Pair<ResourceType, String>>();
      final Map<IntArrayWrapper, String> styleableId2res = new HashMap<IntArrayWrapper, String>();

      if (parseClass(aClass, id2res, styleableId2res, res2id)) {
        AppResourceRepository appResources = AppResourceRepository.getAppResources(myModule, true);
        if (appResources != null) {
          appResources.setCompiledResources(id2res, styleableId2res, res2id);
        }
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
            if (Modifier.isStatic(modifiers)) { // May not be final in library projects
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
}
