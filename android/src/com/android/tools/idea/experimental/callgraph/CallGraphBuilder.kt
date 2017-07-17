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
import org.jetbrains.uast.*
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

private val LOG = Logger.getInstance("#com.android.tools.idea.experimental.callgraph.CallGraphBuilder")

// TODO: Handle unexpected conditions differently.
fun Logger.debugError(msg: String) = if (ApplicationManager.getApplication().isUnitTestMode) error(msg) else warn(msg)

val defaultCallGraphEdges = Edge.Kind.values().filter { it.isLikely || it == BASE }.toTypedArray()

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

fun buildIntraproceduralReceiverEval(files: Collection<UFile>): CallReceiverEvaluator {
  val receiverEval = IntraproceduralReceiverVisitor()
  files.forEach { it.accept(receiverEval) }
  return receiverEval
}

fun buildClassHierarchy(files: Collection<UFile>): ClassHierarchy {
  val classHierarchyVisitor = ClassHierarchyVisitor()
  files.forEach { it.accept(classHierarchyVisitor) }
  return classHierarchyVisitor.classHierarchy
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
    val superClass = node.superClass
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

    val overrides = classHierarchy.allOverridesOf(baseCallee).toList()

    // Create an edge based on the type of call.
    val cannotOverride = !baseCallee.canBeOverriden()
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
  private fun UMethod.canBeOverriden(): Boolean {
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
      } else {
        return false
      }
    }

    override fun visitClass(node: UClass): Boolean = true // Avoid visiting nested classes.
  }
}