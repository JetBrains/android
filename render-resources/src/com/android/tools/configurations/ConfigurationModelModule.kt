/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.configurations

import com.android.sdklib.IAndroidTarget
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ModuleKey
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.CompatibilityRenderTarget

/**
 * Provides all module specific resources required for configuration
 */
interface ConfigurationModelModule {
  val androidPlatform: AndroidPlatform?

  val resourceRepositoryManager: ResourceRepositoryManager?

  val themeInfoProvider: ThemeInfoProvider

  val layoutlibContext: LayoutlibContext

  val androidModuleInfo: AndroidModuleInfo?

  val name: String

  val dependencies: ModuleDependencies

  /** Key used to invalidate drawable caches in Layoutlib. See [ModuleKeyManager] and [ResourceHelper]. */
  val moduleKey: ModuleKey

  val resourcePackage: String?

  fun getCompatibilityTarget(target: IAndroidTarget): CompatibilityRenderTarget
}