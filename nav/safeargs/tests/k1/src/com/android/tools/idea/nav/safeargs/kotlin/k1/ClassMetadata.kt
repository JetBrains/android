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

import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorVisitor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.PropertySetterDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.resolve.MemberComparator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.ResolutionScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.error.ErrorType
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny

// This class contains data collected by visiting Kotlin class descriptors, making it easy to
// check for relevant data. We don't use the rich renderers provided by the Kotlin plugin for this
// purpose, because it's too likely they'll change the implementation in the future and break us.

internal class PropertyMetadata(
  var name: String = "",
  var type: String = "",
  var isMutable: Boolean = false,
) {
  override fun toString(): String {
    return "${if (isMutable) "var" else "val"} $name: $type"
  }
}

internal class ParameterMetadata(var name: String = "", var type: String = "") {
  override fun toString(): String {
    return "$name: $type"
  }
}

internal class FunctionMetadata(
  var name: String = "",
  var params: MutableList<ParameterMetadata> = mutableListOf(),
  var returnType: String? = null,
) {
  override fun toString(): String {
    return "$name(${params.joinToString()})" + if (returnType != null) ": $returnType" else ""
  }
}

internal class ClassMetadata(
  var file: String = "",
  var fqcn: String = "",
  var supertypes: MutableList<String> = mutableListOf(),
  var companionObject: ClassMetadata? = null,
  var constructors: MutableList<FunctionMetadata> = mutableListOf(),
  var properties: MutableList<PropertyMetadata> = mutableListOf(),
  var functions: MutableList<FunctionMetadata> = mutableListOf(),
  var classifiers: MutableList<ClassMetadata> = mutableListOf(),
) {
  companion object {
    private val visitor = MetadataVisitor()

    fun fromDescriptor(descriptor: ClassDescriptor): ClassMetadata {
      return ClassMetadata().apply {
        file = descriptor.source.containingFile.name.orEmpty()
        descriptor.accept(visitor, this)
        descriptor.unsubstitutedPrimaryConstructor?.accept(visitor, this)
        descriptor.unsubstitutedMemberScope.getContributedDescriptors().forEach { desc ->
          if (desc is ClassDescriptor) {
            classifiers.add(fromDescriptor(desc))
          } else {
            desc.accept(visitor, this)
          }
        }
        descriptor.companionObjectDescriptor?.let { companionObjectDescriptor ->
          companionObject = fromDescriptor(companionObjectDescriptor)
        }
      }
    }
  }

  override fun toString(): String {
    return fqcn + if (supertypes.isNotEmpty()) ": ${supertypes.joinToString()}" else ""
  }
}

internal fun ResolutionScope.classesInScope(
  nameFilter: (String) -> Boolean = { true }
): Collection<ClassMetadata> {
  return this.getContributedDescriptors { nameFilter(it.asString()) }
    .sortedWith(MemberComparator.INSTANCE)
    .filterIsInstance<ClassDescriptor>()
    .map { desc -> ClassMetadata.fromDescriptor(desc) }
}

private fun KotlinType.asString(): String {
  return when (this) {
    is ErrorType -> debugMessage.removePrefix("Unresolved type for ")
    else -> fqName!!.asString()
  }
}

private fun List<ParameterDescriptor>.toMetadata(): MutableList<ParameterMetadata> {
  return this.map { paramDesc ->
      ParameterMetadata(paramDesc.name.asString(), paramDesc.type.asString())
    }
    .toMutableList()
}

private fun ConstructorDescriptor.toMetadata(): FunctionMetadata {
  return FunctionMetadata(constructedClass.name.asString(), explicitParameters.toMetadata())
}

private fun FunctionDescriptor.toMetadata(): FunctionMetadata {
  return FunctionMetadata(name.asString(), valueParameters.toMetadata(), returnType?.asString())
}

private fun PropertyDescriptor.toMetadata(): PropertyMetadata {
  return PropertyMetadata(name.asString(), type.asString(), setter != null)
}

private class MetadataVisitor : DeclarationDescriptorVisitor<Unit, ClassMetadata> {
  override fun visitClassDescriptor(descriptor: ClassDescriptor, data: ClassMetadata) {
    data.fqcn = descriptor.fqNameSafe.asString()
    data.supertypes =
      descriptor.typeConstructor.supertypes
        .filter { type -> !type.isAnyOrNullableAny() }
        .map { type -> type.asString() }
        .toMutableList()
  }

  override fun visitConstructorDescriptor(
    constructorDescriptor: ConstructorDescriptor,
    data: ClassMetadata,
  ) {
    data.constructors.add(constructorDescriptor.toMetadata())
  }

  override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: ClassMetadata) {
    data.functions.add(descriptor.toMetadata())
  }

  override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: ClassMetadata) {
    data.properties.add(descriptor.toMetadata())
  }

  override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: ClassMetadata) {}

  override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: ClassMetadata) {}

  override fun visitPropertyGetterDescriptor(
    descriptor: PropertyGetterDescriptor,
    data: ClassMetadata,
  ) {}

  override fun visitPropertySetterDescriptor(
    descriptor: PropertySetterDescriptor,
    data: ClassMetadata,
  ) {}

  override fun visitReceiverParameterDescriptor(
    descriptor: ReceiverParameterDescriptor,
    data: ClassMetadata,
  ) {}

  override fun visitPackageFragmentDescriptor(
    descriptor: PackageFragmentDescriptor,
    data: ClassMetadata,
  ) {}

  override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, data: ClassMetadata) {}

  override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: ClassMetadata) {}

  override fun visitTypeParameterDescriptor(
    descriptor: TypeParameterDescriptor,
    data: ClassMetadata,
  ) {}

  override fun visitValueParameterDescriptor(
    descriptor: ValueParameterDescriptor,
    data: ClassMetadata,
  ) {}

  override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: ClassMetadata) {}
}
