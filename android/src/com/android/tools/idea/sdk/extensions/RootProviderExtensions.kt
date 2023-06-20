/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:JvmName("RootProviderExtensions")

package com.android.tools.idea.sdk.extensions

import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.RootProvider
import java.util.Arrays

fun RootProvider.isEqualTo(rootProvider: RootProvider): Boolean {
  if (containsDifferentRoots(OrderRootType.CLASSES, this, rootProvider)) return false
  if (containsDifferentRoots(OrderRootType.SOURCES, this, rootProvider)) return false
  if (containsDifferentRoots(OrderRootType.DOCUMENTATION, this, rootProvider)) return false
  if (containsDifferentRoots(AnnotationOrderRootType.getInstance(), this, rootProvider)) return false
  return true
}

private fun containsDifferentRoots(
  orderRootType: OrderRootType,
  rootsA: RootProvider,
  rootsB: RootProvider
) = !Arrays.equals(rootsA.getUrls(orderRootType), rootsB.getUrls(orderRootType))