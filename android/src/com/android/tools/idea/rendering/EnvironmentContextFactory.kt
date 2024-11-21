// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.rendering

import com.android.tools.rendering.api.EnvironmentContext
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module

/**
 * Project-level service, responsible for creating [EnvironmentContext] instances.
 */
interface EnvironmentContextFactory {
  fun createContext(module: Module): EnvironmentContext

  companion object {
    @JvmStatic
    fun create(module: Module): EnvironmentContext = module.project.service<EnvironmentContextFactory>().createContext(module)
  }
}
