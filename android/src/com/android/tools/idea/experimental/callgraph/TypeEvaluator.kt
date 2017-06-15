/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.experimental.callgraph

import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastVisitor
import java.util.*

/**
 * Estimates the runtime types of variables.
 * For example, consider the following code
 * ```
 * Interface x = new Impl();
 * x = new SubImpl();
 * ```
 * The static type of `x` is `Interface`, but the [TypeEvaluator] could return that `x` is a subtype of `Impl`.
 */
interface TypeEvaluator : UastVisitor {
  operator fun get(v: UVariable): TypeEstimate
}

data class TypeRange(val type: PsiType, val kind: Kind) {

  enum class Kind { EXACT, SUBTYPE }

  infix fun subsumes(other: TypeRange) = when (kind) {
    Kind.EXACT -> this == other
    Kind.SUBTYPE -> type.isAssignableFrom(other.type)
  }

  infix fun covers(otherType: PsiType) = when (kind) {
    Kind.EXACT -> type == otherType
    Kind.SUBTYPE -> type.isAssignableFrom(otherType)
  }
}

/** Estimates a type using a collection of type ranges. */
data class TypeEstimate(val typeRanges: List<TypeRange> = emptyList()) {

  constructor(range: TypeRange) : this(listOf(range))

  constructor(type: PsiType, rangeKind: TypeRange.Kind) : this(TypeRange(type, rangeKind))

  companion object {
    val BOTTOM = TypeEstimate()
  }

  /** Returns the union of this type estimate with [other]. */
  operator fun plus(other: TypeEstimate): TypeEstimate = when {
    this == BOTTOM -> other
    other == BOTTOM -> this
    else -> {
      fun List<TypeRange>.filterNotSubsumedBy(list: List<TypeRange>) = this.filter { ourRange -> list.none { it subsumes ourRange } }
      val ours = typeRanges.filterNotSubsumedBy(other.typeRanges)
      val theirs = other.typeRanges.filterNotSubsumedBy(ours)
      val union = ours + theirs
      when (union) {
        typeRanges -> this
        other.typeRanges -> other
        else -> TypeEstimate(union)
      }
    }
  }

  infix fun covers(type: PsiType) = typeRanges.any { it covers type }
}

/** Estimates the runtime types of variables using a flow-insensitive traversal of the UAST. */
class StandardTypeEvaluator : TypeEvaluator, AbstractUastVisitor() {
  private val typeEstimates: MutableMap<UVariable, TypeEstimate> = HashMap()

  override fun get(v: UVariable) = typeEstimates[v] ?: TypeEstimate.BOTTOM

  private operator fun set(v: UVariable, estimate: TypeEstimate) = run { typeEstimates[v] = estimate }

  override fun visitVariable(node: UVariable): Boolean {
    node.uastInitializer?.let { handleAssign(node, it) }
    return super<AbstractUastVisitor>.visitVariable(node)
  }

  override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
    val (left, op, right) = node
    if (op == UastBinaryOperator.ASSIGN && left is USimpleNameReferenceExpression) {
      (left.resolveToUElement() as? UVariable)?.let { handleAssign(it, right) }
    }
    return super<AbstractUastVisitor>.visitBinaryExpression(node)
  }

  /** Updates the estimated type of [variable] based on the type of [expr]. */
  private fun handleAssign(variable: UVariable, expr: UExpression) {
    if (expr is UCallExpression && expr.classReference != null) {
      val concreteType = expr.returnType ?: throw Error("Constructors should have a return type")
      this[variable] += TypeEstimate(concreteType, TypeRange.Kind.EXACT)
    }
  }
}