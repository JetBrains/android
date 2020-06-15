package org.jetbrains.android.uipreview;

import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_V7;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_VIEW_HOLDER;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AndroidManifestPackageNameUtils;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.Library;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.rendering.classloading.RenderClassLoader;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.idea.util.FileExtensions;
import com.android.tools.idea.util.VirtualFileSystemOpener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Render class loader responsible for loading classes in custom views and local and library classes
 * used by those custom views (other than the framework itself, which is loaded by a parent class
 * loader via layout library.)
 */
public final class ModuleClassLoader extends RenderClassLoader {
  private static final Logger LOG = Logger.getInstance(ModuleClassLoader.class);

  /** The base module to use as a render context; the class loader will consult the module dependencies and library dependencies
   * of this class as well to find classes */
  private final WeakReference<Module> myModuleReference;

  /** Map from fully qualified class name to the corresponding .class file for each class loaded by this class loader */
  private Map<String, VirtualFile> myClassFiles;
  /** Map from fully qualified class name to the corresponding last modified info for each class loaded by this class loader */
  private Map<String, ClassModificationTimestamp> myClassFilesLastModified;

  private static class ClassModificationTimestamp {
    public final long timestamp;
    public final long length;

    ClassModificationTimestamp(long timestamp, long length) {
      this.timestamp = timestamp;
      this.length = length;
    }
  }

  ModuleClassLoader(@Nullable ClassLoader parent, @NotNull Module module) {
    super(parent);
    myModuleReference = new WeakReference<>(module);

    registerResources(module);
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
            else  {
              LOG.debug("  LocalResourceRepositoryInstance not found");
            }
          }
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("  super.findClass(%s)", anonymizeClassName(name)));
      }
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
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

  @Override
  @NotNull
  public Class<?> loadClass(@NotNull String name) throws ClassNotFoundException {
    // We overload loadClass() to load a class defined in a project
    // rather than a version of it that may already be present in Studio.
    // This is an issue if Studio shares a library or classes with some android code
    // we are trying to preview; the class loaded would be the one already used by
    // Studio rather than the one packaged with the library.
    // If they end up being different (say the lib is more recent than the Studio version),
    // it will likely result in broken preview as functions would be different / not present.
    // The only known case of this at this point is ConstraintLayout (a solver library
    // is used both by the android implementation and by Android Studio).

    // FIXME: While testing this approach, we found an issue on some Windows machine where
    // class loading would be broken. Thus, we limit the fix to the impacted solver classes
    // for now, until we can investigate the problem more in depth on Windows.
    boolean loadFromProject = name.startsWith("android.support.constraint.solver");
    if (loadFromProject) {
      try {
        // Give priority to loading class from this Class Loader.
        // This avoids leaking classes from the plugin into the project.
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
          return loadedClass;
        }
        return load(name);
      }
      catch (Exception ignore) {
        // Catch-all, defer to the parent implementation
      }
    }
    return super.loadClass(name);
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
   * @param name the fully qualified class name
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
    } finally {
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
    for (Library library : moduleSystem.getResolvedDependentLibraries()) {
      if (library instanceof ExternalLibrary && ((ExternalLibrary)library).hasResources()) {
        registerLibraryResources((ExternalLibrary)library, repositoryManager, classRegistry, idManager);
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
    if (repositoryManager.getNamespacing() == AaptOptions.Namespacing.DISABLED) {
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
          } catch (IOException ignore2) {
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
    return ClassLoadingUtilsKt.getLibraryDependenciesJars(myModuleReference.get());
  }

  /** Returns the path to a class file loaded for the given class, if any */
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

  /** Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader */
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
