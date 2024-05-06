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
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.dependencies.AbstractDependenciesModel.DependencyReplacer
import com.android.tools.idea.gradle.dsl.model.dependencies.AbstractDependenciesModel.Fetcher
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModelImpl.Maintainer
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.intellij.psi.PsiElement

// Dependencies model for gralde.toml - declarative
class DeclarativeDependenciesModelImpl(dslElement: DependenciesDslElement) : AbstractDependenciesModel(dslElement) {

  override fun getArtifactFetcher(): Fetcher<ArtifactDependencyModel> = object : Fetcher<ArtifactDependencyModel> {
    override fun fetch(
      configurationName: String,
      element: GradleDslElement,
      resolved: GradleDslElement,
      configurationElement: GradleDslClosure?,
      maintainer: Maintainer,
      dest: MutableList<in ArtifactDependencyModel>
    ) {
      if (element !is GradleDslExpression) return

      if (element is GradleDslExpressionMap) { // Declarative map notation
        val map: Map<String, GradleDslElement> = element.elements
        val notation = map["notation"]
        if (notation is GradleDslLiteral) {
          ArtifactDependencyModelImpl.DynamicNotation.create(
            configurationName, notation, configurationElement, maintainer, null
          )?.let { dest.add(it) }
        } else if (map["alias"] is GradleDslLiteral) {
          val notation: ArtifactDependencyModel? = ArtifactDependencyModelImpl.DynamicNotation.create(
            configurationName, (map["alias"] as GradleDslLiteral), configurationElement, maintainer, null
          )
          if (notation != null) { // TODO add version catalog check
            dest.add(notation) // having reference to catalog require additional settings
            notation.markAsVersionCatalogDependency()
            notation.enableSetThrough()
          }
        } else {
          val notation: ArtifactDependencyModel? = ArtifactDependencyModelImpl.DynamicNotation.create(
            configurationName, (element as GradleDslExpression), configurationElement, maintainer, null
          )
          if (notation != null) {
            dest.add(notation)
          }
        }
      }
    }
  }

  override fun addModule(configurationName: String, path: String, config: String?) {
    DeclarativeModuleDependencyModelImpl.createNew(myDslElement, configurationName, path, config)
  }

  override fun getModuleFetcher(): Fetcher<ModuleDependencyModel> = object : Fetcher<ModuleDependencyModel> {
    override fun fetch(
      configurationName: String,
      element: GradleDslElement,
      resolved: GradleDslElement,
      configurationElement: GradleDslClosure?,
      maintainer: Maintainer,
      dest: MutableList<in ModuleDependencyModel>
    ) {
      if (element is GradleDslExpressionMap) { // Declarative map notation
        val map: Map<String, GradleDslElement> = element.elements
        val project = map["project"]
        if (project is GradleDslLiteral) {
          val model = DeclarativeModuleDependencyModelImpl(configurationName, element, maintainer)
          if (model.path().getValueType() != GradlePropertyModel.ValueType.NONE) {
            dest.add(model)
          }
        }
      }
    }
  }

  override fun getFileTreeFetcher(): Fetcher<FileTreeDependencyModel> {
    return Fetcher<FileTreeDependencyModel> { configurationName, element, resolved, configurationElement, maintainer, dest -> }
  }

  override fun getFileFetcher(): Fetcher<FileDependencyModel> {
    return Fetcher<FileDependencyModel> { configurationName, element, resolved, configurationElement, maintainer, dest -> }
  }

  override fun findByPsiElement(child: PsiElement): GradleDslElement? {
    for (configurationName in myDslElement.properties) {
      for (element in myDslElement.getPropertyElementsByName(configurationName)) {
        if (element is GradleDslExpressionList) {
          for (e in element.expressions) { // for declarative syntax children are Maps
            if (e.getPsiElement() != null && isChildOfParent(child, e.getPsiElement()!!)) {
              return e
            }
          }
        } else {
          if (element.getPsiElement() != null && isChildOfParent(child, element.getPsiElement()!!)) {
            return element
          }
        }
      }
    }
    return null
  }

  override fun <T : DependencyModel> collectFrom(
    configurationName: String,
    element: GradleDslElement,
    byFetcher: Fetcher<T>,
    dest: MutableList<T>
  ) {
    val configurationElement = element.getClosureElement()
    val resolved = resolveElement(element)
    if (resolved is GradleDslExpressionList) {
      for (expression in resolved.expressions) {
        val resolvedExpression = resolveElement(expression!!)
        byFetcher
          .fetch(configurationName, expression, resolvedExpression, configurationElement, listMaintainer, dest)
      }
      return
    }
    return
  }

  private val listMaintainer = object: Maintainer{
    override fun setConfigurationName(dependencyModel:DependencyModelImpl,  newConfigurationName:String):Maintainer  {
      val dslElement = dependencyModel.getDslElement()
      val parentList = dslElement.getParent()?.getParent() ?: return this
      parentList.getNameElement().rename(newConfigurationName);
      parentList.setModified()
      return this
    }
  }

  override fun getDependencyReplacer(): DependencyReplacer {
    return DependencyReplacer { _, element, dependency ->
      if (element is GradleDslExpressionMap) {
        val notation: GradleDslElement? = element?.getPropertyElement("notation")
        if (notation is GradleDslLiteral) notation.setValue(dependency.compactNotation()) else {
          updateGradleExpressionMapWithDependency((element as GradleDslExpressionMap), dependency)
        }
      }
    }
  }
}