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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private val LOG = Logger.getInstance(CallReceiverEvaluator::class.java)

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

/** Refines the given method to the overriding method that would appear in the virtual method table of this class. */
fun Receiver.Class.refineToTarget(method: UMethod) =
    element.findMethodBySignature(method, /*checkBases*/ true)
        ?.navigationElement?.toUElementOfType<UMethod>()
        ?.let { CallTarget.Method(it) }

fun Receiver.Lambda.toTarget() = CallTarget.Lambda(element)
fun Receiver.CallableReference.toTarget() = (element.resolve()?.navigationElement.toUElement() as? UMethod)?.let { CallTarget.Method(it) }

/** Tries to map expressions to receivers without relying on any context. */
class SimpleExpressionReceiverEvaluator(private val cha: ClassHierarchy) : CallReceiverEvaluator {

  override fun get(element: UElement): Collection<Receiver> = when {
    element is ULambdaExpression -> listOf(Receiver.Lambda(element))
    element is UCallableReferenceExpression -> listOf(Receiver.CallableReference(element))
    element is UObjectLiteralExpression -> listOf(Receiver.Class(element.declaration))
    element is UCallExpression && element.kind == UastCallKind.CONSTRUCTOR_CALL -> {
      // Constructor calls always return an exact type.
      val instantiatedClass = (element.returnType as? PsiClassType)
          ?.resolve()?.navigationElement.toUElementOfType<UClass>()
          ?.let { Receiver.Class(it) }
      listOfNotNull(instantiatedClass)
    }
    element is UExpression -> {
      // Use class hierarchy analysis to try to refine a static type to a unique runtime class type.
      val baseClass = (element.getExpressionType() as? PsiClassType)?.resolve()?.navigationElement.toUElementOfType<UClass>()
      if (baseClass == null) emptyList()
      else {
        fun UClass.isInstantiable() = !isInterface && !hasModifierProperty(PsiModifier.ABSTRACT)
        val subtypes = cha.allInheritorsOf(baseClass) + baseClass
        val uniqueReceiverClass = subtypes
            .filter { it.isInstantiable() }
            .singleOrNull()
            ?.let { Receiver.Class(it) }
        listOfNotNull(uniqueReceiverClass)
      }
    }
    else -> emptyList()
  }

  override fun getForImplicitThis(): Collection<Receiver> = emptyList()
}

/** Uses a flow-insensitive UAST traversal to map variables to potential receivers based on local context. */
class IntraproceduralReceiverVisitor(cha: ClassHierarchy) : AbstractUastVisitor(), CallReceiverEvaluator {
  private val simpleExprReceiverEval = SimpleExpressionReceiverEvaluator(cha)
  private val varMap = HashMultimap.create<UVariable, Receiver>()
  private val methodMap = HashMultimap.create<UMethod, Receiver>()
  private val methodsVisited = HashSet<UMethod>()

  override fun get(element: UElement): Collection<Receiver> {
    val ours: Collection<Receiver> = when (element) {
      is UVariable -> varMap[element]
      is USimpleNameReferenceExpression -> {
        (element.resolve()?.navigationElement.toUElementOfType<UVariable>())?.let { varMap[it] } ?: emptyList()
      }
      is UCallExpression -> {
        (element.resolve()?.navigationElement.toUElementOfType<UMethod>())?.let { methodMap[it] } ?: emptyList()
      }
      else -> emptyList()
    }
    val theirs = simpleExprReceiverEval[element]
    return ours + theirs
  }

  override fun getForImplicitThis(): Collection<Receiver> = emptyList()

  override fun visitMethod(node: UMethod): Boolean {
    if (methodsVisited.contains(node))
      return true // Avoids infinite recursion.
    methodsVisited.add(node)
    return super.visitMethod(node)
  }

  override fun afterVisitReturnExpression(node: UReturnExpression) {
    // Map methods to returned receivers.
    val method = node.getContainingUMethod() ?: return
    node.returnExpression?.let { methodMap.putAll(method, this[it]) }
  }

  override fun afterVisitCallExpression(node: UCallExpression) {
    // Try to visit the resolved method first in order to get evidenced return values.
    val resolved = node.resolve()?.navigationElement.toUElementOfType<UMethod>() ?: return
    resolved.accept(this)
  }

  override fun afterVisitVariable(node: UVariable) {
    node.uastInitializer?.let { handleAssign(node, it) }
  }

  override fun afterVisitBinaryExpression(node: UBinaryExpression) {
    val (left, op, right) = node
    if (op == UastBinaryOperator.ASSIGN && left is USimpleNameReferenceExpression) {
      (left.resolve()?.navigationElement.toUElement() as? UVariable)?.let { handleAssign(it, right) }
    }
  }

  private fun handleAssign(v: UVariable, expr: UExpression): Unit {
    varMap.putAll(v, this[expr])
  }
}

/** Convert this call expression into a list of likely targets given a call receiver evaluator. */
fun UCallExpression.getTargets(receiverEval: CallReceiverEvaluator): Collection<CallTarget> {

  // TODO(kotlin-uast-cleanup)
  // Kotlin variable function calls require some special care.
  // In particular, there seems to be no way (through the UAST interface) of resolving a callable variable
  // to its declaration; only a UIdentifier is available. So we use reflection to access the resolved variable
  // from the Kotlin compiler frontend.
  //
  // Note that the nullity check for [classReference] is there as an extra heuristic for
  // for determining whether this is a function expression invocation rather than a normal method call.
  if (methodName == "invoke" && classReference != null) {
    val directLambda = methodIdentifier?.psi?.navigationElement.toUElement() as? ULambdaExpression
    if (directLambda != null)
      return listOf(CallTarget.Lambda(directLambda))

    fun Any.getProperty(name: String) =
        javaClass.kotlin.memberProperties.find { it.name == name }
            ?.apply { isAccessible = true }
            ?.get(this)

    fun Any.invokeMemberFunction(name: String, vararg args: Any?) =
        javaClass.kotlin.memberFunctions.find { it.name == name }
            ?.apply { isAccessible = true }
            ?.call(this, *args)

    val ktDecl = getProperty("resolvedCall")
        ?.getProperty("variableCall")
        ?.getProperty("candidateDescriptor")
        ?.invokeMemberFunction("getSource")
        ?.getProperty("psi")
        as? PsiElement

    val uDecl = ktDecl.toUElementOfType<UDeclarationsExpression>()?.declarations?.singleOrNull()
        ?: ktDecl.toUElement()
        ?: return emptyList()

    return receiverEval[uDecl].mapNotNull { receiver ->
      when (receiver) {
        is Receiver.Class -> null
        is Receiver.Lambda -> receiver.toTarget()
        is Receiver.CallableReference -> receiver.toTarget()
      }
    }
  }

  // "Normal" method calls.
  val method = resolve()?.navigationElement.toUElement() as? UMethod ?: return emptyList()
  if (method.isStatic)
    return listOf(CallTarget.Method(method))
  val receivers = receiver?.let { receiverEval[it] } ?: receiverEval.getForImplicitThis()
  fun isFunctionalCall() = method.psi == LambdaUtil.getFunctionalInterfaceMethod(receiverType)
  return receivers.mapNotNull { receiver ->
    when (receiver) {
      is Receiver.Class -> receiver.refineToTarget(method)
      is Receiver.Lambda -> if (isFunctionalCall()) receiver.toTarget() else null
      is Receiver.CallableReference -> if (isFunctionalCall()) receiver.toTarget() else null
    }
  }
}