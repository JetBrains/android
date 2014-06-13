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
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * The Project data that needs to be persisted for it to be possible to reload the Project without the need of calling Gradle to
 * regenerate this objects.
 */
public class AndroidGradleProjectData implements Serializable {

  private static final boolean ENABLED = !Boolean.getBoolean("studio.disable.synccache");

  private static final Set<Class<?>> SUPPORTED_TYPES =
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

  /**
   * Persists the gradle model of this project to disk.
   *
   * @param project the Project to get the data from.
   */
  public static void save(Project project) {
    if (!ENABLED) {
      return;
    }
    try {
      AndroidGradleProjectData data = createFrom(project);
      if (data != null) {
        File file = getProjectStateFile(project);
        file.getParentFile().mkdirs();
        data.saveTo(file);
      }
    }
    catch (IOException e) {
      LOG.info("Error while saving persistent state from project", e);
    }
  }

  @Nullable
  @VisibleForTesting
  static AndroidGradleProjectData createFrom(Project project) throws IOException {
    AndroidGradleProjectData data = new AndroidGradleProjectData();
    File rootDirPath = new File(project.getBasePath());
    final Module[] modules = ModuleManager.getInstance(project).getModules();
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
          LOG.warn("Trying to create project data from a not initialized project. Abort.");
          return null;
        }
      }

      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        IdeaGradleProject ideaGradleProject = gradleFacet.getGradleProject();
        if (ideaGradleProject != null) {
          data.addFileDependency(rootDirPath, ideaGradleProject.getBuildFile());
          moduleData.myIdeaGradleProject = new IdeaGradleProject(ideaGradleProject.getModuleName(), ideaGradleProject.getTaskNames(),
                                                                 ideaGradleProject.getGradlePath(), ideaGradleProject.getIoBuildFile());
        }
        else {
          LOG.warn("Trying to create project data from a not initialized project. Abort.");
          return null;
        }
      }
      else {
        // We assume this is the application module.
        // TODO: Once the application module has a model this needs to be updated.
        data.addFileDependency(rootDirPath, GradleUtil.getGradleBuildFile(module));
        data.addFileDependency(rootDirPath, GradleUtil.getGradleSettingsFile(rootDirPath));
      }
      data.myData.put(moduleData.myName, moduleData);
    }
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    data.myLastGradleSyncTimestamp = syncState.getLastGradleSyncTimestamp();
    return data;
  }

  private static byte[] createChecksum(@NotNull VirtualFile file) throws IOException {
    return Hashing.md5().hashBytes(file.contentsToByteArray()).asBytes();
  }

  /**
   * Loads the gradle model persisted on disk for the given project.
   *
   * @param project the project for which to load the data.
   * @return whether the load was successful.
   */
  static public boolean loadFromDisk(Project project) {
    if (!ENABLED) {
      return false;
    }
    try {
      return doLoadFromDisk(project);
    }
    catch (IOException e) {
      LOG.info("Error accessing project cache, sync will be needed.");
    }
    catch (ClassNotFoundException e) {
      LOG.info("Cannot recover cache, sync will be needed.");
    }
    return false;
  }

  static private boolean doLoadFromDisk(Project project) throws IOException, ClassNotFoundException {
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
          data.applyTo(project);
          return true;
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
    // TODO: Once there is a project level model, we can get the build directory from there. For now assume "build".
    return new File(VfsUtilCore.virtualToIoFile(project.getBaseDir()),
                    FileUtil.join(GradleUtil.BUILD_DIR_DEFAULT_NAME, AndroidProject.FD_INTERMEDIATES, "model_data.bin"));
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
          Collection<Object> newCollection = null;
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
   * Adds a dependency to the content of the given file.
   * <p/>
   * This method saves a checksum of the content of the given file along with its location. If this file's content is later found
   * to have changed, the persisted data will be considered invalid.
   *
   * @param rootDirPath the root directory.
   * @param vf          the file to add the dependency for.
   * @throws IOException if there is a problem accessing the given file.
   */
  private void addFileDependency(File rootDirPath, @Nullable VirtualFile vf) throws IOException {
    if (vf == null) {
      return;
    }

    File file = VfsUtilCore.virtualToIoFile(vf);
    String key;
    if (FileUtil.isAncestor(rootDirPath, file, true)) {
      key = FileUtil.getRelativePath(rootDirPath, file);
    }
    else {
      key = file.getAbsolutePath();
    }
    myFileChecksums.put(key, createChecksum(vf));
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
      VirtualFile file = VfsUtil.findFileByIoFile(new File(rootDir, entry.getKey()), false);
      if (file == null || !Arrays.equals(entry.getValue(), createChecksum(file))) {
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
  public void applyTo(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ModuleData data = myData.get(module.getName());

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && module.getModuleFile() != null) {
        File moduleFilePath = new File(FileUtil.toSystemDependentName(module.getModuleFile().getPath()));
        File moduleRootDirPath = moduleFilePath.getParentFile();
        IdeaAndroidProject ideaAndroidProject =
          new IdeaAndroidProject(module.getName(), moduleRootDirPath, data.myAndroidProject, data.mySelectedVariant);
        androidFacet.setIdeaAndroidProject(ideaAndroidProject);
      }

      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        gradleFacet.setGradleProject(data.myIdeaGradleProject);
      }
    }
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    syncState.setLastGradleSyncTimestamp(myLastGradleSyncTimestamp);
  }

  /**
   * Saves the data on the given project location.
   *
   * @param file the file where to save this data.
   */
  private void saveTo(File file) {
    try {
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
    catch (IOException e) {
      LOG.warn("Error while trying to save the project state.");
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
  }

  static class WrapperInvocationHandler implements InvocationHandler, Serializable {
    private final Map<String, Object> values;

    WrapperInvocationHandler(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
      return values.get(method.toGenericString());
    }
  }
}
