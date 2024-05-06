/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.SdkConstants.FD_SAMPLE_DATA
import com.android.ide.common.util.PathString
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import java.io.IOException

/**
 * An implementation of [SampleDataDirectoryProvider] which houses a module's sample data directory
 * in the main content root of the module.
 */
class MainContentRootSampleDataDirectoryProvider(module: Module) : SampleDataDirectoryProvider {
  val module: Module = module.getHolderModule()

  override fun getSampleDataDirectory(): PathString? {
    return AndroidFacet.getInstance(module)
      ?.let(AndroidRootUtil::getMainContentRoot)
      ?.toPathString()
      ?.resolve(FD_SAMPLE_DATA)
  }

  @Throws(IOException::class)
  override fun getOrCreateSampleDataDirectory(): PathString? {
    return getSampleDataDirectory()?.apply { VfsUtil.createDirectoryIfMissing(nativePath) }
  }
}
