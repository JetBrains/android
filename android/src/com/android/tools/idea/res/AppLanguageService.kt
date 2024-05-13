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
package com.android.tools.idea.res

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.Companion.getApplicationProjectContextProvider
import com.android.tools.idea.projectsystem.PseudoLocalesToken.Companion.isPseudoLocalesEnabled
import com.android.tools.idea.projectsystem.PseudoLocalesToken.PseudoLocalesState
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.isMainModule
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/** Holds the [applicationId] and all supported locales [localeConfig] of an application. */
data class AppLanguageInfo(val applicationId: String, val localeConfig: Set<LocaleQualifier>)

private val enabledStates = setOf(PseudoLocalesState.ENABLED, PseudoLocalesState.BOTH)
private val pseudoLocales =
  setOf(LocaleQualifier(null, "en", "XA", null), LocaleQualifier(null, "ar", "XB", null))

/** Provides [AppLanguageInfo] for a given Project. */
fun interface AppLanguageService {
  companion object {
    @JvmStatic fun getInstance(project: Project): AppLanguageService = project.service()
  }

  /** Returns the [AppLanguageInfo] of the specified application. */
  @Slow fun getAppLanguageInfo(deviceSerialNumber: String, applicationId: String): AppLanguageInfo?
}

@Service(Service.Level.PROJECT)
class AppLanguageServiceImpl(private val project: Project) : AppLanguageService {

  @Slow
  override fun getAppLanguageInfo(
    deviceSerialNumber: String,
    applicationId: String,
  ): AppLanguageInfo? {
    val facet =
      project
        .getProjectSystem()
        .findModulesWithApplicationId(applicationId)
        .filter { it.isMainModule() }
        .mapNotNull { AndroidFacet.getInstance(it) }
        .firstOrNull { AndroidModel.get(it)?.applicationId == applicationId } ?: return null
    return AppLanguageInfo(
      applicationId,
      getLocaleConfig(facet) + pseudoLocales(deviceSerialNumber, applicationId),
    )
  }

  private fun pseudoLocales(
    deviceSerialNumber: String,
    applicationId: String,
  ): Set<LocaleQualifier> {
    // TODO(b/340298155): getApplicationProjectContext should not require a ddmlib Client
    val adb =
      AdbFileProvider.fromProject(project)
        .get()
        ?.let { AdbService.getInstance()?.getDebugBridge(it) }
        ?.get() ?: AndroidDebugBridge.getBridge() ?: return emptySet()
    val device =
      adb.devices.firstOrNull { it.serialNumber == deviceSerialNumber } ?: return emptySet()
    val client =
      device.clients.firstOrNull { it.clientData.packageName == applicationId } ?: return emptySet()
    val context =
      project
        .getProjectSystem()
        .getApplicationProjectContextProvider()
        .getApplicationProjectContext(client) ?: return emptySet()
    return if (project.getProjectSystem().isPseudoLocalesEnabled(context) in enabledStates)
      pseudoLocales
    else emptySet()
  }
}
