/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.model.impl

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.kotlin.android.model.AndroidModuleInfoProvider

class AndroidModuleInfoProviderImpl(override val module: Module) : AndroidModuleInfoProvider {
    private val androidFacet: AndroidFacet?
        get() = AndroidFacet.getInstance(module)

    override fun isAndroidModule() = androidFacet != null
    override fun isGradleModule() = GradleProjectInfo.getInstance(module.project).isBuildWithGradle

    override fun getAllResourceDirectories() : List<VirtualFile> {
        val facet = androidFacet ?: return emptyList()
        return ResourceFolderManager.getInstance(facet).folders
    }

    override fun getApplicationPackage(): String? {
        return androidFacet?.getModuleSystem()?.getPackageName()
    }

    override fun getActiveSourceProviders(): List<AndroidModuleInfoProvider.SourceProviderMirror> {
        return SourceProviderManager.getInstance(androidFacet ?: return emptyList()).currentSourceProviders.map(::SourceProviderMirrorImpl)
    }

    override fun getMainAndFlavorSourceProviders(): List<AndroidModuleInfoProvider.SourceProviderMirror> {
        @Suppress("DEPRECATION")
        return SourceProviderManager.getInstance(androidFacet ?: return emptyList())
          .mainAndFlavorSourceProviders
          .map(::SourceProviderMirrorImpl)
    }

    private class SourceProviderMirrorImpl(val sourceProvider: NamedIdeaSourceProvider) :
        AndroidModuleInfoProvider.SourceProviderMirror {
        override val name: String
            get() = sourceProvider.name

        override val resDirectories: Collection<VirtualFile>
            get() = sourceProvider.resDirectories.toList()
    }
}
