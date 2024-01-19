/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k1

import com.android.tools.idea.nav.safeargs.psi.xml.SafeArgsXmlTag
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.idea.base.projectStructure.unwrapModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import org.jetbrains.kotlin.resolve.source.getPsi

internal fun Module.getDescriptorsByModulesWithDependencies():
  Map<FqName, List<PackageFragmentDescriptor>> {
  val moduleDescriptor = this.toDescriptor() ?: return emptyMap()
  val descriptorsFromThisModule =
    KtDescriptorCacheModuleService.getInstance(this).getDescriptors(moduleDescriptor).toMutableMap()
  return ModuleRootManager.getInstance(this)
    .getDependencies(false)
    .asSequence()
    .map {
      ProgressManager.checkCanceled()
      it
    }
    .mapNotNull {
      val descriptor = it?.toDescriptor() ?: return@mapNotNull null
      KtDescriptorCacheModuleService.getInstance(it).getDescriptors(descriptor)
    }
    .fold(descriptorsFromThisModule) { acc, curr ->
      // TODO(b/159954452): duplications(e.g Same fragment class declared across multiple nav
      // resource files) need to be
      //  resolved.
      curr.entries.forEach { entry -> acc.merge(entry.key, entry.value) { old, new -> old + new } }
      acc
    }
}

internal fun ModuleInfo.toModule(): Module? {
  return this.unwrapModuleSourceInfo()?.takeIf { it.platform.isJvm() }?.module
}

internal fun ModuleDescriptor.toModule(): Module? {
  return this.moduleInfo?.toModule()
}

class XmlSourceElement(override val psi: PsiElement) : PsiSourceElement

internal fun SourceElement.withFunctionIcon(
  name: String,
  containingClassName: String,
): SourceElement {
  return (this.getPsi() as? SafeArgsXmlTag)?.let {
    XmlSourceElement(
      SafeArgsXmlTag(
        it.getOriginal(),
        IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function),
        name,
        containingClassName,
      )
    )
  } ?: this
}
