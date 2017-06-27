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

import com.android.tools.idea.experimental.callgraph.CallGraph.Edge
import com.android.tools.idea.experimental.callgraph.CallGraph.Edge.Kind.*
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.searches.OverridingMethodsSearch
import org.jetbrains.uast.*
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastVisitor
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance("#com.android.tools.idea.experimental.callgraph.CallGraphBuilder")

// TODO: Handle unexpected conditions differently.
fun Logger.debugError(msg: String) = if (ApplicationManager.getApplication().isUnitTestMode) error(msg) else warn(msg)

val defaultCallGraphEdges = Edge.Kind.values().filter { it.isLikely || it == NON_UNIQUE_BASE }.toTypedArray()

fun buildCallGraph(project: Project, scope: AnalysisScope, vararg edgeKinds: Edge.Kind = defaultCallGraphEdges): CallGraph {
  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  val callGraphVisitor = CallGraphVisitor(*edgeKinds)
  fun visitAll(visitor: UastVisitor) = scope.accept { virtualFile ->
    if (!uastContext.isFileSupported(virtualFile.name)) return@accept true
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@accept true
    val file = uastContext.convertWithParent<UFile>(psiFile) ?: return@accept true
    LOG.info("Adding ${psiFile.name} to call graph")
    file.accept(visitor)
    true
  }
  val time = measureTimeMillis {
    visitAll(callGraphVisitor)
  }
  LOG.info("Call graph built in ${time}ms")
  LOG.info("${callGraphVisitor.callGraph}")
  return callGraphVisitor.callGraph
}

class CallGraphVisitor(private vararg val edgeKinds: Edge.Kind = defaultCallGraphEdges) : AbstractUastVisitor() {
  private val typeEvaluator = StandardTypeEvaluator()
  private val targetEvaluator = CallTargetVisitor()
  private val mutableCallGraph: MutableCallGraph = MutableCallGraph()
  val callGraph: CallGraph get() = mutableCallGraph

  override fun visitFile(node: UFile): Boolean {
    node.accept(typeEvaluator)
    node.accept(targetEvaluator)
    return super.visitFile(node)
  }

  override fun visitClass(node: UClass): Boolean {
    // Check for an implicit call to a super constructor.
    val superClass = node.getSuperClass()
    if (superClass != null) {
      val constructors = node.constructors()
      val thoseWithoutExplicitSuper = constructors.filter {
        val explicitSuperFinder = ExplicitSuperConstructorCallFinder()
        it.accept(explicitSuperFinder)
        !explicitSuperFinder.foundExplicitCall
      }
      val callers: Collection<UElement> = if (constructors.isNotEmpty()) thoseWithoutExplicitSuper else listOf(node)
      val callee: UElement = superClass.constructors().filter { it.uastParameters.isEmpty() }.firstOrNull() ?: superClass
      with(mutableCallGraph) {
        val calleeNode = getNode(callee)
        callers.forEach { getNode(it).edges.add(Edge(calleeNode, /*call*/ null, DIRECT)) }
      }
    }
    return super.visitClass(node)
  }

  override fun visitCallExpression(node: UCallExpression): Boolean {

    // The code below uses early returns liberally.
    // TODO: Clean up style, possibly by using ControlFlowException.
    fun earlyReturn(error: String? = null): Boolean {
      error?.let { LOG.debugError(error) }
      return super.visitCallExpression(node)
    }

    // Find the caller(s) based on surrounding context.
    val parent = node.getParentOfType(
        /*strict*/ true,
        UMethod::class.java, // Method caller.
        ULambdaExpression::class.java, // Lambda caller.
        UClassInitializer::class.java, // Implicit constructor caller due to class initializer.
        UClass::class.java) // Implicit constructor caller due to field initializer.
    val callers: Collection<UElement> = when (parent) {
      is UMethod, is ULambdaExpression -> listOf(parent)
      is UClassInitializer -> {
        if (parent.isStatic) return earlyReturn() // Ignore static initializers for now.
        val containingClass = parent as? UClass ?: parent.getContainingUClass() ?: return earlyReturn("Expected containing class")
        val constructors = containingClass.constructors()
        if (constructors.isNotEmpty()) constructors else listOf(containingClass) // Use class if no explicit constructor exists.
      }
      is UClass -> {
        val decl = node.getParentOfType<UDeclaration>() ?: return earlyReturn("Expected to be inside field initializer")
        if (decl.isStatic) return earlyReturn() // Ignore static field initializers for now.
        val constructors = parent.constructors()
        if (constructors.isNotEmpty()) constructors else listOf(parent)
      }
      else -> return earlyReturn("Could not find a caller for call expression")
    }

    val callerNodes = callers.map { mutableCallGraph.getNode(it) }
    fun addEdge(callee: UElement, kind: Edge.Kind) {
      if (kind !in edgeKinds) return // Filter out unwanted edges.
      val edge = Edge(mutableCallGraph.getNode(callee), node, kind)
      callerNodes.forEach { it.edges.add(edge) }
    }

    val baseCallee = node.resolveToUElement() as? UMethod
    if (baseCallee == null) {
      if (node.isConstructorCall()) {
        // Found a call to a default constructor; create an edge to the instantiated class.
        val constructedClass = node.classReference?.resolveToUElement() as? UClass ?: return earlyReturn("Expected class for constructor")
        addEdge(constructedClass, DIRECT)
      }
      return earlyReturn()
    }

    // TODO: Searching for overriding methods can be slow; may need to add caching or pre-computation.
    val overrides = OverridingMethodsSearch.search(baseCallee).findAll().map { it.toUElement() as? UMethod }.filterNotNull()

    // Create an edge based on the type of call.
    val cannotOverride = !canBeOverriden(baseCallee)
    val throughSuper = node.receiver is USuperExpression
    val isFunctionalCall = baseCallee.psi == LambdaUtil.getFunctionalInterfaceMethod(node.receiver?.getExpressionType())
    val uniqueBase = overrides.isEmpty() && !isFunctionalCall
    val uniqueOverride = !isCallable(baseCallee) && overrides.size == 1 && !isFunctionalCall
    when {
      cannotOverride || throughSuper -> addEdge(baseCallee, DIRECT)
      uniqueBase -> addEdge(baseCallee, UNIQUE)
      uniqueOverride -> addEdge(overrides.first(), UNIQUE)
      else -> {
        // Use static analyses to try to indicate which overriding methods are likely targets.
        val evidencedTargets = targetEvaluator[node].map { it.element }
        evidencedTargets.forEach { addEdge(it, TYPE_EVIDENCED) }
        if (baseCallee !in evidencedTargets) addEdge(baseCallee, NON_UNIQUE_BASE)
        overrides.filter { it !in evidencedTargets }.forEach { addEdge(it, NON_UNIQUE_OVERRIDE) }
      }
    }

    return super.visitCallExpression(node)
  }

  private fun UClass.constructors() = getMethods().filter { it.isConstructor() }

  // TODO: Update for Kotlin, and turn into function with receiver when inspection bugs are fixed.
  private fun isCallable(method: UMethod) = when {
    method.hasModifierProperty(PsiModifier.ABSTRACT) -> false
    method.getContainingClass()?.isInterface() == true -> method.hasModifierProperty(PsiModifier.DEFAULT)
    else -> true
  }

  // TODO: Update for Kotlin, and turn into function with receiver when inspection bugs are fixed.
  private fun canBeOverriden(method: UMethod): Boolean {
    val parentClass = method.getContainingClass()
    return parentClass != null
        && !method.isConstructor()
        && !method.hasModifierProperty(PsiModifier.STATIC)
        && !method.hasModifierProperty(PsiModifier.FINAL)
        && !method.hasModifierProperty(PsiModifier.PRIVATE)
        && parentClass !is PsiAnonymousClass
        && !parentClass.hasModifierProperty(PsiModifier.FINAL)
  }

  /** Tries to find an explicit call to a super constructor. Assumes the first element visited is a constructor. */
  private class ExplicitSuperConstructorCallFinder : AbstractUastVisitor() {
    var foundExplicitCall: Boolean = false

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (node.methodName == "super") {
        foundExplicitCall = true
        return true
      } else {
        return false
      }
    }

    override fun visitClass(node: UClass): Boolean = true // Avoid visiting nested classes.
  }
}