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

import com.google.common.collect.LinkedHashMultimap
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Maps calls to likely targets, including methods and lambdas, using static analysis.
 * For example, consider the following code
 * ```
 * Runnable r = Foo::bar();
 * r.run();
 * ```
 * The call to `run` resolves to the base method in `Runnable`, but we want to know that it will actually dispatch to `Foo#bar`.
 */
interface CallTargetEvaluator {
  operator fun get(call: UCallExpression): Collection<CallTarget>
}

private sealed class Receiver(open val element: UElement) {
  data class Class(override val element: UClass) : Receiver(element)
  data class Lambda(override val element: ULambdaExpression) : Receiver(element)
  data class CallableReference(override val element: UCallableReferenceExpression) : Receiver(element)
}

/** Uses a flow-insensitive UAST traversal to map variables to potential receivers. */
class CallTargetVisitor : AbstractUastVisitor(), CallTargetEvaluator {
  private val potentialReceivers = LinkedHashMultimap.create<UVariable, Receiver>()

  override fun get(call: UCallExpression): Collection<CallTarget> {
    // For now we only consider variable receivers.
    val variable = (call.receiver as? USimpleNameReferenceExpression)?.resolveToUElement() as? UVariable ?: return emptyList()
    val resolved = call.resolveToUElement() as? UMethod ?: return emptyList()
    fun refine(method: UMethod, clazz: UClass): PsiMethod? = clazz.findMethodBySignature(method, /*checkBases*/ true)
    fun isFunctionalCall() = resolved.psi == LambdaUtil.getFunctionalInterfaceMethod(variable.getType())
    fun ULambdaExpression.toLambdaTarget() = CallTarget.Lambda(this)
    fun UCallableReferenceExpression.toMethodTarget() = (resolveToUElement() as? UMethod)?.let { CallTarget.Method(it) }
    return potentialReceivers[variable].mapNotNull { potentialReceiver ->
      when (potentialReceiver) {
        is Receiver.Class -> refine(resolved, potentialReceiver.element)?.toUElementOfType<UMethod>()?.let { CallTarget.Method(it) }
        is Receiver.Lambda -> if (isFunctionalCall()) potentialReceiver.element.toLambdaTarget() else null
        is Receiver.CallableReference -> if (isFunctionalCall()) potentialReceiver.element.toMethodTarget() else null
      }
    }
  }

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

  private fun handleAssign(v: UVariable, expr: UExpression) {
    fun addReceiver(receiver: Receiver) = potentialReceivers.put(v, receiver)
    when {
      expr is UCallExpression && expr.kind == UastCallKind.CONSTRUCTOR_CALL -> {
        val instantiatedClass = (expr.returnType as? PsiClassType)?.resolve()?.toUElementOfType<UClass>()
        if (instantiatedClass != null) {
          addReceiver(Receiver.Class(instantiatedClass))
        }
      }
      expr is ULambdaExpression -> addReceiver(Receiver.Lambda(expr))
      expr is UCallableReferenceExpression -> addReceiver(Receiver.CallableReference(expr))
    }
  }
}