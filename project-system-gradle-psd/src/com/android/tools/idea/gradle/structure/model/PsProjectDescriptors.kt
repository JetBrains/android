/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.model.helpers.androidGradlePluginVersionValues
import com.android.tools.idea.gradle.structure.model.helpers.versionValues
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.KnownValues
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ResolvedValue
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ValueAnnotation
import com.android.tools.idea.gradle.structure.model.meta.VariableMatchingStrategy
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.maybeLiteralValue
import com.android.tools.idea.gradle.structure.model.meta.property
import com.google.common.util.concurrent.ListenableFuture
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import kotlin.reflect.KProperty

object PsProjectDescriptors : ModelDescriptor<PsProject, Nothing, ProjectBuildModel> {
  private const val AGP_GROUP_ID_NAME = "com.android.tools.build:gradle"
  override fun getResolved(model: PsProject): Nothing? = null
  override fun getParsed(model: PsProject): ProjectBuildModel? = model.parsedModel
  override fun prepareForModification(model: PsProject) = Unit
  override fun setModified(model: PsProject) {
    model.isModified = true
  }

  val androidGradlePluginVersion: SimpleProperty<PsProject, String> = run {
    fun ArtifactDependencyModel.isAgp() = configurationName() == "classpath" && compactNotation().startsWith("$AGP_GROUP_ID_NAME:")
    fun PluginModel.isAgp() = name().toString().startsWith("com.android.")

    property(
      "Android Gradle Plugin Version",
      resolvedValueGetter = { null },
      parsedPropertyGetter = {
        val models: List<ResolvedPropertyModel>? =
          projectBuildModel?.run {
            buildscript().dependencies().all()
              .mapNotNull { it as? ArtifactDependencyModel }
              .singleOrNull { it.isAgp() }
              ?.version()
              ?.let { listOf(it) }
            ?: plugins()
              .filter { it.isAgp() }
              .ifNotEmpty { map { it.version() } }
          }
          ?: projectSettingsModel?.run {
            pluginManagement().plugins().plugins().filter { it.isAgp() }.ifNotEmpty { map { it.version() } }
          }
        when {
          models == null -> null
          models.isEmpty() -> error("AGP version parsed property implementation inconsistency")
          else ->
            object : ResolvedPropertyModel by models[0] {
              override fun setValue(value: Any) {
                models.forEach { it.setValue(value) }
              }
            }
        }
      },
      parsedPropertyInitializer = {
        projectBuildModel!!
          .buildscript()
          .dependencies()
          .let { dependencies ->
            dependencies.addArtifact("classpath", "$AGP_GROUP_ID_NAME:0.0")
            dependencies.all()
              .mapNotNull { it as? ArtifactDependencyModel }
              .single { it.isAgp() }
              .version()
          }
      },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      knownValuesGetter = ::androidGradlePluginVersionValues,
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE,
      preferredVariableName = { "agp_version" },
      variableScope = { buildScriptVariables }
    )
  }

  val gradleVersion: SimpleProperty<PsProject, String> = run {
    object : ModelSimpleProperty<PsProject, String> {

      override fun bindContext(model: PsProject): ModelPropertyContext<String> =
        object : ModelPropertyContext<String> {
          override fun parse(value: String): Annotated<ParsedValue<String>> = when {
            value.isBlank() -> ParsedValue.NotSet.annotateWithError("Required.")
            else -> ParsedValue.Set.Parsed(value, DslText.Literal).annotated()
          }

          override fun parseEditorText(text: String): Annotated<ParsedValue<String>> = parse(text)

          override fun format(value: String): String = value

          override fun getKnownValues(): ListenableFuture<KnownValues<String>> = versionValues()
        }

      override val description: String = "Gradle Version"

      override fun bind(model: PsProject): ModelPropertyCore<String> =
        object : ModelPropertyCore<String> {
          override fun getParsedValue(): Annotated<ParsedValue<String>> =
            model
              .getGradleVersionValue(notApplied = true)
              ?.let { ParsedValue.Set.Parsed(it, DslText.Literal).annotated() }
            ?: ParsedValue.NotSet.annotated()

          override fun setParsedValue(value: ParsedValue<String>) {
            value.maybeLiteralValue?.let { model.setGradleVersionValue(it) }
          }

          override val isModified: Boolean?
            get() =
              model.getGradleVersionValue(true) != model.getGradleVersionValue(false)

          override fun getResolvedValue(): ResolvedValue<String> = ResolvedValue.NotResolved()

          override val description: String = "Gradle Version"
          override val defaultValueGetter: (() -> String?)? = null
          override val variableScope: (() -> PsVariablesScope?)? = { PsVariablesScope.NONE }
          override fun annotateParsedResolvedMismatch(): ValueAnnotation? = null
        }

      override fun getValue(thisRef: PsProject, property: KProperty<*>): ParsedValue<String> =
        bind(thisRef).getParsedValue().value

      override fun setValue(thisRef: PsProject, property: KProperty<*>, value: ParsedValue<String>) =
        bind(thisRef).setParsedValue(value)
    }
  }

  override fun enumerateModels(model: PsProject): Collection<PsModel> = model.modules
  override val properties: Collection<ModelProperty<PsProject, *, *, *>> = listOf(androidGradlePluginVersion)
}