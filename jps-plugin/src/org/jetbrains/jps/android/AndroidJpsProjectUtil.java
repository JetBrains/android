package org.jetbrains.jps.android;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;

/**
 * This class contains methods moved from {@link AndroidJpsUtil} to ensure that they can be compiled and run under Java 6. This is a
 * temporary solution to support building non-Android projects using Java 6.
 *
 * @author nik
 */
public class AndroidJpsProjectUtil {
  @NonNls public static final String ANDROID_STORAGE_DIR = "android";
  @NonNls public static final String GENERATED_RESOURCES_DIR_NAME = "generated_resources";
  @NonNls private static final String GENERATED_SOURCES_FOLDER_NAME = "generated_sources";
  @NonNls private static final String COPIED_SOURCES_FOLDER_NAME = "copied_sources";

  static JpsAndroidModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsAndroidModuleExtensionImpl.KIND);
  }

  /**
   * Indicates whether the given project is a non-Gradle Android project.
   *
   * @param project the given project.
   * @return {@code true} if the the given project is a non-Gradle Android project, {@code false} otherwise.
   */
  public static boolean isAndroidProjectWithoutGradleFacet(@NotNull JpsProject project) {
    return isAndroidProjectWithoutGradleFacet(project.getModules());
  }

  /**
   * Indicates whether the given modules belong to a non-Gradle Android project.
   *
   * @param chunk the given modules.
   * @return {@code true} if the the given modules belong to a non-Gradle Android project, {@code false} otherwise.
   */
  public static boolean isAndroidProjectWithoutGradleFacet(@NotNull ModuleChunk chunk) {
    return isAndroidProjectWithoutGradleFacet(chunk.getModules());
  }

  private static boolean isAndroidProjectWithoutGradleFacet(@NotNull Collection<JpsModule> modules) {
    boolean hasAndroidFacet = false;
    for (JpsModule module : modules) {
      JpsAndroidModuleExtension androidFacet = getExtension(module);
      if (androidFacet != null) {
        hasAndroidFacet = true;
        if (androidFacet.isGradleProject()) {
          return false;
        }
      }
    }
    return hasAndroidFacet;
  }

  @NotNull
  public static File getGeneratedResourcesStorage(@NotNull JpsModule module, BuildDataManager dataManager) {
    return getGeneratedResourcesStorage(module, dataManager.getDataPaths());
  }

  @NotNull
  static File getGeneratedResourcesStorage(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File targetDataRoot = dataPaths.getTargetDataRoot(
      new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    return getStorageDir(targetDataRoot, GENERATED_RESOURCES_DIR_NAME);
  }

  @NotNull
  public static File getStorageFile(@NotNull File dataStorageRoot, @NotNull String storageName) {
    return new File(getStorageDir(dataStorageRoot, storageName), storageName);
  }

  @NotNull
  public static File getStorageDir(@NotNull File dataStorageRoot, @NotNull String storageName) {
    return new File(new File(dataStorageRoot, ANDROID_STORAGE_DIR), storageName);
  }

  @NotNull
  public static File getGeneratedSourcesStorage(@NotNull JpsModule module, final BuildDataPaths dataPaths) {
    final File targetDataRoot = dataPaths.getTargetDataRoot(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    return getStorageDir(targetDataRoot, GENERATED_SOURCES_FOLDER_NAME);
  }

  @NotNull
  public static File getCopiedSourcesStorage(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File targetDataRoot = dataPaths.getTargetDataRoot(
      new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    return getStorageDir(targetDataRoot, COPIED_SOURCES_FOLDER_NAME);
  }
}
