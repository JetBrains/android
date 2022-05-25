/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.testutils.MockitoKt
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.project.SafeArgsKtPackageProviderExtension
import com.android.tools.idea.nav.safeargs.project.SafeArgsSyntheticPackageProvider
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ArgsClassKtDescriptorsTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  @Test
  fun checkContributorsOfArgsClass() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg_one"
                app:argType="string" />
            <argument
                android:name="arg_two"
                app:argType="integer[]" />
            <argument
                android:name="arg_three"
                app:argType=".Fragment1" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val argsClassMetadata = fragmentProvider
      .getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope() }
      .single()

    assertThat(argsClassMetadata.fqcn).isEqualTo("test.safeargs.Fragment1Args")
    assertThat(argsClassMetadata.constructors.map { it.toString() }).containsExactly(
      "Fragment1Args(argOne: kotlin.String, argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1)"
    )

    assertThat(argsClassMetadata.classifiers.map { it.toString() }).containsExactly(
      "test.safeargs.Fragment1Args.Companion"
    )

    assertThat(argsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "fromBundle(bundle: android.os.Bundle): test.safeargs.Fragment1Args"
    )

    assertThat(argsClassMetadata.properties.map { it.toString() }).containsExactly(
      "val argOne: kotlin.String",
      "val argTwo: kotlin.IntArray",
      "val argThree: test.safeargs.Fragment1"
    )

    assertThat(argsClassMetadata.functions.map { it.toString() }).containsExactly(
      // generated functions of data class
      "component1(): kotlin.String",
      "component2(): kotlin.IntArray",
      "component3(): test.safeargs.Fragment1",
      "copy(argOne: kotlin.String, argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1): test.safeargs.Fragment1Args",
      // normal functions
      "toBundle(): android.os.Bundle"
    )
  }

  @Test
  fun checkContributorsOfArgsClass_After_AdjustParamsWithDefaultsFix() {
    safeArgsRule.addFakeNavigationDependency(SafeArgsFeatureVersions.ADJUST_PARAMS_WITH_DEFAULTS)

    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg_one"
                app:argType="string"
                android:defaultValue="foo" />
            <argument
                android:name="arg_two"
                app:argType="integer[]" />
            <argument
                android:name="arg_three"
                app:argType=".Fragment1" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val argsClassMetadata = fragmentProvider
      .getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope() }
      .single()

    assertThat(argsClassMetadata.fqcn).isEqualTo("test.safeargs.Fragment1Args")
    assertThat(argsClassMetadata.constructors.map { it.toString() }).containsExactly(
      "Fragment1Args(argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1, argOne: kotlin.String)"
    )

    assertThat(argsClassMetadata.classifiers.map { it.toString() }).containsExactly(
      "test.safeargs.Fragment1Args.Companion"
    )

    assertThat(argsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "fromBundle(bundle: android.os.Bundle): test.safeargs.Fragment1Args",
      "fromSavedStateHandle(savedStateHandle: androidx.lifecycle.SavedStateHandle): test.safeargs.Fragment1Args",
    )

    assertThat(argsClassMetadata.properties.map { it.toString() }).containsExactly(
      "val argOne: kotlin.String",
      "val argTwo: kotlin.IntArray",
      "val argThree: test.safeargs.Fragment1"
    )

    assertThat(argsClassMetadata.functions.map { it.toString() }).containsExactly(
      // generated functions of data class
      "component1(): kotlin.String",
      "component2(): kotlin.IntArray",
      "component3(): test.safeargs.Fragment1",
      "copy(argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1, argOne: kotlin.String): test.safeargs.Fragment1Args",
      // normal functions
      "toSavedStateHandle(): androidx.lifecycle.SavedStateHandle",
      "toBundle(): android.os.Bundle"
    )
  }

  @Test
  fun checkContributorsOfArgsClass_AfterToSavedStateHandleFeature() {
    safeArgsRule.addFakeNavigationDependency(SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE)

    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg_one"
                app:argType="string" />
            <argument
                android:name="arg_two"
                app:argType="integer[]" />
            <argument
                android:name="arg_three"
                app:argType=".Fragment1" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val argsClassMetadata = fragmentProvider
      .getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope() }
      .single()

    assertThat(argsClassMetadata.fqcn).isEqualTo("test.safeargs.Fragment1Args")
    assertThat(argsClassMetadata.constructors.map { it.toString() }).containsExactly(
      "Fragment1Args(argOne: kotlin.String, argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1)"
    )

    assertThat(argsClassMetadata.classifiers.map { it.toString() }).containsExactly(
      "test.safeargs.Fragment1Args.Companion"
    )

    assertThat(argsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "fromBundle(bundle: android.os.Bundle): test.safeargs.Fragment1Args",
      "fromSavedStateHandle(savedStateHandle: androidx.lifecycle.SavedStateHandle): test.safeargs.Fragment1Args",
    )

    assertThat(argsClassMetadata.properties.map { it.toString() }).containsExactly(
      "val argOne: kotlin.String",
      "val argTwo: kotlin.IntArray",
      "val argThree: test.safeargs.Fragment1"
    )

    assertThat(argsClassMetadata.functions.map { it.toString() }).containsExactly(
      // generated functions of data class
      "component1(): kotlin.String",
      "component2(): kotlin.IntArray",
      "component3(): test.safeargs.Fragment1",
      "copy(argOne: kotlin.String, argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1): test.safeargs.Fragment1Args",
      // normal functions
      "toSavedStateHandle(): androidx.lifecycle.SavedStateHandle",
      "toBundle(): android.os.Bundle"
    )
  }

  @Test
  fun checkContributorsOfArgsClass_AfterFromSavedStateHandleFeature() {
    safeArgsRule.addFakeNavigationDependency(SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE)

    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg_one"
                app:argType="string" />
            <argument
                android:name="arg_two"
                app:argType="integer[]" />
            <argument
                android:name="arg_three"
                app:argType=".Fragment1" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val argsClassMetadata = fragmentProvider
      .getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope() }
      .single()

    assertThat(argsClassMetadata.fqcn).isEqualTo("test.safeargs.Fragment1Args")
    assertThat(argsClassMetadata.constructors.map { it.toString() }).containsExactly(
      "Fragment1Args(argOne: kotlin.String, argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1)"
    )

    assertThat(argsClassMetadata.classifiers.map { it.toString() }).containsExactly(
      "test.safeargs.Fragment1Args.Companion"
    )

    assertThat(argsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "fromBundle(bundle: android.os.Bundle): test.safeargs.Fragment1Args",
      "fromSavedStateHandle(savedStateHandle: androidx.lifecycle.SavedStateHandle): test.safeargs.Fragment1Args",
    )

    assertThat(argsClassMetadata.properties.map { it.toString() }).containsExactly(
      "val argOne: kotlin.String",
      "val argTwo: kotlin.IntArray",
      "val argThree: test.safeargs.Fragment1"
    )

    assertThat(argsClassMetadata.functions.map { it.toString() }).containsExactly(
      // generated functions of data class
      "component1(): kotlin.String",
      "component2(): kotlin.IntArray",
      "component3(): test.safeargs.Fragment1",
      "copy(argOne: kotlin.String, argTwo: kotlin.IntArray, argThree: test.safeargs.Fragment1): test.safeargs.Fragment1Args",
      // normal functions
      "toBundle(): android.os.Bundle"
    )
  }
}