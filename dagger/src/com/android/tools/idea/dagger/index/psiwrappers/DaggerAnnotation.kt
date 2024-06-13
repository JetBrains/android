/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.index.psiwrappers

import com.android.tools.idea.dagger.DaggerClass
import com.android.tools.idea.dagger.DaggerClasses
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated

enum class DaggerAnnotation(val daggerClass: DaggerClass) {
  ASSISTED(DaggerClasses.Assisted),
  ASSISTED_FACTORY(DaggerClasses.AssistedFactory),
  ASSISTED_INJECT(DaggerClasses.AssistedInject),
  BINDS(DaggerClasses.Binds),
  BINDS_INSTANCE(DaggerClasses.BindsInstance),
  BINDS_OPTIONAL_OF(DaggerClasses.BindsOptionalOf),
  COMPONENT(DaggerClasses.Component),
  COMPONENT_BUILDER(DaggerClasses.ComponentBuilder),
  COMPONENT_FACTORY(DaggerClasses.ComponentFactory),
  ENTRY_POINT(DaggerClasses.EntryPoint),
  INJECT(DaggerClasses.Inject),
  MODULE(DaggerClasses.Module),
  PROVIDES(DaggerClasses.Provides),
  SUBCOMPONENT(DaggerClasses.Subcomponent);

  val classId: ClassId
    get() = daggerClass.classId

  val fqName: FqName
    get() = daggerClass.fqName

  val fqNameString: String
    get() = daggerClass.fqNameString
}

fun PsiModifierListOwner.hasAnnotation(annotation: DaggerAnnotation): Boolean =
  this.hasAnnotation(annotation.fqNameString)

fun KtAnnotated.hasAnnotation(annotation: DaggerAnnotation): Boolean =
  this.hasAnnotation(annotation.classId)
