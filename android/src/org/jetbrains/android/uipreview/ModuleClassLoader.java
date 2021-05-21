// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.uipreview;

import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_V7;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_VIEW_HOLDER;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
import static com.android.tools.idea.rendering.classloading.ClassConverter.getCurrentClassVersion;
import static com.android.tools.idea.rendering.classloading.UtilKt.multiTransformOf;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AndroidManifestPackageNameUtils;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalLibrary;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.Namespacing;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.rendering.classloading.ConstantRemapperManager;
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform;
import com.android.tools.idea.rendering.classloading.RenderClassLoader;
import com.android.tools.idea.rendering.classloading.RepackageTransform;
import com.android.tools.idea.rendering.classloading.ThreadLocalRenameTransform;
import com.android.tools.idea.rendering.classloading.VersionClassTransform;
import com.android.tools.idea.rendering.classloading.ViewMethodWrapperTransform;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.idea.util.FileExtensions;
import com.android.tools.idea.util.VirtualFileSystemOpener;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassVisitor;

/**
 * Render class loader responsible for loading classes in custom views and local and library classes
 * used by those custom views (other than the framework itself, which is loaded by a parent class
 * loader via layout library.)
 */
public final class ModuleClassLoader extends RenderClassLoader {
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
  static final Function<ClassVisitor, ClassVisitor> PROJECT_DEFAULT_TRANSFORMS = multiTransformOf(
    visitor -> new ViewMethodWrapperTransform(visitor),
    visitor -> new VersionClassTransform(visitor, getCurrentClassVersion(), 0),
    visitor -> new ThreadLocalRenameTransform(visitor),
    // Leave this transformation as last so the rest of the transformations operate on the regular names.
    visitor -> new RepackageTransform(visitor, PACKAGES_TO_RENAME, INTERNAL_PACKAGE)
  );

  static final Function<ClassVisitor, ClassVisitor> NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS = multiTransformOf(
    visitor -> new ViewMethodWrapperTransform(visitor),
    visitor -> new VersionClassTransform(visitor, getCurrentClassVersion(), 0),
    visitor -> new ThreadLocalRenameTransform(visitor),
    visitor -> new PreviewAnimationClockMethodTransform(visitor),
    // Leave this transformation as last so the rest of the transformations operate on the regular names.
    visitor -> new RepackageTransform(visitor, PACKAGES_TO_RENAME, INTERNAL_PACKAGE)
  );

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
   * Map from fully qualified class name to the corresponding .class file for each class loaded by this class loader
   */
  private Map<String, VirtualFile> myClassFiles;
  /**
   * Map from fully qualified class name to the corresponding last modified info for each class loaded by this class loader
   */
  private Map<String, ClassModificationTimestamp> myClassFilesLastModified;

  private final List<Path> mAdditionalLibraries;

  private static class ClassModificationTimestamp {
    public final long timestamp;
    public final long length;

    ClassModificationTimestamp(long timestamp, long length) {
      this.timestamp = timestamp;
      this.length = length;
    }
  }


  /**
   * Method uses to remap type names using {@link ModuleClassLoader#INTERNAL_PACKAGE} as prefix to its original name so they original
   * class can be loaded from the file system correctly.
   */
  @NotNull
  private static String nonProjectClassNameLookup(@NotNull String name) {
    return StringUtil.trimStart(name, INTERNAL_PACKAGE);
  }

  ModuleClassLoader(@Nullable ClassLoader parent, @NotNull Module module,
                    @NotNull Function<ClassVisitor, ClassVisitor> projectTransformations,
                    @NotNull Function<ClassVisitor, ClassVisitor> nonProjectTransformations) {
    super(parent, projectTransformations, nonProjectTransformations, ModuleClassLoader::nonProjectClassNameLookup);
    myModuleReference = new WeakReference<>(module);
    mAdditionalLibraries = getAdditionalLibraries();
    myConstantRemapperModificationCount = ConstantRemapperManager.INSTANCE.getConstantRemapper().getModificationCount();

    registerResources(module);
  }

  ModuleClassLoader(@Nullable ClassLoader parent, @NotNull Module module) {
    this(parent, module, PROJECT_DEFAULT_TRANSFORMS, NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS);
  }

  @NotNull
  private static List<Path> getAdditionalLibraries() {
    String layoutlibDistributionPath = StudioEmbeddedRenderTarget.getEmbeddedLayoutLibPath();
    if (layoutlibDistributionPath == null) {
      return Collections.emptyList(); // Error is already logged by getEmbeddedLayoutLibPath
    }

    String relativeCoroutineLibPath = FileUtil.toSystemIndependentName("data/layoutlib-extensions.jar");
    return List.of(new File(layoutlibDistributionPath, relativeCoroutineLibPath).toPath());
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
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    if ("kotlinx.coroutines.android.AndroidDispatcherFactory".equals(name)) {
      // Hide this class to avoid the coroutines in the project loading the AndroidDispatcherFactory for now.
      // b/162056408
      //
      // Throwing an exception here (other than ClassNotFoundException) will force the FastServiceLoader to fallback
      // to the regular class loading. This allows us to inject our own DispatcherFactory, specific to Layoutlib.
      throw new IllegalArgumentException("AndroidDispatcherFactory not supported by layoutlib");
    }

    if (isRepackagedClass(name)) {
      // This should not happen for "regularly" loaded classes. It will happen for classes loaded using
      // reflection like Class.forName("kotlin.a.b.c"). In this case, we need to redirect the loading to the right
      // class by injecting the correct name.
      name = INTERNAL_PACKAGE + name;
    }

    return super.loadClass(name);
  }

  @Override
  @NotNull
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("findClass(%s)", name));
    }

    Module module = myModuleReference.get();
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
      throw e;
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

  /**
   * Loads a class from the project, either from the given module or one of the source code dependencies. If the class
   * is in a library dependency, like a jar file, this method will not find the class.
   */
  @Nullable
  private Class<?> loadClassFromModuleOrDependency(@NotNull Module module, @NotNull String name) {
    if (module.isDisposed()) {
      return null;
    }

    VirtualFile classFile = ProjectSystemUtil.getModuleSystem(module).findClassFile(name);

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
    if (myClassFiles == null) {
      myClassFiles = new ConcurrentHashMap<>();
      myClassFilesLastModified = new ConcurrentHashMap<>();
    }
    myClassFiles.put(name, classFile);
    myClassFilesLastModified.put(name, new ClassModificationTimestamp(classFile.getTimeStamp(), classFile.getLength()));

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
    for (ExternalLibrary library : moduleSystem.getResolvedLibraryDependencies()) {
      if (library.getHasResources()) {
        registerLibraryResources(library, repositoryManager, classRegistry, idManager);
      }
    }
  }

  private static void registerLibraryResources(@NotNull ExternalLibrary library,
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
  private static String getPackageName(@NotNull ExternalLibrary library) {
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
  protected List<Path> getExternalJars() {
    return Lists.newArrayList(
      Iterables.concat(mAdditionalLibraries, ClassLoadingUtilsKt.getLibraryDependenciesJars(myModuleReference.get())));
  }

  /**
   * Returns the path to a class file loaded for the given class, if any
   */
  @Nullable
  private VirtualFile getClassFile(@NotNull String className) {
    if (myClassFiles == null) {
      return null;
    }
    VirtualFile file = myClassFiles.get(className);
    if (file == null) {
      return null;
    }
    return file.isValid() ? file : null;
  }

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader
   */
  boolean isUpToDate() {
    if (myClassFiles != null) {
      for (Map.Entry<String, VirtualFile> entry : myClassFiles.entrySet()) {
        String className = entry.getKey();
        VirtualFile classFile = entry.getValue();
        if (!classFile.isValid()) {
          return false;
        }
        ClassModificationTimestamp lastModifiedStamp = myClassFilesLastModified.get(className);
        if (lastModifiedStamp != null) {
          long loadedModifiedTime = lastModifiedStamp.timestamp;
          long loadedModifiedLength = lastModifiedStamp.length;
          long classFileModifiedTime = classFile.getTimeStamp();
          long classFileModifiedLength = classFile.getLength();
          if ((classFileModifiedTime > 0L && loadedModifiedTime > 0L && loadedModifiedTime < classFileModifiedTime)
              || loadedModifiedLength != classFileModifiedLength) {
            return false;
          }
        }
      }
    }

    return areDependenciesUpToDate();
  }

  public boolean isClassLoaded(@NotNull String className) {
    return findLoadedClass(className) != null;
  }
}
