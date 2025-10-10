/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreDirect
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreRef
import com.android.tools.idea.gradle.model.impl.IdeDependencyCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeMultiVariantDataImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeProjectPathImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantBuildInformationImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.sync.InternedModels
import com.android.tools.idea.testing.AndroidProjectBuilder
import java.io.File
import kotlin.collections.flatMap
import kotlin.collections.orEmpty
import nl.jqno.equalsverifier.EqualsVerifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class X(val hashCode: Int) {

}

@RunWith(JUnit4::class)
class ModelEqualityTest {
  @Test
  fun testIdeAndroidProjectImpl() {
    val project = AndroidProjectBuilder().build().invoke(
      "projectName",
      ":",
      File("root"),
      File("root/module"),
      "9.0.0",
      InternedModels(buildRootDirectory = null)
    ).androidProject
    EqualsVerifier.forClass(IdeAndroidProjectImpl::class.java)
      .withCachedHashCode("hashCode", "computeHashCode", project)
      .verify()
  }

  @Test
  fun testIdeAndroidArtifactImpl() {
    EqualsVerifier.forClass(IdeAndroidArtifactImpl::class.java)
      .withOnlyTheseFields("core") // delegates equality check to core
      .verify()
  }

  @Test
  fun testIdeJavaArtifactImpl() {
    EqualsVerifier.forClass(IdeJavaArtifactImpl::class.java)
      .withOnlyTheseFields("core") // delegates equality check to core
      .verify()
  }

  @Test
  fun testIdeDependencies() {
    val dependencies = listOf(IdeDependencyCoreImpl(LibraryReference(0), dependencies = listOf(1,2,3,4,5)))
    val direct = IdeDependenciesCoreDirect(dependencies)
    val ref =  IdeDependenciesCoreRef(direct, 1, dependencies)

    EqualsVerifier.forClass(IdeDependenciesCoreDirect::class.java)
      .withCachedHashCode("hashCode", "computeHashCode", direct)
      .verify()
    EqualsVerifier.forClass(IdeDependenciesCoreRef::class.java)
      .withOnlyTheseFields("dependencies") // delegates equality check to dependencies, rest of the fields are used to derive it
      .withCachedHashCode("hashCode", "computeHashCode", ref)
      .verify()
  }

  @Test
  fun testIdeVariantImpl() {
    EqualsVerifier.forClass(IdeVariantImpl::class.java)
      .withOnlyTheseFields("core") // delegates equality check to core
      .verify()
  }

  @Test
  fun testGradleAndroidModelData() {
    EqualsVerifier.forClass(GradleAndroidModelData::class.java).verify()
  }
}