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
import static com.android.AndroidXConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.tools.idea.LogAnonymizerUtil.anonymize;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.resources.AndroidManifestPackageNameUtils;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.IRenderLogger;
import com.android.tools.idea.rendering.RenderModelModule;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.android.tools.idea.res.ResourceIdManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.intellij.facet.Facet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
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

  @NotNull private final RenderModelModule myModule;
  @NotNull private final Map<String, Class<?>> myLoadedClasses = Maps.newHashMap();
  /** Classes that are being loaded currently. */
  @NotNull private final Multiset<Class<?>> myLoadingClasses = HashMultiset.create(5);
  /** Classes that have been modified after compilation. */
  @Nullable private final Object myCredential;
  @NotNull private final LayoutLibrary myLayoutLibrary;
  /** {@link IRenderLogger} used to log loading problems. */
  @NotNull private IRenderLogger myLogger;
  @NotNull private final ClassLoader myClassLoader;

  public ViewLoader(@NotNull LayoutLibrary layoutLib, @NotNull RenderModelModule module, @NotNull IRenderLogger logger,
                    @Nullable Object credential,
                    @NotNull ClassLoader classLoader) {
    myLayoutLibrary = layoutLib;
    myModule = module;
    myLogger = logger;
    myCredential = credential;
    myClassLoader = classLoader;
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
    String packageName = ProjectSystemUtil.getModuleSystem(module).getPackageName();
    return packageName == null ? null : packageName + '.' + R_CLASS;
  }

  @Nullable
  private static String getPackageName(ExternalAndroidLibrary library) {
    String packageName = library.getPackageName();
    if (packageName == null) {
      // Try the manifest if the package name is not directly set.
      PathString manifest = library.getManifestFile();
      if (manifest != null) {
        try {
          packageName = AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifest);
        }
        catch (IOException e) {
          LOG.info(String.format("getPackageName: failed to find packageName for library %1$s", library.libraryName()));
        }
      }
      if (packageName == null) {
        return null;
      }
    }
    return packageName + '.' + R_CLASS;
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
    Class<?> aClass = myLoadedClasses.get(className);

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadClassA(%s)", anonymizeClassName(className)));
    }

    try {
      if (aClass != null) {
        return createNewInstance(aClass, constructorSignature, constructorArgs, isView);
      }
      aClass = loadClass(className, isView);

      if (aClass != null) {
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
      myLogger.addBrokenClass(className, cause);
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
  public Class<?> loadClass(@NotNull String className, boolean logError) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadClassB(%s)", anonymizeClassName(className)));
    }

    try {
      return myClassLoader.loadClass(className);
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
      return DumbService.getInstance(myModule.getProject()).runReadActionInSmartMode(() -> {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myModule.getProject());
        PsiClass psiClass = facade.findClass(className, myModule.getIdeaModule().getModuleWithDependenciesAndLibrariesScope(false));

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

  private static Stream<String> getRClassesNames(@NotNull Module module) {
    return Stream.concat(
      Stream.concat(
          Stream.of(module),
          // Get all project (not external libraries) dependencies
          AndroidDependenciesCache.getAllAndroidDependencies(module, false).stream().map(Facet::getModule)
        )
        .map(ViewLoader::getRClassName),
      // Get all external (libraries) dependencies
      ProjectSystemUtil.getModuleSystem(module).getAndroidLibraryDependencies(DependencyScopeType.MAIN).stream()
        .map(ViewLoader::getPackageName)
    );
  }

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly. Only needed if views are compiled against an R class with
   * final fields.
   *
   * @see ResourceIdManager#getFinalIdsUsed()
   */
  public void loadAndParseRClassSilently() {
    // All the ids are loaded into the idManager for the "app module".
    ResourceIdManager idManager = myModule.getResourceIdManager();
    idManager.resetCompiledIds(this::loadRClasses);
  }

  private void loadRClasses(@NotNull ResourceIdManager.RClassParser rClassParser) {
    getRClassesNames(myModule.getIdeaModule()).forEach((rClassName) -> {
      try {
        if (rClassName == null) {
          LOG.info(
            String.format("loadAndParseRClass: failed to find manifest package for project %1$s", myModule.getProject().getName()));
          return;
        }
        myLogger.setResourceClass(rClassName);
        loadAndParseRClass(rClassName, rClassParser);
      }
      catch (ClassNotFoundException | NoClassDefFoundError e) {
        myLogger.setMissingResourceClass();
      }
    });
  }

  @VisibleForTesting
  void loadAndParseRClass(@NotNull String className, @NotNull ResourceIdManager.RClassParser rClassParser) throws ClassNotFoundException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("loadAndParseRClass(%s)", anonymizeClassName(className)));
    }

    Class<?> aClass = myLoadedClasses.get(className);

    if (aClass == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("  The R class is not loaded.");
      }

      final boolean isClassLoaded = hasLoadedClass(className);
      aClass = myClassLoader.loadClass(className);

      if (!isClassLoaded) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  Class found in module %s, first time load.", anonymize(myModule.getIdeaModule())));
        }
      }
      else {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  Class already loaded in module %s.", anonymize(myModule.getIdeaModule())));
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("  Class loaded");
      }

      myLoadedClasses.put(className, aClass);
      myLogger.setHasLoadedClasses();
    }

    rClassParser.parse(aClass);

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("END loadAndParseRClass(%s)", anonymizeClassName(className)));
    }
  }

  /**
   * Returns true if this ViewLoaded has loaded the given class.
   */
  public boolean hasLoadedClass(@NotNull String classFqn) {
    return myLoadedClasses.containsKey(classFqn);
  }
}
