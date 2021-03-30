/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading;

import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
import static com.android.tools.idea.rendering.classloading.ClassConverter.isValidClassFile;

import com.android.SdkConstants;
import com.android.annotations.concurrency.GuardedBy;
import com.google.common.base.Charsets;
import com.google.common.base.Suppliers;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.android.uipreview.ClassBinaryCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;

/**
 * Class loader which can load classes for rendering and if necessary
 * convert the class file format down from unknown versions to a known version.
 */
public abstract class RenderClassLoader extends ClassLoader implements PseudoClassLocator {
  protected static final Logger LOG = Logger.getInstance(RenderClassLoader.class);

  private static final UrlClassLoader.CachePool ourLoaderCachePool = UrlClassLoader.createCachePool();

  private final ClassTransform myProjectClassesTransformationProvider;
  private final ClassTransform myNonProjectClassesTransformationProvider;
  private final Object myJarClassLoaderLock = new Object();
  private final Function<String, String> myNonProjectClassNameLookup;
  @GuardedBy("myJarClassLoaderLock")
  private final Supplier<UrlClassLoader> myJarClassLoader = Suppliers.memoize(() -> createJarClassLoader(getExternalJars()));
  protected boolean myInsideJarClassLoader;
  private final ClassBinaryCache myTransformedClassCache;
  private final Object myCachedTransformationUniqueIdLock = new Object();
  @GuardedBy("myCachedTransformationUniqueIdLock")
  @Nullable
  private String myCachedTransformationUniqueId = null;
  private boolean myAllowExternalJarFileLocking;

  /**
   * Creates a new {@link RenderClassLoader}.
   *
   * @param parent the parent {@link ClassLoader}
   * @param projectClassesTransformationProvider a {@link ClassTransform} that given a {@link ClassVisitor} returns a new one applying any desired
   *                                             transformation. This transformation is only applied to classes from the user project.
   * @param nonProjectClassesTransformationProvider a {@link ClassTransform} that given a {@link ClassVisitor} returns a new one applying any
   *                                                desired transformation. This transformation is applied to all classes except user
   *                                                project classes.
   * @param nonProjectClassNameLookup a {@link Function} that allows mapping "modified" class names to its original form so they can be
   *                                  correctly loaded from the file system. For example, if the class loader is renaming classes to names
   *                                  that do not exist on disk, this allows the {@link RenderClassLoader} to lookup the correct name and
   *                                  load it from disk.
   * @param cache a binary class representation cache to speed up jar file reads where possible.
   * @param allowExternalJarFileLocking if true, jar files belonging to external libraries can be locked while in use by this class loader.
   *                                    This speeds up the I/O but can cause problems on Windows systems.
   */
  public RenderClassLoader(@Nullable ClassLoader parent,
                           @NotNull ClassTransform projectClassesTransformationProvider,
                           @NotNull ClassTransform nonProjectClassesTransformationProvider,
                           @NotNull Function<String, String> nonProjectClassNameLookup,
                           @NotNull ClassBinaryCache cache,
                           boolean allowExternalJarFileLocking) {
    super(parent);
    myProjectClassesTransformationProvider = projectClassesTransformationProvider;
    myNonProjectClassesTransformationProvider = nonProjectClassesTransformationProvider;
    myNonProjectClassNameLookup = nonProjectClassNameLookup;
    myTransformedClassCache = cache;
    myAllowExternalJarFileLocking = allowExternalJarFileLocking;
  }


  /**
   * Creates a new {@link RenderClassLoader} with transformations that apply to both project and non project classes..
   *
   * @param parent the parent {@link ClassLoader}.
   * @param transformationProvider a {@link Function} that given a {@link ClassVisitor} returns a new one applying any desired
   *                               transformation.
   */
  @TestOnly
  public RenderClassLoader(@Nullable ClassLoader parent, @NotNull ClassTransform transformationProvider) {
    this(parent, transformationProvider, transformationProvider, Function.identity(), ClassBinaryCache.NO_CACHE, false);
  }

  /**
   * Creates a new {@link RenderClassLoader} with no transformations.
   *
   * @param parent the parent {@link ClassLoader}.
   */
  @TestOnly
  public RenderClassLoader(@Nullable ClassLoader parent) {
    this(parent, ClassTransform.getIdentity(), ClassTransform.getIdentity(), Function.identity(), ClassBinaryCache.NO_CACHE, false);
  }

  private static String calculateTransformationsUniqueId(@NotNull ClassTransform projectClassesTransformationProvider,
                                                         @NotNull ClassTransform nonProjectClassesTransformationProvider) {
    //noinspection UnstableApiUsage
    return Hashing.goodFastHash(64).newHasher()
      .putString(projectClassesTransformationProvider.getId(), Charsets.UTF_8)
      .putString(nonProjectClassesTransformationProvider.getId(), Charsets.UTF_8)
      .hash()
      .toString();
  }

  @NotNull
  private String getTransformationsUniqueId() {
    synchronized (myCachedTransformationUniqueIdLock) {
      if (myCachedTransformationUniqueId == null) {
        myCachedTransformationUniqueId = calculateTransformationsUniqueId(myProjectClassesTransformationProvider, myNonProjectClassesTransformationProvider);
      }

      return myCachedTransformationUniqueId;
    }
  }

  public boolean areTransformationsUpToDate(@NotNull ClassTransform projectClassesTransformationProvider,
                                            @NotNull ClassTransform nonProjectClassesTransformationProvider) {
    return getTransformationsUniqueId()
      .equals(calculateTransformationsUniqueId(projectClassesTransformationProvider, nonProjectClassesTransformationProvider));
  }

  protected abstract List<URL> getExternalJars();

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    return load(name);
  }

  @NotNull
  protected Class<?> load(String name) throws ClassNotFoundException {
    return loadClassFromNonProjectDependency(name);
  }

  @NotNull
  @Override
  public PseudoClass locatePseudoClass(@NotNull String classFqn) {
    try (InputStream is = getResourceAsStream(classFqn.replace(".", "/") + ".class")) {
      if (is != null) {
        byte[] data = ByteStreams.toByteArray(is);
        return PseudoClass.Companion.fromByteArray(data, this);
      }
    }
    catch (IOException e) {
      LOG.debug(e);
    }

    return PseudoClass.Companion.objectPseudoClass();
  }

  @NotNull
  private UrlClassLoader createJarClassLoader(@NotNull List<URL> urls) {
    return UrlClassLoader.build()
      .parent(new FirewalledResourcesClassLoader(this))
      .urls(urls)
      .useCache(ourLoaderCachePool, url -> true)
      .allowLock(myAllowExternalJarFileLocking)
      .setLogErrorOnMissingJar(false)
      .get();
  }

  @NotNull
  protected Class<?> loadClassFromNonProjectDependency(@NotNull String name) throws ClassNotFoundException {
    try {
      byte[] data = getNonProjectClassData(name, myNonProjectClassesTransformationProvider);
      return defineClassAndPackage(name, data, 0, data.length);
    }
    catch (IOException | ClassNotFoundException e) {
      LOG.debug(e);
      if (LOG.isDebugEnabled()) LOG.debug(String.format("ClassNotFoundException(%s)", name));
      throw new ClassNotFoundException(name, e);
    }
  }

  /**
   * Loads from disk the given class and applies the transformation.
   */
  @NotNull
  private byte[] getNonProjectClassData(@NotNull String name, @NotNull ClassTransform classTransform) throws ClassNotFoundException, IOException {
    String classTransformId = classTransform.getId();
    byte[] cachedData = myTransformedClassCache.get(name, classTransformId);
    if (cachedData != null) {
      return cachedData;
    }

    UrlClassLoader jarClassLoaders;
    synchronized (myJarClassLoaderLock) {
      jarClassLoaders = myJarClassLoader.get();
    }

    String diskLookupName = myNonProjectClassNameLookup.apply(name);
    myInsideJarClassLoader = true;
    try {
      // We do not request the stream from URL because it is problematic, see https://stackoverflow.com/questions/7071761
      String classResourceName = diskLookupName.replace('.', '/') + SdkConstants.DOT_CLASS;
      URL classUrl = jarClassLoaders.getResource(classResourceName);
      if (classUrl == null) {
        throw new ClassNotFoundException(name);
      }
      try (InputStream is = jarClassLoaders.getResourceAsStream(classResourceName)) {
        if (is == null) {
          throw new ClassNotFoundException(name);
        }
        byte[] data = ByteStreams.toByteArray(is);

        if (!isValidClassFile(data)) {
          throw new ClassFormatError(name);
        }
        byte[] transformedData = rewriteClass(name, data, classTransform, ClassWriter.COMPUTE_MAXS);
        Pair<String, String> splitPath = URLUtil.splitJarUrl(classUrl.getPath());
        if (splitPath != null) {
          myTransformedClassCache.put(name, classTransformId, splitPath.first, transformedData);
        } else {
          LOG.warn("Could not find the file for " + classUrl);
        }
        return transformedData;
      }
    }
    finally {
      myInsideJarClassLoader = false;
    }
  }

  @Nullable
  protected Class<?> loadClassFile(String fqcn, @NotNull VirtualFile classFile) {
    try {
      byte[] data = classFile.contentsToByteArray();
      return loadClass(fqcn, data);
    }
    catch (IOException e) {
      LOG.warn(e);
    }

    return null;
  }

  protected byte[] rewriteClass(@NotNull String fqcn, @NotNull byte[] classData, @NotNull ClassTransform transformations, int flags) {
    return ClassConverter.rewriteClass(classData, transformations, flags, this);
  }

  @NotNull
  protected Class<?> loadClass(@NotNull String fqcn, @NotNull byte[] data) {
    if (!isValidClassFile(data)) {
      throw new ClassFormatError(fqcn);
    }

    byte[] rewritten = rewriteClass(fqcn, data,  myProjectClassesTransformationProvider, ClassWriter.COMPUTE_FRAMES);
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Defining class '%s' from disk file", anonymizeClassName(fqcn)));
      }
      return defineClassAndPackage(fqcn, rewritten, 0, rewritten.length);
    } catch (UnsupportedClassVersionError inner) {
      LOG.debug(inner);
      // Wrap the UnsupportedClassVersionError as a InconvertibleClassError
      // such that clients can look up the actual bytecode version required.
      throw InconvertibleClassError.wrap(inner, fqcn, data);
    }
  }

  @NotNull
  protected Class<?> defineClassAndPackage(@NotNull String name, @NotNull byte[] b, int offset, int len) {
    int i = name.lastIndexOf('.');
    if (i > 0) {
      String packageName = name.substring(0, i);
      Package pkg = getPackage(packageName);
      if (pkg == null) {
        definePackage(packageName, null, null, null, null, null, null, null);
      }
    }
    return defineClass(name, b, offset, len);
  }

  protected boolean areDependenciesUpToDate() {
    List<URL> updatedDependencies = getExternalJars();
    List<URL> currentDependencies;
    synchronized (myJarClassLoaderLock) {
      currentDependencies = myJarClassLoader.get().getUrls();
    }

    if (updatedDependencies.size() != currentDependencies.size()) {
      return false;
    }

    return currentDependencies.containsAll(updatedDependencies);

  }

  // TODO(b/151089727): Fix this
  // Technically, this is incorrect and we should never override getResources method. However, we can not handle dependencies properly
  // in studio project and cannot properly isolate layoutlib class/resource loader from the libraries available in Android plugin. This
  // allows us to ignore all the resources from the Android plugin and use only resource from the Android project module dependencies.
  // In theory we are actually "loosing" resource from standard library and layoutlib, though we are currently not expecting any to be
  // there. All the rest (from other dependencies of core plugin, android plugin etc.) should never be accessible from here.
  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    synchronized (myJarClassLoaderLock) {
      return myJarClassLoader.get().getResources(name);
    }
  }

  @NotNull
  public ClassTransform getProjectClassesTransformationProvider() {
    return myProjectClassesTransformationProvider;
  }

  @NotNull
  public ClassTransform getNonProjectClassesTransformationProvider() {
    return myNonProjectClassesTransformationProvider;
  }
}
