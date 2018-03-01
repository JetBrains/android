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

import android.view.Gravity;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.layoutlib.bridge.MockView;
import com.android.resources.ResourceType;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.IRenderLogger;
import com.android.tools.idea.rendering.InconvertibleClassError;
import com.android.tools.idea.rendering.RenderProblem;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.utils.HtmlBuilder;
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
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.LogAnonymizerUtil.anonymize;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
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
  @Nullable private ModuleClassLoader myModuleClassLoader;

  public ViewLoader(@NotNull LayoutLibrary layoutLib, @NotNull AndroidFacet facet, @NotNull IRenderLogger logger,
                    @Nullable Object credential) {
    myLayoutLibrary = layoutLib;
    myModule = facet.getModule();
    myLogger = logger;
    myCredential = credential;
  }

  /**
   * Sets the {@link LayoutLog} logger to use for error messages during problems.
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

      final Manifest manifest = facet.getManifest();
      if (manifest == null) {
        return null;
      }

      final String packageName = manifest.getPackage().getValue();
      return packageName == null ? null : packageName + '.' + R_CLASS;
    });
  }

  private static boolean parseClass(@NotNull Class<?> rClass,
                                    @NotNull ResourceNamespace namespace,
                                    @NotNull TIntObjectHashMap<ResourceReference> id2res,
                                    @NotNull TObjectIntHashMap<ResourceReference> res2id) throws ClassNotFoundException {
    try {
      final Class<?>[] nestedClasses;
      try {
        nestedClasses = rClass.getDeclaredClasses();
      }
      catch (LinkageError e) {
        final Throwable cause = e.getCause();

        LOG.debug(e);
        if (cause instanceof ClassNotFoundException) {
          throw (ClassNotFoundException)cause;
        }
        throw e;
      }
      for (Class<?> resClass : nestedClasses) {
        final ResourceType resType = ResourceType.getEnum(resClass.getSimpleName());
        if (resType == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("  '%s' is not a valid resource type", anonymizeClassName(resClass.getSimpleName())));
          }
          continue;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  Defining resource type '%s'", anonymizeClassName(resClass.getSimpleName())));
        }

        for (Field field : resClass.getDeclaredFields()) {
          if (!Modifier.isStatic(field.getModifiers())) { // May not be final in library projects
            if (LOG.isDebugEnabled()) {
              LOG.debug(String.format("  '%s' field is not static, skipping", field.getName()));
            }

            continue;
          }

          final Class<?> type = field.getType();
          if (type == int.class) {
            final Integer value = (Integer)field.get(null);
            ResourceReference reference = new ResourceReference(namespace, resType, field.getName());
            id2res.put(value, reference);
            res2id.put(reference, value);
            if (LOG.isDebugEnabled()) {
              LOG.debug(String.format("  '%s' defined as int", field.getName()));
            }
          }
          else if (type != int[].class) {
            // styleables are represented as arrays in the R class.
            LOG.error("Unknown field type in R class: " + type);
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
      return createMockView(className, constructorSignature, constructorArgs);
    }
    catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | InstantiationException | NoSuchFieldException | NoSuchMethodException e) {
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

  @NotNull
  private ViewLoaderExtension[] getExtensions() {
    ExtensionsArea area = Extensions.getArea(myModule.getProject());
    if (!area.hasExtensionPoint(ViewLoaderExtension.EP_NAME.getName())) {
      return EMPTY_EXTENSION_LIST;
    }
    return area.getExtensionPoint(ViewLoaderExtension.EP_NAME).getExtensions();
  }

  @NotNull
  private ModuleClassLoader getModuleClassLoader() {
    if (myModuleClassLoader == null) {
      // Allow creating class loaders during rendering; may be prevented by the RenderSecurityManager
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        myModuleClassLoader = ModuleClassLoader.get(myLayoutLibrary, myModule);
      }
      finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }

    return myModuleClassLoader;
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
  private MockView createMockView(@NotNull String className, @Nullable Class<?>[] constructorSignature, @Nullable Object[] constructorArgs)
      throws
          ClassNotFoundException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException,
          NoSuchFieldException {
    MockView mockView = (MockView)createNewInstance(MockView.class, constructorSignature, constructorArgs, true);
    String label = getShortClassName(className);
    switch (label) {
      case VIEW_FRAGMENT:
        label = "<fragment>";
        // TODO:
        // Append "\nPick preview layout from the \"Fragment Layout\" context menu"
        // when used from the layout editor
        break;
      case VIEW_INCLUDE:
        label = "Text";
        break;
    }

    mockView.setText(label);
    mockView.setGravity(Gravity.CENTER);

    return mockView;
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
      ModuleClassLoader moduleClassLoader = getModuleClassLoader();

      for (ViewLoaderExtension extension : getExtensions()) {
        Class<?> loadedClass = extension.loadClass(className, moduleClassLoader);
        if (loadedClass != null) {
          return loadedClass;
        }
      }

      return moduleClassLoader.loadClass(className);
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

      final ModuleClassLoader moduleClassLoader = getModuleClassLoader();
      final boolean isClassLoaded = moduleClassLoader.isClassLoaded(className);
      aClass = moduleClassLoader.loadClass(className);

      if (!isClassLoaded && aClass != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  Class found in module %s, first time load.", anonymize(myModule)));
        }

        // This is the first time we've found the resources. The dynamic R classes generated for aar libraries are now stale and must be
        // regenerated. Clear the ModuleClassLoader and reload the R class.
        myLoadedClasses.clear();
        ModuleClassLoader.clearCache(myModule);
        myModuleClassLoader = null;
        aClass = getModuleClassLoader().loadClass(className);
        idManager.resetDynamicIds();
      }
      else {
        if (LOG.isDebugEnabled()) {
          if (isClassLoaded) {
            LOG.debug(String.format("  Class already loaded in module %s.", anonymize(myModule)));
          }
        }
      }
      if (aClass != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("  Class loaded");
        }

        myLoadedClasses.put(className, aClass);
        myLogger.setHasLoadedClasses();
      }
    }

    if (aClass != null) {
      final TObjectIntHashMap<ResourceReference> res2id = new TObjectIntHashMap<>();
      final TIntObjectHashMap<ResourceReference> id2res = new TIntObjectHashMap<>();

      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null) {
        if (parseClass(aClass, ResourceRepositoryManager.getOrCreateInstance(facet).getNamespace(), id2res, res2id)) {
          idManager.setCompiledIds(res2id, id2res);
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("END loadAndParseRClass(%s)", anonymizeClassName(className)));
    }
  }
}
