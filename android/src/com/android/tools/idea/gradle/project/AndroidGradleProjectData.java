/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.JavaModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;

/**
 * The Project data that needs to be persisted for it to be possible to reload the Project without the need of calling Gradle to
 * regenerate this objects.
 */
public class AndroidGradleProjectData implements Serializable {
  @NotNull @NonNls private static final String STATE_FILE_NAME = "model_data.bin";
  private static final boolean ENABLED = !Boolean.getBoolean("studio.disable.synccache");

  @SuppressWarnings("unchecked") private static final Set<Class<?>> SUPPORTED_TYPES =
    ImmutableSet.of(File.class, Boolean.class, String.class, Collection.class, Map.class, Set.class);

  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectData.class);

  /**
   * A map from module name to its data
   */
  private Map<String, ModuleData> myData = Maps.newHashMap();

  /**
   * A set of files and their MD5 that this data depends on.
   */
  private Map<String, byte[]> myFileChecksums = Maps.newHashMap();

  /**
   * The model version
   */
  private String myGradlePluginVersion = SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;

  /**
   * The last time a sync was done.
   */
  private long myLastGradleSyncTimestamp = -1L;

  private AndroidGradleProjectData() {
  }

  public static void removeFrom(@NotNull Project project) {
    if (!ENABLED) {
      return;
    }
    try {
      File stateFile = getProjectStateFile(project);
      if (stateFile.isFile()) {
        FileUtil.delete(stateFile);
      }
    }
    catch (IOException e) {
      LOG.warn(String.format("Failed to remove state for project %1$s'", project.getName()));
    }
  }

  /**
   * Persists the gradle model of this project to disk.
   *
   * @param project the project to get the data from.
   */
  public static void save(@NotNull Project project) {
    if (!ENABLED) {
      return;
    }
    try {
      AndroidGradleProjectData data = createFrom(project);
      if (data != null) {
        File file = getProjectStateFile(project);
        FileUtil.ensureExists(file.getParentFile());
        data.saveTo(file);
      }
    }
    catch (IOException e) {
      LOG.info(String.format("Error while saving persistent state from project '%1$s'", project.getName()), e);
    }
  }

  @Nullable
  @VisibleForTesting
  static AndroidGradleProjectData createFrom(@NotNull Project project) throws IOException {
    AndroidGradleProjectData data = new AndroidGradleProjectData();
    File rootDirPath = new File(project.getBasePath());
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ModuleData moduleData = new ModuleData();

      moduleData.myName = module.getName();

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        IdeaAndroidProject ideaAndroidProject = androidFacet.getIdeaAndroidProject();
        if (ideaAndroidProject != null) {
          moduleData.myAndroidProject = reproxy(AndroidProject.class, ideaAndroidProject.getDelegate());
          moduleData.mySelectedVariant = ideaAndroidProject.getSelectedVariant().getName();
        }
        else {
          LOG.warn(String.format("Trying to create project data from a not initialized project '%1$s'. Abort.", project.getName()));
          return null;
        }
      }

      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        IdeaGradleProject ideaGradleProject = gradleFacet.getGradleProject();
        if (ideaGradleProject != null) {
          data.addFileDependency(rootDirPath, ideaGradleProject.getBuildFile());
          moduleData.myIdeaGradleProject = ideaGradleProject;
        }
        else {
          LOG.warn(String.format("Trying to create project data from a not initialized project '%1$s'. Abort.", project.getName()));
          return null;
        }
      }

      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null) {
        moduleData.myJavaModel = javaFacet.getJavaModel();
      }

      if (Projects.isGradleProjectModule(module)) {
        data.addFileDependency(rootDirPath, GradleUtil.getGradleBuildFile(module));
        data.addFileDependency(rootDirPath, GradleUtil.getGradleSettingsFile(rootDirPath));
        data.addFileDependency(rootDirPath, new File(rootDirPath, SdkConstants.FN_GRADLE_PROPERTIES));
        data.addFileDependency(rootDirPath, new File(rootDirPath, SdkConstants.FN_LOCAL_PROPERTIES));
        data.addFileDependency(rootDirPath, getGradleUserSettingsFile());
      }

      data.myData.put(moduleData.myName, moduleData);
    }
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    data.myLastGradleSyncTimestamp = syncState.getLastGradleSyncTimestamp();
    return data;
  }

  @Nullable
  private static File getGradleUserSettingsFile() {
    String homePath = System.getProperty("user.home");
    if (homePath == null) {
      return null;
    }
    return new File(homePath, FileUtil.join(SdkConstants.DOT_GRADLE, SdkConstants.FN_GRADLE_PROPERTIES));
  }

  @NotNull
  private static byte[] createChecksum(@NotNull File file) throws IOException {
    // For files tracked by the IDE we get the content from the virtual files, otherwise we revert to io.
    VirtualFile vf = VfsUtil.findFileByIoFile(file, true);
    byte[] data = new byte[] {};
    if (vf != null) {
      vf.refresh(false, false);
      if (vf.exists()) {
        data = vf.contentsToByteArray();
      }
    } else if (file.exists()) {
      data = Files.toByteArray(file);
    }
    return Hashing.md5().hashBytes(data).asBytes();
  }

  /**
   * Loads the gradle model persisted on disk for the given project.
   *
   * @param project the project for which to load the data.
   * @return whether the load was successful.
   */
  public static boolean loadFromDisk(@NotNull Project project) {
    if (!ENABLED || needsAndroidSdkSync(project)) {
      return false;
    }
    try {
      return doLoadFromDisk(project);
    }
    catch (IOException e) {
      LOG.info(String.format("Error accessing state cache for project '%1$s', sync will be needed.", project.getName()));
    }
    catch (ClassNotFoundException e) {
      LOG.info(String.format("Cannot recover state cache for project '%1$s', sync will be needed.", project.getName()));
    }
    return false;
  }

  private static boolean needsAndroidSdkSync(@NotNull Project project) {
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      final File ideSdkPath = DefaultSdks.getDefaultAndroidHome();
      if (ideSdkPath != null) {
        if (needsLPreviewPlatformReset()) {
          // reset the Android SDK home to force recreation of IDEA SDKs.
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              DefaultSdks.setDefaultAndroidHome(ideSdkPath, DefaultSdks.getDefaultJdk());
            }
          });
          return true;
        }
        try {
          LocalProperties localProperties = new LocalProperties(project);
          File projectSdkPath = localProperties.getAndroidSdkPath();
          return projectSdkPath == null || !FileUtil.filesEqual(ideSdkPath, projectSdkPath);
        }
        catch (IOException ignored) {
        }
      }
      return true;
    }
    return false;
  }

  private static boolean needsLPreviewPlatformReset() {
    // Repair SDK for 'android-L'. See: https://code.google.com/p/android/issues/detail?id=72589
    // TODO: remove this at some point (it's only there to upgrade user settings for people who used 0.8.0 and 0.8.1 with 20 and 21
    // installed simultaneously)
    for (Sdk sdk : DefaultSdks.getEligibleAndroidSdks()) {
      SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
      if (additionalData instanceof AndroidSdkAdditionalData) {
        AndroidPlatform androidPlatform = ((AndroidSdkAdditionalData)additionalData).getAndroidPlatform();
        if (androidPlatform != null) {
          IAndroidTarget target = androidPlatform.getTarget();
          AndroidVersion version = target.getVersion();
          if ("L".equals(version.getApiString()) && version.getApiLevel() == 20 && version.isPreview()) {
            // This is "android-L"
            String androidJarPath = target.getPath(IAndroidTarget.ANDROID_JAR);
            File expectedPath = new File(androidJarPath);
            VirtualFile[] libraryFiles = sdk.getRootProvider().getFiles(CLASSES);
            for (VirtualFile libraryFile : libraryFiles) {
              // Match the expected path of android.jar vs. the actual path. The expected path is the one coming from SDK Manager, while
              // the actual path is the one in the IDEA SDK.
              if (FN_FRAMEWORK_LIBRARY.equals(libraryFile.getName())) {
                File actualPath = VfsUtilCore.virtualToIoFile(libraryFile);
                return !FileUtil.filesEqual(expectedPath, actualPath);
              }
            }
            // android.jar was never found.
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean doLoadFromDisk(@NotNull Project project) throws IOException, ClassNotFoundException {
    FileInputStream fin = null;
    try {
      File rootDirPath = new File(FileUtil.toSystemDependentName(project.getBasePath()));
      File dataFile = getProjectStateFile(project);
      if (!dataFile.exists()) {
        return false;
      }
      fin = new FileInputStream(dataFile);
      ObjectInputStream ois = new ObjectInputStream(fin);
      try {
        AndroidGradleProjectData data = (AndroidGradleProjectData)ois.readObject();
        if (data.validate(rootDirPath)) {
          if (data.applyTo(project)) {
            PostProjectSetupTasksExecutor.getInstance(project).onProjectRestoreFromDisk();
            return true;
          }
        }
      }
      finally {
        Closeables.close(ois, false);
      }
    }
    finally {
      Closeables.close(fin, false);
    }
    return false;
  }

  @NotNull
  private static File getProjectStateFile(@NotNull Project project) throws IOException {
    Module projectModule = Projects.findGradleProjectModule(project);
    if (projectModule != null) {
      File buildFolderPath = Projects.getBuildFolderPath(projectModule);
      if (buildFolderPath != null) {
        return new File(buildFolderPath, FileUtil.join(AndroidProject.FD_INTERMEDIATES, STATE_FILE_NAME));
      }
    }
    // TODO: Once we upgrade to Gradle 2.0, we can get the build directory from there. For now assume "build".
    return new File(VfsUtilCore.virtualToIoFile(project.getBaseDir()),
                    FileUtil.join(GradleUtil.BUILD_DIR_DEFAULT_NAME, AndroidProject.FD_INTERMEDIATES, STATE_FILE_NAME));
  }

  /**
   * Regenerate proxy objects with a serializable version of a proxy.
   * This method is intended to be run on objects that are a bag of properties, particularly custom Gradle model objects.
   * Here we assume that the given object can be represented as a map of method name to return value. The original object
   * is regenerated using this assumption which gives a serializable/deserializable object.
   *
   * @param object the object to 'reproxy'.
   * @param type   the runtime type of the object. This is the expected type of object, and must be a superclass or equals to T.
   * @param <T>    the type of the object.
   * @return the reproxied object.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  @VisibleForTesting
  static <T> T reproxy(Type type, T object) {
    if (object == null) {
      return null;
    }

    if (type instanceof ParameterizedType) {
      ParameterizedType genericType = (ParameterizedType)type;
      if (genericType.getRawType() instanceof Class) {
        Class<?> genericClass = (Class<?>)genericType.getRawType();
        if (Collection.class.isAssignableFrom(genericClass)) {
          Collection<Object> collection = (Collection<Object>)object;
          Collection<Object> newCollection;
          if (genericClass.isAssignableFrom(ArrayList.class)) {
            newCollection = Lists.newArrayListWithCapacity(collection.size());
          }
          else if (genericClass.isAssignableFrom(Set.class)) {
            newCollection = Sets.newLinkedHashSet();
          }
          else {
            throw new IllegalStateException("Unsupported collection type: " + genericClass.getCanonicalName());
          }
          Type argument = genericType.getActualTypeArguments()[0];
          for (Object item : collection) {
            newCollection.add(reproxy(argument, item));
          }
          return (T)newCollection;
        }
        else if (Map.class.isAssignableFrom(genericClass)) {
          Map<Object, Object> map = (Map<Object, Object>)object;
          Map<Object, Object> newMap = Maps.newLinkedHashMap();
          Type keyType = genericType.getActualTypeArguments()[0];
          Type valueType = genericType.getActualTypeArguments()[1];
          for (Map.Entry entry : map.entrySet()) {
            newMap.put(reproxy(keyType, entry.getKey()), reproxy(valueType, entry.getValue()));
          }
          return (T)newMap;
        }
        else {
          throw new IllegalStateException("Unsupported generic type: " + genericClass.getCanonicalName());
        }
      }
      else {
        throw new IllegalStateException("Unsupported raw type.");
      }
    }

    // Only modify proxy objects...
    if (!Proxy.isProxyClass(object.getClass())) {
      return object;
    }

    // ...that are not our own proxy.
    if (Proxy.getInvocationHandler(object) instanceof WrapperInvocationHandler) {
      return object;
    }

    Class<?>[] interfaces = object.getClass().getInterfaces();
    if (interfaces.length != 1) {
      throw new IllegalStateException("Cannot 'reproxy' a class with multiple interfaces");
    }
    Class<?> clazz = interfaces[0];

    final Map<String, Object> values = Maps.newHashMap();
    for (Method m : clazz.getMethods()) {
      try {
        if (Modifier.isPublic(m.getModifiers())) {
          values.put(m.toGenericString(), reproxy(m.getGenericReturnType(), m.invoke(object)));
        }
      }
      catch (IllegalAccessException e) {
        throw new IllegalStateException("A non public method shouldn't have been called.", e);
      }
      catch (InvocationTargetException e) {
        throw new IllegalStateException("Invalid method receiver.", e);
      }
    }
    return (T)Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new WrapperInvocationHandler(values));
  }

  @VisibleForTesting
  static boolean isSupported(@NotNull Class<?> clazz) {
    return clazz.isPrimitive() || SUPPORTED_TYPES.contains(clazz);
  }

  /**
   * Adds a dependency to the content of the given virtual file. @see addFileDependency(File, File)
   */
  private void addFileDependency(File rootDirPath, @Nullable VirtualFile vf) throws IOException {
    addFileDependency(rootDirPath, vf != null ? VfsUtilCore.virtualToIoFile(vf) : null);
  }

  /**
   * Adds a dependency to the content of the given file.
   * <p/>
   * This method saves a checksum of the content of the given file along with its location. If this file's content is later found
   * to have changed, the persisted data will be considered invalid.
   *
   * @param rootDirPath the root directory.
   * @param file        the file to add the dependency for.
   * @throws IOException if there is a problem accessing the given file.
   */
  private void addFileDependency(File rootDirPath, @Nullable File file) throws IOException {
    if (file == null) {
      return;
    }
    String key;
    if (FileUtil.isAncestor(rootDirPath, file, true)) {
      key = FileUtil.getRelativePath(rootDirPath, file);
    }
    else {
      key = file.getAbsolutePath();
    }
    myFileChecksums.put(key, createChecksum(file));
  }

  /**
   * Validates that the received data can be applied to the project at rootDir.
   * <p/>
   * This validates that all the files this model depends on, still have the same content checksum and that the gradle model version
   * is still the same.
   *
   * @param rootDir the root directory where to find the files.
   * @return whether the data is still valid.
   * @throws IOException if there is a problem accessing these files.
   */
  private boolean validate(@NotNull File rootDir) throws IOException {
    if (!myGradlePluginVersion.equals(SdkConstants.GRADLE_PLUGIN_LATEST_VERSION)) {
      return false;
    }

    for (Map.Entry<String, byte[]> entry : myFileChecksums.entrySet()) {
      File file = new File(entry.getKey());
      if (!file.isAbsolute()) {
        file = new File(rootDir, file.getPath());
      }
      if (!Arrays.equals(entry.getValue(), createChecksum(file))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Applies this data to the given project.
   *
   * @param project the project to apply the data to.
   */
  @VisibleForTesting
  public boolean applyTo(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ModuleData data = myData.get(module.getName());
      // If no data is found, the cache doesn't match the project structure and we should resync.
      if (data == null) {
        return false;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      String moduleFilePath = module.getModuleFilePath(); // System dependent absolute path.
      if (androidFacet != null) {
        if (data.myAndroidProject != null) {
          File moduleFile = new File(moduleFilePath);
          assert moduleFile.getParent() != null : moduleFile.getPath();
          File moduleRootDirPath = moduleFile.getParentFile();
          IdeaAndroidProject ideaAndroidProject =
            new IdeaAndroidProject(module.getName(), moduleRootDirPath, data.myAndroidProject, data.mySelectedVariant);
          androidFacet.setIdeaAndroidProject(ideaAndroidProject);
        }
        else {
          return false;
        }
      }

      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        gradleFacet.setGradleProject(data.myIdeaGradleProject);
      }

      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null && data.myJavaModel != null) {
        javaFacet.setJavaModel(data.myJavaModel);
      }
    }
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    syncState.setLastGradleSyncTimestamp(myLastGradleSyncTimestamp);

    return true;
  }

  /**
   * Saves the data on the given project location.
   *
   * @param file the file where to save this data.
   */
  private void saveTo(File file) throws IOException {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(file);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      try {
        oos.writeObject(this);
      }
      finally {
        Closeables.close(oos, false);
      }
    }
    finally {
      Closeables.close(fos, false);
    }
  }

  @VisibleForTesting
  Map<String, ModuleData> getModuleData() {
    return myData;
  }

  @VisibleForTesting
  Map<String, byte[]> getFileChecksums() {
    return myFileChecksums;
  }

  /**
   * The persistent data to store per project Module.
   */
  static class ModuleData implements Serializable {
    public String myName;
    public IdeaGradleProject myIdeaGradleProject;
    public AndroidProject myAndroidProject;
    public String mySelectedVariant;
    public JavaModel myJavaModel;
  }

  static class WrapperInvocationHandler implements InvocationHandler, Serializable {
    private static final Method TO_STRING = getObjectMethod("toString");
    private static final Method HASHCODE = getObjectMethod("hashCode");
    private static final Method EQUALS = getObjectMethod("equals", Object.class);
    private final Map<String, Object> values;

    WrapperInvocationHandler(@NotNull Map<String, Object> values) {
      this.values = values;
    }

    @NotNull
    private static Method getObjectMethod(@NotNull String name, @NotNull Class<?>... types) {
      try {
        return Object.class.getMethod(name, types);
      }
      catch (NoSuchMethodException e) {
        throw new IllegalStateException("Method should exist in Object", e);
      }
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
      if (method.equals(TO_STRING)) {
        return method.invoke(this, objects);
      }
      else if (method.equals(HASHCODE)) {
        return method.invoke(this, objects);
      }
      else if (method.equals(EQUALS)) {
        return proxyEquals(objects[0]);
      }
      else {
        String key = method.toGenericString();
        if (!values.containsKey(key)) {
          LOG.warn("Invoking a non-existent reproxy method: " + key);
        }
        return values.get(key);
      }
    }

    private boolean proxyEquals(Object other) {
      return other != null && Proxy.isProxyClass(other.getClass()) && Proxy.getInvocationHandler(other).equals(this);
    }
  }
}
