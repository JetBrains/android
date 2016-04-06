/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.idea.gradle.util.ProxyUtil.reproxy;
import static java.util.Collections.sort;

public class NativeAndroidGradleModel implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;
  private static final Logger LOG = Logger.getInstance(NativeAndroidGradleModel.class);

  @NotNull private ProjectSystemId myProjectSystemId;
  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private NativeAndroidProject myNativeAndroidProject;
  // TODO: Serialize the model using the proxy objects to cache the model data properly.

  @Nullable private transient CountDownLatch myProxyNativeAndroidProjectLatch;
  @Nullable private NativeAndroidProject myProxyNativeAndroidProject;

  @NotNull private Map<String, NativeVariant> myVariantsByName = Maps.newHashMap();
  @NotNull private Map<String, NativeToolchain> myToolchainsByName = Maps.newHashMap();
  @NotNull private Map<String, NativeSettings> mySettingsByName = Maps.newHashMap();

  @SuppressWarnings("NullableProblems") // Set in the constructor.
  @NotNull private String mySelectedVariantName;

  @Nullable
  public static NativeAndroidGradleModel get(@NotNull Module module) {
    NativeAndroidGradleFacet facet = NativeAndroidGradleFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static NativeAndroidGradleModel get(@NotNull NativeAndroidGradleFacet androidFacet) {
    NativeAndroidGradleModel androidModel = androidFacet.getNativeAndroidGradleModel();
    if (androidModel == null) {
      return null;
    }
    return androidModel;
  }

  public NativeAndroidGradleModel(@NotNull ProjectSystemId projectSystemId,
                                  @NotNull String moduleName,
                                  @NotNull File rootDirPath,
                                  @NotNull NativeAndroidProject nativeAndroidProject) {
    myProjectSystemId = projectSystemId;
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myNativeAndroidProject = nativeAndroidProject;

    // Compute the proxy object to avoid re-proxying the model during every serialization operation and also schedule it to run
    // asynchronously to avoid blocking the project sync operation for reproxying to complete.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      myProxyNativeAndroidProjectLatch = new CountDownLatch(1);
      myProxyNativeAndroidProject = reproxy(NativeAndroidProject.class, myNativeAndroidProject);
      myProxyNativeAndroidProjectLatch.countDown();
    });

    populateVariantsByName();
    populateToolchainsByName();
    populateSettingsByName();

    initializeSelectedVariant();
  }

  private void populateVariantsByName() {
    for (NativeArtifact artifact : myNativeAndroidProject.getArtifacts()) {
      String variantName = artifact.getGroupName();
      NativeVariant variant = myVariantsByName.get(variantName);
      if (variant == null) {
        variant = new NativeVariant(variantName);
        myVariantsByName.put(variant.getName(), variant);
      }
      variant.addArtifact(artifact);
    }
    if (myVariantsByName.isEmpty()) {
      // There will mostly be at least one variant, but create a dummy variant when there are none.
      myVariantsByName.put("-----", new NativeVariant("-----"));
    }
  }

  private void populateToolchainsByName() {
    for (NativeToolchain toolchain : myNativeAndroidProject.getToolChains()) {
      myToolchainsByName.put(toolchain.getName(), toolchain);
    }
  }

  private void populateSettingsByName() {
    for (NativeSettings settings : myNativeAndroidProject.getSettings()) {
      mySettingsByName.put(settings.getName(), settings);
    }
  }

  private void initializeSelectedVariant() {
    Set<String> variantNames = myVariantsByName.keySet();
    assert !variantNames.isEmpty();

    if (variantNames.size() == 1) {
      mySelectedVariantName = Iterables.getOnlyElement(variantNames);
      return;
    }

    for (String variantName : variantNames) {
      if (variantName.equals("debug")) {
        mySelectedVariantName = variantName;
        return;
      }
    }

    List<String> sortedVariantNames = Lists.newArrayList(variantNames);
    sort(sortedVariantNames);
    assert !sortedVariantNames.isEmpty();
    mySelectedVariantName = sortedVariantNames.get(0);
  }

  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return myProjectSystemId;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  @NotNull
  public NativeAndroidProject getNativeAndroidProject() {
    return myNativeAndroidProject;
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myVariantsByName.keySet();
  }

  @NotNull
  public Collection<NativeVariant> getVariants() {
    return myVariantsByName.values();
  }

  @NotNull
  public NativeVariant getSelectedVariant() {
    NativeVariant selected = myVariantsByName.get(mySelectedVariantName);
    assert selected != null;
    return selected;
  }

  public void setSelectedVariantName(@NotNull String name) {
    Collection<String> variantNames = getVariantNames();
    if (variantNames.contains(name)) {
      mySelectedVariantName = name;
    }
    else {
      initializeSelectedVariant();
    }
  }

  @Nullable
  public NativeToolchain findToolchain(@NotNull String toolchainName) {
    return myToolchainsByName.get(toolchainName);
  }

  @Nullable
  public NativeSettings findSettings(@NotNull String settingsName) {
    return mySettingsByName.get(settingsName);
  }

  /**
   * A proxy object of the Native Android Gradle project is created and maintained for persisting the model data. The same proxy object is
   * also used to visualize the model information in {@link InternalAndroidModelView}.
   *
   * <p>If the proxy operation is still going on, this method will be blocked until that is completed.
   *
   * @return the proxy object of the imported Native Android Gradle project.
   */
  @NotNull
  public NativeAndroidProject waitForAndGetProxyAndroidProject() {
    waitForProxyAndroidProject();
    assert myProxyNativeAndroidProject != null;
    return myProxyNativeAndroidProject;
  }

  /**
   * A proxy object of the Native Android Gradle project is created and maintained for persisting the model data. The same proxy object is
   * also used to visualize the model information in {@link InternalAndroidModelView}.
   *
   * <p>This method will return immediately if the proxy operation is already completed, or will be blocked until that is completed.
   */
  public void waitForProxyAndroidProject() {
    if (myProxyNativeAndroidProjectLatch != null) {
      try {
        myProxyNativeAndroidProjectLatch.await();
      }
      catch (InterruptedException e) {
        LOG.error(e);
        Thread.currentThread().interrupt();
      }
    }
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    waitForProxyAndroidProject();

    out.writeObject(myProjectSystemId);
    out.writeObject(myModuleName);
    out.writeObject(myRootDirPath);
    out.writeObject(myProxyNativeAndroidProject);
    out.writeObject(mySelectedVariantName);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myProjectSystemId = (ProjectSystemId)in.readObject();
    myModuleName = (String)in.readObject();
    myRootDirPath = (File)in.readObject();
    myNativeAndroidProject = (NativeAndroidProject)in.readObject();
    mySelectedVariantName = (String)in.readObject();

    myProxyNativeAndroidProject = myNativeAndroidProject;

    myVariantsByName = Maps.newHashMap();
    myToolchainsByName = Maps.newHashMap();
    mySettingsByName = Maps.newHashMap();

    populateVariantsByName();
    populateToolchainsByName();
    populateSettingsByName();
  }

  public static class NativeVariant {
    @NotNull private final String myVariantName;
    @NotNull private final Map<String, NativeArtifact> myArtifactsByName = Maps.newHashMap();

    private NativeVariant(@NotNull String variantName) {
      myVariantName = variantName;
    }

    private void addArtifact(@NotNull NativeArtifact artifact) {
      myArtifactsByName.put(artifact.getName(), artifact);
    }

    @NotNull
    public String getName() {
      return myVariantName;
    }

    @NotNull
    public Collection<NativeArtifact> getArtifacts() {
      return myArtifactsByName.values();
    }

    @NotNull
    public Collection<File> getSourceFolders() {
      Set<File> sourceFolders = Sets.newLinkedHashSet();
      for (NativeArtifact artifact : getArtifacts()) {
        for (File headerRoot : artifact.getExportedHeaders()) {
          sourceFolders.add(headerRoot);
        }
        for (NativeFolder sourceFolder : artifact.getSourceFolders()) {
          sourceFolders.add(sourceFolder.getFolderPath());
        }
        for (NativeFile sourceFile : artifact.getSourceFiles()) {
          File parentFile = sourceFile.getFilePath().getParentFile();
          if (parentFile != null) {
            sourceFolders.add(parentFile);
          }
        }
      }
      return ImmutableList.copyOf(sourceFolders);
    }
  }
}
