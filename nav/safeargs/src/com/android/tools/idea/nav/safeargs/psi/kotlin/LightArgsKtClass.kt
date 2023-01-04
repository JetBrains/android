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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.psi.java.toCamelCase
import com.android.tools.idea.nav.safeargs.psi.xml.SafeArgsXmlTag
import com.android.tools.idea.nav.safeargs.psi.xml.XmlSourceElement
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.intellij.psi.impl.source.xml.XmlTagImpl
import com.intellij.psi.xml.XmlTag
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.idea.KotlinIcons
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
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.utils.Printer

/**
 * Kt class descriptors for Args classes generated from navigation xml files.
 *
 * An "Arg" represents an argument which can get passed from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 * <argument
 *    android:name="message"
 *    app:argType="string" />
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  data class FirstFragmentArgs( val message: String) : NavArgs {
 *       fun toBundle(): Bundle
 *       fun toSavedStateHandle(): SavedStateHandle
 *
 *       companion object {
 *          fun fromBundle(bundle: Bundle): FirstFragmentArgs
 *          fun fromSavedStateHandle(handle: SavedStateHandle): FirstFragmentArgs
 *       }
 *
 * ```
 */
class LightArgsKtClass(
  private val navigationVersion: GradleVersion,
  name: Name,
  private val destination: NavDestinationData,
  superTypes: Collection<KotlinType>,
  sourceElement: SourceElement,
  containingDescriptor: DeclarationDescriptor,
  private val storageManager: StorageManager
) : ClassDescriptorImpl(containingDescriptor, name, Modality.FINAL, ClassKind.CLASS, superTypes, sourceElement, false, storageManager) {

  private val _primaryConstructor = storageManager.createLazyValue { computePrimaryConstructor() }
  private val _companionObject = storageManager.createLazyValue { computeCompanionObject() }
  private val scope = storageManager.createLazyValue { ArgsClassScope() }

  override fun getUnsubstitutedMemberScope(): MemberScope = scope()
  override fun getUnsubstitutedMemberScope(kotlinTypeRefiner: KotlinTypeRefiner): MemberScope = unsubstitutedMemberScope

  override fun getConstructors() = listOf(_primaryConstructor())
  override fun getUnsubstitutedPrimaryConstructor() = _primaryConstructor()
  override fun getCompanionObjectDescriptor() = _companionObject()

  private fun computePrimaryConstructor(): ClassConstructorDescriptor {
    val valueParametersProvider = { constructor: ClassConstructorDescriptor ->
      val resolvedArguments = if (navigationVersion >= SafeArgsFeatureVersions.ADJUST_PARAMS_WITH_DEFAULTS)
        destination.arguments.sortedBy { it.defaultValue != null }
      else
        destination.arguments

      var index = 0
      resolvedArguments
        .asSequence()
        .map { arg ->
          val pName = Name.identifier(arg.name.toCamelCase())
          val pType = this.builtIns.getKotlinType(arg.type, arg.defaultValue, containingDeclaration.module, arg.isNonNull())
          val hasDefaultValue = arg.defaultValue != null
          ValueParameterDescriptorImpl(constructor, null, index++, Annotations.EMPTY, pName, pType, hasDefaultValue,
                                       false, false, null, SourceElement.NO_SOURCE)
        }
        .toList()
    }
    return this.createConstructor(valueParametersProvider)
  }

  private fun computeCompanionObject(): ClassDescriptor {
    val argsClassDescriptor = this@LightArgsKtClass
    return object : ClassDescriptorImpl(argsClassDescriptor, Name.identifier("Companion"), Modality.FINAL,
                                        ClassKind.OBJECT, emptyList(), argsClassDescriptor.source, false, storageManager) {

      private val companionObjectScope = storageManager.createLazyValue { CompanionScope() }
      private val companionObject = this
      override fun isCompanionObject() = true
      override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = null
      override fun getConstructors(): Collection<ClassConstructorDescriptor> = emptyList()
      override fun getUnsubstitutedMemberScope() = companionObjectScope()
      override fun getUnsubstitutedMemberScope(kotlinTypeRefiner: KotlinTypeRefiner): MemberScope = unsubstitutedMemberScope

      private inner class CompanionScope : MemberScopeImpl() {
        private val companionMethods = storageManager.createLazyValue {
          val methods = mutableListOf<SimpleFunctionDescriptor>()

          val fromBundleParametersProvider = { method: SimpleFunctionDescriptorImpl ->
            val bundleType = argsClassDescriptor.builtIns.getKotlinType("android.os.Bundle", null, argsClassDescriptor.module)
            val bundleParam = ValueParameterDescriptorImpl(
              method, null, 0, Annotations.EMPTY, Name.identifier("bundle"), bundleType,
              false, false, false, null, SourceElement.NO_SOURCE
            )
            listOf(bundleParam)
          }

          methods.add(companionObject.createMethod(
            name = "fromBundle",
            returnType = argsClassDescriptor.getDefaultType(),
            valueParametersProvider = fromBundleParametersProvider
          ))

          if (navigationVersion >= SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE) {
            val fromSavedStateHandleParametersProvider = { method: SimpleFunctionDescriptorImpl ->
              val handleType =
                argsClassDescriptor.builtIns.getKotlinType("androidx.lifecycle.SavedStateHandle", null, argsClassDescriptor.module)
              val handleParam = ValueParameterDescriptorImpl(
                method, null, 0, Annotations.EMPTY, Name.identifier("savedStateHandle"), handleType,
                false, false, false, null, SourceElement.NO_SOURCE
              )
              listOf(handleParam)
            }

            methods.add(companionObject.createMethod(
              name = "fromSavedStateHandle",
              returnType = argsClassDescriptor.getDefaultType(),
              valueParametersProvider = fromSavedStateHandleParametersProvider
            ))
          }

          methods
        }

        override fun getContributedDescriptors(kindFilter: DescriptorKindFilter,
                                               nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
          return companionMethods().filter { kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name) }
        }

        override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
          return companionMethods().filter { it.name == name }
        }

        override fun printScopeStructure(p: Printer) {
          p.println(this::class.java.simpleName)
        }
      }
    }
  }

  private inner class ArgsClassScope : MemberScopeImpl() {
    private val argsClassDescriptor = this@LightArgsKtClass
    private val methods = storageManager.createLazyValue {
      val methods = mutableListOf<SimpleFunctionDescriptor>()

      val bundleType = argsClassDescriptor.builtIns.getKotlinType("android.os.Bundle", null, argsClassDescriptor.module)
      val savedStateHandleType = argsClassDescriptor.builtIns.getKotlinType(
        "androidx.lifecycle.SavedStateHandle",
        null,
        argsClassDescriptor.module
      )

      // Add toBundle method.
      methods.add(
        argsClassDescriptor.createMethod(
          name = "toBundle",
          returnType = bundleType,
        )
      )

      // Add copy method.
      methods.add(
        argsClassDescriptor.createMethod(
          name = "copy",
          returnType = argsClassDescriptor.getDefaultType(),
          valueParametersProvider = { argsClassDescriptor.unsubstitutedPrimaryConstructor.valueParameters }
        )
      )

      // Add component functions.
      var index = 1
      destination.arguments
        .asSequence()
        .map { arg ->
          val methodName = "component" + index++
          val returnType = argsClassDescriptor.builtIns
            .getKotlinType(arg.type, arg.defaultValue, argsClassDescriptor.module, arg.isNonNull())
          val xmlTag = argsClassDescriptor.source.getPsi() as? XmlTag
          val resolvedSourceElement = xmlTag?.findChildTagElementByNameAttr(SdkConstants.TAG_ARGUMENT, arg.name)?.let {
            XmlSourceElement(
              SafeArgsXmlTag(
                it as XmlTagImpl,
                PlatformIcons.FUNCTION_ICON,
                methodName,
                argsClassDescriptor.fqNameSafe.asString()
              )
            )
          } ?: argsClassDescriptor.source

          argsClassDescriptor.createMethod(
            name = methodName,
            returnType = returnType,
            isOperator = true,
            sourceElement = resolvedSourceElement
          )
        }
        .map { methods.add(it) }
        .toList()

      // Add on version specific methods since the navigation library side is keeping introducing new methods.
      if (navigationVersion >= SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE) {
        methods.add(
          argsClassDescriptor.createMethod(
            name = "toSavedStateHandle",
            returnType = savedStateHandleType
          )
        )
      }

      methods
    }

    private val properties = storageManager.createLazyValue {
      destination.arguments
        .asSequence()
        .map { arg ->
          val pName = arg.name.toCamelCase()
          val pType = argsClassDescriptor.builtIns
            .getKotlinType(arg.type, arg.defaultValue, argsClassDescriptor.module, arg.isNonNull())
          val xmlTag = argsClassDescriptor.source.getPsi() as? XmlTag
          val resolvedSourceElement = xmlTag?.findChildTagElementByNameAttr(SdkConstants.TAG_ARGUMENT, arg.name)?.let {
            XmlSourceElement(
              SafeArgsXmlTag(
                it as XmlTagImpl,
                KotlinIcons.FIELD_VAL,
                arg.name,
                argsClassDescriptor.fqNameSafe.asString()
              )
            )
          } ?: argsClassDescriptor.source
          argsClassDescriptor.createProperty(pName, pType, resolvedSourceElement)
        }
        .toList()
    }

    private val classifiers = storageManager.createLazyValue { listOf(companionObjectDescriptor) }

    override fun getContributedDescriptors(
      kindFilter: DescriptorKindFilter,
      nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> {
      return methods().filter { kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name) } +
             properties().filter { kindFilter.acceptsKinds(DescriptorKindFilter.VARIABLES_MASK) && nameFilter(it.name) } +
             classifiers().filter { kindFilter.acceptsKinds(DescriptorKindFilter.SINGLETON_CLASSIFIERS_MASK) && nameFilter(it.name) }
    }

    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? {
      return classifiers().firstOrNull { it.name == name }
    }

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
      return methods().filter { it.name == name }
    }

    override fun getContributedVariables(name: Name, location: LookupLocation): List<PropertyDescriptor> {
      return properties().filter { it.name == name }
    }

    override fun printScopeStructure(p: Printer) {
      p.println(this::class.java.simpleName)
    }
  }
}
