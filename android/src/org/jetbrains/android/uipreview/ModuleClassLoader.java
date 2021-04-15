package org.jetbrains.android.uipreview;

import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_V7;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_VIEW_HOLDER;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
import static com.android.tools.idea.flags.StudioFlags.NELE_CLASS_BINARY_CACHE;
import static com.android.tools.idea.rendering.classloading.ClassConverter.getCurrentClassVersion;
import static com.android.tools.idea.rendering.classloading.ReflectionUtilKt.findMethodLike;
import static com.android.tools.idea.rendering.classloading.UtilKt.toClassTransform;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AndroidManifestPackageNameUtils;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.layoutlib.reflection.TrackingThreadLocal;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.Namespacing;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.classloading.ClassTransform;
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform;
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper;
import com.android.tools.idea.rendering.classloading.PseudoClass;
import com.android.tools.idea.rendering.classloading.RenderClassLoader;
import com.android.tools.idea.rendering.classloading.RepackageTransform;
import com.android.tools.idea.rendering.classloading.ThreadControllingTransform;
import com.android.tools.idea.rendering.classloading.ThreadLocalTrackingTransform;
import com.android.tools.idea.rendering.classloading.VersionClassTransform;
import com.android.tools.idea.rendering.classloading.ViewMethodWrapperTransform;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.idea.util.FileExtensions;
import com.android.tools.idea.util.VirtualFileSystemOpener;
import com.android.utils.SdkUtils;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Render class loader responsible for loading classes in custom views and local and library classes
 * used by those custom views (other than the framework itself, which is loaded by a parent class
 * loader via layout library.)
 */
public final class ModuleClassLoader extends RenderClassLoader implements ModuleProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(ModuleClassLoader.class);

  /**
   * Package name used to "re-package" certain classes that would conflict with the ones in the Studio class loader.
   * This applies to all packages defined in {@link ModuleClassLoader#PACKAGES_TO_RENAME}.
   */
  private static final String INTERNAL_PACKAGE = "_layoutlib_._internal_.";

  /**
   * If the project uses any of these libraries, we re-package them with a different name (prefixing them with
   * {@link ModuleClassLoader#INTERNAL_PACKAGE} so they do not conflict with the versions loaded in Studio.
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
    // Leave this transformation as last so the rest of the transformations operate on the regular names.
    visitor -> new RepackageTransform(visitor, PACKAGES_TO_RENAME, INTERNAL_PACKAGE)
  );

  static final ClassTransform NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS = toClassTransform(
    ViewMethodWrapperTransform::new,
    visitor -> new VersionClassTransform(visitor, getCurrentClassVersion(), 0),
    ThreadLocalTrackingTransform::new,
    ThreadControllingTransform::new,
    PreviewAnimationClockMethodTransform::new,
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
   * Modification count used to track the [ConstantRemapper] modifications. If this does not match the current count, the class loader is
   * out of date since new transformations might be needed.
   */
  private final long myConstantRemapperModificationCount;

  /**
   * Interface for reporting load times and general diagnostics.
   */
  @NotNull
  private final ModuleClassLoaderDiagnosticsWrite myDiagnostics;

  /**
   * Holds the provider that allows finding the {@link PsiFile}
   */
  private final Supplier<PsiFile> myPsiFileProvider;
  /**
   * Holds the provider that allows finding the source file that originated this
   * {@link ModuleClassLoader}. It allows for scoping the search of .class files.
   */
  private final Supplier<VirtualFile> mySourceFileProvider;

  /**
   * Map from fully qualified class name to the corresponding .class file for each class loaded by this class loader
   */
  private final Map<String, VirtualFile> myClassFiles = new ConcurrentHashMap<>();
  /**
   * Map from fully qualified class name to the corresponding last modified info for each class loaded by this class loader
   */
  private final Map<String, ClassModificationTimestamp> myClassFilesLastModified = new ConcurrentHashMap<>();

  private final List<URL> mAdditionalLibraries;

  private final Set<String> loadedClasses = new HashSet<>();

  /**
   * Method uses to remap type names using {@link ModuleClassLoader#INTERNAL_PACKAGE} as prefix to its original name so they original
   * class can be loaded from the file system correctly.
   */
  @NotNull
  private static String onDiskClassNameLookup(@NotNull String name) {
    return StringUtil.trimStart(name, INTERNAL_PACKAGE);
  }

  ModuleClassLoader(@Nullable ClassLoader parent, @NotNull ModuleRenderContext renderContext,
                    @NotNull ClassTransform projectTransformations,
                    @NotNull ClassTransform nonProjectTransformations,
                    @NotNull ModuleClassLoaderDiagnosticsWrite diagnostics) {
    this(parent, renderContext, projectTransformations, nonProjectTransformations,
         NELE_CLASS_BINARY_CACHE.get() ? ClassBinaryCacheManager.getInstance().getCache(renderContext.getModule()) : ClassBinaryCache.NO_CACHE,
         diagnostics);
  }

  private ModuleClassLoader(@Nullable ClassLoader parent, @NotNull ModuleRenderContext renderContext,
                    @NotNull ClassTransform projectTransformations,
                    @NotNull ClassTransform nonProjectTransformations,
                    @NotNull ClassBinaryCache cache,
                    @NotNull ModuleClassLoaderDiagnosticsWrite diagnostics) {
    super(parent, projectTransformations, nonProjectTransformations, ModuleClassLoader::onDiskClassNameLookup, cache, !SystemInfo.isWindows);
    Disposer.register(renderContext.getModule(), this);
    myModuleReference = new WeakReference<>(renderContext.getModule());
    // Extracting the provider into a variable to avoid the lambda capturing a reference to renderContext
    myPsiFileProvider = renderContext.getFileProvider();
    mySourceFileProvider = () -> {
      PsiFile file = myPsiFileProvider.get();
      return file != null ? file.getVirtualFile() : null;
    };
    mAdditionalLibraries = getAdditionalLibraries();
    myConstantRemapperModificationCount = ProjectConstantRemapper.getInstance(renderContext.getProject()).getModificationCount();
    myDiagnostics = diagnostics;

    registerResources(renderContext.getModule());
    cache.setDependencies(ContainerUtil.map(getExternalJars(), URL::getPath));
  }

  @NotNull
  private static List<URL> getAdditionalLibraries() {
    String layoutlibDistributionPath = StudioEmbeddedRenderTarget.getEmbeddedLayoutLibPath();
    if (layoutlibDistributionPath == null) {
      return Collections.emptyList(); // Error is already logged by getEmbeddedLayoutLibPath
    }

    String relativeCoroutineLibPath = FileUtil.toSystemIndependentName("data/layoutlib-extensions.jar");
    try {
      return Lists.newArrayList(SdkUtils.fileToUrl(new File(layoutlibDistributionPath, relativeCoroutineLibPath)));
    }
    catch (MalformedURLException e) {
      LOG.error("Failed to find layoutlib-extensions library", e);
      return Collections.emptyList();
    }
  }

  /**
   * Returns true for any classFqn that is supposed to be repackaged.
   */
  private static boolean isRepackagedClass(@NotNull String classFqn) {
    for (String pkgName : PACKAGES_TO_RENAME) {
      if (classFqn.startsWith(pkgName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected byte[] rewriteClass(@NotNull String fqcn,
                                @NotNull byte[] classData,
                                @NotNull ClassTransform transformations,
                                int flags) {
    long startTimeMs = System.currentTimeMillis();
    try {
      return super.rewriteClass(fqcn, classData, transformations, flags);
    } finally {
      myDiagnostics.classRewritten(fqcn, classData.length, System.currentTimeMillis() - startTimeMs);
    }
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    if ("kotlinx.coroutines.android.AndroidDispatcherFactory".equals(name)) {
      // Hide this class to avoid the coroutines in the project loading the AndroidDispatcherFactory for now.
      // b/162056408
      //
      // Throwing an exception here (other than ClassNotFoundException) will force the FastServiceLoader to fallback
      // to the regular class loading. This allows us to inject our own DispatcherFactory, specific to Layoutlib.
      throw new IllegalArgumentException("AndroidDispatcherFactory not supported by layoutlib");
    }

    long startTimeMs = System.currentTimeMillis();
    myDiagnostics.classLoadStart(name);
    boolean classLoaded = true;
    try {
      if (isRepackagedClass(name)) {
        // This should not happen for "regularly" loaded classes. It will happen for classes loaded using
        // reflection like Class.forName("kotlin.a.b.c"). In this case, we need to redirect the loading to the right
        // class by injecting the correct name.
        name = INTERNAL_PACKAGE + name;
      }

      return super.loadClass(name);
    } catch (ClassNotFoundException e) {
      classLoaded = false;
      throw e;
    } finally {
      if (classLoaded) {
        myDiagnostics.classLoadedEnd(name, System.currentTimeMillis() - startTimeMs);
        loadedClasses.add(name);
      }
    }
  }

  @Override
  @NotNull
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("findClass(%s)", name));
    }

    Module module = myModuleReference.get();
    long startTimeMs = System.currentTimeMillis();
    boolean classFound = true;
    myDiagnostics.classFindStart(name);
    try {
      if (!myInsideJarClassLoader) {
        if (module != null) {
          if (isResourceClassName(name)) {
            AndroidFacet facet = AndroidFacet.getInstance(module);
            if (facet != null) {
              ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(facet);
              byte[] data = ResourceClassRegistry.get(module.getProject()).findClassDefinition(name, repositoryManager);
              if (data != null) {
                LOG.debug("  Defining class from AAR registry");
                return loadClass(name, data);
              }
            }
            else {
              LOG.debug("  LocalResourceRepositoryInstance not found");
            }
          }
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("  super.findClass(%s)", anonymizeClassName(name)));
      }
      return super.findClass(name);
    }
    catch (ClassNotFoundException e) {
      byte[] clazz = null;
      if (RecyclerViewHelper.CN_CUSTOM_ADAPTER.equals(name)) {
        clazz = RecyclerViewHelper.getAdapterClass(DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_V7),
                                                   DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_VIEW_HOLDER),
                                                   DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_ADAPTER));
      }
      if (RecyclerViewHelper.CN_CUSTOM_VIEW_HOLDER.equals(name)) {
        clazz = RecyclerViewHelper.getViewHolder(DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_V7),
                                                 DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_VIEW_HOLDER),
                                                 DependencyManagementUtil.mapAndroidxName(module, CLASS_RECYCLER_VIEW_ADAPTER));
      }
      if (clazz != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("  Defining RecyclerView helper class");
        }
        return defineClassAndPackage(name, clazz, 0, clazz.length);
      }
      LOG.debug(e);
      classFound = false;
      throw e;
    } finally {
      myDiagnostics.classFindEnd(name, classFound, System.currentTimeMillis() - startTimeMs);
    }
  }

  @Override
  @NotNull
  protected Class<?> load(@NotNull String name) throws ClassNotFoundException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("load(%s)", anonymizeClassName(name)));
    }

    Module module = myModuleReference.get();
    if (module == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("  ClassNotFoundException(%s)", name));
      }
      throw new ClassNotFoundException(name);
    }
    Class<?> aClass = loadClassFromModuleOrDependency(module, name);

    if (aClass != null) {
      return aClass;
    }
    return loadClassFromNonProjectDependency(name);
  }

  @NotNull
  @Override
  public PseudoClass locatePseudoClass(@NotNull String classFqn) {
    String diskLookupName = onDiskClassNameLookup(classFqn);
    PseudoClass pseudoClass = super.locatePseudoClass(diskLookupName);

    if (!pseudoClass.getName().equals(diskLookupName)) {
      Module module = myModuleReference.get();
      if (module == null || module.isDisposed()) {
        return PseudoClass.Companion.objectPseudoClass();
      }

      VirtualFile classFile = ProjectSystemUtil.getModuleSystem(module)
        .getClassFileFinderForSourceFile(mySourceFileProvider.get())
        .findClassFile(diskLookupName);
      if (classFile != null) {
        try {
          return PseudoClass.Companion.fromByteArray(classFile.contentsToByteArray(), this);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }

    if (isRepackagedClass(classFqn)) {
      // If this is one of the repackaged classes, we have to restore the name. This will be usually the case when we are, for example,
      // looking for a super class of a repackaged class. For example _layoutlib_._internal_.repackagedname.classB will have a as super
      // repackagename.classA. We need to make sure that class is resolved as _layoutlib_._internal_.repackagedname.classA.
      classFqn = INTERNAL_PACKAGE + classFqn;
    }
    return pseudoClass.withNewName(classFqn);
  }

  /**
   * Loads a class from the project, either from the given module or one of the source code dependencies. If the class
   * is in a library dependency, like a jar file, this method will not find the class.
   */
  @Nullable
  private Class<?> loadClassFromModuleOrDependency(@NotNull Module module, @NotNull String name) {
    if (module.isDisposed()) {
      return null;
    }

    VirtualFile classFile = ProjectSystemUtil.getModuleSystem(module)
      .getClassFileFinderForSourceFile(mySourceFileProvider.get())
      .findClassFile(onDiskClassNameLookup(name));

    if (classFile == null) {
      return null;
    }

    return loadClassFile(name, classFile);
  }

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
    // Don't flag R.java edits; not done by user
    if (isResourceClassName(name)) {
      return false;
    }

    Module module = myModuleReference.get();
    if (module == null) {
      return false;
    }
    VirtualFile classFile = getClassFile(name);

    // Make sure the class file is up to date and if not, log an error
    if (classFile == null) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || AndroidModel.get(facet) == null) {
      return false;
    }
    // Allow file system access for timestamps.
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      return AndroidModel.get(facet).isClassFileOutOfDate(module, name, classFile);
    }
    finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }

  // matches foo.bar.R or foo.bar.R$baz
  private static final Pattern RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$");

  private static boolean isResourceClassName(@NotNull String className) {
    return RESOURCE_CLASS_NAME.matcher(className).matches();
  }

  @Override
  @Nullable
  protected Class<?> loadClassFile(@NotNull String name, @NotNull VirtualFile classFile) {
    myClassFiles.put(name, classFile);
    myClassFilesLastModified.put(name, ClassModificationTimestamp.fromVirtualFile(classFile));

    return super.loadClassFile(name, classFile);
  }

  private static void registerResources(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return;
    }
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(androidFacet);
    ResourceIdManager idManager = ResourceIdManager.get(module);
    ResourceClassRegistry classRegistry = ResourceClassRegistry.get(module.getProject());

    // If final ids are used, we will read the real class from disk later (in loadAndParseRClass), using this class loader. So we
    // can't treat it specially here, or we will read the wrong bytecode later.
    if (!idManager.finalIdsUsed()) {
      classRegistry.addLibrary(repositoryManager.getAppResources(),
                               idManager,
                               ReadAction.compute(() -> AndroidManifestUtils.getPackageName(androidFacet)),
                               repositoryManager.getNamespace());
    }

    for (AndroidFacet dependency : AndroidUtils.getAllAndroidDependencies(module, false)) {
      classRegistry.addLibrary(repositoryManager.getAppResources(),
                               idManager,
                               ReadAction.compute(() -> AndroidManifestUtils.getPackageName(dependency)),
                               repositoryManager.getNamespace());
    }

    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    for (ExternalAndroidLibrary library : moduleSystem.getAndroidLibraryDependencies()) {
      if (library.getHasResources()) {
        registerLibraryResources(library, repositoryManager, classRegistry, idManager);
      }
    }
  }

  private static void registerLibraryResources(@NotNull ExternalAndroidLibrary library,
                                               @NotNull ResourceRepositoryManager repositoryManager,
                                               @NotNull ResourceClassRegistry classRegistry,
                                               @NotNull ResourceIdManager idManager) {
    LocalResourceRepository appResources = repositoryManager.getAppResources();

    // Choose which resources should be in the generated R class. This is described in the JavaDoc of ResourceClassGenerator.
    ResourceRepository rClassContents;
    ResourceNamespace resourcesNamespace;
    String packageName;
    if (repositoryManager.getNamespacing() == Namespacing.DISABLED) {
      packageName = getPackageName(library);
      if (packageName == null) {
        return;
      }
      rClassContents = appResources;
      resourcesNamespace = ResourceNamespace.RES_AUTO;
    }
    else {
      SingleNamespaceResourceRepository aarResources = repositoryManager.findLibraryResources(library);
      if (aarResources == null) {
        return;
      }

      rClassContents = aarResources;
      resourcesNamespace = aarResources.getNamespace();
      packageName = aarResources.getPackageName();
    }

    classRegistry.addLibrary(rClassContents, idManager, packageName, resourcesNamespace);
  }

  @Nullable
  private static String getPackageName(@NotNull ExternalAndroidLibrary library) {
    if (library.getPackageName() != null) {
      return library.getPackageName();
    }
    PathString manifestFile = library.getManifestFile();
    if (manifestFile != null) {
      try {
        return AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifestFile);
      }
      catch (IOException ignore) {
        // Workaround for https://issuetracker.google.com/127647973
        // Until fixed, the VFS might have an outdated view of the gradle cache directory. Some manifests might appear as missing but they
        // are actually there. In those cases, we issue a refresh call to fix the problem.
        if (VirtualFileSystemOpener.INSTANCE.recognizes(manifestFile)) {
          FileExtensions.toVirtualFile(manifestFile, true);

          try {
            return AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifestFile);
          }
          catch (IOException ignore2) {
            return null;
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns the list of external JAR files referenced by the class loader.
   */
  @Override
  @NotNull
  protected List<URL> getExternalJars() {
    return Lists.newArrayList(
      Iterables.concat(mAdditionalLibraries, ClassLoadingUtilsKt.getLibraryDependenciesJars(myModuleReference.get())));
  }

  /**
   * Returns the path to a class file loaded for the given class, if any
   */
  @Nullable
  private VirtualFile getClassFile(@NotNull String className) {
    VirtualFile file = myClassFiles.get(className);
    if (file == null) {
      return null;
    }
    return file.isValid() ? file : null;
  }

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader
   */
  public boolean isUpToDate() {
    // We check the dependencies first since it does not require disk access.
    if (!areDependenciesUpToDate()) return false;

    for (Map.Entry<String, VirtualFile> entry : myClassFiles.entrySet()) {
      VirtualFile classFile = entry.getValue();
      if (!classFile.isValid()) {
        return false;
      }

      String className = entry.getKey();
      ClassModificationTimestamp lastModifiedStamp = myClassFilesLastModified.get(className);
      if (lastModifiedStamp != null && !lastModifiedStamp.isUpToDate(classFile)) {
        return false;
      }
    }

    return true;
  }

  public boolean isClassLoaded(@NotNull String className) {
    return findLoadedClass(className) != null;
  }

  @Override
  @Nullable
  public Module getModule() {
    return myModuleReference.get();
  }

  @NotNull
  public ModuleClassLoaderDiagnosticsRead getStats() {
    return myDiagnostics;
  }

  @NotNull
  public ImmutableCollection<String> getLoadedClasses() {
    return ImmutableList.copyOf(loadedClasses);
  }

  @Nullable
  public ModuleRenderContext getModuleContext() {
    Module module = myModuleReference.get();
    return module == null ? null : ModuleRenderContext.forFile(module, myPsiFileProvider);
  }

  /**
   * If coroutine DefaultExecutor exists, waits for the its thread to stop or 1.1s whichever is faster, otherwise returns instantly.
   */
  private void waitForCoroutineThreadToStop() {
    try {
      Class<?> defaultExecutorClass = findLoadedClass(INTERNAL_PACKAGE + "kotlinx.coroutines.DefaultExecutor");
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

  @Override
  public void dispose() {
    myClassFiles.clear();
    myClassFilesLastModified.clear();

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
