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
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Path
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile

interface BuildSystemLiveEditServices<P : AndroidProjectSystem, C: ApplicationProjectContext> : Token {
  fun isApplicable(applicationProjectContext: ApplicationProjectContext): Boolean

  fun getApplicationServices(applicationProjectContext: C): ApplicationLiveEditServices

  fun disqualifyingBytecodeTransformation(applicationProjectContext: C): BuildSystemBytecodeTransformation?

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

class BuildSystemBytecodeTransformation(val buildHasTransformation: Boolean, val transformationPoints: List<String>)

sealed interface DesugarConfigs {
  class NotKnown(val message: String?): DesugarConfigs
  class Known(val configs: List<Path>): DesugarConfigs
}

/**
 * A collection of services provided by the project system to support live edit in the associated already running Android application.
 */
interface ApplicationLiveEditServices {
  /**
   * A descriptor of the dependencies of an original build system compilation unit.
   *
   * Note: Implementations are supposed to support equaility via [Any.equals] and [Any.hashCode] in order to allow the caller to group
   *       source files/classes from the same compilation unit together.
   */
  interface CompilationDependencies {
    /**
     * Returns the list of all jars on the transitive runtime classpath of this compilation unit that are not produced by this or other in
     * project-scope compilation units, i.e. their class files are not returned by [ApplicationLiveEditServices.getClassContent].
     */
    fun getExternalLibraries(): List<Path>

    /**
     * Returns the list of all jars on the boot classpath of this compilation unit.
     */
    fun getBootClasspath(): List<Path>
  }

  /**
   * Returns dependencies of the compilation unit that includes [file] in the scope of ths application.
   */
  fun getCompilationDependencies(file: PsiFile): CompilationDependencies?

  fun getClassContent(file: VirtualFile, className: String): ClassContent?
  fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration
  fun getDesugarConfigs(): DesugarConfigs
  fun getRuntimeVersionString(): String

  @TestOnly
  class LegacyForTests(private val project: Project): ApplicationLiveEditServices {
    data class CompilationDependenciesImpl(val module: Module): ApplicationLiveEditServices.CompilationDependencies {
      override fun getExternalLibraries(): List<Path> {
        return AndroidRootUtil.getExternalLibraries(module)
          .map { VfsUtilCore.virtualToIoFile(it).toPath() }
      }

      override fun getBootClasspath(): List<Path> {
        return emptyList()
      }
    }

    override fun getClassContent(file: VirtualFile, className: String): ClassContent? {
      val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
      // TODO: solodkyy - ??? this is not the same for non main modules in gradle but gradle should not be here.
      return module.getModuleSystem().moduleClassFileFinder.findClassFile(className)
    }

    override fun getCompilationDependencies(file: PsiFile): ApplicationLiveEditServices.CompilationDependencies? {
      return file.module?.let { CompilationDependenciesImpl(it)}
    }

    override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
      return getCompilerConfiguration(ktFile.module!!, ktFile)
    }

    override fun getDesugarConfigs() = DesugarConfigs.NotKnown("Desugar config not set up in unit tests yet.")

    override fun getRuntimeVersionString(): String = DEFAULT_RUNTIME_VERSION
  }

  @TestOnly
  class ApplicationLiveEditServicesForTests(
    private val classFiles: Map<String, ByteArray>,
    val versionString: String = DEFAULT_RUNTIME_VERSION,
  ): ApplicationLiveEditServices {
    data class CompilationDependenciesImpl(val module: Module): ApplicationLiveEditServices.CompilationDependencies {
      override fun getExternalLibraries(): List<Path> {
        return AndroidRootUtil.getExternalLibraries(module)
          .map { VfsUtilCore.virtualToIoFile(it).toPath() }
      }

      override fun getBootClasspath(): List<Path> {
        error("Not implemented") // TODO: acleung - This is not right. DesugarerCompileTest passes only because this method throws.
      }
    }

    override fun getClassContent(file: VirtualFile, className: String): ClassContent? {
      return classFiles[className]?.let { ClassContent.forTests(it) }
    }

    override fun getCompilationDependencies(file: PsiFile): ApplicationLiveEditServices.CompilationDependencies? {
      return file.module?.let { CompilationDependenciesImpl(it)}
    }

    override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
      return ktFile.module?.let { module -> getCompilerConfiguration(module, ktFile) }
             ?: error("Cannot get kotlin compiler configuration for $ktFile")
    }

    override fun getDesugarConfigs() = DesugarConfigs.NotKnown("No Desugar config.")

    override fun getRuntimeVersionString() = versionString
  }

  companion object {
    /** Default version of the runtime to use if the dependency resolution fails when looking for the daemon. */
    const val DEFAULT_RUNTIME_VERSION = "1.1.0-alpha02"
  }
}
