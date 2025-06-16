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

import com.android.SdkConstants
import com.android.tools.idea.nav.safeargs.SafeArgsFeature
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.module.NavEntry
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.psi.ArgumentUtils.getActionsWithResolvedArguments
import com.android.tools.idea.nav.safeargs.psi.java.toCamelCase
import com.android.tools.idea.nav.safeargs.psi.xml.SafeArgsXmlTag
import com.android.tools.idea.nav.safeargs.psi.xml.findFirstMatchingElementByTraversingUp
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.impl.source.xml.XmlTagImpl
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.utils.Printer

/**
 * Kt class descriptors for Directions classes generated from navigation xml files.
 *
 * A "Direction" represents functionality that takes you away from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 * ```
 *  <navigation>
 *    <fragment id="@+id/mainMenu">
 *      <action id="@+id/actionToOptions"
 *        destination="@id/options" />
 *      <argument
 *        android:name="message"
 *        app:argType="string" />
 *    </fragment>
 *
 *    <fragment id="@+id/options">
 *      <action id="@+id/actionToMainMenu"
 *        destination="@id/mainMenu"/>
 *     </fragment>
 *  </navigation>
 * ```
 *
 * This would generate a class like the following:
 * ```
 *  class MainMenuDirections {
 *    companion object {
 *        fun actionMainMenuToOptions(): NavDirections
 *    }
 *  }
 *
 *   class OptionsDirections {
 *    companion object {
 *        fun actionToMainMenu(message: String): NavDirections
 *    }
 *  }
 *
 * ```
 */
class LightDirectionsKtClass(
  private val navInfo: NavInfo,
  private val navEntry: NavEntry,
  name: Name,
  private val destination: NavDestinationData,
  sourceElement: SourceElement,
  containingDescriptor: DeclarationDescriptor,
  private val storageManager: StorageManager,
) :
  ClassDescriptorImpl(
    containingDescriptor,
    name,
    Modality.FINAL,
    ClassKind.CLASS,
    emptyList(),
    sourceElement,
    false,
    storageManager,
  ) {

  private val LOG
    get() = Logger.getInstance(LightDirectionsKtClass::class.java)

  private val _companionObject = storageManager.createLazyValue { computeCompanionObject() }
  private val scope = storageManager.createLazyValue { DirectionsClassScope() }

  override fun getUnsubstitutedMemberScope(): MemberScope = scope()

  override fun getUnsubstitutedMemberScope(kotlinTypeRefiner: KotlinTypeRefiner): MemberScope =
    unsubstitutedMemberScope

  override fun getConstructors(): Collection<ClassConstructorDescriptor> = emptyList()

  override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = null

  override fun getCompanionObjectDescriptor() = _companionObject()

  private fun computeCompanionObject(): ClassDescriptor {
    val directionsClassDescriptor = this@LightDirectionsKtClass
    return object :
      ClassDescriptorImpl(
        directionsClassDescriptor,
        Name.identifier("Companion"),
        Modality.FINAL,
        ClassKind.OBJECT,
        emptyList(),
        directionsClassDescriptor.source,
        false,
        storageManager,
      ) {
      private val companionScope = storageManager.createLazyValue { CompanionObjectScope() }
      private val companionObject = this

      override fun isCompanionObject() = true

      override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = null

      override fun getConstructors(): Collection<ClassConstructorDescriptor> = emptyList()

      override fun getUnsubstitutedMemberScope() = companionScope()

      override fun getUnsubstitutedMemberScope(kotlinTypeRefiner: KotlinTypeRefiner): MemberScope =
        unsubstitutedMemberScope

      private inner class CompanionObjectScope : MemberScopeImpl() {
        private val companionMethods =
          storageManager.createLazyValue {
            // action methods
            val navDirectionType =
              directionsClassDescriptor.builtIns.getKotlinType(
                "androidx.navigation.NavDirections",
                null,
                directionsClassDescriptor.module,
              )
            destination
              .getActionsWithResolvedArguments(
                navEntry.data,
                navInfo.packageName,
                adjustArgumentsWithDefaults =
                  (navInfo.navFeatures.contains(SafeArgsFeature.ADJUST_PARAMS_WITH_DEFAULTS)),
              )
              .asSequence()
              .mapNotNull { action ->
                val valueParametersProvider = { method: SimpleFunctionDescriptorImpl ->
                  var index = 0
                  action.arguments
                    .asSequence()
                    .map { arg ->
                      val pName = Name.identifier(arg.name.toCamelCase())
                      val pType =
                        directionsClassDescriptor.builtIns.getKotlinType(
                          arg.type,
                          arg.defaultValue,
                          directionsClassDescriptor.module,
                          arg.isNonNull(),
                        )
                      val hasDefaultValue = arg.defaultValue != null
                      ValueParameterDescriptorImpl(
                        method,
                        null,
                        index++,
                        Annotations.EMPTY,
                        pName,
                        pType,
                        hasDefaultValue,
                        false,
                        false,
                        null,
                        SourceElement.NO_SOURCE,
                      )
                    }
                    .toList()
                }

                val methodName = action.id.toCamelCase()
                val resolvedSourceElement =
                  (directionsClassDescriptor.source.getPsi() as? XmlTag)
                    ?.findFirstMatchingElementByTraversingUp(SdkConstants.TAG_ACTION, action.id)
                    ?.let {
                      XmlSourceElement(
                        SafeArgsXmlTag(
                          it as XmlTagImpl,
                          IconManager.getInstance().getPlatformIcon(PlatformIcons.Function),
                          methodName,
                          companionObject.fqNameSafe.asString(),
                        )
                      )
                    } ?: directionsClassDescriptor.source

                companionObject.createMethod(
                  name = methodName,
                  returnType = navDirectionType,
                  valueParametersProvider = valueParametersProvider,
                  sourceElement = resolvedSourceElement,
                )
              }
              .toList()
          }

        override fun getContributedDescriptors(
          kindFilter: DescriptorKindFilter,
          nameFilter: (Name) -> Boolean,
        ): Collection<DeclarationDescriptor> {
          return companionMethods().filter {
            kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name)
          }
        }

        override fun getContributedFunctions(
          name: Name,
          location: LookupLocation,
        ): Collection<SimpleFunctionDescriptor> {
          return companionMethods().filter { it.name == name }
        }

        override fun printScopeStructure(p: Printer) {
          p.println(this::class.java.simpleName)
        }
      }
    }
  }

  private inner class DirectionsClassScope : MemberScopeImpl() {
    private val classifiers = storageManager.createLazyValue { listOf(companionObjectDescriptor) }

    override fun getContributedDescriptors(
      kindFilter: DescriptorKindFilter,
      nameFilter: (Name) -> Boolean,
    ): Collection<DeclarationDescriptor> {
      return classifiers().filter {
        kindFilter.acceptsKinds(DescriptorKindFilter.SINGLETON_CLASSIFIERS_MASK) &&
          nameFilter(it.name)
      }
    }

    override fun getContributedClassifier(
      name: Name,
      location: LookupLocation,
    ): ClassifierDescriptor? {
      return classifiers().firstOrNull { it.name == name }
    }

    override fun printScopeStructure(p: Printer) {
      p.println(this::class.java.simpleName)
    }
  }
}
