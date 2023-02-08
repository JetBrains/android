/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getValue

class PsVersionCatalog(override val name: String, override val parent: PsProject) : PsChildModel() {
  override val descriptor by Descriptors
  override val isDeclared: Boolean = parent.isDeclared
  var parsedModel: GradleVersionCatalogModel? = null ; private set

  private var myVariables: PsVariables? = null

  val variables: PsVariablesScope
    get() {
      val prefix = if(parsedModel?.isDefault == true) "Default version catalog:" else "Version catalog:"
      val description = prefix + " $name (${parsedModel?.file?.name})"
      return myVariables ?: PsVariables(this, description, "Version catalog: $name", null).also { myVariables = it }
    }

  fun init(parsedModel: GradleVersionCatalogModel){
    this.parsedModel = parsedModel
    myVariables?.refresh()
  }

  object Descriptors : ModelDescriptor<PsVersionCatalog, Nothing, GradleVersionCatalogModel> {
    override fun getResolved(model: PsVersionCatalog): Nothing? = null
    override fun getParsed(model: PsVersionCatalog): GradleVersionCatalogModel? = model.parsedModel
    override fun prepareForModification(model: PsVersionCatalog) = Unit
    override fun setModified(model: PsVersionCatalog) {
      model.isModified = true
    }
  }

}