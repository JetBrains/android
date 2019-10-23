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
package com.android.tools.idea.gradle.project.model;

import static java.util.Collections.sort;

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.NativeVariantInfo;
import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.IdeNativeVariantAbi;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkModuleModel implements ModuleModel {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 4L;

  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private IdeNativeAndroidProject myAndroidProject;

  @NotNull private transient NdkModelFeatures myFeatures;
  @Nullable private transient GradleVersion myModelVersion;

  @NotNull private List<IdeNativeVariantAbi> myVariantAbi = new ArrayList<>();
  // Map of all variants, key: debug-x86, value: NdkVariantName(debug, x86).
  @NotNull private Map<String, NdkVariantName> myVariantNamesByVariantAndAbiName = new HashMap<>();
  // Map of synced variants. For full-variants sync, contains all variants form myVariantNames.
  @NotNull private Map<String, NdkVariant> myVariantsByName = new HashMap<>();
  @NotNull private Map<String, NativeToolchain> myToolchainsByName = new HashMap<>();
  @NotNull private Map<String, NativeSettings> mySettingsByName = new HashMap<>();

  @SuppressWarnings("NullableProblems") // Set in the constructor.
  @NotNull private String mySelectedVariantName;

  @Nullable
  public static NdkModuleModel get(@NotNull Module module) {
    NdkFacet facet = NdkFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static NdkModuleModel get(@NotNull NdkFacet ndkFacet) {
    NdkModuleModel androidModel = ndkFacet.getNdkModuleModel();
    if (androidModel == null) {
      return null;
    }
    return androidModel;
  }

  public NdkModuleModel(@NotNull String moduleName,
                        @NotNull File rootDirPath,
                        @NotNull IdeNativeAndroidProject androidProject,
                        @NotNull List<IdeNativeVariantAbi> variantAbi) {
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myAndroidProject = androidProject;
    myVariantAbi.addAll(variantAbi);

    parseAndSetModelVersion();
    myFeatures = new NdkModelFeatures(myModelVersion);

    populateModuleFields();

    initializeSelectedVariant();
  }

  private void populateModuleFields() {
    if (myVariantAbi.isEmpty()) {
      // Full-variants sync.
      populateForFullVariantsSync();
    }
    else {
      // Single-variant sync.
      populateForSingleVariantSync();
    }

    if (myVariantsByName.isEmpty()) {
      // There will mostly be at least one variant, but create a dummy variant when there are none.
      myVariantsByName.put(DummyNdkVariant.variantNameWithAbi,
                           new NdkVariant(DummyNdkVariant.variantNameWithAbi, myFeatures.isExportedHeadersSupported()));
      myVariantNamesByVariantAndAbiName
        .put(DummyNdkVariant.variantNameWithAbi, new NdkVariantName(DummyNdkVariant.variantNameWithoutAbi, DummyNdkVariant.abiName));
    }
  }

  // Call this method for full variants sync.
  private void populateForFullVariantsSync() {
    for (NativeArtifact artifact : myAndroidProject.getArtifacts()) {
      String variantName = myFeatures.isGroupNameSupported() ? artifact.getGroupName() : artifact.getName();
      NdkVariantName ndkVariantName = new NdkVariantName(variantName, artifact.getAbi());
      NdkVariant variant = myVariantsByName.get(ndkVariantName.displayName);
      if (variant == null) {
        variant = new NdkVariant(ndkVariantName.displayName, myFeatures.isExportedHeadersSupported());
        myVariantsByName.put(ndkVariantName.displayName, variant);
        myVariantNamesByVariantAndAbiName.put(ndkVariantName.displayName, ndkVariantName);
      }
      variant.addArtifact(artifact);
    }

    // populate toolchains
    populateToolchains(myAndroidProject.getToolChains());

    // populate settings
    populateSettings(myAndroidProject.getSettings());
  }

  // Call this method for single variant sync.
  private void populateForSingleVariantSync() {
    for (Map.Entry<String, NativeVariantInfo> entry : myAndroidProject.getVariantInfos().entrySet()) {
      for (String abi : entry.getValue().getAbiNames()) {
        NdkVariantName ndkVariantName = new NdkVariantName(entry.getKey(), abi);
        myVariantNamesByVariantAndAbiName.put(ndkVariantName.displayName, ndkVariantName);
      }
    }

    for (IdeNativeVariantAbi variantAbi : myVariantAbi) {
      populateForNativeVariantAbi(variantAbi);
    }
  }

  private void populateForNativeVariantAbi(@NotNull IdeNativeVariantAbi variantAbi) {
    String variantName = getNdkVariantName(variantAbi.getVariantName(), variantAbi.getAbi());
    NdkVariant variant = new NdkVariant(variantName, myFeatures.isExportedHeadersSupported());
    for (NativeArtifact artifact : variantAbi.getArtifacts()) {
      variant.addArtifact(artifact);
    }
    myVariantsByName.put(variantName, variant);

    // populate toolchains
    populateToolchains(variantAbi.getToolChains());

    // populate settings
    populateSettings(variantAbi.getSettings());
  }

  private void populateToolchains(@NotNull Collection<NativeToolchain> nativeToolchains) {
    for (NativeToolchain toolchain : nativeToolchains) {
      myToolchainsByName.put(toolchain.getName(), toolchain);
    }
  }

  private void populateSettings(@NotNull Collection<NativeSettings> nativeSettings) {
    for (NativeSettings settings : nativeSettings) {
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
      if (variantName.equals("debug") || variantName.equals(getNdkVariantName("debug", "x86"))) {
        mySelectedVariantName = variantName;
        return;
      }
    }

    List<String> sortedVariantNames = new ArrayList<>(variantNames);
    sort(sortedVariantNames);
    assert !sortedVariantNames.isEmpty();
    mySelectedVariantName = sortedVariantNames.get(0);
  }

  /**
   * Inject the Variant-Only Sync model to existing NdkModuleModel.
   * Since the build files were not changed from last sync, only add the new VariantAbi to existing list.
   *
   * @param variantAbi The NativeVariantAbi model obtained from Variant-Only sync.
   */
  public void addVariantOnlyModuleModel(@NotNull IdeNativeVariantAbi variantAbi) {
    myVariantAbi.add(variantAbi);
    populateForNativeVariantAbi(variantAbi);
  }

  private void parseAndSetModelVersion() {
    myModelVersion = GradleVersion.tryParse(myAndroidProject.getModelVersion());
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  @NotNull
  public IdeNativeAndroidProject getAndroidProject() {
    return myAndroidProject;
  }

  /**
   * Returns a list of all NdkVariant names. For single-variant sync, some variant names may not synced.
   */
  @NotNull
  public Collection<String> getNdkVariantNames() {
    return myVariantNamesByVariantAndAbiName.keySet();
  }

  /**
   * Returns the artifact name of a given ndkVariantName, which will be used as variant name for non-native models.
   *
   * @param ndkVariantName the display name of ndk variant. For example: debug-x86.
   */
  @NotNull
  public String getVariantName(@NotNull String ndkVariantName) {
    NdkVariantName result = myVariantNamesByVariantAndAbiName.get(ndkVariantName);
    if (result == null) {
      throw new RuntimeException(String.format(
        "Variant named '%s' but only variants named '%s' were found.",
        ndkVariantName,
        Joiner.on(",").join(myVariantNamesByVariantAndAbiName.keySet())));
    }

    return myVariantNamesByVariantAndAbiName.get(ndkVariantName).variant;
  }

  /**
   * Returns the abi name of a given ndkVariantName.
   *
   * @param ndkVariantName the display name of ndk variant. For example: debug-x86.
   */
  @NotNull
  public String getAbiName(@NotNull String ndkVariantName) {
    return myVariantNamesByVariantAndAbiName.get(ndkVariantName).abi;
  }

  @NotNull
  public static String getNdkVariantName(@NotNull String variant, @NotNull String abi) {
    return variant + "-" + abi;
  }

  @NotNull
  public Collection<NdkVariant> getVariants() {
    return myVariantsByName.values();
  }

  @NotNull
  public NdkVariant getSelectedVariant() {
    NdkVariant selected = myVariantsByName.get(mySelectedVariantName);
    assert selected != null;
    return selected;
  }

  /**
   * @return true if the variant model with given name has been requested before.
   */
  public boolean variantExists(@NotNull String variantName) {
    return myVariantsByName.containsKey(variantName);
  }

  public void setSelectedVariantName(@NotNull String name) {
    // Select from synced variants.
    Collection<String> variantNames = myVariantsByName.keySet();
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

  @NotNull
  public NdkModelFeatures getFeatures() {
    return myFeatures;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(myModuleName);
    out.writeObject(myRootDirPath);
    out.writeObject(myAndroidProject);
    out.writeObject(mySelectedVariantName);
    out.writeObject(myVariantAbi);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myModuleName = (String)in.readObject();
    myRootDirPath = (File)in.readObject();
    myAndroidProject = (IdeNativeAndroidProject)in.readObject();
    mySelectedVariantName = (String)in.readObject();
    //noinspection unchecked
    myVariantAbi = (List<IdeNativeVariantAbi>)in.readObject();

    parseAndSetModelVersion();
    myFeatures = new NdkModelFeatures(myModelVersion);

    myVariantNamesByVariantAndAbiName = new HashMap<>();
    myVariantsByName = new HashMap<>();
    myToolchainsByName = new HashMap<>();
    mySettingsByName = new HashMap<>();

    populateModuleFields();
  }

  @Override
  public int hashCode() {
    // Hashcode should consist of what's written in writeObject. Everything else is derived from these so those don't matter wrt to
    // identity.
    return Objects.hash(
      myModuleName,
      myRootDirPath,
      myAndroidProject,
      mySelectedVariantName,
      myVariantAbi);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof NdkModuleModel)) {
      return false;
    }
    NdkModuleModel that = (NdkModuleModel)obj;
    if (!Objects.equals(this.myModuleName, that.myModuleName)) {
      return false;
    }
    if (!Objects.equals(this.myRootDirPath, that.myRootDirPath)) {
      return false;
    }
    if (!Objects.equals(this.myAndroidProject, that.myAndroidProject)) {
      return false;
    }
    if (!Objects.equals(this.mySelectedVariantName, that.mySelectedVariantName)) {
      return false;
    }
    if (!Objects.equals(this.myVariantAbi, that.myVariantAbi)) {
      return false;
    }

    return true;
  }

  // If there are no real NDK variants (e.g., there are no artifacts to deduce variants from), a dummy variant will
  // be created.
  public static class DummyNdkVariant {
    private static final String variantNameWithoutAbi = "---";
    private static final String abiName = "--";
    public static final String variantNameWithAbi = variantNameWithoutAbi + "-" + abiName;
  }
}
