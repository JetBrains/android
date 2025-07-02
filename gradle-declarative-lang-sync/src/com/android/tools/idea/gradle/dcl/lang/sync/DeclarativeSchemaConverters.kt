/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.sync

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.AssignmentAugmentation
import org.gradle.declarative.dsl.schema.AssignmentAugmentationKind
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.DataTypeRef.Name
import org.gradle.declarative.dsl.schema.DataTypeRef.Type
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel

fun DeclarativeSchemaModel.convertProject(): ProjectSchemas {
  val projectSchemas = projectSequence.steps.map { it.evaluationSchemaForStep.analysisSchema }
  return ProjectSchemas(projectSchemas.map { it.convert() }.toSet())
}

fun DeclarativeSchemaModel.convertSettings(): SettingsSchemas {
  val settingSchemas = settingsSequence.steps.map { it.evaluationSchemaForStep.analysisSchema }
  return SettingsSchemas(settingSchemas.map { it.convert() }.toSet())
}

@VisibleForTesting
fun AnalysisSchema.convert(): BuildDeclarativeSchema {
  val dataClasses = mutableMapOf<FullName, ClassType>()
  val topFunctions = mutableMapOf<String, SchemaFunction>()
  val augmentedTypes = mutableMapOf<FullName, List<AugmentationKind>>()
  val infixFunctions = mutableMapOf<String, SchemaFunction>()
  val schema = BuildDeclarativeSchema(null, dataClasses, topFunctions, augmentedTypes, infixFunctions)
  val top = topLevelReceiverType.convert(schema)
  schema.topLevelReceiver = top
  dataClasses.putAll(
    dataClassTypesByFqName.mapNotNull {
      keyValue -> keyValue.value.convert(schema)?.let { keyValue.key.convert() to it }
    }.toMap()
  )
  safeRun {
    dataClasses.putAll(
      genericInstantiationsByFqName.mapNotNull {
        // deliberately ignore generic type for now
        keyValue ->
        keyValue.value.values.firstOrNull()?.convert(schema)?.let { keyValue.key.convert() to it }
      }.toMap()
    )
  }
  safeRun {
    augmentedTypes.putAll(
      assignmentAugmentationsByTypeName.mapNotNull {
        // deliberately ignore generic type for now
        keyValue -> keyValue.key.convert() to keyValue.value.mapNotNull { it.convert() }
      }.toMap()
    )
  }
  //safeRun {
  //  infixFunctions.putAll(
  //    infixFunctionsByFqName.mapNotNull {
  //      keyValue ->
  //      keyValue.value.convert()?.let { keyValue.key.simpleName to it }
  //    }.toMap()
  //  )
  //}
  topFunctions.putAll(
    externalFunctionsByFqName.mapNotNull { keyValue ->
      keyValue.value.convert()?.let { keyValue.key.simpleName to it }
    }.toMap()
  )
  return schema
}

fun safeRun(f: () -> Unit) {
  try {
    f.invoke()
  }
  catch (e: Exception) {
    LOG.warn(ExternalSystemException("Caught exception during declarative schema serialization", e))
  }
}

private fun DataTopLevelFunction.convert(): SchemaFunction? {
  val parameters = parameters.mapNotNull { it.convert() }
  val convertedSemantics = semantics.convert() ?: return null
  return SchemaFunction(simpleName, parameters, convertedSemantics)
}

private fun DataType.ClassDataType.convert(schema: BuildDeclarativeSchema): ClassType? =
  when (this) {
    is EnumClass -> convert()
    is DataClass -> convert(schema)
    is DataType.ParameterizedTypeInstance -> convert()
    else -> null
  }

private fun DataClass.convert(schema: BuildDeclarativeSchema): ClassModel {
  val supertypes = supertypes.map {
    FullName(it.qualifiedName)
  }.toSet()
  val properties = properties.mapNotNull { dataProp ->
    dataProp.valueType.convert()?.let {
      DataProperty(dataProp.name, it)
    }
  }
  val memberFunctions = memberFunctions.mapNotNull { it.convert(schema) }
  return ClassModel(memberFunctions, FullName(name.qualifiedName), properties, supertypes)
}

private fun DataType.ParameterizedTypeInstance.convert(): ParameterizedClassModel =
  ParameterizedClassModel(FullName(name.qualifiedName), this.typeArguments.mapNotNull { it.convert() })

private fun EnumClass.convert(): EnumModel =
  EnumModel(FullName(name.qualifiedName), this.entryNames)

private fun AssignmentAugmentation.convert(): AugmentationKind? =
  if (this.kind is AssignmentAugmentationKind.Plus) AugmentationKind.PLUS else null

private fun FunctionSemantics.convert(): FunctionSemantic? = when (this) {
  is FunctionSemantics.AccessAndConfigure ->
    when (val type = accessor.objectType) {
      is Name -> BlockFunction(type.convert())
      is Type -> (type.dataType as? DataClass)?.let { BlockFunction(DataClassRef(FullName(it.name.qualifiedName))) }
      else -> null
    }
  is FunctionSemantics.Pure -> returnValueType.convert()?.let { PlainFunction(it) }
  is FunctionSemantics.AddAndConfigure -> {
    val configType = configuredType.convert()
    if (configureBlockRequirement.isValidIfLambdaIsPresent(true) && configType is DataClassRef) {
      BlockFunction(configType) // if lambda is possible - make it block function
    } else returnValueType.convert()?.let { PlainFunction(it) }
  }
  is FunctionSemantics.Builder -> returnValueType.convert()?.let { PlainFunction(it) }
  else -> {
    LOG.warn("Cannot recognize declarative schema class of FunctionSemantics:" + javaClass.canonicalName)
    null
  }
}

private fun SchemaMemberFunction.convert(schema: BuildDeclarativeSchema): com.android.tools.idea.gradle.dcl.lang.sync.SchemaMemberFunction? {
  val parameters = parameters.mapNotNull { it.convert() }
  val convertedReceiver = (receiver as? Name)?.convert() ?: return null
  val convertedSemantics = semantics.convert() ?: return null
  return SchemaMemberFunction(convertedReceiver, simpleName, parameters, convertedSemantics)
}

private fun DataParameter.convert() =
  type.convert()?.let { IdeDataParameter(name, it) }

private fun FqName.convert() = FullName(qualifiedName)
private fun DataType.convert(): PrimitiveType? = when (this) {
  is DataType.IntDataType -> SimpleDataType.INT
  is DataType.LongDataType -> SimpleDataType.LONG
  is DataType.UnitType -> SimpleDataType.UNIT
  is DataType.StringDataType -> SimpleDataType.STRING
  is DataType.BooleanDataType -> SimpleDataType.BOOLEAN
  is DataType.NullType -> SimpleDataType.NULL
  is DataType.TypeVariableUsage -> GenericType
  is EnumClass ->{
    LOG.warn("Cannot recognize declarative schema enum of DataType:" + javaClass.canonicalName)
    null
  }
  is DataClass -> {
    LOG.warn("Cannot recognize declarative schema class of DataType:" + javaClass.canonicalName)
    null
  }
  else -> {
    LOG.warn("Cannot recognize declarative type of DataType:" + javaClass.canonicalName)
    null
  }
}

private fun DataTypeRef.convert(): DataTypeReference? = when (this) {
  is Name -> convert()
  is Type -> dataType.convert()?.let {
    when (it) {
      is SimpleDataType -> SimpleTypeRef(it)
      is GenericType -> GenericTypeRef
    }
  }
  is DataTypeRef.NameWithArgs -> convert()
  else -> {
    LOG.warn("Cannot recognize declarative schema class of DataTypeRef:" + javaClass.canonicalName)
    null
  }
}


private fun DataTypeRef.NameWithArgs.convert(): DataClassRefWithTypes = DataClassRefWithTypes(fqName.convert(),
                                                                                              typeArguments.mapNotNull { it.convert() })

private fun DataType.ParameterizedTypeInstance.TypeArgument.convert(): GenericTypeArgument? = when (this) {
  is DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument -> type.convert()?.let { ConcreteGeneric(it) }
  is DataType.ParameterizedTypeInstance.TypeArgument.StarProjection -> StarGeneric
}

private fun Name.convert(): DataClassRef = DataClassRef(fqName.convert())

private val LOG = Logger.getInstance(GradleSchemaProjectResolver::class.java)