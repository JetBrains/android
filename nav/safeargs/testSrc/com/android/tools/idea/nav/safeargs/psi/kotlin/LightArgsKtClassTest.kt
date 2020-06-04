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
import com.android.tools.idea.nav.safeargs.project.SafeArgSyntheticPackageProvider
import com.android.tools.idea.nav.safeargs.project.SafeArgsKtPackageProviderExtension
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.MemberComparator
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
                android:name="arg1"
                app:argType="string" />
            <argument
                android:name="arg2"
                app:argType="integer[]" />
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
    ) as SafeArgSyntheticPackageProvider

    val renderer = DescriptorRenderer.COMPACT_WITH_MODIFIERS

    val argsClassDescriptor = fragmentProvider.getPackageFragments(FqName("test.safeargs"))
      .first()
      .getMemberScope()
      .getContributedDescriptors()
      .filter { it is LightArgsKtClass }
      .first() as LightArgsKtClass

    // Check primary constructor
    argsClassDescriptor.unsubstitutedPrimaryConstructor.let {
      val rendered = renderer.render(it)
      assertThat(rendered).isEqualTo("public constructor Fragment1Args(arg1: kotlin.String, arg2: kotlin.IntArray)")
    }

    // Check companion objects
    val companionObject = argsClassDescriptor.companionObjectDescriptor
      .unsubstitutedMemberScope
      .getContributedDescriptors()
      .sortedWith(MemberComparator.INSTANCE)
      .map { renderer.render(it) }

    assertThat(companionObject).containsExactly(
      // unresolved type is due to the mock sdk in test setup
      "public final fun fromBundle(bundle: [ERROR : android.os.Bundle]): test.safeargs.Fragment1Args")

    // Check methods
    val contributors = argsClassDescriptor.unsubstitutedMemberScope
      .getContributedDescriptors()
      .sortedWith(MemberComparator.INSTANCE)
      .map { renderer.render(it) }

    assertThat(contributors).containsExactly(
      /*properties*/
      "public final val arg1: kotlin.String",
      "public final val arg2: kotlin.IntArray",
      /*generated functions of data class*/
      "public final fun component1(): kotlin.String",
      "public final fun component2(): kotlin.IntArray",
      "public final fun copy(arg1: kotlin.String, arg2: kotlin.IntArray): test.safeargs.Fragment1Args",
      /*functions*/
      "public final fun toBundle(): [ERROR : android.os.Bundle]" // unresolved type is due to the mock sdk in test setup
    )
  }
}