/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.model.impl

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.IdeaSourceProvider
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.SourceProviderManager
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

    override fun getApplicationPackage() = androidFacet?.let { Manifest.getMainManifest(it) }?.`package`?.toString()

    override fun getMainSourceProvider(): AndroidModuleInfoProvider.SourceProviderMirror? {
        return androidFacet?.let { SourceProviderManager.getInstance(it).mainIdeaSourceProvider }?.let(::SourceProviderMirrorImpl)
    }

    override fun getActiveSourceProviders(): List<AndroidModuleInfoProvider.SourceProviderMirror> {
        return IdeaSourceProvider.getCurrentSourceProviders(androidFacet ?: return emptyList()).map(::SourceProviderMirrorImpl)
    }

    override fun getMainAndFlavorSourceProviders(): List<AndroidModuleInfoProvider.SourceProviderMirror> {
        return IdeaSourceProvider.getMainAndFlavorSourceProviders(androidFacet ?: return emptyList()).map(::SourceProviderMirrorImpl)
    }

    private class SourceProviderMirrorImpl(val sourceProvider: IdeaSourceProvider) :
        AndroidModuleInfoProvider.SourceProviderMirror {
        override val name: String
            get() = sourceProvider.name

        override val resDirectories: Collection<VirtualFile>
            get() = sourceProvider.resDirectories
    }
}
