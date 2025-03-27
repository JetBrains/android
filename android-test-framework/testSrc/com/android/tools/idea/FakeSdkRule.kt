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
package com.android.tools.idea

import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.NamedExternalResource
import org.junit.runner.Description
import org.mockito.Mockito.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

/**
 * Beginning of an attempt to allow the Sdk to be mocked out with one that contains only the given components.
 *
 * TODO: combine this and com.android.sdklib.TempSdkManager
 */
class FakeSdkRule(val projectRule: AndroidProjectRule, val sdkPath: Path = createInMemoryFileSystemAndFolder("sdk"))
  : NamedExternalResource() {

  var packages: RepositoryPackages = RepositoryPackages()
  val localPackages = mutableListOf<LocalPackage>()
  val remotePackages = mutableListOf<RemotePackage>()

  fun withLocalPackage(localPackage: LocalPackage) = apply { localPackages.add(localPackage) }
  fun withLocalPackage(path: String, location: String) =
    apply { localPackages.add(FakePackage.FakeLocalPackage(path, sdkPath.resolve(location))) }
  fun withRemotePackage(remotePackage: RemotePackage) = apply { remotePackages.add(remotePackage) }

  fun addLocalPackage(path: String, location: String) {
    packages.setLocalPkgInfos(packages.localPackages.values.plus(FakePackage.FakeLocalPackage(path, sdkPath.resolve(location))))
  }

  override fun before(description: Description) {
    packages = RepositoryPackages(localPackages, remotePackages)
    val repoManager = FakeRepoManager(sdkPath, packages)
    val sdkHandler = AndroidSdkHandler(sdkPath, null, repoManager)

    val ideSdks = spy(IdeSdks.getInstance())
    whenever(ideSdks.androidSdkPath).thenReturn(File(sdkPath.toString()))
    IdeComponents(projectRule.fixture).replaceApplicationService(IdeSdks::class.java, ideSdks)

    val androidSdks = spy(AndroidSdks.getInstance())
    whenever(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    IdeComponents(projectRule.fixture).replaceApplicationService(AndroidSdks::class.java, androidSdks)
  }

  override fun after(description: Description) {
  }
}