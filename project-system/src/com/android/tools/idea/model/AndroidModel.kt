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
package com.android.tools.idea.model

import com.android.projectmodel.DynamicResourceValue
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.lint.detector.api.Desugaring
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import java.io.File
import java.util.EnumSet
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/**
 * A common interface for Android module models.
 */
interface AndroidModel {
  /**
   * @return the current application ID.
   *
   * NOTE: Some implementations may return [.UNINITIALIZED_APPLICATION_ID] when unable to get the application id.
   */
  val applicationId: String

  /**
   * @return all the application IDs of artifacts this Android module could produce.
   */
  val allApplicationIds: Set<String>

  /**
   * @return whether the manifest package is overridden.
   * TODO: Potentially dedupe with computePackageName.
   */
  fun overridesManifestPackage(): Boolean

  /**
   * @return whether the application is debuggable, or `null` if not specified.
   */
  val isDebuggable: Boolean

  /**
   * @return the minimum supported SDK version.
   * [AndroidModuleInfo.getMinSdkVersion]
   */
  val minSdkVersion: AndroidVersion

  /**
   * @return the `minSdkVersion` that we pass to the runtime. This is normally the same as [.getMinSdkVersion], but with
   * "preview" platforms the `minSdkVersion`, `targetSdkVersion` and `compileSdkVersion` are all coerced to the same
   * "preview" platform value. This method should be used by launch code for example or packaging code.
   */
  val runtimeMinSdkVersion: AndroidVersion

  /**
   * @return the target SDK version.
   * [AndroidModuleInfo.getTargetSdkVersion]
   */
  val targetSdkVersion: AndroidVersion?

  val supportedAbis: EnumSet<Abi> get() = EnumSet.allOf<Abi>(Abi::class.java)

  val namespacing: Namespacing

  /** @return the set of desugaring capabilities of the build system in use.
   */
  val desugaring: Set<Desugaring>

  /**
   * @return the set of optional lint rule jars that override lint jars collected from lint model. It provides an easy to return lint rule
   * jars without creating lint model implementation. Normally null for gradle project.
   */
  val lintRuleJarsOverride: Iterable<File>? get() = null


  /** Returns the set of build-system-provided resource values and overrides. */
  val resValues: Map<String, DynamicResourceValue> get() = mapOf<String, DynamicResourceValue>()

  val testOptions: TestOptions
    get() = TestOptions.DEFAULT

  val testExecutionOption: TestExecutionOption?
    get() = this.testOptions.executionOption

  /**
   * Returns the resource prefix to use, if any. This is an optional prefix which can be set and
   * which is used by the defaults to automatically choose new resources with a certain prefix,
   * warn if resources are not using the given prefix, etc. This helps work with resources in the
   * app namespace where there could otherwise be unintentional duplicated resource names between
   * unrelated libraries.
   *
   * @return the optional resource prefix, or null if not set
   */
  val resourcePrefix: String?
    get() = null

  val isBaseSplit: Boolean
    /**
     * Returns true if this is the base feature split.
     */
    get() = false

  val isInstantAppCompatible: Boolean
    /**
     * Returns true if this variant is instant app compatible, intended to be possibly built and
     * served in an instant app context. This is populated during sync from the project's manifest.
     * Only application modules and dynamic feature modules will set this property.
     *
     * @return true if this variant is instant app compatible
     * @since 3.3
     */
    get() = false

  companion object {
    @JvmStatic
    fun get(facet: AndroidFacet): AndroidModel? {
      return facet.getModuleSystem().androidModel ?: if (ApplicationManager.getApplication().isUnitTestMode) {
        // Also query the test model.
        // Ideally we shouldn't allow this but many tests do this already and the migration off it is not straightforward.
        facet.getUserData<AndroidModel?>(ANDROID_MODEL_KEY)
      } else {
        null
      }
    }

    @JvmStatic
    fun get(module: Module): AndroidModel? {
      val facet = AndroidFacet.getInstance(module)
      return if (facet == null) null else get(facet)
    }

    /* Sets the android model through the specific project system's implementation. */
    @JvmStatic
    fun set(facet: AndroidFacet, androidModel: AndroidModel) {
      facet.getModuleSystem().setAndroidModel(facet, androidModel)
    }

    /* Test helper for setting Android model. Consider using [AndroidProjectRule] instead. */
    @JvmStatic
    @TestOnly
    fun setForTests(facet: AndroidFacet, androidModel: AndroidModel) {
       facet.putUserData<AndroidModel?>(ANDROID_MODEL_KEY, androidModel)
    }

    /**
     * Returns `true` if `facet` has been configured from and is kept in sync with an external model of the project.
     */
    @JvmStatic
    fun isRequired(facet: AndroidFacet): Boolean {
      @Suppress("DEPRECATION") // This is one of legitimate usages of this property.
      return !facet.getProperties().ALLOW_USER_CONFIGURATION
    }

    const val UNINITIALIZED_APPLICATION_ID: String = "uninitialized.application.id"

    val ANDROID_MODEL_KEY: Key<AndroidModel> = Key.create<AndroidModel>(AndroidModel::class.java.getName())
  }
}