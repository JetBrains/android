/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install.patch;

import static com.android.tools.idea.sdk.install.patch.PatchInstallerUtil.getPatcherFile;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.lang.UrlClassLoader;
import java.awt.Component;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Studio side of the integration between studio and the IJ patcher.
 */
public class PatchRunner {
  /**
   * Runner class we'll invoke from the jar.
   */
  private static final String RUNNER_CLASS_NAME = "com.intellij.updater.Runner";

  /**
   * Interface from the patcher jar for the UI. Needed to get the update method.
   */
  private static final String UPDATER_UI_CLASS_NAME = "com.intellij.updater.UpdaterUI";

  /**
   * Interface for the actual UI class we'll create.
   */
  private static final String REPO_UI_CLASS_NAME = "com.android.tools.idea.sdk.updater.RepoUpdaterUI";

  /**
   * Class that can generate patches that this patcher can read.
   */
  private static final String PATCH_GENERATOR_CLASS_NAME = "com.android.tools.idea.sdk.updater.PatchGenerator";

  private final Class<?> myRunnerClass;
  private final Class<?> myUiBaseClass;
  private final Class<?> myUiClass;
  private final Class<?> myGeneratorClass;

  /**
   * Cache of patcher classes. Key is jar file, subkey is class name.
   */
  private static final Map<LocalPackage, PatchRunner> ourCache = new WeakHashMap<>();

  /**
   * Run the IJ patcher by reflection.
   */
  public boolean run(@NotNull Path destination, @NotNull Path patchFile, @NotNull ProgressIndicator progress)
    throws RestartRequiredException {
    Object ui;
    try {
      ui = myUiClass.getDeclaredConstructor(Component.class, ProgressIndicator.class).newInstance(null, progress);
    }
    catch (ReflectiveOperationException e) {
      progress.logWarning("Failed to create updater UI!", e);
      return false;
    }

    Method initLogger;
    try {
      initLogger = myRunnerClass.getMethod("initLogger");
      initLogger.invoke(null);
    }
    catch (ReflectiveOperationException e) {
      progress.logWarning("Failed to initialize logger!", e);
      return false;
    }

    Method doInstall;
    try {
      doInstall = myRunnerClass.getMethod("doInstall", String.class, myUiBaseClass, String.class);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to find main method in runner!", e);
      return false;
    }

    try {
      progress.logInfo("Running patcher...");
      if (!(Boolean)doInstall.invoke(null, patchFile.toString(), ui, destination.toString())) {
        progress.logWarning("Failed to apply patch");
        return false;
      }
      progress.logInfo("Patch applied.");
    }
    catch (InvocationTargetException e) {
      if (e.getCause() instanceof RestartRequiredException) {
        throw (RestartRequiredException)e.getTargetException();
      }
      progress.logWarning("Failed to run patcher", e);
      return false;
    }
    catch (Throwable e) {
      progress.logWarning("Failed to run patcher", e);
      return false;
    }

    return true;

  }

  /**
   * Generate a patch.
   *
   * @param existingRoot        The "From" (the original state of the files before the patch is applied.
   * @param newRoot             The "To" (the new state after the patch is applied).
   * @param existingDescription A user-friendly description of the existing package.
   * @param newDescription      A user-friendly description of the new package.
   * @param destination         The patch file to generate.
   * @return {@code true} if the generation succeeded.
   */
  public boolean generatePatch(@Nullable File existingRoot,
                               @NotNull File newRoot,
                               @Nullable String existingDescription,
                               @NotNull String newDescription,
                               @NotNull File destination,
                               @NotNull ProgressIndicator progress) {
    try {
      Method generateMethod = myGeneratorClass.getMethod("generateFullPackage", File.class, File.class, File.class, String.class,
                                                         String.class, ProgressIndicator.class);
      return (Boolean)generateMethod.invoke(null, newRoot, existingRoot, destination, existingDescription, newDescription, progress);
    }
    catch (NoSuchMethodException e) {
      progress.logWarning("Patcher doesn't support full package generation!");
      return false;
    }
    catch (InvocationTargetException e) {
      Throwable reason = e.getTargetException();
      progress.logWarning("Patch invocation failed! ", reason);
      return false;
    }
    catch (IllegalAccessException e) {
      progress.logWarning("Patch generation failed!");
      return false;
    }
  }

  @VisibleForTesting
  PatchRunner(@NotNull Class<?> runnerClass,
              @NotNull Class<?> uiBaseClass,
              @NotNull Class<?> uiClass,
              @NotNull Class<?> generatorClass) {
    myRunnerClass = runnerClass;
    myUiBaseClass = uiBaseClass;
    myUiClass = uiClass;
    myGeneratorClass = generatorClass;
  }

  /**
   * Class loader that prioritizes classes from the given jar.
   */
  private static class PreferentialClassLoader extends UrlClassLoader {
    public PreferentialClassLoader(Path patcherJar) {
      super(UrlClassLoader.build().files(Collections.singletonList(patcherJar)).parent(PatchRunner.class.getClassLoader()), null, false);
    }

    @Override
    public Class<?> loadClass(String name)
      throws ClassNotFoundException
    {
      synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c != null) {
          return c;
        }
        try {
          return findClass(name);
        } catch (ClassNotFoundException e) {
          return getParent().loadClass(name);
        }
      }
    }
  }

  public static class RestartRequiredException extends RuntimeException {
  }

  public interface Factory {
    @Nullable
    PatchRunner getPatchRunner(@NotNull LocalPackage runnerPackage, @NotNull ProgressIndicator progress);
  }

  public static class DefaultFactory implements Factory {
    @Override
    @Nullable
    public PatchRunner getPatchRunner(@NotNull LocalPackage runnerPackage, @NotNull ProgressIndicator progress) {
      PatchRunner result = ourCache.get(runnerPackage);
      if (result != null) {
        return result;
      }
      try {
        ClassLoader loader = getLoader(runnerPackage, progress);
        if (loader == null) return null;
        try {
          Class<?> runnerClass = Class.forName(RUNNER_CLASS_NAME, true, loader);
          Class<?> uiBaseClass = Class.forName(UPDATER_UI_CLASS_NAME, true, loader);
          Class<?> uiClass = Class.forName(REPO_UI_CLASS_NAME, true, loader);
          Class<?> generatorClass = Class.forName(PATCH_GENERATOR_CLASS_NAME, true, loader);

          result = new PatchRunner(runnerClass, uiBaseClass, uiClass, generatorClass);
          ourCache.put(runnerPackage, result);
        }
        catch (Throwable e) {
          Logger.getInstance(PatchRunner.class).warn("Patch runner failed", e);
          throw e;
        }
        return result;
      }
      catch (ClassNotFoundException e) {
        progress.logWarning("Failed to load patcher classes!");
        return null;
      }
    }
  }

  @Nullable
  static ClassLoader getLoader(@NotNull LocalPackage runnerPackage, @NotNull ProgressIndicator progress) {
    Path patcherFile = getPatcherFile(runnerPackage);
    if (patcherFile == null) {
      progress.logWarning("Failed to find patcher JAR!");
      return null;
    }
    return new PreferentialClassLoader(patcherFile);
  }
}
