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
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.MemberComparator
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SafeArgNavigationKtTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  @Test
  fun canNavigateToXmlTagFromArgsKtClass() {
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
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
          <argument
                android:name="arg_two"
                app:argType="string" />
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


    val argsClassDescriptors: List<LightArgsKtClass> = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().getContributedDescriptors() }
      .sortedWith(MemberComparator.INSTANCE)
      .mapNotNull { it as? LightArgsKtClass }

    assertThat(argsClassDescriptors.size).isEqualTo(2)
    // check Fragment1Args class navigation
    argsClassDescriptors[0].let {
      val navigationElement = it.source.getPsi()!!
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/fragment1\"")

      it.unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          when (descriptor) {
            is PropertyDescriptorImpl -> {
              val resolvedNavigationElement = descriptor.source.getPsi()!!
              assertThat(resolvedNavigationElement is XmlTag).isTrue()
              assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
                """
                <argument
                        android:name="arg_one"
                        app:argType="string" />
                """.trimIndent())
            }
            is SimpleFunctionDescriptorImpl -> {
              val resolvedNavigationElement = descriptor.source.getPsi()!!
              assertThat(resolvedNavigationElement is XmlTag).isTrue()
              if (descriptor.name.asString() == "component1") {
                assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
                  """
                <argument
                        android:name="arg_one"
                        app:argType="string" />
                """.trimIndent())
              }
              else {
                assertThat((resolvedNavigationElement as XmlTag).text).contains("id=\"@+id/fragment1\"")
              }
            }
          }
        }
    }

    // check Fragment2Args class navigation
    argsClassDescriptors[1].let {
      val navigationElement = it.source.getPsi()!!
      // check class
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/fragment2\"")

      // check properties and methods
      it.unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          when (descriptor) {
            is PropertyDescriptorImpl -> {
              val resolvedNavigationElement = descriptor.source.getPsi()!!
              assertThat(resolvedNavigationElement is XmlTag).isTrue()
              assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
                """
                <argument
                        android:name="arg_two"
                        app:argType="string" />
                """.trimIndent())
            }
            is SimpleFunctionDescriptorImpl -> {
              val resolvedNavigationElement = descriptor.source.getPsi()!!
              assertThat(resolvedNavigationElement is XmlTag).isTrue()

              if (descriptor.name.asString() == "component1") {
                assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
                  """
                <argument
                        android:name="arg_two"
                        app:argType="string" />
                """.trimIndent())
              }
              else {
                assertThat((resolvedNavigationElement as XmlTag).text).contains("id=\"@+id/fragment2\"")
              }
            }
          }
        }
    }
  }

  @Test
  fun canNavigateToXmlTagFromDirectionsKtClass() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <action
            android:id="@+id/action_to_nested"
            app:destination="@id/nested" />

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1" >
            <action
              android:id="@+id/action_fragment1_to_fragment2"
              app:destination="@id/fragment2" />
            <action
              android:id="@+id/action_fragment1_to_fragment3"
              app:destination="@id/fragment3" />
          </fragment>

          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2" >
          </fragment>

          <navigation
              android:id="@+id/nested"
              app:startDestination="@id/fragment3">

            <fragment
                android:id="@+id/fragment3"
                android:name="test.safeargs.Fragment3"
                android:label="Fragment3">
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

    val directionsClassDescriptors: List<LightDirectionsKtClass> = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .flatMap { it.getMemberScope().getContributedDescriptors() }
      .sortedWith(MemberComparator.INSTANCE)
      .mapNotNull { it as? LightDirectionsKtClass }

    assertThat(directionsClassDescriptors.size).isEqualTo(5)

    // check fragment1directions class navigation
    directionsClassDescriptors[0].let {
      val navigationElement = it.source.getPsi()!!
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/fragment1\"")

      it.companionObjectDescriptor
        .unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          val resolvedNavigationElement = (descriptor as SimpleFunctionDescriptorImpl).source.getPsi()!!
          assertThat(resolvedNavigationElement is XmlTag).isTrue()

          when (descriptor.name.toString()) {
            "actionFragment1ToFragment2" -> {
              assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
                """
                <action
                      android:id="@+id/action_fragment1_to_fragment2"
                      app:destination="@id/fragment2" />
                """.trimIndent())
            }
            "actionFragment1ToFragment3" -> {
              assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
                """
                <action
                      android:id="@+id/action_fragment1_to_fragment3"
                      app:destination="@id/fragment3" />
                """.trimIndent())
            }
          }
        }
    }

    // check fragment2Directions class navigation
    directionsClassDescriptors[1].let {
      val navigationElement = it.source.getPsi()!!
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/fragment2\"")

      it.companionObjectDescriptor
        .unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          val resolvedNavigationElement = (descriptor as SimpleFunctionDescriptorImpl).source.getPsi()!!
          assertThat(resolvedNavigationElement is XmlTag).isTrue()
          assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
            """
                <action
                    android:id="@+id/action_to_nested"
                    app:destination="@id/nested" />
            """.trimIndent())
        }
    }

    // check fragment3Directions class navigation
    directionsClassDescriptors[2].let {
      val navigationElement = it.source.getPsi()!!
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/fragment3\"")

      it.companionObjectDescriptor
        .unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          val resolvedNavigationElement = (descriptor as SimpleFunctionDescriptorImpl).source.getPsi()!!
          assertThat(resolvedNavigationElement is XmlTag).isTrue()
          assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
            """
                <action
                    android:id="@+id/action_to_nested"
                    app:destination="@id/nested" />
            """.trimIndent())
        }
    }

    // check mainDirections class navigation
    directionsClassDescriptors[3].let {
      val navigationElement = it.source.getPsi()!!
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/main\"")

      it.companionObjectDescriptor
        .unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          val resolvedNavigationElement = (descriptor as SimpleFunctionDescriptorImpl).source.getPsi()!!
          assertThat(resolvedNavigationElement is XmlTag).isTrue()
          assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
            """
                <action
                    android:id="@+id/action_to_nested"
                    app:destination="@id/nested" />
            """.trimIndent())
        }
    }

    // check nestedDirections class navigation
    directionsClassDescriptors[4].let {
      val navigationElement = it.source.getPsi()!!
      assertThat(navigationElement).isInstanceOf(XmlTag::class.java)
      assertThat((navigationElement as XmlTag).text).contains("id=\"@+id/nested\"")

      it.companionObjectDescriptor
        .unsubstitutedMemberScope
        .getContributedDescriptors()
        .sortedWith(MemberComparator.INSTANCE)
        .forEach { descriptor ->
          val resolvedNavigationElement = (descriptor as SimpleFunctionDescriptorImpl).source.getPsi()!!
          assertThat(resolvedNavigationElement is XmlTag).isTrue()
          assertThat((resolvedNavigationElement as XmlTag).text).isEqualTo(
            """
                <action
                    android:id="@+id/action_to_nested"
                    app:destination="@id/nested" />
            """.trimIndent())
        }
    }
  }
}