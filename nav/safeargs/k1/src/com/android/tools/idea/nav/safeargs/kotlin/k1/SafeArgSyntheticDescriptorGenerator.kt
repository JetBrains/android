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

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType

/** Class, method and property descriptors generator */
internal fun ClassDescriptorImpl.createMethod(
  name: String,
  returnType: KotlinType,
  isOperator: Boolean = false,
  valueParametersProvider: (SimpleFunctionDescriptorImpl) -> List<ValueParameterDescriptor> = {
    emptyList()
  },
  sourceElement: SourceElement = this.source.withFunctionIcon(name, this.fqNameSafe.asString()),
): SimpleFunctionDescriptorImpl {

  val method =
    object :
      SimpleFunctionDescriptorImpl(
        this,
        null,
        Annotations.EMPTY,
        Name.identifier(name),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        sourceElement,
      ) {
      override fun isOperator(): Boolean {
        return isOperator
      }
    }

  return method.initialize(
    null,
    this.thisAsReceiverParameter,
    emptyList(),
    valueParametersProvider(method),
    returnType,
    Modality.FINAL,
    DescriptorVisibilities.PUBLIC,
  )
}

internal fun ClassDescriptorImpl.createConstructor(
  valueParameterProvider: (ClassConstructorDescriptor) -> List<ValueParameterDescriptor> = {
    emptyList()
  }
): ClassConstructorDescriptor {
  return ClassConstructorDescriptorImpl.createSynthesized(
      this,
      Annotations.EMPTY,
      true,
      this.source,
    )
    .apply {
      this.initialize(valueParameterProvider(this), DescriptorVisibilities.PUBLIC)
      this.returnType = this@createConstructor.defaultType
    }
}

internal fun ClassDescriptorImpl.createProperty(
  name: String,
  type: KotlinType,
  sourceElement: SourceElement = SourceElement.NO_SOURCE,
): PropertyDescriptor {
  val property =
    object :
      PropertyDescriptorImpl(
        this,
        null,
        Annotations.EMPTY,
        Modality.FINAL,
        DescriptorVisibilities.PUBLIC,
        false,
        Name.identifier(name),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        sourceElement, // todo: refined navigation element
        false,
        false,
        false,
        false,
        false,
        false,
      ) {}

  property.setType(type, emptyList<TypeParameterDescriptor>(), this.thisAsReceiverParameter, null)

  val getter =
    PropertyGetterDescriptorImpl(
      property,
      Annotations.EMPTY,
      Modality.FINAL,
      DescriptorVisibilities.PUBLIC,
      false,
      false,
      false,
      CallableMemberDescriptor.Kind.SYNTHESIZED,
      null,
      sourceElement,
    )
  getter.initialize(type)

  property.initialize(getter, null)
  return property
}
