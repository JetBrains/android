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
package com.android.tools.idea.model;

import com.android.projectmodel.DynamicResourceValue;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.detector.api.Desugaring;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A common interface for Android module models.
 */
public interface AndroidModel {

  String UNINITIALIZED_APPLICATION_ID = "uninitialized.application.id";

  Key<AndroidModel> KEY = Key.create(AndroidModel.class.getName());

  @Nullable
  static AndroidModel get(@NotNull AndroidFacet facet) {
    return facet.getUserData(KEY);
  }

  @Nullable
  static AndroidModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : get(facet);
  }

  /**
   * Sets the model used by this AndroidFacet. This method is meant to be called from build-system specific code that sets up the project
   * during sync.
   *
   * <p>NOTE: Please consider using {@link AndroidProjectRule#withAndroidModel()} or similar methods to configure a test project before using
   * this method.
   */
  static void set(@NotNull AndroidFacet facet, @Nullable AndroidModel androidModel) {
    facet.putUserData(KEY, androidModel);
    facet.getModule().getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
  }

  /**
   * Returns {@code true} if {@code facet} has been configured from and is kept in sync with an external model of the project.
   */
  static boolean isRequired(@NotNull AndroidFacet facet) {
    //noinspection deprecation  This is one of legitimate usages of this property.
    return !facet.getProperties().ALLOW_USER_CONFIGURATION;
  }

  /**
   * @return the current application ID.
   *
   * NOTE: Some implementations may return {@link #UNINITIALIZED_APPLICATION_ID} when unable to get the application id.
   */
  @NotNull
  String getApplicationId();

  /**
   * @return all the application IDs of artifacts this Android module could produce.
   */
  @NotNull
  Set<String> getAllApplicationIds();

  /**
   * @return whether the manifest package is overridden.
   * TODO: Potentially dedupe with computePackageName.
   */
  boolean overridesManifestPackage();

  /**
   * @return whether the application is debuggable, or {@code null} if not specified.
   */
  Boolean isDebuggable();

  /**
   * @return the minimum supported SDK version.
   * {@link AndroidModuleInfo#getMinSdkVersion()}
   */
  @Nullable
  AndroidVersion getMinSdkVersion();

  /**
   * @return the {@code minSdkVersion} that we pass to the runtime. This is normally the same as {@link #getMinSdkVersion()}, but with
   * "preview" platforms the {@code minSdkVersion}, {@code targetSdkVersion} and {@code compileSdkVersion} are all coerced to the same
   * "preview" platform value. This method should be used by launch code for example or packaging code.
   */
  @Nullable
  AndroidVersion getRuntimeMinSdkVersion();

  /**
   * @return the target SDK version.
   * {@link AndroidModuleInfo#getTargetSdkVersion()}
   */
  @Nullable
  AndroidVersion getTargetSdkVersion();

  /**
   * Indicates whether the given file or directory is generated.
   *
   * @param file the file or directory.
   * @return {@code true} if the given file or directory is generated; {@code false} otherwise.
   */
  boolean isGenerated(@NotNull VirtualFile file);

  /**
   * @return A provider for finding .class output files and external .jars.
   */
  @NotNull
  ClassJarProvider getClassJarProvider();

  /**
   * @return Whether the class specified by fqcn is out of date and needs to be rebuilt.
   * <p>
   * NOTE: Implementations are not necessarily able to detect all the cases when the file is out of date. Therefore, {@code false} should
   *       be interpreted as meaning "not known".
   */
  boolean isClassFileOutOfDate(@NotNull Module module, @NotNull String fqcn, @NotNull VirtualFile classFile);

  @NotNull
  Namespacing getNamespacing();

  /** @return the set of desugaring capabilities of the build system in use. */
  @NotNull
  Set<Desugaring> getDesugaring();

  /**
   * Returns the set of build-system-provided resource values and overrides.
   */
  @NotNull
  default Map<String, DynamicResourceValue> getResValues() {
    return Collections.emptyMap();
  }

  @Nullable
  default TestExecutionOption getTestExecutionOption() { return null; }
}
