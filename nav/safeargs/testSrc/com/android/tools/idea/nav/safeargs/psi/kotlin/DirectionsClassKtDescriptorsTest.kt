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
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DirectionsClassKtDescriptorsTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  @Test
  fun checkContributorsOfDirectionsClass() {
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
                android:name="arg1"
                app:argType="string" />
            <action
                android:id="@+id/action_Fragment1_to_Fragment2"
                app:destination="@id/fragment2" />
            <action
                android:id="@+id/action_Fragment1_to_Main"
                app:popUpTo="@id/main" />
                
            <!-- Dummy action -->
            <action android:id="@+id/action_without_destination" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" />
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo()
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .first()

    assertThat(directionsClassMetadata.constructors.map { it.toString() }).containsExactly(
      "Fragment1Directions()"
    )
    assertThat(directionsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "actionFragment1ToFragment2(): androidx.navigation.NavDirections",
      "actionFragment1ToMain(): androidx.navigation.NavDirections"
    )
    assertThat(directionsClassMetadata.functions).isEmpty()
  }

  @Test
  fun testOverriddenArguments() {
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
            <action
              android:id="@+id/action_fragment1_to_fragment2"
              app:destination="@id/fragment2" >
              <argument
                android:name="overriddenArg"
                app:argType="string" />
                
              <argument
                  android:name="overriddenArgWithDefaultValue"
                  app:argType="integer"
                  android:defaultValue="1" />
            </action>
          </fragment>
          
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
            <argument
                android:name="arg"
                app:argType="string" />
                
            <argument
                android:name="overriddenArgWithDefaultValue"
                app:argType="integer" />
                
            <action
              android:id="@+id/action_fragment2_to_main"
              app:destination="@id/main" >
                <argument
                  android:name="overriddenArgWithDefaultValue"
                  app:argType="integer"
                  android:defaultValue="1" />
            </action>
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo()
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .sortedBy { it.fqcn }

    assertThat(directionsClassMetadata.size).isEqualTo(2)

    directionsClassMetadata[0].let { directionsClass ->
      assertThat(directionsClass.constructors.map { it.toString() }).containsExactly(
        "Fragment1Directions()"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionFragment1ToFragment2(overriddenArg: kotlin.String, overriddenArgWithDefaultValue: kotlin.Int, arg: kotlin.String)" +
        ": androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }

    directionsClassMetadata[1].let { directionsClass ->
      assertThat(directionsClass.constructors.map { it.toString() }).containsExactly(
        "Fragment2Directions()"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionFragment2ToMain(overriddenArgWithDefaultValue: kotlin.Int): androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }
  }

  @Test
  fun testIncludedNavigationCase() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <include app:graph="@navigation/included_graph" />
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >

              <action
                  android:id="@+id/action_Fragment2_to_IncludedGraph"
                  app:destination="@id/included_graph" />                  
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val safeArgProviderExtension = PackageFragmentProviderExtension.getInstances(safeArgsRule.project).first {
      it is SafeArgsKtPackageProviderExtension
    }

    val traceMock: BindingTrace = MockitoKt.mock()
    val moduleSourceInfo = safeArgsRule.module.productionSourceInfo()
    val moduleDescriptor = safeArgsRule.module.toDescriptor()

    val fragmentProvider = safeArgProviderExtension.getPackageFragmentProvider(
      project = safeArgsRule.project,
      module = moduleDescriptor!!,
      storageManager = LockBasedStorageManager.NO_LOCKS,
      trace = traceMock,
      moduleInfo = moduleSourceInfo,
      lookupTracker = LookupTracker.DO_NOTHING
    ) as SafeArgsSyntheticPackageProvider

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .sortedBy { it.fqcn }

    assertThat(directionsClassMetadata.size).isEqualTo(1)

    directionsClassMetadata[0].let { directionsClass ->
      assertThat(directionsClass.constructors.map { it.toString() }).containsExactly(
        "Fragment2Directions()"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionFragment2ToIncludedGraph(): androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }
  }
}