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
import com.android.tools.idea.res.StudioResourceRepositoryManager
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
                
            <!-- Sample action -->
            <action android:id="@+id/action_without_destination" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" />
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

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

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .first()

    assertThat(directionsClassMetadata.constructors).isEmpty()
    assertThat(directionsClassMetadata.classifiers.map { it.toString() }).containsExactly(
      "test.safeargs.Fragment1Directions.Companion"
    )
    assertThat(directionsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "actionFragment1ToFragment2(): androidx.navigation.NavDirections",
      "actionFragment1ToMain(): androidx.navigation.NavDirections"
    )
    assertThat(directionsClassMetadata.functions).isEmpty()
  }

  @Test
  fun testOverriddenArguments_After_AdjustParamsWithDefaultsFix() {
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
            <action
              android:id="@+id/action_fragment1_to_fragment2"
              app:destination="@id/fragment2" >
              <argument
                  android:name="overriddenArgWithDefaultValue"
                  app:argType="integer"
                  android:defaultValue="1" />
              <argument
                android:name="overriddenArg"
                app:argType="string" />
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
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

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

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .sortedBy { it.fqcn }

    assertThat(directionsClassMetadata.size).isEqualTo(2)

    directionsClassMetadata[0].let { directionsClass ->
      assertThat(directionsClass.constructors).isEmpty()
      assertThat(directionsClass.classifiers.map { it.toString() }).containsExactly(
        "test.safeargs.Fragment1Directions.Companion"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionFragment1ToFragment2(overriddenArg: kotlin.String, arg: kotlin.String, overriddenArgWithDefaultValue: kotlin.Int)" +
        ": androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }

    directionsClassMetadata[1].let { directionsClass ->
      assertThat(directionsClass.constructors).isEmpty()
      assertThat(directionsClass.classifiers.map { it.toString() }).containsExactly(
        "test.safeargs.Fragment2Directions.Companion"
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
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

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

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .sortedBy { it.fqcn }

    assertThat(directionsClassMetadata.size).isEqualTo(1)

    directionsClassMetadata[0].let { directionsClass ->
      assertThat(directionsClass.constructors).isEmpty()
      assertThat(directionsClass.classifiers.map { it.toString() }).containsExactly(
        "test.safeargs.Fragment2Directions.Companion"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionFragment2ToIncludedGraph(): androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }
  }

  @Test
  fun testGlobalActionCase() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">
            
          <action
                android:id="@+id/action_to_IncludedGraph"
                app:destination="@id/included_graph" />  
            
          <navigation
                android:id="@+id/inner_navigation"
                app:startDestination="@id/inner_fragment">
                
            <action
                android:id="@+id/action_InnerNavigation_to_IncludedGraph"
                app:destination="@id/included_graph" />  
                 
            <fragment
                android:id="@+id/fragment2"
                android:name="test.safeargs.Fragment2"
                android:label="Fragment2" >

                <action
                    android:id="@+id/action_Fragment2_to_IncludedGraph"
                    app:destination="@id/included_graph" />  
                    
                <!-- Same action with one of global actions -->
                <action
                    android:id="@+id/action_to_IncludedGraph"
                    app:destination="@id/included_graph" />  
            </fragment>
                
          </navigation>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

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

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .sortedBy { it.fqcn }

    assertThat(directionsClassMetadata.size).isEqualTo(3)

    directionsClassMetadata[0].let { directionsClass ->
      assertThat(directionsClass.constructors).isEmpty()
      assertThat(directionsClass.classifiers.map { it.toString() }).containsExactly(
        "test.safeargs.Fragment2Directions.Companion"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionFragment2ToIncludedGraph(): androidx.navigation.NavDirections",
        "actionToIncludedGraph(): androidx.navigation.NavDirections",
        "actionInnerNavigationToIncludedGraph(): androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }

    directionsClassMetadata[1].let { directionsClass ->
      assertThat(directionsClass.constructors).isEmpty()
      assertThat(directionsClass.classifiers.map { it.toString() }).containsExactly(
        "test.safeargs.InnerNavigationDirections.Companion"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionToIncludedGraph(): androidx.navigation.NavDirections",
        "actionInnerNavigationToIncludedGraph(): androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }

    directionsClassMetadata[2].let { directionsClass ->
      assertThat(directionsClass.constructors).isEmpty()
      assertThat(directionsClass.classifiers.map { it.toString() }).containsExactly(
        "test.safeargs.MainDirections.Companion"
      )
      assertThat(directionsClass.companionObject!!.functions.map { it.toString() }).containsExactly(
        "actionToIncludedGraph(): androidx.navigation.NavDirections"
      )
      assertThat(directionsClass.functions).isEmpty()
    }
  }

  @Test
  fun checkNoDestinationDefinedCase() {
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
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >
            <action
                android:id="@+id/action_Fragment2_to_Fragment1"
                app:popUpTo="@id/fragment1" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

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

    val directionsClassMetadata = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().classesInScope { name -> name.endsWith("Directions") } }
      .first()

    assertThat(directionsClassMetadata.constructors).isEmpty()
    assertThat(directionsClassMetadata.classifiers.map { it.toString() }).containsExactly(
      "test.safeargs.Fragment2Directions.Companion"
    )
    assertThat(directionsClassMetadata.companionObject!!.functions.map { it.toString() }).containsExactly(
      "actionFragment2ToFragment1(): androidx.navigation.NavDirections"
    )
    assertThat(directionsClassMetadata.functions).isEmpty()
  }
}