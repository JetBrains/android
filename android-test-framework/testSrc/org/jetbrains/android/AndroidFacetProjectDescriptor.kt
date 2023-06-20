// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.jetbrains.android

import com.android.tools.idea.testing.Sdks
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor

object AndroidFacetProjectDescriptor : DefaultLightProjectDescriptor() {
  override fun getSdk(): Sdk {
    // SDKs used by light fixtures are not in the global table. This way heavy fixtures that clean the global table in tearDown() don't
    // affect the shared light modules.
    return Sdks.createLatestAndroidSdk(null, AndroidFacetProjectDescriptor::class.qualifiedName, false)
  }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    AndroidTestCase.addAndroidFacetAndSdk(module, false)
  }
}
