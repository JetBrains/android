package org.jetbrains.android.uipreview;

import static com.android.tools.idea.flags.StudioFlags.NELE_CLASS_BINARY_CACHE;
import static com.android.tools.idea.rendering.classloading.ClassConverter.getCurrentClassVersion;
import static com.android.tools.idea.rendering.classloading.ReflectionUtilKt.findMethodLike;
import static com.android.tools.idea.rendering.classloading.UtilKt.toClassTransform;
import static org.jetbrains.android.uipreview.ModuleClassLoaderUtil.INTERNAL_PACKAGE;

import com.android.layoutlib.reflection.TrackingThreadLocal;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.classloading.ClassTransform;
import com.android.tools.idea.rendering.classloading.CooperativeInterruptTransform;
import com.android.tools.idea.rendering.classloading.FilteringClassLoader;
import com.android.tools.idea.rendering.classloading.FirewalledResourcesClassLoader;
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform;
import com.android.tools.idea.rendering.classloading.RepackageTransform;
import com.android.tools.idea.rendering.classloading.RequestExecutorTransform;
import com.android.tools.idea.rendering.classloading.ResourcesCompatTransform;
import com.android.tools.idea.rendering.classloading.ThreadControllingTransform;
import com.android.tools.idea.rendering.classloading.ThreadLocalTrackingTransform;
import com.android.tools.idea.rendering.classloading.VersionClassTransform;
import com.android.tools.idea.rendering.classloading.ViewMethodWrapperTransform;
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader;
import com.android.tools.idea.rendering.classloading.loaders.ProjectSystemClassLoader;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.WeakReferenceDisposableWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jetbrains.android.uipreview.classloading.LibraryResourceClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Render class loader responsible for loading classes in custom views and local and library classes
 * used by those custom views (other than the framework itself, which is loaded by a parent class
 * loader via layout library.)
 */
public final class ModuleClassLoader extends DelegatingClassLoader implements ModuleProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(ModuleClassLoader.class);

  /**
   * If the project uses any of these libraries, we re-package them with a different name (prefixing them with
   * {@link ModuleClassLoaderUtil#INTERNAL_PACKAGE} so they do not conflict with the versions loaded in Studio.
   */
  private static final ImmutableList<String> PACKAGES_TO_RENAME = ImmutableList.of(
    "kotlin.",
    "kotlinx.",
    "android.support.constraint.solver."
  );

  /**
   * Classes are rewritten by applying the following transformations:
   * <ul>
   *   <li>Updates the class file version with a version runnable in the current JDK
   *   <li>Replaces onDraw, onMeasure and onLayout for custom views
   *   <li>Replaces ThreadLocal class with TrackingThreadLocal
   *   <li>Redirects calls to PreviewAnimationClock's notifySubscribe and notifyUnsubscribe to ComposePreviewAnimationManager
   *   <li>Repackages certain classes to avoid loading the Studio versions from the Studio class loader
   * </ul>
   * Note that it does not attempt to handle cases where class file constructs cannot
   * be represented in the target version. This is intended for uses such as for example
   * the Android R class, which is simple and can be converted to pretty much any class file
   * version, which makes it possible to load it in an IDE layout render execution context
   * even if it has been compiled for a later version.
   * <p/>
   * For custom views (classes that inherit from android.view.View or any widget in android.widget.*)
   * the onDraw, onMeasure and onLayout methods are replaced with methods that capture any exceptions thrown.
   * This way we avoid custom views breaking the rendering.
   */
  static final ClassTransform PROJECT_DEFAULT_TRANSFORMS = toClassTransform(
    ViewMethodWrapperTransform::new,
    visitor -> new VersionClassTransform(visitor, getCurrentClassVersion(), 0),
    ThreadLocalTrackingTransform::new,
    ThreadControllingTransform::new,
    CooperativeInterruptTransform::new,
    // Leave this transformation as last so the rest of the transformations operate on the regular names.
    visitor -> new RepackageTransform(visitor, PACKAGES_TO_RENAME, INTERNAL_PACKAGE)
  );

  static final ClassTransform NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS = toClassTransform(
    ViewMethodWrapperTransform::new,
    visitor -> new VersionClassTransform(visitor, getCurrentClassVersion(), 0),
    ThreadLocalTrackingTransform::new,
    ThreadControllingTransform::new,
    PreviewAnimationClockMethodTransform::new,
    ResourcesCompatTransform::new,
    RequestExecutorTransform::new,
    // Leave this transformation as last so the rest of the transformations operate on the regular names.
    visitor -> new RepackageTransform(visitor, PACKAGES_TO_RENAME, INTERNAL_PACKAGE)
  );

  private static final ExecutorService ourDisposeService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("ModuleClassLoader Dispose Thread", 1);

  /**
   * The base module to use as a render context; the class loader will consult the module dependencies and library dependencies
   * of this class as well to find classes
   */
  private final WeakReference<Module> myModuleReference;

  /**
   * Interface for reporting load times and general diagnostics.
   */
  @NotNull
  private final ModuleClassLoaderDiagnosticsWrite myDiagnostics;

  /**
   * Holds the provider that allows finding the {@link PsiFile}
   */
  private final Supplier<PsiFile> myPsiFileProvider;
  private final ModuleClassLoaderImpl myImpl;
  /**
   * Saves the given {@link ClassLoader} passed as parent to this {@link ModuleClassLoader}. This allows to check
   * the parent compatibility in {@link #isCompatibleParentClassLoader(ClassLoader).}
   */
  private final ClassLoader myParentAtConstruction;

  private final AtomicBoolean isDisposed = new AtomicBoolean(false);

  ModuleClassLoader(@Nullable ClassLoader parent, @NotNull ModuleRenderContext renderContext,
                    @NotNull ClassTransform projectTransformations,
                    @NotNull ClassTransform nonProjectTransformations,
                    @NotNull ModuleClassLoaderDiagnosticsWrite diagnostics) {
    this(parent, renderContext, projectTransformations, nonProjectTransformations,
         NELE_CLASS_BINARY_CACHE.get()
         ? ClassBinaryCacheManager.getInstance().getCache(renderContext.getModule())
         : ClassBinaryCache.NO_CACHE,
         diagnostics);
  }

  private ModuleClassLoader(@Nullable ClassLoader parent,
                            @NotNull ModuleRenderContext renderContext,
                            @NotNull ModuleClassLoaderImpl loader,
                            @NotNull ModuleClassLoaderDiagnosticsWrite diagnostics) {
    super(
      new LibraryResourceClassLoader(
        new FirewalledResourcesClassLoader(
          // Do not allow to load kotlin standard library from the plugin class loader since it can lead to
          // a version mismatch.
          FilteringClassLoader.disallowedPrefixes(parent, PACKAGES_TO_RENAME)),
        renderContext.getModule()), loader);

    myParentAtConstruction = parent;
    myImpl = loader;
    myModuleReference = new WeakReference<>(renderContext.getModule());
    Disposer.register(renderContext.getModule(), new WeakReferenceDisposableWrapper(this));
    // Extracting the provider into a variable to avoid the lambda capturing a reference to renderContext
    myPsiFileProvider = renderContext.getFileProvider();
    myDiagnostics = diagnostics;
  }

  @NotNull
  private static ProjectSystemClassLoader createDefaultProjectSystemClassLoader(
    @NotNull Module theModule, @NotNull Supplier<PsiFile> psiFileProvider
  ) {
    WeakReference<Module> moduleRef = new WeakReference<>(theModule);
    return new ProjectSystemClassLoader((fqcn) -> {
      Module module = moduleRef.get();
      if (module == null || module.isDisposed()) return null;

      PsiFile psiFile = psiFileProvider.get();
      VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;

      return ProjectSystemUtil.getModuleSystem(module)
        .getClassFileFinderForSourceFile(virtualFile)
        .findClassFile(fqcn);
    });
  }

  private ModuleClassLoader(@Nullable ClassLoader parent, @NotNull ModuleRenderContext renderContext,
                            @NotNull ClassTransform projectTransformations,
                            @NotNull ClassTransform nonProjectTransformations,
                            @NotNull ClassBinaryCache cache,
                            @NotNull ModuleClassLoaderDiagnosticsWrite diagnostics) {
    this(
      parent,
      renderContext,
      new ModuleClassLoaderImpl(
        renderContext.getModule(),
        createDefaultProjectSystemClassLoader(renderContext.getModule(), renderContext.getFileProvider()),
        parent,
        projectTransformations,
        nonProjectTransformations,
        cache,
        diagnostics),
      diagnostics);
  }

  /**
   * Checks if the given parent {@link ClassLoader} is the same as the given to this {@link ModuleClassLoader} at construction
   * time. This class loader adds additional parents to the chain so {@link ClassLoader#getParent()} can not be used directly.
   */
  boolean isCompatibleParentClassLoader(@Nullable ClassLoader parent) {
    return getParentAtConstruction() == parent;
  }

  /**
   * Returns the given parent {@link ClassLoader} at construction time.
   * This class loader adds additional parents to the chain so {@link ClassLoader#getParent()} can not be used directly.
   */
  @Nullable
  ClassLoader getParentAtConstruction() {
    return myParentAtConstruction;
  }

  @NotNull
  public Set<String> getNonProjectLoadedClasses() { return myImpl.getNonProjectLoadedClassNames(); }

  @NotNull
  public Set<String> getProjectLoadedClasses() { return myImpl.getProjectLoadedClassNames(); }

  @NotNull
  public ClassTransform getProjectClassesTransform() { return myImpl.getProjectTransforms(); }

  @NotNull
  public ClassTransform getNonProjectClassesTransform() { return myImpl.getNonProjectTransforms(); }

  /**
   * Determines whether the class specified by the given qualified name has a source file in the IDE that
   * has been edited more recently than its corresponding class file.
   *
   * <p>This method requires the indexing to have finished.
   *
   * <p><b>Note that this method can only answer queries for classes that this class loader has previously
   * loaded!</b>
   *
   * @param name         the fully qualified class name
   * @param myCredential a render sandbox credential
   * @return true if the source file has been modified, or false if not (or if the source file cannot be found)
   */
  public boolean isSourceModified(@NotNull String name, @Nullable Object myCredential) {
    if (!myImpl.getProjectLoadedClassNames().contains(name) ||
        ModuleClassLoaderUtil.isResourceClassName(name)) {
      return false;
    }

    Module module = getModule();
    if (module == null) return false;

    // Allow file system access for timestamps.
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      VirtualFile virtualFile = myImpl.findClassVirtualFile(name);
      if (virtualFile == null) return false;
      return ModuleClassLoaderUtil.isSourceModified(module, name, virtualFile);
    }
    finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }

  public boolean areDependenciesUpToDate() {
    Module module = getModule();
    if (module == null) return true;

    List<Path> currentlyLoadedLibraries = myImpl.getExternalLibraries();
    List<Path> moduleLibraries = ModuleClassLoaderUtil.getExternalLibraries(module);

    return currentlyLoadedLibraries.size() == moduleLibraries.size() &&
           currentlyLoadedLibraries.containsAll(moduleLibraries);
  }

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader.
   * This method just provides the non-cached version of {@link #isUserCodeUpToDate}. {@link #isUserCodeUpToDate} will cache
   * the result of this call until a PSI modification happens.
   */
  @VisibleForTesting
  boolean isUserCodeUpToDateNonCached() { return ModuleClassLoaderUtil.isUserCodeUpToDate(myImpl); }

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader. Always returns
   * false if there has not been any PSI changes.
   */
  public boolean isUserCodeUpToDate() {
    Module module = getModule();
    if (module == null) return true;
    // Cache the result of isUserCodeUpToDateNonCached until any PSI modifications have happened.
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(myImpl, () ->
      CachedValueProvider.Result.create(isUserCodeUpToDateNonCached(),
                                        PsiModificationTracker.MODIFICATION_COUNT,
                                        ModuleClassLoaderOverlays.getInstance(module)));
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    // The ModuleClassLoader overrides getResources to allow user code to access resources that are part of the libraries.
    // Instead of loading from the plugin class loader, we redirect the request to load it from the project libraries.
    return myImpl.getResources(name);
  }

  @Nullable
  @Override
  public URL getResource(String name) {
    // The ModuleClassLoader overrides getResources to allow user code to access resources that are part of the libraries.
    // Instead of loading from the plugin class loader, we redirect the request to load it from the project libraries.
    return myImpl.getResource(name);
  }

  public boolean isClassLoaded(@NotNull String className) {
    return findLoadedClass(className) != null;
  }

  @Override
  protected void onBeforeLoadClass(@NotNull String fqcn) { myDiagnostics.classLoadStart(fqcn); }

  @Override
  protected void onAfterLoadClass(@NotNull String fqcn, boolean loaded, long durationMs) { myDiagnostics.classLoadedEnd(fqcn, durationMs); }

  @Override
  protected void onBeforeFindClass(@NotNull String fqcn) { myDiagnostics.classFindStart(fqcn); }

  @Override
  protected void onAfterFindClass(@NotNull String fqcn, boolean found, long durationMs) { myDiagnostics.classFindEnd(fqcn, found,
                                                                                                                     durationMs); }

  @Override
  @Nullable
  public Module getModule() {
    return myModuleReference.get();
  }

  @NotNull
  public ModuleClassLoaderDiagnosticsRead getStats() {
    return myDiagnostics;
  }

  @Nullable
  public ModuleRenderContext getModuleContext() {
    Module module = getModule();
    return module == null ? null : ModuleRenderContext.forFile(module, myPsiFileProvider);
  }

  /**
   * Injects the given file with the passed fqcn so it looks like loaded from the project. Only for testing.
   */
  @TestOnly
  void injectProjectClassFile(@NotNull String fqcn, @NotNull VirtualFile file) {
    myImpl.injectProjectClassFile(fqcn, file);
  }

  /**
   * Injects the given [fqcn] as if it had been loaded by the overlay loader. Only for testing.
   */
  @TestOnly
  void injectProjectOvelaryLoadedClass(@NotNull String fqcn) {
    myImpl.injectProjectOvelaryLoadedClass(fqcn);
  }

  /**
   * If coroutine DefaultExecutor exists, waits for the its thread to stop or 1.1s whichever is faster, otherwise returns instantly.
   */
  private void waitForCoroutineThreadToStop() {
    try {
      Class<?> defaultExecutorClass = findLoadedClass(ModuleClassLoaderUtil.INTERNAL_PACKAGE + "kotlinx.coroutines.DefaultExecutor");
      if (defaultExecutorClass == null) {
        return;
      }
      // Kotlin bytecode generation converts isThreadPresent property into isThreadPresent$kotlinx_coroutines_core() method
      Method isThreadPresentMethod = findMethodLike(defaultExecutorClass, "isThreadPresent");
      if (isThreadPresentMethod == null) {
        LOG.warn("Method to check coroutine thread existence is not found.");
        return;
      }
      Field instanceField = defaultExecutorClass.getDeclaredField("INSTANCE");
      Object defaultExecutorObj = instanceField.get(null);

      isThreadPresentMethod.setAccessible(true);
      // DefaultExecutor thread has DEFAULT_KEEP_ALIVE of 1000ms. We expect it to disappear after at most of 1100ms waiting.
      final int ITERATIONS = 11;
      for (int i = 0; i <= ITERATIONS; ++i) {
        if (!(Boolean)isThreadPresentMethod.invoke(defaultExecutorObj)) {
          return;
        }
        if (i != ITERATIONS) {
          Thread.sleep(100);
        }
      }
      LOG.warn("DefaultExecutor thread is still running");
    } catch (Throwable t) {
      LOG.warn(t);
    }
  }

  @Nullable
  ModuleClassLoader copy(@NotNull ModuleClassLoaderDiagnosticsWrite diagnostics) {
    ModuleRenderContext renderContext = getModuleContext();
    if (isDisposed() || renderContext == null || renderContext.isDisposed()) return null;
    return new ModuleClassLoader(myParentAtConstruction, renderContext, getProjectClassesTransform(), getNonProjectClassesTransform(),
                                 diagnostics);
  }

  public boolean isDisposed() {
    return isDisposed.get();
  }

  @Override
  public void dispose() {
    isDisposed.set(true);
    myImpl.dispose();
    ourDisposeService.submit(() -> {
      waitForCoroutineThreadToStop();

      Set<ThreadLocal<?>> threadLocals = TrackingThreadLocal.clearThreadLocals(this);
      if (threadLocals == null || threadLocals.isEmpty()) {
        return;
      }

      // Because we are clearing-up ThreadLocals, the code must run on the Layoutlib Thread
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        for (ThreadLocal<?> threadLocal: threadLocals) {
          try {
            threadLocal.remove();
          } catch (Exception e) {
            LOG.warn(e); // Failure detected here will most probably cause a memory leak
          }
        }
      });
    });
  }
}
