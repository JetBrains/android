// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.android.synthetic.idea.res

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.android.model.AndroidModuleInfoProvider
import org.jetbrains.kotlin.android.synthetic.idea.androidExtensionsIsEnabled
import org.jetbrains.kotlin.android.synthetic.idea.androidExtensionsIsExperimental
import org.jetbrains.kotlin.android.synthetic.idea.findAndroidModuleInfo
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutXmlFileManager
import org.jetbrains.kotlin.android.synthetic.res.AndroidPackageFragmentProviderExtension

class IDEAndroidPackageFragmentProviderExtension(val project: Project) : AndroidPackageFragmentProviderExtension() {
    override fun isExperimental(moduleInfo: ModuleInfo?): Boolean {
        return moduleInfo?.androidExtensionsIsExperimental ?: false
    }

    override fun getLayoutXmlFileManager(project: Project, moduleInfo: ModuleInfo?): AndroidLayoutXmlFileManager? {
        val module = moduleInfo?.findAndroidModuleInfo()?.module ?: return null
        if (!isAndroidExtensionsEnabled(module)) return null
        return module.getService(AndroidLayoutXmlFileManager::class.java)
    }

    private fun isAndroidExtensionsEnabled(module: Module): Boolean {
        // Android Extensions should be always enabled for Android/JPS
        if (isLegacyIdeaAndroidModule(module)) return true
        return module.androidExtensionsIsEnabled
    }

    private fun isLegacyIdeaAndroidModule(module: Module): Boolean {
        val infoProvider = AndroidModuleInfoProvider.getInstance(module) ?: return false
        return infoProvider.isAndroidModule() && !infoProvider.isGradleModule()
    }

    override fun <T : Any> createLazyValue(value: () -> T): () -> T {
        return { ClearableLazyValue.create<T> { value() }.value }
    }
}