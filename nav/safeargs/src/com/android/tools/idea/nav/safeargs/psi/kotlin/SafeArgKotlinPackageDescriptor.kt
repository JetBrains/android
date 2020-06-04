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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.tools.idea.nav.safeargs.module.SafeArgsResourceForKtDescriptors
import com.android.tools.idea.nav.safeargs.psi.xml.XmlSourceElement
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.utils.Printer
import org.jetbrains.kotlin.utils.alwaysTrue

class SafeArgSyntheticPackageResourceData(val moduleNavResource: Collection<SafeArgsResourceForKtDescriptors.NavEntryKt>)

/**
 * Kt module-wise descriptors
 */
class SafeArgKotlinPackageDescriptor(
  module: ModuleDescriptor,
  fqName: FqName,
  val packageResourceData: SafeArgSyntheticPackageResourceData,
  private val storageManager: StorageManager
) : PackageFragmentDescriptorImpl(module, fqName) {
  private val scope = storageManager.createLazyValue { SafeArgModuleScope() }
  override fun getMemberScope(): MemberScope = scope()
  private val safeArgPackageDescriptor = this@SafeArgKotlinPackageDescriptor

  private inner class SafeArgModuleScope : MemberScopeImpl() {
    private val classes = storageManager.createLazyValue {
      packageResourceData.moduleNavResource
        .asSequence()
        .flatMap { navEntry ->
          generateSafeArgsClasses(navEntry, safeArgPackageDescriptor, storageManager).asSequence()
        }
        .toList()
    }

    private fun generateSafeArgsClasses(
      navEntry: SafeArgsResourceForKtDescriptors.NavEntryKt,
      packageDescriptor: SafeArgKotlinPackageDescriptor,
      storageManager: StorageManager
    ): Collection<ClassDescriptor> {
      val backingXmlFile = PsiManager.getInstance(navEntry.project).findFile(navEntry.file)
      // xml-wise
      val sourceElement = backingXmlFile?.let { XmlSourceElement(it as PsiElement) } ?: SourceElement.NO_SOURCE

      return createLightArgsClasses(navEntry, sourceElement, packageDescriptor, storageManager) +
             createLightDirectionsClasses(navEntry, sourceElement, packageDescriptor, storageManager)
    }

    private fun createLightDirectionsClasses(
      entry: SafeArgsResourceForKtDescriptors.NavEntryKt,
      sourceElement: SourceElement,
      packageDescriptor: SafeArgKotlinPackageDescriptor,
      storageManager: StorageManager
    ): Collection<ClassDescriptor> {
      return entry.data.root.allDestinations
        .asSequence()
        .filter { destination -> destination.actions.isNotEmpty() }
        .map { destination ->
          val className = destination.name.substringAfterLast('.').let {
            Name.identifier(it + "Directions")
          }
          val resolvedSourceElement = (sourceElement.getPsi() as? XmlFile)
                                        ?.findXmlTagById(destination.id)
                                        ?.let { XmlSourceElement(it) }
                                      ?: sourceElement
          LightDirectionsKtClass(className, destination, entry.data, resolvedSourceElement, packageDescriptor, storageManager)
        }
        .toList()
    }

    private fun createLightArgsClasses(
      entry: SafeArgsResourceForKtDescriptors.NavEntryKt,
      sourceElement: SourceElement,
      packageDescriptor: SafeArgKotlinPackageDescriptor,
      storageManager: StorageManager
    ): Collection<ClassDescriptor> {
      return entry.data.root.allFragments
        .asSequence()
        .filter { fragment -> fragment.arguments.isNotEmpty() }
        .map { fragment ->
          val className = fragment.name.substringAfterLast('.').let {
            Name.identifier(it + "Args")
          }
          val navArgType = packageDescriptor.builtIns.getKotlinType("androidx.navigation.NavArgs", null, packageDescriptor.module)
          val resolvedSourceElement = (sourceElement.getPsi() as? XmlFile)
                                        ?.findXmlTagById(fragment.id)
                                        ?.let { XmlSourceElement(it) }
                                      ?: sourceElement
          LightArgsKtClass(className, fragment, listOf(navArgType), resolvedSourceElement, packageDescriptor, storageManager)
        }
        .toList()
    }

    override fun getContributedDescriptors(
      kindFilter: DescriptorKindFilter,
      nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> {

      return classes().filter { kindFilter.acceptsKinds(DescriptorKindFilter.CLASSIFIERS_MASK) && nameFilter(it.name) }
    }

    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? {
      return classes().firstOrNull { it.name == name }
    }

    override fun getClassifierNames(): Set<Name> {
      return getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS, alwaysTrue()).filterIsInstance<ClassifierDescriptor>().mapTo(
        mutableSetOf()) { it.name }
    }

    override fun printScopeStructure(p: Printer) {
      p.println(this::class.java.simpleName)
    }
  }
}