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

import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.CLASS_ATTRIBUTE_SET;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.tools.idea.LogAnonymizerUtil.anonymize;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.IRenderLogger;
import com.android.tools.idea.rendering.RenderProblem;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.rendering.classloading.InconvertibleClassError;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.utils.HtmlBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for loading views for the layout editor on demand, and reporting issues with class
 * loading, instance creation, etc.
 * If the project is indexing, the ViewLoader will not be able to detect the modification times
 * in the project so it will not report outdated classes.
 */
public class ViewLoader {
  private static final Logger LOG = Logger.getInstance(ViewLoader.class);
  /** Number of instances of a custom view that are allowed to nest inside itself. */
  private static final int ALLOWED_NESTED_VIEWS = 100;

  private static final ViewLoaderExtension[] EMPTY_EXTENSION_LIST = new ViewLoaderExtension[0];

  @NotNull private final Module myModule;
  @NotNull private final Map<String, Class<?>> myLoadedClasses = Maps.newHashMap();
  /** Classes that are being loaded currently. */
  @NotNull private final Multiset<Class<?>> myLoadingClasses = HashMultiset.create(5);
  /** Classes that have been modified after compilation. */
  @NotNull private final Set<String> myRecentlyModifiedClasses = Sets.newHashSetWithExpectedSize(5);
  @Nullable private final Object myCredential;
  @NotNull private final LayoutLibrary myLayoutLibrary;
  /** {@link IRenderLogger} used to log loading problems. */
  @NotNull private IRenderLogger myLogger;
  @NotNull private final ModuleClassLoader myModuleClassLoader;

  public ViewLoader(@NotNull LayoutLibrary layoutLib, @NotNull AndroidFacet facet, @NotNull IRenderLogger logger,
                    @Nullable Object credential,
                    @NotNull ModuleClassLoader classLoader) {
    myLayoutLibrary = layoutLib;
    myModule = facet.getModule();
    myLogger = logger;
    myCredential = credential;
    myModuleClassLoader = classLoader;
  }

  /**
   * Sets the {@link ILayoutLog} logger to use for error messages during problems.
   *
   * @param logger the new logger to use
   */
  public void setLogger(@NotNull IRenderLogger logger) {
    myLogger = logger;
  }

  @Nullable
  private static String getRClassName(@NotNull final Module module) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }

      final Manifest manifest = Manifest.getMainManifest(facet);
      if (manifest == null) {
        return null;
      }

      final String packageName = manifest.getPackage().getValue();
      return packageName == null ? null : packageName + '.' + R_CLASS;
    });
  }

  /**
   * Like loadView, but doesn't log  exceptions if failed and doesn't try to create a mock view.
   */
  @Nullable
  public Object loadClass(String className, Class<?>[] constructorSignature, Object[] constructorArgs) {
    // RecyclerView.Adapter is an abstract class, but its instance is needed for RecyclerView to work correctly. So, when LayoutLib asks for
    // its instance, we define a new class which extends the Adapter class.
    // We check whether the class being loaded is the support or the androidx one and use the appropiate adapter that references to the
    // right namespace.
    if (CLASS_RECYCLER_VIEW_ADAPTER.newName().equals(className)) {
      className = RecyclerViewHelper.CN_ANDROIDX_CUSTOM_ADAPTER;
      constructorSignature = ArrayUtil.EMPTY_CLASS_ARRAY;
      constructorArgs = ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    else if (CLASS_RECYCLER_VIEW_ADAPTER.oldName().equals(className)) {
      className = RecyclerViewHelper.CN_SUPPORT_CUSTOM_ADAPTER;
      constructorSignature = ArrayUtil.EMPTY_CLASS_ARRAY;
      constructorArgs = ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    return loadClass(className, constructorSignature, constructorArgs, false);
  }

  @Nullable
  public Object loadView(@NotNull String className, @Nullable Class<?>[] constructorSignature, @Nullable Object[] constructorArgs)
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
      return myLayoutLibrary.createMockView(getShortClassName(className), constructorSignature, constructorArgs);
    }
    catch (InvocationTargetException | IllegalAccessException | InstantiationException | NoSuchMethodException e) {
      throw new ClassNotFoundException(className, e);
    }
  }

  @Nullable
  private Object loadClass(@NotNull String className, @Nullable Class<?>[] constructorSignature, @Nullable Object[] constructorArgs, boolean isView) {
    assert myLogger != null;
    Class<?> aClass = myLoadedClasses.get(className);

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadClassA(%s)", anonymizeClassName(className)));
    }

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
          if (LOG.isDebugEnabled()) {
            LOG.debug("  instance created");
          }
          return viewObject;
        }
        finally {
          myLoadingClasses.remove(aClass);
        }
      }
    }
    catch (InconvertibleClassError e) {
      myLogger.addIncorrectFormatClass(e.getClassName(), e);
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    catch (LinkageError | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InstantiationException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      myLogger.addBrokenClass(className, e);
    }
    catch (InvocationTargetException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      final Throwable cause = e.getCause();
      if (cause instanceof InconvertibleClassError) {
        InconvertibleClassError error = (InconvertibleClassError)cause;
        myLogger.addIncorrectFormatClass(error.getClassName(), error);
      }
      else {
        myLogger.addBrokenClass(className, cause);
      }
    }
    return null;
  }

  /**
   * Checks if a class with this name was loaded by this loader.
   * @param name binary name of a class, see {@link ClassLoader}
   * @return true if a class with this name was loaded, false otherwise
   */
  public boolean isClassLoaded(@NonNull String name) {
    return myLoadedClasses.containsKey(name);
  }

  @NotNull
  private ViewLoaderExtension[] getExtensions() {
    ExtensionsArea area = Extensions.getArea(myModule.getProject());
    if (!area.hasExtensionPoint(ViewLoaderExtension.EP_NAME.getName())) {
      return EMPTY_EXTENSION_LIST;
    }
    return area.getExtensionPoint(ViewLoaderExtension.EP_NAME).getExtensions();
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
                      myLogger.getLinkManager().createBuildProjectUrl());
      myLogger.addMessage(problem);
    }
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @VisibleForTesting
  @NotNull
  static String getShortClassName(@NotNull String fqcn) {
    int first = fqcn.indexOf('.');
    int last = fqcn.lastIndexOf('.');
    if (fqcn.startsWith(ANDROID_PKG_PREFIX)) {
      // android.foo.Name -> android...Name
      if (last > first) {
        return fqcn.substring(0, first) + ".." + fqcn.substring(last);
      }
    }
    else {
      // com.example.p1.p2.MyClass -> com.example...MyClass
      first = fqcn.indexOf('.', first + 1);
      if (last > first && first >= 0) {
        return fqcn.substring(0, first) + ".." + fqcn.substring(last);
      }
    }

    return fqcn;
  }

  @NotNull
  private Object createNewInstance(@NotNull Class<?> clazz,
                                   @Nullable Class<?>[] constructorSignature,
                                   @Nullable Object[] constructorParameters,
                                   boolean isView)
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

      final int paramsCount = constructorSignature != null ? constructorSignature.length : 0;
      if (paramsCount == 0) {
        throw e;
      }
      assert constructorParameters != null;

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
            sig[j - 1] = myLayoutLibrary.getClassLoader().loadClass(CLASS_ATTRIBUTE_SET);
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
                                clazz.getSimpleName()), null, null);
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
  public Class<?> loadClass(@NotNull String className, boolean logError) throws InconvertibleClassError {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadClassB(%s)", anonymizeClassName(className)));
    }

    try {
      for (ViewLoaderExtension extension : getExtensions()) {
        Class<?> loadedClass = extension.loadClass(className, myModuleClassLoader);
        if (loadedClass != null) {
          return loadedClass;
        }
      }

      return myModuleClassLoader.loadClass(className);
    }
    catch (ClassNotFoundException e) {
      if (logError && !className.equals(VIEW_FRAGMENT)) {
        myLogger.addMissingClass(className);
      }
      return null;
    }
  }

  @Nullable
  private Object createViewFromSuperclass(@NotNull final String className,
                                          @Nullable final Class<?>[] constructorSignature,
                                          @Nullable final Object[] constructorArgs) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("createViewFromSuperClass(%s)", anonymizeClassName(className)));
    }

    // Creating views from the superclass calls into PSI which may need
    // I/O access (for example when it consults the Java class index
    // and that index needs to be lazily updated.)
    // We run most of the method as a safe region, but we exit the
    // safe region before calling {@link #createNewInstance} (which can
    // call user code), and enter it again upon return.
    final Ref<Boolean> token = new Ref<>();
    token.set(RenderSecurityManager.enterSafeRegion(myCredential));
    try {
      return ApplicationManager.getApplication().runReadAction((Computable<Object>)() -> {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myModule.getProject());
        PsiClass psiClass = facade.findClass(className, myModule.getModuleWithDependenciesAndLibrariesScope(false));

        if (psiClass == null) {
          return null;
        }
        psiClass = psiClass.getSuperClass();
        final Set<String> visited = new HashSet<>();

        while (psiClass != null) {
          final String qName = psiClass.getQualifiedName();
          if (LOG.isDebugEnabled()) {
            LOG.debug("  parent " + anonymizeClassName(qName));
          }

          if (qName == null ||
              !visited.add(qName) ||
              AndroidUtils.VIEW_CLASS_NAME.equals(psiClass.getQualifiedName())) {
            break;
          }

          if (!AndroidUtils.isAbstract(psiClass)) {
            try {
              Class<?> aClass = myLoadedClasses.get(qName);
              if (aClass == null) {
                aClass = myLayoutLibrary.getClassLoader().loadClass(qName);
                if (aClass != null) {
                  myLoadedClasses.put(qName, aClass);
                }
              }
              if (aClass != null) {
                try {
                  RenderSecurityManager.exitSafeRegion(token.get());
                  return createNewInstance(aClass, constructorSignature, constructorArgs, true);
                }
                finally {
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
      });
    }
    finally {
      RenderSecurityManager.exitSafeRegion(token.get());
    }
  }

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly. Only needed if views are compiled against an R class with
   * final fields.
   *
   * @see ResourceIdManager#getFinalIdsUsed()
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
    catch (ClassNotFoundException | NoClassDefFoundError e) {
      myLogger.setMissingResourceClass();
    }
    catch (InconvertibleClassError e) {
      assert rClassName != null;
      myLogger.addIncorrectFormatClass(rClassName, e);
    }
  }

  @VisibleForTesting
  void loadAndParseRClass(@NotNull String className) throws ClassNotFoundException, InconvertibleClassError {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadAndParseRClass(%s)", anonymizeClassName(className)));
    }

    Class<?> aClass = myLoadedClasses.get(className);
    ResourceIdManager idManager = ResourceIdManager.get(myModule);

    if (aClass == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("  The R class is not loaded.");
      }

      final boolean isClassLoaded = myModuleClassLoader.isClassLoaded(className);
      aClass = myModuleClassLoader.loadClass(className);

      if (!isClassLoaded) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  Class found in module %s, first time load.", anonymize(myModule)));
        }

        idManager.resetDynamicIds();
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  Class already loaded in module %s.", anonymize(myModule)));
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("  Class loaded");
      }

      myLoadedClasses.put(className, aClass);
      myLogger.setHasLoadedClasses();
    }

    idManager.loadCompiledIds(aClass);

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("END loadAndParseRClass(%s)", anonymizeClassName(className)));
    }
  }

  /**
   * Returns true if this ViewLoaded has loaded the given class.
   */
  public boolean hasLoadedClass(@NotNull String classFqn) {
    return myModuleClassLoader.isClassLoaded(classFqn);
  }
}
