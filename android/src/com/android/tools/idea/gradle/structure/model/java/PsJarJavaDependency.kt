/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.java

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.model.PsDeclaredJarDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedJarDependency
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.getValue

class PsDeclaredJarJavaDependency constructor(
  parent: PsJavaModule
) : PsJarJavaDependency(parent), PsDeclaredJarDependency {
  override lateinit var parsedModel: DependencyModel ; private set

  fun init(parsedModel: FileDependencyModel) {
    this.parsedModel = parsedModel
  }

  fun init(parsedModel: FileTreeDependencyModel) {
    this.parsedModel = parsedModel
  }

  override val descriptor by PsDeclaredJarJavaDependency.Descriptor
  override val kind: PsJarDependency.Kind get() = when (parsedModel) {
    is FileDependencyModel -> PsJarDependency.Kind.FILE
    is FileTreeDependencyModel -> PsJarDependency.Kind.FILE_TREE
    else -> error("Unsupported dependency model: ${parsedModel.javaClass.name}")
  }
  override val filePath: String get() =
    (parsedModel as? FileDependencyModel)?.file()?.asString()
    ?: (parsedModel as? FileTreeDependencyModel)?.dir()?.asString()
    ?: ""

  private inline fun getFileTreeStringListProperty(propertyGetter: FileTreeDependencyModel.() -> ResolvedPropertyModel?) =
    (parsedModel as? FileTreeDependencyModel)?.propertyGetter()?.toList()?.map { it.resolve().asString().orEmpty() }.orEmpty()

  override val includes: List<String> get() = getFileTreeStringListProperty { includes() }
  override val excludes: List<String> get() = getFileTreeStringListProperty { excludes() }

  override val isDeclared: Boolean = true
  override val configurationName: String get() = parsedModel.configurationName()
  override val joinedConfigurationNames: String get() = configurationName

  object Descriptor : ModelDescriptor<PsDeclaredJarJavaDependency, Nothing, DependencyModel> {
    override fun getResolved(model: PsDeclaredJarJavaDependency): Nothing? = null
    override fun getParsed(model: PsDeclaredJarJavaDependency): DependencyModel? = model.parsedModel

    override fun prepareForModification(model: PsDeclaredJarJavaDependency) = Unit

    override fun setModified(model: PsDeclaredJarJavaDependency) {
      // TODO("NOTE: There is no need to re-index the declared dependency collection. Version is not a part of the key.")
      model.isModified = true
      // TODO(solodkyy): Make setModified() customizable at the property level since some properties will need to call resetDependencies().
      // TODO(b/118814130): Java resolved dependency collection is not refreshed when requested version changes
      model.parent.fireDependencyModifiedEvent(lazy {
        model.parent.dependencies.findJarDependencies(model.filePath).firstOrNull { it.configurationName == model.configurationName }
      })
    }

    override val properties: Collection<ModelProperty<PsDeclaredJarJavaDependency, *, *, *>> = listOf()
  }
}

class PsResolvedJarJavaDependency(
  parent: PsJavaModule,
  val collection: PsResolvedJavaDependencyCollection,
  override val filePath: String,
  override val declaredDependencies: List<PsDeclaredJarJavaDependency>
): PsJarJavaDependency(parent), PsResolvedJarDependency {
  override val isDeclared: Boolean get() = !declaredDependencies.isEmpty()
}

abstract class PsJarJavaDependency internal constructor(
  parent: PsJavaModule
) : PsJavaDependency(parent), PsJarDependency {

  override val name: String get() = filePath

  override fun toText(): String = filePath

  override fun toString(): String = toText()

}
