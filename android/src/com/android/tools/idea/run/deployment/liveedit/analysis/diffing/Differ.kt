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
package com.android.tools.idea.run.deployment.liveedit.analysis.diffing

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLabels
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLocalVariable
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrParameter
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrTryCatchBlock

private typealias ClassVisit = ClassVisitor.() -> Unit
private typealias MethodVisit = MethodVisitor.() -> Unit
private typealias FieldVisit = FieldVisitor.() -> Unit
private typealias AnnotationVisit = AnnotationVisitor.() -> Unit
private typealias ParameterVisit = ParameterVisitor.() -> Unit
private typealias TryCatchBlockVisit = TryCatchBlockVisitor.() -> Unit
private typealias LocalVariableVisit = LocalVariableVisitor.() -> Unit

object Differ {
  fun diff(old: ByteArray, new: ByteArray): ClassDiff? = diffClasses(IrClass(old), IrClass(new))
  fun diff(old: IrClass, new: IrClass) = diffClasses(old, new)
}

private fun diffClasses(old: IrClass, new: IrClass): ClassDiff? {
  if (old.name != new.name) {
    throw IllegalArgumentException("Diffing distinct classes: '${old.name}' and '${new.name}'")
  }

  // Accumulate a list of change visits, represented as closures with a receiver of type ClassVisitor. This pattern is repeated for all
  // classfile elements.
  val visits = mutableListOf<ClassVisit>()
  when {
    old.access != new.access -> visits.add { visitAccess(new.access - old.access, old.access - new.access) }
    old.signature != new.signature -> visits.add { visitSignature(old.signature, new.signature) }
    old.superName != new.superName -> visits.add { visitSuperName(old.superName, new.superName) }
    old.interfaces != new.interfaces -> visits.add { visitInterfaces(new.interfaces - old.interfaces, old.interfaces - new.interfaces) }
    old.version != new.version -> visits.add { visitVersion(old.version, new.version) }
    old.enclosingMethod != new.enclosingMethod -> visits.add { visitEnclosingMethod(old.enclosingMethod, new.enclosingMethod) }
  }

  // Associate fields based on their name; a field with a modified name is treated as a new field. Visit fields before methods so that
  // changes to fields that cause changes to methods will be reported first.
  val fields = diffLists(old.fields, new.fields, ::diffFields) { it.name }
  if (fields.added.isNotEmpty() || fields.removed.isNotEmpty() || fields.modified.isNotEmpty()) {
    visits.add { visitFields(fields.added, fields.removed, fields.modified) }
  }

  // Associate methods based on their name + descriptor (parameter types + return type); a method with a modified name/descriptor is treated
  // as a new method.
  val methods = diffLists(old.methods, new.methods, ::diffMethods) { it.name + it.desc }
  if (methods.added.isNotEmpty() || methods.removed.isNotEmpty() || methods.modified.isNotEmpty()) {
    visits.add { visitMethods(methods.added, methods.removed, methods.modified) }
  }

  // Associate annotations based on their type; changing an annotation type is treated as adding a new annotation.
  val annotations = diffLists(old.annotations, new.annotations, ::diffAnnotations) { it.desc }
  if (annotations.added.isNotEmpty() || annotations.removed.isNotEmpty() || annotations.modified.isNotEmpty()) {
    visits.add { visitAnnotations(annotations.added, annotations.removed, annotations.modified) }
  }

  if (visits.isEmpty()) {
    return null
  }

  return ClassDiffImpl(old.name, visits)
}

private fun diffMethods(old: IrMethod, new: IrMethod): MethodDiff? {
  if (old.name != new.name || old.desc != new.desc) {
    throw IllegalArgumentException("Diffing distinct methods: '${old.name}${old.desc}' and '${new.name}${new.desc}'")
  }

  val visits = mutableListOf<MethodVisit>()
  when {
    old.access != new.access -> visits.add { visitAccess(new.access - old.access, old.access - new.access) }
    old.signature != new.signature -> visits.add { visitSignature(old.signature, new.signature) }
    old.instructions != new.instructions -> visits.add { visitInstructions(old.instructions, new.instructions) }
  }

  // Associate parameters based on their index. This allows parameter renaming to be treated as a modification.
  val parameters = diffLists(old.parameters, new.parameters, ::diffParameters) { it.index }
  if (parameters.added.isNotEmpty() || parameters.removed.isNotEmpty() || parameters.modified.isNotEmpty()) {
    visits.add { visitParameters(parameters.added, parameters.removed, parameters.modified) }
  }

  // Associate local variables based on their index. We cannot associate based on the name, since multiple local variables may share a name.
  val localVariables = diffLists(old.localVariables, new.localVariables, ::diffLocalVars) { it.index }
  if (localVariables.added.isNotEmpty() || localVariables.removed.isNotEmpty() || localVariables.modified.isNotEmpty()) {
    visits.add { visitLocalVariables(localVariables.added, localVariables.removed, localVariables.modified) }
  }

  // Associate try/catch blocks based on their position in the code; if a block has changed position, treat it as a new block.
  val tryCatchBlocks = diffLists(old.tryCatchBlocks, new.tryCatchBlocks, ::diffTryCatch) {
    Triple(it.start.index, it.handler.index, it.end.index)
  }
  if (tryCatchBlocks.added.isNotEmpty() || tryCatchBlocks.removed.isNotEmpty() || tryCatchBlocks.modified.isNotEmpty()) {
    visits.add { visitTryCatchBlocks(tryCatchBlocks.added, tryCatchBlocks.removed, tryCatchBlocks.modified) }
  }

  // Associate annotations based on their type; changing an annotation type is treated as adding a new annotation.
  val annotations = diffLists(old.annotations, new.annotations, ::diffAnnotations) { it.desc }
  if (annotations.added.isNotEmpty() || annotations.removed.isNotEmpty() || annotations.modified.isNotEmpty()) {
    visits.add { visitAnnotations(annotations.added, annotations.removed, annotations.modified) }
  }

  if (visits.isEmpty()) {
    return null
  }

  return MethodDiffImpl(old.name, old.desc, visits)
}

private fun diffFields(old: IrField, new: IrField): FieldDiff? {
  if (old.name != new.name) {
    throw IllegalArgumentException("Diffing distinct fields: '${old.name}' and '${new.name}'")
  }

  val visits = mutableListOf<FieldVisit>()
  when {
    old.desc != new.desc -> visits.add { visitDesc(old.desc, new.desc) }
    old.access != new.access -> visits.add { visitAccess(new.access - old.access, old.access - new.access) }
    old.signature != new.signature -> visits.add { visitSignature(old.signature, new.signature) }
    old.value != new.value -> visits.add { visitValue(old.value, new.value) }
  }

  // Associate annotations based on their type; changing an annotation type is treated as adding a new annotation.
  val annotations = diffLists(old.annotations, new.annotations, ::diffAnnotations) { it.desc }
  if (annotations.added.isNotEmpty() || annotations.removed.isNotEmpty() || annotations.modified.isNotEmpty()) {
    visits.add { visitAnnotations(annotations.added, annotations.removed, annotations.modified) }
  }

  if (visits.isEmpty()) {
    return null
  }

  return FieldDiffImpl(old.name, visits)
}

private fun diffParameters(old: IrParameter, new: IrParameter): ParameterDiff? {
  if (old.index != new.index) {
    throw IllegalArgumentException("Diffing parameters with different indices: ${old.index} and ${new.index}")
  }

  val visits = mutableListOf<ParameterVisit>()
  when {
    old.name != new.name -> visits.add { visitName(old.name, new.name) }
    old.access != new.access -> visits.add { visitAccess(new.access - old.access, old.access - new.access) }
  }

  // Associate annotations based on their type; changing an annotation type is treated as adding a new annotation.
  val annotations = diffLists(old.annotations, new.annotations, ::diffAnnotations) { it.desc }
  if (annotations.added.isNotEmpty() || annotations.removed.isNotEmpty() || annotations.modified.isNotEmpty()) {
    visits.add { visitAnnotations(annotations.added, annotations.removed, annotations.modified) }
  }

  if (visits.isEmpty()) {
    return null
  }

  return ParameterDiffImpl(old.index, visits)
}

private fun diffLocalVars(old: IrLocalVariable, new: IrLocalVariable): LocalVariableDiff? {
  if (old.index != new.index) {
    throw IllegalArgumentException("Diffing distinct local variables:${old.index} and ${new.index}")
  }

  val visits = mutableListOf<LocalVariableVisit>()
  when {
    old.name != new.name -> visits.add { visitName(old.name, new.name) }
    old.desc != new.desc -> visits.add { visitDesc(old.desc, new.desc) }
    old.signature != new.signature -> visits.add { visitSignature(old.signature, new.signature) }
    old.start != new.start -> visits.add { visitStart(old.start, new.start) }
    old.end != new.end -> visits.add { visitEnd(old.end, new.end) }
  }

  if (visits.isEmpty()) {
    return null
  }

  return LocalVariableDiffImpl(old.index, visits)
}

private fun diffTryCatch(old: IrTryCatchBlock, new: IrTryCatchBlock): TryCatchBlockDiff? {
  if (old.start != new.start || old.handler != new.handler || old.end != new.end) {
    throw IllegalArgumentException("Comparing distinct try/catch blocks")
  }

  if (old.type == new.type) {
    return null
  }

  return TryCatchBlockDiffImpl(old.start, old.handler, old.end, listOf { visitType(old.type, new.type) })
}

private fun diffAnnotations(old: IrAnnotation, new: IrAnnotation): AnnotationDiff? {
  if (old.desc != new.desc) {
    throw IllegalArgumentException("Diffing distinct annotations: '${old.desc}' and '${new.desc}'")
  }

  if (old.values == new.values) {
    return null
  }

  return AnnotationDiffImpl(old.desc, listOf { visitValues(old.values, new.values) })
}

/**
 * Produce a diff of two lists of items of [Type].
 *
 * @param old the original list of items. Items present in [old] not in [new] will be added to [ListDiff.removed]
 * @param new the new list of items. Items present in [new] but not in [old] will be added to [ListDiff.added]
 * @param  diffFunc a function to compare items with the same key and return a diff. Should return null if there is no difference
 * @param keyFunc a function to extract a key from each item. Items with the same key will be diffed with [diffFunc] and the result, if not
 * null, will be added to [ListDiff.modified]
 */
private fun <Type, DiffType> diffLists(old: List<Type>,
                                       new: List<Type>,
                                       diffFunc: (Type, Type) -> DiffType?,
                                       keyFunc: (Type) -> Any): ListDiff<Type, DiffType> {
  val added = mutableListOf<Type>()
  val removed = mutableListOf<Type>()
  val modified = mutableListOf<DiffType>()

  val oldMap = old.associateBy { keyFunc(it) }
  val newMap = new.associateBy { keyFunc(it) }
  (oldMap.keys + newMap.keys).forEach {
    val oldValue = oldMap[it]
    val newValue = newMap[it]
    if (oldValue != null && newValue != null) {
      diffFunc(oldValue, newValue)?.let { diff -> modified.add(diff) }
    } else if (newValue != null) {
      added.add(newValue)
    } else if (oldValue != null) {
      removed.add(oldValue)
    }
  }
  return ListDiff(added, removed, modified)
}

private class ListDiff<Type, DiffType>(val added: List<Type>, val removed: List<Type>, val modified: List<DiffType>)

private class ClassDiffImpl(override val name: String, val visits: List<ClassVisit>) : ClassDiff {
  override fun accept(visitor: ClassVisitor) = visits.forEach { visitor.it() }
}

private class MethodDiffImpl(override val name: String, override val desc: String, val visits: List<MethodVisit>) : MethodDiff {
  override fun accept(visitor: MethodVisitor) = visits.forEach { visitor.it() }
}

private class FieldDiffImpl(override val name: String, val visits: List<FieldVisit>) : FieldDiff {
  override fun accept(visitor: FieldVisitor) = visits.forEach { visitor.it() }
}

private class ParameterDiffImpl(override val index: Int, val visits: List<ParameterVisit>) : ParameterDiff {
  override fun accept(visitor: ParameterVisitor) = visits.forEach { visitor.it() }
}

private class LocalVariableDiffImpl(override val index: Int, val visits: List<LocalVariableVisit>) : LocalVariableDiff {
  override fun accept(visitor: LocalVariableVisitor) = visits.forEach { visitor.it() }
}

private class AnnotationDiffImpl(override val desc: String, val visits: List<AnnotationVisit>) : AnnotationDiff {
  override fun accept(visitor: AnnotationVisitor) = visits.forEach { visitor.it() }
}

private class TryCatchBlockDiffImpl(override val start: IrLabels.IrLabel,
                                    override val handler: IrLabels.IrLabel,
                                    override val end: IrLabels.IrLabel,
                                    val visits: List<TryCatchBlockVisit>) : TryCatchBlockDiff {
  override fun accept(visitor: TryCatchBlockVisitor) = visits.forEach { visitor.it() }
}