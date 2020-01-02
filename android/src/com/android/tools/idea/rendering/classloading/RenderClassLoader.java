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
import static com.android.tools.idea.rendering.classloading.ClassConverter.getCurrentClassVersion;
import static com.android.tools.idea.rendering.classloading.ClassConverter.isValidClassFile;

import com.android.SdkConstants;
import com.google.common.base.Suppliers;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.lang.UrlClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassVisitor;

/**
 * Class loader which can load classes for rendering and if necessary
 * convert the class file format down from unknown versions to a known version.
 */
public abstract class RenderClassLoader extends ClassLoader {
  protected static final Logger LOG = Logger.getInstance(RenderClassLoader.class);

  /**
   * Classes are rewritten by applying the following transformations:
   * <ul>
   *   <li>Updates the class file version with a version runnable in the current JDK
   *   <li>Replaces onDraw, onMeasure and onLayout for custom views
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
  private static final Function<ClassVisitor, ClassVisitor> DEFAULT_TRANSFORMS = visitor ->
    new ViewMethodWrapperTransform(new VersionClassTransform(visitor, getCurrentClassVersion(), 0));

  private final Object myJarClassLoaderLock = new Object();
  @GuardedBy("myJarClassLoaderLock")
  private Supplier<UrlClassLoader> myJarClassLoader = Suppliers.memoize(() -> createJarClassLoader(getExternalJars()));
  protected boolean myInsideJarClassLoader;

  public RenderClassLoader(@Nullable ClassLoader parent) {
    super(parent);
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
  private UrlClassLoader createJarClassLoader(@NotNull List<URL> urls) {
    return UrlClassLoader.build()
      .parent(this)
      .urls(urls)
      .setLogErrorOnMissingJar(false)
      .get();
  }

  @NotNull
  protected Class<?> loadClassFromNonProjectDependency(@NotNull String name) throws ClassNotFoundException {
    UrlClassLoader jarClassLoaders;
    synchronized (myJarClassLoaderLock) {
      jarClassLoaders = myJarClassLoader.get();
    }

    myInsideJarClassLoader = true;
    try (InputStream is = jarClassLoaders.getResourceAsStream(name.replace('.', '/') + SdkConstants.DOT_CLASS)) {
      if (is == null) {
        throw new ClassNotFoundException(name);
      }
      byte[] data = ByteStreams.toByteArray(is);

      if (!isValidClassFile(data)) {
        throw new ClassFormatError(name);
      }
      byte[] rewritten = ClassConverter.rewriteClass(data, DEFAULT_TRANSFORMS);
      return defineClassAndPackage(name, rewritten, 0, rewritten.length);
    }
    catch (IOException | ClassNotFoundException e) {
      LOG.debug(e);
    }
    finally {
      myInsideJarClassLoader = false;
    }

    if (LOG.isDebugEnabled()) LOG.debug(String.format("ClassNotFoundException(%s)", name));
    throw new ClassNotFoundException(name);
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

  @NotNull
  protected Class<?> loadClass(@NotNull String fqcn, @NotNull byte[] data) {
    if (!isValidClassFile(data)) {
      throw new ClassFormatError(fqcn);
    }

    byte[] rewritten = ClassConverter.rewriteClass(data, DEFAULT_TRANSFORMS);
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
}
