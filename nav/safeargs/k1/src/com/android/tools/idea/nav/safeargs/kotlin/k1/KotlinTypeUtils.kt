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

import com.android.tools.idea.nav.safeargs.psi.java.getPsiTypeStr
import com.android.tools.idea.projectsystem.getModuleSystem
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.error.ErrorTypeKind
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.typeUtil.makeNullable

/**
 * Return kotlin type with nullability info.
 *
 * Or it falls back to [UnresolvedType].
 */
fun KotlinBuiltIns.getKotlinType(
  typeStr: String?,
  defaultValue: String?,
  moduleDescriptor: ModuleDescriptor,
  isNonNull: Boolean = true,
): KotlinType {
  val modulePackageName =
    moduleDescriptor.module.toModule()?.getModuleSystem()?.getPackageName() ?: ""
  val resolvedTypeStr = getPsiTypeStr(modulePackageName, typeStr, defaultValue)

  // array type
  if (resolvedTypeStr.endsWith("[]")) {
    val type = resolvedTypeStr.removeSuffix("[]")
    val arrayType =
      try {
        JvmPrimitiveType.get(type).primitiveType.let { getPrimitiveArrayKotlinType(it) }
      } catch (e: AssertionError) {
        this.getArrayType(Variance.INVARIANT, getKotlinClassType(FqName(type), moduleDescriptor))
      }
    if (isNonNull) return arrayType else return arrayType.makeNullable()
  }

  return try {
    JvmPrimitiveType.get(resolvedTypeStr).primitiveType.let { getPrimitiveKotlinType(it) }
  } catch (e: AssertionError) {
    val rawType = getKotlinClassType(FqName(resolvedTypeStr), moduleDescriptor)

    if (isNonNull) return rawType else return rawType.makeNullable()
  }
}

private fun KotlinBuiltIns.getKotlinClassType(
  fqName: FqName,
  moduleDescriptor: ModuleDescriptor,
): KotlinType {
  val classId = JavaToKotlinClassMap.mapJavaToKotlin(fqName)
  val classDescriptor =
    if (classId != null) getBuiltInClassByFqName(classId.asSingleFqName()) else null
  return classDescriptor?.defaultType
    ?: ClassId.topLevel(fqName).let {
      moduleDescriptor.findClassAcrossModuleDependencies(it)?.defaultType
    }
    ?: fqName.getUnresolvedType()
}

private fun FqName.getUnresolvedType(): KotlinType {
  val presentableName = this.toString()
  return ErrorUtils.createErrorType(ErrorTypeKind.UNRESOLVED_TYPE, presentableName)
}
