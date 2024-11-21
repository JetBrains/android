// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.rendering

import com.android.tools.rendering.api.EnvironmentContext
import com.intellij.openapi.module.Module

class StudioEnvironmentContextFactory : EnvironmentContextFactory {
  override fun createContext(module: Module): EnvironmentContext = StudioEnvironmentContext(module)
}
