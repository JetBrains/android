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

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.index.NavXmlIndex
import com.android.tools.idea.nav.safeargs.module.NavEntry
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.module.NavInfoFetcher
import com.android.tools.idea.nav.safeargs.psi.xml.SafeArgsXmlTag
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.impl.source.xml.XmlTagImpl
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager

/**
 * A module service which stores safe args kt package descriptors([KtArgsPackageDescriptor]s and
 * [KtDirectionsPackageDescriptor]s) by querying from [NavXmlIndex].
 */
class KtDescriptorCacheModuleService(private val module: Module) : Disposable.Default {
  private val fetcher = NavInfoFetcher(this, module, SafeArgsMode.KOTLIN)

  private data class QualifiedDescriptor(
    val fqName: FqName,
    val descriptor: PackageFragmentDescriptor
  )

  companion object {
    @JvmStatic
    fun getInstance(module: Module) =
      module.getService(KtDescriptorCacheModuleService::class.java)!!
  }

  fun getDescriptors(
    moduleDescriptor: ModuleDescriptor
  ): Map<FqName, List<PackageFragmentDescriptor>> {
    ProgressManager.checkCanceled()

    val navInfo = fetcher.getCurrentNavInfo() ?: return emptyMap()

    return navInfo.entries
      .asSequence()
      .flatMap { navEntry ->
        val sourceElement =
          navEntry.backingXmlFile?.let { XmlSourceElement(it) } ?: SourceElement.NO_SOURCE
        val navFileInfo = SafeArgsNavFileInfo(moduleDescriptor, module, navInfo, navEntry)

        val packages =
          createArgsPackages(navFileInfo, sourceElement) +
            createDirectionsPackages(navFileInfo, sourceElement)

        packages.asSequence()
      }
      .groupBy({ it.fqName }, { it.descriptor })
  }

  private fun createDirectionsPackages(
    navFileInfo: SafeArgsNavFileInfo,
    sourceElement: SourceElement,
    storageManager: StorageManager = LockBasedStorageManager.NO_LOCKS
  ): Collection<QualifiedDescriptor> {
    return navFileInfo.navEntry.data.resolvedDestinations
      .asSequence()
      .filter { destination -> destination.actions.isNotEmpty() }
      .mapNotNull { destination ->
        val fqName =
          destination.name.let { name ->
            val resolvedName =
              if (!name.startsWith('.')) name else "${navFileInfo.navInfo.packageName}$name"
            resolvedName + "Directions"
          }

        val className = fqName.substringAfterLast('.').let { Name.identifier(it) }
        val packageName = FqName(fqName.substringBeforeLast('.'))

        val resolvedSourceElement =
          (sourceElement.getPsi() as? XmlFile)?.findXmlTagById(destination.id)?.let {
            XmlSourceElement(
              SafeArgsXmlTag(
                it as XmlTagImpl,
                IconManager.getInstance().getPlatformIcon(PlatformIcons.Class),
                className.asString(),
                packageName.asString()
              )
            )
          } ?: sourceElement

        val packageDescriptor =
          KtDirectionsPackageDescriptor(
            navFileInfo,
            packageName,
            className,
            destination,
            resolvedSourceElement,
            storageManager
          )

        QualifiedDescriptor(packageName, packageDescriptor)
      }
      .toList()
  }

  private fun createArgsPackages(
    navFileInfo: SafeArgsNavFileInfo,
    sourceElement: SourceElement,
    storageManager: StorageManager = LockBasedStorageManager.NO_LOCKS
  ): Collection<QualifiedDescriptor> {
    return navFileInfo.navEntry.data.resolvedDestinations
      .asSequence()
      .filter { destination -> destination.arguments.isNotEmpty() }
      .mapNotNull { destination ->
        val fqName =
          destination.name.let { name ->
            val resolvedName =
              if (!name.startsWith('.')) name else "${navFileInfo.navInfo.packageName}$name"
            resolvedName + "Args"
          }

        val className = fqName.substringAfterLast('.').let { Name.identifier(it) }
        val packageName = FqName(fqName.substringBeforeLast('.'))

        val resolvedSourceElement =
          (sourceElement.getPsi() as? XmlFile)?.findXmlTagById(destination.id)?.let {
            XmlSourceElement(
              SafeArgsXmlTag(
                it as XmlTagImpl,
                IconManager.getInstance().getPlatformIcon(PlatformIcons.Class),
                className.asString(),
                packageName.asString()
              )
            )
          } ?: sourceElement

        val superTypesProvider = { packageDescriptor: PackageFragmentDescriptorImpl ->
          val ktType =
            packageDescriptor.builtIns.getKotlinType(
              "androidx.navigation.NavArgs",
              null,
              packageDescriptor.module
            )
          listOf(ktType)
        }

        val packageDescriptor =
          KtArgsPackageDescriptor(
            navFileInfo,
            packageName,
            className,
            destination,
            superTypesProvider,
            resolvedSourceElement,
            storageManager
          )

        QualifiedDescriptor(packageName, packageDescriptor)
      }
      .toList()
  }
}

class SafeArgsNavFileInfo(
  val moduleDescriptor: ModuleDescriptor,
  val module: Module,
  val navInfo: NavInfo,
  val navEntry: NavEntry
)
