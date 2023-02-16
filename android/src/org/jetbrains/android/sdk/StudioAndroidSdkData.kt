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
package org.jetbrains.android.sdk

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.repository.AndroidSdkHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.android.facet.AndroidFacet

/**
 * Studio specific constructors for [AndroidSdkData].
 */
class StudioAndroidSdkData {
  private class ModuleSdkDataHolder private constructor(facet: AndroidFacet) : Disposable {
    private var myFacet: AndroidFacet?
    val sdkHandler: AndroidSdkHandler
    val sdkData: AndroidSdkData?

    init {
      myFacet = facet
      Disposer.register(facet, this)
      val platform = AndroidPlatform.getInstance(facet.module)
      sdkData = platform?.sdkData
      sdkHandler = sdkData?.sdkHandler ?: AndroidSdkHandler.getInstance(AndroidLocationsSingleton, null)
    }

    override fun dispose() {
      myFacet!!.putUserData(KEY, null)
      myFacet = null
    }

    companion object {
      private val KEY: Key<ModuleSdkDataHolder> = Key.create(ModuleSdkDataHolder::class.java.name)
      fun getInstance(facet: AndroidFacet): ModuleSdkDataHolder {
        var sdkDataHolder = facet.getUserData(KEY)
        if (sdkDataHolder == null) {
          sdkDataHolder = ModuleSdkDataHolder(facet)
          facet.putUserData(KEY, sdkDataHolder)
        }
        return sdkDataHolder
      }
    }
  }

  companion object {
    @JvmStatic
    fun getSdkData(facet: AndroidFacet) = ModuleSdkDataHolder.getInstance(facet).sdkData

    @JvmStatic
    fun getSdkHolder(facet: AndroidFacet) = ModuleSdkDataHolder.getInstance(facet).sdkHandler

    @JvmStatic
    fun getSdkData(project: Project) = ProjectRootManager.getInstance(project).projectSdk?.let { AndroidSdkData.getSdkData(it) }

    @JvmStatic
    fun getSdkData(module: Module) = getSdkData(module.project)
  }
}