// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.compose

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration

internal fun KtCallableDeclaration.returnTypeClassifierFqName(): FqName? {
  val descriptor = this.resolveToDescriptorIfAny() as? CallableDescriptor ?: return null
  val returnType = descriptor.returnType ?: return null
  return returnType.fqName
}
