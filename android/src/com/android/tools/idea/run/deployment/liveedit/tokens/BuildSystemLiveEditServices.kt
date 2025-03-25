/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.tokens

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getToken
import com.android.tools.idea.run.deployment.liveedit.getCompilerConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile

interface BuildSystemLiveEditServices<P : AndroidProjectSystem, C: ApplicationProjectContext> : Token {
  fun isApplicable(applicationProjectContext: ApplicationProjectContext): Boolean

  fun getApplicationServices(applicationProjectContext: C): ApplicationLiveEditServices

  companion object {
    val EP_NAME =
      ExtensionPointName<BuildSystemLiveEditServices<AndroidProjectSystem, ApplicationProjectContext>>(
        "com.android.tools.idea.liveedit.tokens.buildSystemLiveEditServices"
      )

    /**
     * Returns an instance of [BuildSystemLiveEditServices] applicable to [this] project system.
     *
     * Note, that the method returns an interface projection that does not accept [ApplicationProjectContext]s.
     * Use [ApplicationProjectContext.getBuildSystemLiveEditServices] to get an instance suitable for handling application contexts.
     */
    @JvmStatic
    fun AndroidProjectSystem.getBuildSystemLiveEditServices(): BuildSystemLiveEditServices<*, *> {
      return getToken(EP_NAME)
    }

    /**
     * Returns an instance of [BuildSystemLiveEditServices.ApplicationLiveEditServices] that serves [this] specific instance.
     */
    @JvmStatic
    fun ApplicationProjectContext.getApplicationLiveEditServices(): ApplicationLiveEditServices? {
      return getBuildSystemLiveEditServices()?.getApplicationServices(this)
    }

    @JvmStatic
    fun <R: ApplicationProjectContext> R.getBuildSystemLiveEditServices(): BuildSystemLiveEditServices<*, R>? {
      @Suppress("UNCHECKED_CAST")
      return EP_NAME.extensionList.singleOrNull { it.isApplicable(this) } as? BuildSystemLiveEditServices<*, R>
    }
  }
}

interface ApplicationLiveEditServices {
  fun getClassContent(file: VirtualFile, className: String): ClassContent?
  fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration

  @TestOnly
  class LegacyForTests(private val project: Project): ApplicationLiveEditServices {
    override fun getClassContent(file: VirtualFile, className: String): ClassContent? {
      val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
      // TODO: solodkyy - ??? this is not the same for non main modules in gradle but gradle should not be here.
      return module.getModuleSystem().moduleClassFileFinder.findClassFile(className)
    }

    override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
      return getCompilerConfiguration(ktFile.module!!, ktFile)
    }
  }

  @TestOnly
  class ApplicationLiveEditServicesForTests(private val classFiles: Map<String, ByteArray>): ApplicationLiveEditServices {
    override fun getClassContent(file: VirtualFile, className: String): ClassContent? {
      return classFiles[className]?.let { ClassContent.forTests(it) }
    }

    override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
      return ktFile.module?.let { module -> getCompilerConfiguration(module, ktFile) }
        ?: error("Cannot get kotlin compiler configuration for $ktFile")
    }
  }
}
