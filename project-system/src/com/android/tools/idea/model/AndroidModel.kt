/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.sdklib.devices.Abi;
import com.android.tools.lint.detector.api.Desugaring;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
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
  @NotNull
  AndroidVersion getMinSdkVersion();

  /**
   * @return the {@code minSdkVersion} that we pass to the runtime. This is normally the same as {@link #getMinSdkVersion()}, but with
   * "preview" platforms the {@code minSdkVersion}, {@code targetSdkVersion} and {@code compileSdkVersion} are all coerced to the same
   * "preview" platform value. This method should be used by launch code for example or packaging code.
   */
  @NotNull
  AndroidVersion getRuntimeMinSdkVersion();

  /**
   * @return the target SDK version.
   * {@link AndroidModuleInfo#getTargetSdkVersion()}
   */
  @Nullable
  AndroidVersion getTargetSdkVersion();

  default @NotNull EnumSet<Abi> getSupportedAbis() { return EnumSet.allOf(Abi.class); }

  @NotNull
  Namespacing getNamespacing();

  /** @return the set of desugaring capabilities of the build system in use. */
  @NotNull
  Set<Desugaring> getDesugaring();

  /**
   * @return the set of optional lint rule jars that override lint jars collected from lint model. It provides an easy to return lint rule
   * jars without creating lint model implementation. Normally null for gradle project.
   */
  @Nullable
  default Iterable<File> getLintRuleJarsOverride() { return null; }

  /**
   * Returns the set of build-system-provided resource values and overrides.
   */
  @NotNull
  default Map<String, DynamicResourceValue> getResValues() {
    return Collections.emptyMap();
  }

  @NotNull
  default TestOptions getTestOptions() { return TestOptions.DEFAULT; }

  @Nullable
  default TestExecutionOption getTestExecutionOption() {
    return getTestOptions().getExecutionOption();
  }

  /**
   * Returns the resource prefix to use, if any. This is an optional prefix which can be set and
   * which is used by the defaults to automatically choose new resources with a certain prefix,
   * warn if resources are not using the given prefix, etc. This helps work with resources in the
   * app namespace where there could otherwise be unintentional duplicated resource names between
   * unrelated libraries.
   *
   * @return the optional resource prefix, or null if not set
   */
  @Nullable
  default String getResourcePrefix() { return null; }

  /**
   * Returns true if this is the base feature split.
   */
  default boolean isBaseSplit() { return false; }

  /**
   * Returns true if this variant is instant app compatible, intended to be possibly built and
   * served in an instant app context. This is populated during sync from the project's manifest.
   * Only application modules and dynamic feature modules will set this property.
   *
   * @return true if this variant is instant app compatible
   * @since 3.3
   */
  default boolean isInstantAppCompatible() { return false; }
}
