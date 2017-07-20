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
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.*
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

private val LOG = Logger.getInstance("#com.android.tools.idea.experimental.callgraph.CallGraphBuilder")

val defaultCallGraphEdges = Edge.Kind.values().filter { it.isLikely || it == BASE || it == INVOKE }.toTypedArray()

fun buildUFiles(project: Project, scope: AnalysisScope): Collection<UFile> {
  val res = ArrayList<UFile>()
  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  scope.accept { virtualFile ->
    if (!uastContext.isFileSupported(virtualFile.name)) return@accept true
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@accept true
    val file = uastContext.convertWithParent<UFile>(psiFile) ?: return@accept true
    res.add(file)
  }
  return res
}

fun buildClassHierarchy(files: Collection<UFile>): ClassHierarchy {
  val classHierarchyVisitor = ClassHierarchyVisitor()
  files.forEach { it.accept(classHierarchyVisitor) }
  return classHierarchyVisitor.classHierarchy
}

fun buildIntraproceduralReceiverEval(files: Collection<UFile>, cha: ClassHierarchy): CallReceiverEvaluator {
  val receiverEval = IntraproceduralReceiverVisitor(cha)
  files.forEach { it.accept(receiverEval) }
  return receiverEval
}

fun buildCallGraph(files: Collection<UFile>,
                   receiverEval: CallReceiverEvaluator,
                   classHierarchy: ClassHierarchy,
                   vararg edgeKinds: Edge.Kind = defaultCallGraphEdges): CallGraph {
  val callGraphVisitor = CallGraphVisitor(receiverEval, classHierarchy, *edgeKinds)
  files.forEach { it.accept(callGraphVisitor) }
  LOG.info("${callGraphVisitor.callGraph}")
  return callGraphVisitor.callGraph
}

class CallGraphVisitor(private val receiverEval: CallReceiverEvaluator,
                       private val classHierarchy: ClassHierarchy,
                       private vararg val edgeKinds: Edge.Kind = defaultCallGraphEdges) : AbstractUastVisitor() {
  private val mutableCallGraph: MutableCallGraph = MutableCallGraph()
  val callGraph: CallGraph get() = mutableCallGraph

  override fun visitElement(node: UElement): Boolean {
    when (node) {
      // Eagerly add nodes to the graph, even if they have no edges; edges may materialize during contextual call path analysis.
      is UMethod, is ULambdaExpression -> mutableCallGraph.getNode(node)
    }
    return super.visitElement(node)
  }

  override fun visitClass(node: UClass): Boolean {
    // Check for an implicit call to a super constructor.
    val superClass = node.superClass?.psi?.navigationElement.toUElementOfType<UClass>()
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

    // Find surrounding context.
    val parent = node.getParentOfType(
        /*strict*/ true,
        UMethod::class.java,
        ULambdaExpression::class.java,
        UClassInitializer::class.java,
        UField::class.java)

    // Find the caller(s) based on surrounding context.
    val callers: Collection<UElement> = when (parent) {
      is UMethod, is ULambdaExpression -> {
        // Method or lambda caller.
        listOf(parent)
      }
      is UClassInitializer, is UField -> {
        // Implicit constructor callers due to class initializer.
        val decl = parent as UDeclaration
        if (decl.isStatic) // Ignore static initializers for now.
          return super.visitCallExpression(node)
        val containingClass = decl.getContainingUClass()
            ?: run {
          LOG.warn("Expected containing class for call within class initializer: ${node.asSourceString()}")
          return super.visitCallExpression(node)
        }
        // When necessary we use the containing class as the caller, indicating a default constructor.
        val ctors = containingClass.constructors()
        if (ctors.isNotEmpty()) ctors else listOf(containingClass)
      }
      else -> {
        // No caller found; this can happen for, e.g., annotation instantiations.
        return super.visitCallExpression(node)
      }
    }

    val callerNodes = callers.map { mutableCallGraph.getNode(it) }
    fun addEdge(callee: UElement?, kind: Edge.Kind) {
      if (kind !in edgeKinds) return // Filter out unwanted edges.
      val edge = Edge(callee?.let { mutableCallGraph.getNode(it) }, node, kind)
      callerNodes.forEach { it.edges.add(edge) }
    }

    val baseCallee = node.resolve().toUElementOfType<UMethod>()
    if (baseCallee == null) {
      if (node.isConstructorCall()) {
        // Found a call to a default constructor; create an edge to the instantiated class.
        val constructedClass = node.classReference?.resolve()?.navigationElement.toUElement() as? UClass
            ?: return super.visitCallExpression(node) // Unable to resolve class.
        addEdge(constructedClass, DIRECT)
      }
      else {
        // This is likely an invocation of a function expression, such as a Kotlin lambda.
        addEdge(null, INVOKE)
        node.getTargets(receiverEval).forEach { addEdge(it.element, TYPE_EVIDENCED) }
      }
      return super.visitCallExpression(node)
    }

    val overrides = classHierarchy.allOverridesOf(baseCallee).toList()

    // Create an edge based on the type of call.
    val cannotOverride = !baseCallee.canBeOverridden()
    val throughSuper = node.receiver is USuperExpression
    val isFunctionalCall = baseCallee.psi == LambdaUtil.getFunctionalInterfaceMethod(node.receiverType)
    val uniqueBase = overrides.isEmpty() && !isFunctionalCall
    val uniqueOverride = !baseCallee.isCallable() && overrides.size == 1 && !isFunctionalCall
    when {
      cannotOverride || throughSuper -> addEdge(baseCallee, DIRECT)
      uniqueBase -> addEdge(baseCallee, UNIQUE)
      uniqueOverride -> {
        addEdge(baseCallee, BASE) // We don't want to lose an edge to the base callee.
        addEdge(overrides.first(), UNIQUE)
      }
      else -> {
        // Use static analyses to try to indicate which overriding methods are likely targets.
        val evidencedTargets = node.getTargets(receiverEval).map { it.element }
        evidencedTargets.forEach { addEdge(it, TYPE_EVIDENCED) }
        if (baseCallee !in evidencedTargets) addEdge(baseCallee, BASE) // We don't want to lose the edge to the base callee.
        overrides.filter { it !in evidencedTargets }.forEach { addEdge(it, NON_UNIQUE_OVERRIDE) }
      }
    }

    return super.visitCallExpression(node)
  }

  private fun UClass.constructors() = methods.filter { it.isConstructor }

  // TODO: Verify functionality for Kotlin.
  private fun UMethod.isCallable() = when {
    hasModifierProperty(PsiModifier.ABSTRACT) -> false
    containingClass?.isInterface == true -> hasModifierProperty(PsiModifier.DEFAULT)
    else -> true
  }

  // TODO: Verify functionality for Kotlin.
  private fun UMethod.canBeOverridden(): Boolean {
    val parentClass = containingClass
    return parentClass != null
        && !isConstructor
        && !hasModifierProperty(PsiModifier.STATIC)
        && !hasModifierProperty(PsiModifier.FINAL)
        && !hasModifierProperty(PsiModifier.PRIVATE)
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
      }
      else {
        return false
      }
    }

    override fun visitClass(node: UClass): Boolean = true // Avoid visiting nested classes.
  }
}