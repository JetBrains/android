// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.android.wizard

import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.getInitialSdkLocation
import org.jetbrains.kotlin.tools.projectWizard.plugins.AndroidSdkProvider
import java.nio.file.Path

class AndroidSdkProviderImpl : AndroidSdkProvider {
  override fun getSdkPath(): Path {
    return getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK).toPath()
  }
}