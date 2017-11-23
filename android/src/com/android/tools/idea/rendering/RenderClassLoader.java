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
package com.android.tools.idea.rendering;

import com.google.common.io.ByteStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.tools.idea.rendering.ClassConverter.isValidClassFile;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;

/**
 * Class loader which can load classes for rendering and if necessary
 * convert the class file format down from unknown versions to a known version.
 */
public abstract class RenderClassLoader extends ClassLoader {
  protected static final Logger LOG = Logger.getInstance(RenderClassLoader.class);

  // By default we do not use preload or use the cache but we will offer this fields to debug cases of uses where the
  // disk I/O is very slow. We could try these flags and see if it helps.
  private static boolean USE_PRELOAD = Boolean.getBoolean("render.class.loader.preload");
  private static boolean USE_CACHE = Boolean.getBoolean("render.class.loader.cache");

  protected UrlClassLoader myJarClassLoader;
  protected boolean myInsideJarClassLoader;
  protected final int myLayoutlibApiLevel;

  public RenderClassLoader(@Nullable ClassLoader parent, int layoutlibApiLevel) {
    super(parent);
    myLayoutlibApiLevel = layoutlibApiLevel;
  }

  protected abstract List<URL> getExternalJars();

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    return load(name);
  }

  @NotNull
  protected Class<?> load(String name) throws ClassNotFoundException {
    Class<?> clz = loadClassFromJar(name);
    if (clz != null) {
      return clz;
    }

    throw new ClassNotFoundException(name);
  }

  @Nullable
  protected Class<?> loadClassFromJar(@NotNull String name) {
    if (myJarClassLoader == null) {
      final List<URL> externalJars = getExternalJars();
      myJarClassLoader = createClassLoader(externalJars);
    }

    try {
      myInsideJarClassLoader = true;
      String relative = name.replace('.', '/').concat(DOT_CLASS);
      InputStream is = myJarClassLoader.getResourceAsStream(relative);
      if (is != null) {
        byte[] data = ByteStreams.toByteArray(is);
        is.close();
        if (!isValidClassFile(data)) {
          throw new ClassFormatError(name);
        }

        byte[] rewritten = convertClass(data);
        try {
          if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Defining class '%s' from .jar file", anonymizeClassName(name)));
          }
          return defineClassAndPackage(name, rewritten, 0, rewritten.length);
        }
        catch (UnsupportedClassVersionError inner) {
          LOG.debug(inner);
          // Wrap the UnsupportedClassVersionError as a InconvertibleClassError
          // such that clients can look up the actual bytecode version required.
          throw InconvertibleClassError.wrap(inner, name, data);
        }
      }
      return null;
    } catch (IOException ex) {
      LOG.debug(ex);
      throw new Error("Failed to load class " + name, ex);
    }
    finally {
      myInsideJarClassLoader = false;
    }
  }

  protected UrlClassLoader createClassLoader(List<URL> externalJars) {
    UrlClassLoader.Builder builder = UrlClassLoader.build()
      .parent(this)
      .urls(externalJars);

    if (LOG.isDebugEnabled()) {
      LOG.debug("usePreload = " + USE_PRELOAD);
      LOG.debug("useCache = " + USE_CACHE);
    }
    if (!USE_PRELOAD) {
      builder.noPreload();
    }

    if (USE_CACHE) {
      builder.useCache();
    }

    try {
      // The setLogErrorOnMissingJar was added in Android Studio. We need to call it via reflection until the
      // change gets upstreamed.
      Method setLogErrorOnMissingJar = UrlClassLoader.Builder.class.getMethod("setLogErrorOnMissingJar", boolean.class);
      setLogErrorOnMissingJar.invoke(builder, false);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
    }

    return builder.get();
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

  @Nullable
  protected Class<?> loadClass(@NotNull String fqcn, @Nullable byte[] data) {
    if (data == null) {
      return null;
    }

    if (!isValidClassFile(data)) {
      throw new ClassFormatError(fqcn);
    }

    byte[] rewritten = convertClass(data);
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
  protected byte[] convertClass(@NotNull byte[] data) {
    return ClassConverter.rewriteClass(data);
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
}
