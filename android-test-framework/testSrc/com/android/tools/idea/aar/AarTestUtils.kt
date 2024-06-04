// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AarTestUtils")

package com.android.tools.idea.aar

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.aar.AarSourceResourceRepository
import com.android.test.testutils.TestUtils
import com.android.tools.idea.res.DynamicValueResourceRepository
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.res.LocalResourceRepository
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet


const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0"
const val TEST_DATA_DIR = "tools/base/resource-repository/test/resources/aar"

@JvmOverloads
fun getTestAarRepositoryFromExplodedAar(libraryDirName: String = "my_aar_lib"): AarSourceResourceRepository {
  return AarSourceResourceRepository.create(
    TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/$libraryDirName/res"),
    AAR_LIBRARY_NAME
  )
}

@JvmOverloads
fun createTestModuleRepository(
  facet: AndroidFacet,
  resourceDirectories: Collection<VirtualFile>,
  namespace: ResourceNamespace = ResourceNamespace.RES_AUTO,
  dynamicRepo: DynamicValueResourceRepository? = null
): LocalResourceRepository<VirtualFile> {
  return ModuleResourceRepository.createForTest(facet, resourceDirectories, namespace, dynamicRepo)
}