// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.android.synthetic.idea.res

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import org.jetbrains.kotlin.android.model.AndroidModuleInfoProvider
import org.jetbrains.kotlin.android.model.AndroidModuleInfoProvider.SourceProviderMirror
import org.jetbrains.kotlin.android.synthetic.AndroidConst.SYNTHETIC_PACKAGE_PATH_LENGTH
import org.jetbrains.kotlin.android.synthetic.idea.AndroidPsiTreeChangePreprocessor
import org.jetbrains.kotlin.android.synthetic.idea.AndroidXmlVisitor
import org.jetbrains.kotlin.android.synthetic.idea.androidExtensionsIsExperimental
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayout
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutGroup
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutGroupData
import org.jetbrains.kotlin.android.synthetic.res.AndroidLayoutXmlFileManager
import org.jetbrains.kotlin.android.synthetic.res.AndroidModule
import org.jetbrains.kotlin.android.synthetic.res.AndroidModuleData
import org.jetbrains.kotlin.android.synthetic.res.AndroidResource
import org.jetbrains.kotlin.android.synthetic.res.AndroidVariant
import org.jetbrains.kotlin.android.synthetic.res.AndroidVariantData
import org.jetbrains.kotlin.android.synthetic.res.cachedValue
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

open class IDEAndroidCommonLayoutXmlFileManager(val module: Module) : AndroidLayoutXmlFileManager(module.project) {
    override val androidModule: AndroidModule?
        get() = AndroidModuleInfoProvider.getInstance(module)?.let { getAndroidModuleInfo(it) }

    @Volatile
    private var _moduleData: CachedValue<AndroidModuleData>? = null

    override fun getModuleData(): AndroidModuleData {
        if (androidModule == null) {
            _moduleData = null
        }
        else {
            if (_moduleData == null) {
                _moduleData = cachedValue(project) {
                    CachedValueProvider.Result.create(
                            super.getModuleData(),
                            getPsiTreeChangePreprocessor(), ProjectRootModificationTracker.getInstance(project))
                }
            }
        }
        return _moduleData?.value ?: AndroidModuleData.EMPTY
    }

    private fun getPsiTreeChangePreprocessor(): PsiTreeChangePreprocessor {
        return PsiTreeChangePreprocessor.EP.findExtension(AndroidPsiTreeChangePreprocessor::class.java, project)!!
    }

    protected open fun getLayouts(layoutGroup: AndroidLayoutGroupData): List<PsiFile> =
      layoutGroup.layouts.filter { it.isValid }

    override fun doExtractResources(layoutGroup: AndroidLayoutGroupData, module: ModuleDescriptor): AndroidLayoutGroup {
        val layouts = getLayouts(layoutGroup).map { layout ->
            val resources = arrayListOf<AndroidResource>()
            layout.accept(AndroidXmlVisitor { id, widgetType, attribute ->
                resources += parseAndroidResource(id, widgetType, attribute.valueElement)
            })
            AndroidLayout(resources)
        }

        return AndroidLayoutGroup(layoutGroup.name, layouts)
    }

    override fun propertyToXmlAttributes(propertyDescriptor: PropertyDescriptor): List<PsiElement> {
        val fqPath = propertyDescriptor.fqNameUnsafe.pathSegments()
        if (fqPath.size <= SYNTHETIC_PACKAGE_PATH_LENGTH) return listOf()

        fun handle(variantData: AndroidVariantData, defaultVariant: Boolean = false): List<PsiElement>? {
            val layoutNamePosition = SYNTHETIC_PACKAGE_PATH_LENGTH + (if (defaultVariant) 0 else 1)
            val layoutName = fqPath[layoutNamePosition].asString()

            val layoutFiles = variantData.layouts[layoutName] ?: return null
            if (layoutFiles.isEmpty()) return null

            val propertyName = propertyDescriptor.name.asString()

            val attributes = arrayListOf<PsiElement>()
            val visitor = AndroidXmlVisitor { retId, _, valueElement ->
                if (retId.name == propertyName) attributes.add(valueElement)
            }

            layoutFiles.forEach { it.accept(visitor) }
            return attributes
        }

        for (variantData in getModuleData().variants) {
            if (variantData.variant.isMainVariant && fqPath.size == SYNTHETIC_PACKAGE_PATH_LENGTH + 2) {
                handle(variantData, true)?.let { return it }
            }
            else if (fqPath[SYNTHETIC_PACKAGE_PATH_LENGTH].asString() == variantData.variant.name) {
                handle(variantData)?.let { return it }
            }
        }

        return listOf()
    }

    private fun SourceProviderMirror.toVariant() = let {
        val list = resDirectories.mapNotNull { it.canonicalPath }
        val fixedName = if (name != "") name else "main"  // IdeaSourceProvider's for legacy projects rename the main source set.
        AndroidVariant(fixedName, list)
    }

    private fun getAndroidModuleInfo(androidInfoProvider: AndroidModuleInfoProvider): AndroidModule? {
        val applicationPackage = androidInfoProvider.getApplicationPackage() ?: return null
        val sourceProviders =
          when (androidInfoProvider.module.androidExtensionsIsExperimental) {
              true -> androidInfoProvider.getActiveSourceProviders()
              else -> @Suppress("DEPRECATION") androidInfoProvider.getMainAndFlavorSourceProviders()
          }
        return AndroidModule(applicationPackage, sourceProviders.map { it.toVariant() })
    }
}