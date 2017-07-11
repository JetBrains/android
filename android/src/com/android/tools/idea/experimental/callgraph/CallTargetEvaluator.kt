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

import com.google.common.collect.HashMultimap
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Maps expressions and variables to likely receivers, including classes and lambdas, using static analysis.
 * For example, consider the following code
 * ```
 * Runnable r = Foo::bar();
 * r.run();
 * ```
 * The call to `run` resolves to the base method in `Runnable`, but we want to know that it will actually dispatch to `Foo#bar`.
 * This information is captured by mapping `r` to the method reference `Foo::bar`.
 *
 * Note that call receiver evaluators often compose with and augment each other.
 */
interface CallReceiverEvaluator {

  operator fun get(element: UElement): Collection<Receiver>

  /** Evaluates potential receivers for `this` separately, since `this` can be implicit. */
  fun getForImplicitThis(): Collection<Receiver>
}

/** Represents a potential call handler, such as a class or lambda expression. */
sealed class Receiver(open val element: UElement) {
  data class Class(override val element: UClass) : Receiver(element)
  data class Lambda(override val element: ULambdaExpression) : Receiver(element)
  data class CallableReference(override val element: UCallableReferenceExpression) : Receiver(element)
}

/** Tries to map expressions to receivers without relying on any context. */
class SimpleExpressionReceiverEvaluator : CallReceiverEvaluator {

  override fun get(element: UElement): Collection<Receiver> = when {
    element is ULambdaExpression -> listOf(Receiver.Lambda(element))
    element is UCallableReferenceExpression -> listOf(Receiver.CallableReference(element))
    element is UObjectLiteralExpression -> listOf(Receiver.Class(element.declaration))
    element is UCallExpression && element.kind == UastCallKind.CONSTRUCTOR_CALL -> {
      val instantiatedClass = (element.returnType as? PsiClassType)?.resolve()?.toUElementOfType<UClass>()
      listOfNotNull(instantiatedClass?.let { Receiver.Class(it) })
    }
    else -> emptyList()
  }

  override fun getForImplicitThis(): Collection<Receiver> = emptyList()
}

/** Uses a flow-insensitive UAST traversal to map variables to potential receivers based on local context. */
class IntraproceduralReceiverVisitor : AbstractUastVisitor(), CallReceiverEvaluator {
  private val simpleExprReceiverEval = SimpleExpressionReceiverEvaluator()
  private val varMap = HashMultimap.create<UVariable, Receiver>()

  override fun get(element: UElement): Collection<Receiver> = when (element) {
    is UVariable -> varMap[element]
    is USimpleNameReferenceExpression -> element.resolveToUElement()?.let { this[it] } ?: emptyList() // Recurse when resolved.
    else -> simpleExprReceiverEval[element]
  }

  override fun getForImplicitThis(): Collection<Receiver> = emptyList()

  override fun visitVariable(node: UVariable): Boolean {
    node.uastInitializer?.let { handleAssign(node, it) }
    return super.visitVariable(node)
  }

  override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
    val (left, op, right) = node
    if (op == UastBinaryOperator.ASSIGN && left is USimpleNameReferenceExpression) {
      (left.resolveToUElement() as? UVariable)?.let { handleAssign(it, right) }
    }
    return super.visitBinaryExpression(node)
  }

  private fun handleAssign(v: UVariable, expr: UExpression): Unit {
    varMap.putAll(v, this[expr])
  }
}

/** Convert this call expression into a list of likely targets given a call receiver evaluator. */
fun UCallExpression.getTargets(receiverEval: CallReceiverEvaluator): Collection<CallTarget> {
  // TODO: Need to test with Kotlin to make sure lambda calls are handled correctly (since they don't go through functional interfaces)
  val resolved = this.resolveToUElement() as? UMethod ?: return emptyList()
  if (resolved.isStatic)
    return listOf(CallTarget.Method(resolved))
  val receivers = receiver?.let { receiverEval[it] } ?: receiverEval.getForImplicitThis()
  fun refine(method: UMethod, clazz: UClass): PsiMethod? = clazz.findMethodBySignature(method, /*checkBases*/ true)
  fun isFunctionalCall() = resolved.psi == LambdaUtil.getFunctionalInterfaceMethod(receiverType)
  fun ULambdaExpression.toLambdaTarget() = CallTarget.Lambda(this)
  fun UCallableReferenceExpression.toMethodTarget() = (resolveToUElement() as? UMethod)?.let { CallTarget.Method(it) }
  return receivers.mapNotNull { receiver ->
    when (receiver) {
      is Receiver.Class -> refine(resolved, receiver.element)?.toUElementOfType<UMethod>()?.let { CallTarget.Method(it) }
      is Receiver.Lambda -> if (isFunctionalCall()) receiver.element.toLambdaTarget() else null
      is Receiver.CallableReference -> if (isFunctionalCall()) receiver.element.toMethodTarget() else null
    }
  }
}