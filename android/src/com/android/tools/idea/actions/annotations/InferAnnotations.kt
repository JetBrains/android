/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:Suppress("UElementAsPsi")

package com.android.tools.idea.actions.annotations

import com.android.AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.resources.ResourceType
import com.android.tools.idea.actions.annotations.InferredConstraints.Companion.describeResource
import com.android.tools.idea.actions.annotations.InferredConstraints.Companion.inheritMethodAnnotation
import com.android.tools.idea.actions.annotations.InferredConstraints.Companion.inheritParameterAnnotation
import com.android.tools.idea.actions.annotations.InferredConstraints.Companion.transferArgumentToParameter
import com.android.tools.idea.actions.annotations.InferredConstraints.Companion.transferReturnToMethod
import com.android.tools.idea.lint.common.isNewLineNeededForAnnotation
import com.android.tools.lint.checks.ObjectAnimatorDetector.Companion.KEEP_ANNOTATION
import com.android.tools.lint.checks.PermissionDetector.Companion.handlesException
import com.android.tools.lint.checks.SECURITY_EXCEPTION
import com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER
import com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER
import com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
import com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER
import com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER
import com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER
import com.android.tools.lint.client.api.TYPE_LONG_WRAPPER
import com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.ResourceEvaluator.ANY_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.RES_SUFFIX
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.helpers.DefaultJavaEvaluator
import com.google.common.collect.Lists
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getSuperNames
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind.Companion.NESTED_ARRAY_INITIALIZER
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.math.min

/**
 * Infer support annotations, e.g. if a method returns
 * `R.drawable.something`, the method should be annotated with
 * `@DrawableRes`.
 *
 * TODO:
 * * Control flow analysis on method calls
 * * Check for resource type errors and warn if any are found, since they
 *   will lead to incorrect inferences!
 * * Can I do a custom dialog UI? There I could let you choose things like
 *   whether to infer ranges, control whether to show
 *   a report, and explain issue with false positives
 * * Look at reflection calls and proguard keep rules to add in @Keep
 * * Make sure I flow all annotations not inferred (such as range
 *   annotations)
 * * Check overridden methods: when doing resolve and hitting an interface
 *   I should check what implementations do
 * * When analyzing overriding methods, also see if I find *conflicting*
 *   annotations. For the Nullable/Nonnull scenario for example, it's
 *   possible for an overriding method to have a NonNull return value
 *   whereas that's not true for its super implementation. Make sure
 *   I don't come up with false annotation inferences like that.
 * * Look into inferring @IntDef. Approach: If we have a javadoc which
 *   lists multiple? Or what if we have a getter or setter for a field and
 *   I can tell how the field is being used wrt bits? How do I name it?
 * * Look into inferring range restrictions. Perhaps have a setting for
 *   this since it can be helpful but not conclusive.
 * * Infer ranges -
 * * * Transitively through passing in calls and return values
 * * * Based on range checking in the method or of the return values
 * * Idea: methods and fields ONLY referenced from tests (or from other
 *   @VisibleFromTesting methods) should
 *   be annotated with @VisibleFromTesting
 * * Reflection: Support Kotlin reflection APIs
 * * Support Property objects like View.ALPHA
 * * Hook up the settings flag such that I properly filter out whether to
 *   offer threads, ranges, resource-related things, etc.
 * * Check whether I have to apply any save or commit document operations
 *   afterwards!
 * * Exempt methods like getInt and methods that take an int.class or
 *   Integer.TYPE parameter since they are probably value stores
 */
class InferAnnotations(val settings: InferAnnotationsSettings, val project: Project) {
  private var numAnnotationsAdded = 0
  private val allConstraints: MutableMap<SmartPsiElementPointer<out PsiElement>, InferredConstraints> = HashMap(400)
  private val pointerManager: SmartPointerManager = SmartPointerManager.getInstance(project)
  private var securityException: PsiClass? = null

  // For debugging only
  @Suppress("unused")
  private fun dumpConstraints(): String {
    val sb = StringBuilder()
    for ((key, value) in allConstraints) {
      val signature = key.element?.getSignature()
      val valueString = value.toString().replace("androidx.annotation.", "").let { it.ifBlank { "<nothing>" } }
      sb.append(signature).append(" => ").append(valueString.trim()).append("\n")
    }
    return sb.toString()
  }

  class ConstraintUsageInfo constructor(element: PsiElement, val constraints: InferredConstraints) : UsageInfo(element) {
    val inferred: List<String> get() = constraints.getExplanations()

    // We can't change the presentation of each usage info, so instead we use tooltips to explain
    // why something was annotated the way it is
    override fun getTooltipText(): String? {
      if (inferred.isEmpty()) {
        return null
      }
      return inferred.joinToString("; ") {
        it.substringAfter(':')
      }
    }
  }

  private fun UAnnotated.getConstraintAnchor(): PsiElement {
    val element = sourcePsi ?: javaPsi ?: error("Attempted to look up constraints for a virtual element")
    return element.getConstraintAnchor()
  }

  private fun PsiElement.getConstraintAnchor(): PsiElement =
    // We unwrap PSI elements first such that in the case of Kotlin properties we associate them
    // all together (e.g. an inference made against the field initializer will apply to the getter and setter etc.)
    this.unwrapped ?: this

  private fun PsiElement.getPointer(): SmartPsiElementPointer<PsiElement> {
    return pointerManager.createSmartPsiElementPointer(getConstraintAnchor())
  }

  fun getConstraints(annotated: UAnnotated): InferredConstraints {
    val element = annotated.getConstraintAnchor()
    val pointer = element.getPointer()
    return allConstraints[pointer]
      ?: InferredConstraints.create(this, evaluator, annotated, element).also { allConstraints[pointer] = it }
  }

  fun getConstraints(annotated: PsiModifierListOwner): InferredConstraints {
    val pointer = annotated.getPointer()
    return allConstraints[pointer]
      ?: InferredConstraints.create(this, evaluator, annotated).also { allConstraints[pointer] = it }
  }

  @TestOnly
  fun apply(
    settings: InferAnnotationsSettings,
    project: Project
  ) {
    for ((owner, value) in allConstraints) {
      val element = owner.element ?: continue
      annotateConstraints(settings, project, value, element)
    }
    if (allConstraints.isEmpty()) {
      throw RuntimeException("Nothing found to infer")
    }
  }

  fun collect(
    usages: MutableList<UsageInfo>,
    scope: AnalysisScope,
    includeBinaries: Boolean = false,
    ignoreScope: Boolean = includeBinaries
  ) {
    for ((pointer, constraints) in allConstraints) {
      if (!constraints.modified ||
        constraints.readOnly && (!includeBinaries || settings.publicOnly && constraints.psi !is PsiCompiledElement)
      ) {
        continue
      }
      val element = pointer.element
      if (element != null && (ignoreScope || scope.contains(element)) && !shouldIgnore(element)) {
        usages.add(ConstraintUsageInfo(element, constraints))
      }
    }
  }

  private fun shouldIgnore(element: PsiElement): Boolean {
    if (!settings.annotateLocalVariables) {
      if (element is PsiLocalVariable) return true
      if (element is PsiParameter && element.declarationScope is PsiForeachStatement) return true
    }
    return false
  }

  fun collect(psiFile: PsiFile) {
    val file = UastFacade.convertElementWithParent(psiFile, UFile::class.java) as? UFile ?: return

    // This isn't quite right; this does iteration for a single file, but
    // really newly added annotations can change previously visited files'
    // inferred data too. We should do it in a more global way.
    var prevNumAnnotationsAdded: Int
    var pass = 0
    do {
      val visitor = InferenceVisitor()
      prevNumAnnotationsAdded = numAnnotationsAdded
      file.acceptSourceFile(visitor)
      pass++
    }
    while (prevNumAnnotationsAdded < numAnnotationsAdded && pass < MAX_PASSES)
  }

  val evaluator = DefaultJavaEvaluator(project, null)

  private fun getJavaClassType(element: UExpression?): PsiType? {
    element ?: return null

    val sourcePsi = element.sourcePsi
    if (sourcePsi is PsiClassObjectAccessExpression) {
      // Work around PSI bug: the type does not include the type
      return sourcePsi.operand.type
    }

    if (element is UParenthesizedExpression) {
      return getJavaClassType(element.expression)
    }

    // First try the type inferred from the Psi, in case it's a known class reference.
    val type = element.getExpressionType()

    if ((type == null || type == UastErrorType) && element is UQualifiedReferenceExpression) {
      val identifier =
        (element.selector.skipParenthesizedExprDown() as? USimpleNameReferenceExpression)?.identifier
      if (identifier == "javaPrimitiveType" || identifier == "TYPE" || identifier == "java") {
        return getJavaClassType(element.receiver)
      }
    }
    if (type is PsiClassType && type.parameterCount == 1) {
      var clazz = type.parameters[0]

      if (clazz is PsiClassType) {

        // PsiPrimitiveType.getUnboxedType does not work properly when clazz.resolve() returns null
        if (clazz.resolve() == null) {
          when (clazz.canonicalText) {
            TYPE_BOOLEAN_WRAPPER -> return PsiType.BOOLEAN
            TYPE_INTEGER_WRAPPER -> return PsiType.INT
            TYPE_BYTE_WRAPPER -> return PsiType.BYTE
            TYPE_SHORT_WRAPPER -> return PsiType.SHORT
            TYPE_LONG_WRAPPER -> return PsiType.LONG
            TYPE_DOUBLE_WRAPPER -> return PsiType.DOUBLE
            TYPE_FLOAT_WRAPPER -> return PsiType.FLOAT
            TYPE_CHARACTER_WRAPPER -> return PsiType.CHAR
          }
        }
        PsiPrimitiveType.getUnboxedType(clazz)?.let {
          // Make sure we extract the primitive type (int.class, Integer.TYPE in Java,
          // Int::class.javaPrimitiveType in Kotlin)
          if (element is UQualifiedReferenceExpression) {
            val identifier =
              (element.selector.skipParenthesizedExprDown() as? USimpleNameReferenceExpression)?.identifier
            if (identifier == "javaPrimitiveType" || identifier == "TYPE") {
              clazz = it
            }
          }
          if (element is UClassLiteralExpression && element.evaluate() is PsiPrimitiveType) {
            clazz = it
          }
        }
        return clazz
      }
    }

    return type
  }

  private fun findReflectiveReference(call: UCallExpression): UAnnotated? {
    val callName = call.methodName ?: call.methodIdentifier?.name

    val isConstructor: Boolean
    val isField: Boolean
    when (callName) {
      "getDeclaredMethod", "getMethod" -> {
        isField = false; isConstructor = false
      }
      "getDeclaredField", "getField" -> {
        isField = true; isConstructor = false
      }
      "getDeclaredConstructor", "getConstructor" -> {
        isField = false; isConstructor = true
      }
      else -> return null
    }

    val getMethodArguments = call.valueArguments
    val methodName = if (isConstructor) {
      null
    } else {
      getMethodArguments.firstOrNull()?.evaluateString() ?: return null
    }
    val classReceiver = call.receiver

    if (classReceiver is UClassLiteralExpression) {
      // Foo.class.getDeclaredMethod(name) => "Foo"
      val className = classReceiver.type?.canonicalText ?: return null
      return findReflectiveReference(className, methodName, getMethodArguments, isConstructor, isField)
    }

    val qualifier = if (classReceiver is UQualifiedReferenceExpression) {
      val receiver = classReceiver.receiver
      if (receiver is UClassLiteralExpression) {
        receiver
      } else {
        classReceiver.selector
      }
    } else if (classReceiver is UReferenceExpression) {
      val initializer = (classReceiver.resolve() as? PsiVariable)?.let { UastFacade.getInitializerBody(it) }
      if (initializer is UQualifiedReferenceExpression) {
        val receiver = initializer.receiver
        if (receiver is UClassLiteralExpression) {
          receiver
        } else {
          initializer.selector
        }
      } else {
        initializer
      }
    } else {
      return null
    }

    if (qualifier is UCallExpression) {
      val forNameCall = qualifier.methodName ?: qualifier.methodIdentifier?.name ?: return null
      if ("loadClass" != forNameCall && "forName" != forNameCall) {
        return null
      }
      val arguments2 = qualifier.valueArguments
      val className = arguments2.firstOrNull()?.evaluateString()
      return findReflectiveReference(className, methodName, getMethodArguments, isConstructor, isField)
    } else if (qualifier is UClassLiteralExpression) {
      val className = qualifier.type?.canonicalText ?: return null
      return findReflectiveReference(className, methodName, getMethodArguments, isConstructor, isField)
    }

    return null
  }

  private fun findReflectiveReference(
    className: String?,
    name: String?,
    arguments: List<UExpression>,
    isConstructor: Boolean,
    isField: Boolean
  ): UAnnotated? {
    className ?: return null
    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project)) ?: return null
    if (isField) {
      return psiClass.findFieldByName(name, true)?.let { UastFacade.convertElement(it, null) } as? UAnnotated
    }
    val methods =
      if (isConstructor) {
        psiClass.constructors
      } else {
        psiClass.findMethodsByName(name, true)
      }
    if (methods.size == 1) {
      return methods[0].let { UastFacade.convertElement(it, null) } as? UAnnotated
    } else if (methods.isEmpty()) {
      return null
    }
    for (method in methods) {
      // Try to match parameters
      val parameters = method.parameterList.parameters
      val offset = if (isConstructor) 0 else 1
      if (arguments.size == parameters.size + offset) {
        if (parameters.indices.all { i -> typesMatch(getJavaClassType(arguments[i + offset]), parameters[i].type) }) {
          return method.let { UastFacade.convertElement(it, null) } as? UAnnotated
        }
      }
    }
    return null
  }

  private fun getSecurityException(): PsiClass? {
    return securityException
      ?: JavaPsiFacade.getInstance(project).findClass(SECURITY_EXCEPTION, GlobalSearchScope.allScope(project))
        .also { securityException = it }
  }

  private inner class InferenceVisitor : AbstractUastVisitor() {
    override fun visitMethod(node: UMethod): Boolean {
      val sourcePsi = node.sourcePsi
      if (sourcePsi is KtClass ||
        sourcePsi is KtParameter ||
        sourcePsi is KtProperty
      ) {
        // Inline class, or constructor property, or class property; in these cases we're not
        // talking about a real method here, but UAST synthetic methods for things like accessors;
        // we don't want to create separate constraints for these
        return false
      }

      val done = super.visitMethod(node)

      val annotatedNode = node as UAnnotated
      val constraints = getConstraints(annotatedNode)
      if (settings.inherit) {
        val hierarchyAnnotations = evaluator.getAllAnnotations(annotatedNode, true)
        val hierarchyConstraints = InferredConstraints.create(
          this@InferAnnotations, annotatedNode.getConstraintAnchor(), annotations = hierarchyAnnotations,
          readOnly = false, ignore = constraints.ignore
        )
        val filter: (String) -> Boolean = ::inheritMethodAnnotation
        addConstraints(node, hierarchyConstraints, filter) { "because it extends or is overridden by an annotated method" }
      }

      val method = node
      method.uastBody?.accept(object : AbstractUastVisitor() {
        private var returnsFromMethod = false

        override fun visitClass(node: UClass): Boolean {
          return true
        }

        override fun visitThrowExpression(node: UThrowExpression): Boolean {
          returnsFromMethod = true
          return super.visitThrowExpression(node)
        }

        override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
          return true
        }

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
          returnsFromMethod = true
          return super.visitReturnExpression(node)
        }

        // Visiting calls in the method body
        override fun visitCallExpression(node: UCallExpression): Boolean {
          super.visitCallExpression(node)
          val calledMethod = node.resolve()
          if (calledMethod != null) {
            val calledConstraints = getConstraints(calledMethod)
            val calledPermissionRequirements = calledConstraints.permissionReferences
            if (calledPermissionRequirements != null && isUnconditionallyReachable(method, node) &&
              !handlesException(node, getSecurityException(), true, SECURITY_EXCEPTION)
            ) {
              if (constraints.addPermissionRequirement(calledPermissionRequirements, calledConstraints.requireAllPermissions)) {
                numAnnotationsAdded++
                if (!calledConstraints.readOnly || settings.includeBinaries) {
                  val signature = calledMethod.getSignature()
                  val explanation = calledConstraints.getPermissionAnnotationsString() + " because it calls " + signature
                  constraints.addExplanation(method, explanation)
                }
              }
            }
          }

          val reflectiveReference = findReflectiveReference(node)
          if (reflectiveReference != null) {
            val reflectedConstraint = getConstraints(reflectiveReference)
            if (reflectedConstraint.addAnnotation(KEEP_ANNOTATION)) {
              numAnnotationsAdded++
              if (!reflectedConstraint.readOnly || settings.includeBinaries) {
                val signature = method.getSignature()
                val explanation = "@Keep because it is called reflectively from $signature"
                reflectedConstraint.addExplanation(reflectiveReference, explanation)
              }
            }
          }

          val name = node.methodName ?: node.methodIdentifier?.name
          if (name != null && name.startsWith("enforce") &&
            (
              "enforceCallingOrSelfPermission" == name || "enforceCallingOrSelfUriPermission" == name ||
                "enforceCallingPermission" == name || "enforceCallingUriPermission" == name ||
                "enforcePermission" == name || "enforceUriPermission" == name
              )
          ) {
            // TODO: Determine whether this method is reached *unconditionally*
            // and use that to merge multiple requirements in the method as well as
            // the permission conditional flag
            val args = node.valueArguments
            if (args.isNotEmpty()) {
              val first = args[0]
              if (first is UReferenceExpression) {
                val resolved = first.resolve()
                if (resolved is PsiField) {
                  if (resolved.hasModifierProperty(PsiModifier.FINAL) && resolved.hasModifierProperty(PsiModifier.STATIC)) {
                    if (isUnconditionallyReachable(method, node) && !handlesException(node, getSecurityException(), true, SECURITY_EXCEPTION) &&
                      constraints.addPermissionRequirement(listOf(resolved), true)
                    ) {
                      numAnnotationsAdded++
                      if (!constraints.readOnly || settings.includeBinaries) {
                        val explanation = constraints.getPermissionAnnotationsString() + " because it calls " + name
                        constraints.addExplanation(method, explanation)
                      }
                    }
                    return false
                  }
                }
              }
              val v = first.evaluate()
              if (v is String) {
                if (isUnconditionallyReachable(method, node) && !handlesException(node, getSecurityException(), true, SECURITY_EXCEPTION) &&
                  constraints.addPermissionRequirement(listOf(v), true)
                ) {
                  numAnnotationsAdded++
                  if (!constraints.readOnly || settings.includeBinaries) {
                    val message = constraints.getPermissionAnnotationsString() + " because it calls " + name
                    constraints.addExplanation(method, message)
                  }
                }
              }
            }
          }
          return false
        }

        private fun isUnconditionallyReachable(method: UMethod, expression: UCallExpression): Boolean {
          if (returnsFromMethod) {
            return false
          }
          var curr = expression.uastParent
          var prev = curr
          while (curr != null) {
            if (curr === method) {
              return true
            }
            if (curr is UIfExpression || curr is USwitchExpression) {
              return false
            }
            if (curr is UBinaryExpression) {
              // Check for short circuit evaluation:  A && B && C -- here A is unconditional, B and C is not
              val binaryExpression = curr
              if (prev !== binaryExpression.leftOperand && binaryExpression.operator === UastBinaryOperator.LOGICAL_AND) {
                return false
              }
            }
            prev = curr
            curr = curr.uastParent
          }
          return true
        }
      })
      return done
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      super.visitCallExpression(node)
      if (node.valueArguments.isEmpty()) {
        return false
      }

      // If we call a method where a parameter is annotated with a resource type annotation,
      // we can conclude that the thing we're passing in must also have that resource type.
      // Unless the method allows multiple resource types (or @AnyRes); we can't just assume
      // that this specific instance also allows all resource types.

      // TODO: For performance reasons consider only doing this lookup later after one of the arguments
      // have been found to be interesting!

      val psiMethod = node.resolve() ?: return false
      val method = UastFacade.convertElement(psiMethod, null, UMethod::class.java) as? UMethod ?: return false
      val parameters = method.uastParameters
      val mapping = evaluator.computeArgumentMapping(node, psiMethod)

      for ((argument, parameter) in mapping) {
        // Do we know something about the argument? If so, pass it to the parameter.
        val resourceTypeMap = argument.getResourceTypes()
        if (resourceTypeMap.isNotEmpty() &&
          // if we're calling into generic code, like Math.abs(x), we don't want to infer anyhting about x
          !psiMethod.isGeneralCode()
        ) {
          // If we see a call to some generic method, such as
          //    prettyPrint(R.id.foo)
          // or
          //    intent.putExtra(key, R.id.foo)
          // we shouldn't conclude that ALL calls to that method must also
          // use the same resource type! In other words, if we
          // see a method that takes non-integers, or an actual put method
          // (2 parameter method where our target is the second parameter and
          // the name begins with put) we ignore it.
          if (PsiType.INT != parameter.type ||
            parameter.parameterIndex() == 1 && parameters.size == 2 && method.name.startsWith("put")
          ) {
            continue
          }
          val uastParameter = parameters.firstOrNull { it.sourcePsi == parameter || it.javaPsi == parameter }
          // We don't annotate platform code either since it's often generic; e.g. passing in a dimension to Math.round doesn't
          // mean all arguments to Math.round are dimensions!
          if (uastParameter != null) {
            addResourceAnnotations(uastParameter, resourceTypeMap) { type ->
              val calledFrom = node.getParentOfType<UMethod>(true)?.getSignature()?.let { " from $it" } ?: ""
              explain("because it's passed", "in a call$calledFrom", resourceTypeMap, type, argument)
            }
          }
        }

        // Do we know something about the parameter? If so, pass it to the argument.
        if (argument is UResolvable) {
          val resolved = argument.resolve() ?: continue
          if (resolved.isGeneralCode()) {
            continue
          }
          if (resolved is KtParameter &&
            (resolved.isLambdaParameter || resolved.isFunctionTypeParameter || resolved.isLoopParameter || resolved.isCatchParameter)
          ) {
            // Kotlin PSI uses KtParameter in a few other places where we shouldn't annotate; skip these
            continue
          } else {
            val parent = resolved.parent
            if (parent != null && (parent is PsiForeachStatement || parent.parent is PsiLambdaExpression)) {
              // Java lambda parameter
              continue
            }
          }
          val uastElement = UastFacade.convertElement(resolved, null) as? UAnnotated ?: continue
          if (uastElement is ULocalVariable && !settings.annotateLocalVariables) {
            continue
          }

          val parameterConstraints = getConstraints(parameter)
          val parameterTypes = parameterConstraints.getResourceTypes()
          if (parameterTypes.size == 1 && !parameterTypes.contains(ANY_RES_ANNOTATION.newName())) {
            if (isResourceField(uastElement.sourcePsi as? PsiField)) {
              // When we pass R.string.app_name to a method annotated @StringRes we can obviously
              // conclude that R.string.app_name is also a @StringRes, but that's implicit
              // from it being an actual R class field
              return false
            }

            val filter: (String) -> Boolean = ::transferArgumentToParameter
            addConstraints(uastElement, parameterConstraints, filter) { type ->
              val describe = describeResource(type)
              val suffix = if (describe.isNotBlank()) ", $describe" else ""
              "because it's passed to the ${parameter.name} parameter in ${method.getSignature()}$suffix"
            }
          }
        }
      }

      return false
    }

    fun PsiElement.isGeneralCode(): Boolean {
      val psiClass = when (this) {
        is PsiClass -> this
        is PsiMember -> containingClass
        else -> null
      }
      val className = psiClass?.qualifiedName ?: return false
      return className.startsWith("kotlin.") || className.startsWith("java.") || className.startsWith("android.") ||
             className.contains(".math.") || className.contains(".Math")
    }

    fun isResourceField(field: PsiField?): Boolean {
      var rClass: PsiClass? = field?.containingClass ?: return false
      rClass = rClass?.containingClass ?: return false
      if (AndroidUtils.R_CLASS_NAME == rClass.name) {
        return true
      }
      return false
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      super.visitReturnExpression(node)
      val returnValue = node.returnExpression ?: return false
      if (returnValue is UResolvable) {
        if (returnValue.getParentOfType(true, UMethod::class.java, ULambdaExpression::class.java) is ULambdaExpression) {
          return false
        }
        val resolved = returnValue.resolve()
        if (resolved is PsiModifierListOwner) {
          if (resolved is PsiMethod && resolved.isConstructor) {
            return false
          }
          val method = node.getParentOfType<UMethod>() ?: return false // shouldn't happen
          val filter: (String) -> Boolean = ::transferReturnToMethod
          val returnedConstraints = getConstraints(resolved)
          if (addConstraints(method, returnedConstraints, filter) { type ->
            when (resolved) {
              // Parameter: don't use full signature, it's obvious from the context
              is PsiParameter -> "because it returns ${resolved.name} annotated with ${returnedConstraints.getAnnotationSource(type, true)}"
              is PsiMethod -> "because it returns ${resolved.getSignature()} annotated with ${returnedConstraints.getAnnotationSource(type, true)}"
              else -> "because it returns ${returnValue.sourcePsi?.text}, ${describeResource(type)}"
            }
          }
          ) {
            return false
          }
        }
      }
      val resourceTypeMap = returnValue.getResourceTypes()
      if (resourceTypeMap.isNotEmpty()) {
        val method = node.getParentOfType<UMethod>() ?: return false // shouldn't happen
        addResourceAnnotations(method, resourceTypeMap) { type ->
          explain("because it returns", "", resourceTypeMap, type, returnValue)
        }
      }
      return false
    }

    /**
     * Given an AST element, returns the inferred resource types that the
     * element can contain, in a map pointing to the corresponding closest AST
     * element.
     */
    private fun UElement.getResourceTypes(): Map<String, UElement> {
      return mutableMapOf<String, UElement>().apply { addResourceTypes(this@getResourceTypes, this) }
    }

    /**
     * Evaluates the given node and returns the resource types applicable to
     * the node, if any.
     *
     * We can't use the [ResourceEvaluator] directly because we want to (1)
     * introduce existing constraint lookup into the middle (we don't have
     * annotations back into PSI during the middle of analysis) and (2) pick
     * out the specific elements that provide a given resource type.
     *
     * @param element the element to compute the types for
     * @return the corresponding resource types
     */
    fun addResourceTypes(element: UElement?, map: MutableMap<String, UElement>) {
      element ?: return
      if (element is UIfExpression) {
        val known = ConstantEvaluator.evaluate(null, element.condition)
        if (known == true && element.thenExpression != null) {
          addResourceTypes(element.thenExpression, map)
        } else if (known == false && element.elseExpression != null) {
          addResourceTypes(element.elseExpression, map)
        } else {
          addResourceTypes(element.thenExpression, map)
          addResourceTypes(element.elseExpression, map)
        }
        return
      } else if (element is UResolvable) {
        val call = element.findSelector()
        if (call is UCallExpression) {
          val method = call.resolve()
          if (method != null) {
            getConstraints(method).getResourceTypes().forEach { map[it] = call }
            return
          } else if (call.kind === NESTED_ARRAY_INITIALIZER) {
            for (argument in call.valueArguments) {
              addResourceTypes(argument, map)
            }
            return
          }
        } else {
          // Reference to a constant like R.string.app_name
          val url = ResourceEvaluator.getResourceConstant(element)
          if (url != null) {
            val annotation = SUPPORT_ANNOTATIONS_PREFIX.newName() + StringUtil.capitalize(url.type.mapType().getName()) + RES_SUFFIX
            map[annotation] = element
            return
          }
        }
        val resolved = element.resolve()
        if (resolved is PsiModifierListOwner) {
          if (resolved is PsiVariable) {
            val lastAssignment = findLastAssignment(resolved, element)
            lastAssignment?.let {
              addResourceTypes(it, map)
            }
          }

          getConstraints(resolved).getResourceTypes().forEach { map[it] = element }
        }
      } else if (element is UBlockExpression) {
        val expressions = element.expressions
        addResourceTypes(expressions.lastOrNull(), map)
      } else if (element is UParenthesizedExpression) {
        addResourceTypes(element.expression, map)
      }
    }

    private fun addResourceAnnotations(target: UAnnotated, resourceTypeMap: Map<String, UElement>, because: (String) -> String): Boolean {
      val constraints = getConstraints(target)
      val existingTypes = constraints.getResourceTypes()
      if (existingTypes.contains(ANY_RES_ANNOTATION.newName())) {
        // Already contain @AnyRes; nothing new to be added
        return false
      }
      var added = false
      constraints.addResourceTypes(resourceTypeMap.keys) { qualifiedName ->
        added = true
        if (!constraints.readOnly || settings.includeBinaries) {
          numAnnotationsAdded++
          val explanation = constraints.getAnnotationSource(qualifiedName, true) + " " + because(qualifiedName)
          constraints.addExplanation(target, explanation)
        }
      }
      return added
    }

    private fun addConstraints(
      target: UAnnotated,
      newConstraints: InferredConstraints,
      filter: (String) -> Boolean = { true },
      because: (String) -> String
    ): Boolean {
      val constraints = getConstraints(target)
      var added = false
      constraints.addConstraints(newConstraints, filter) { qualifiedName ->
        added = true
        if (!constraints.readOnly || settings.includeBinaries) {
          numAnnotationsAdded++
          val explanation = constraints.getAnnotationSource(qualifiedName, true) + " " + because(qualifiedName)
          constraints.addExplanation(target, explanation)
        }
      }
      return added
    }

    override fun visitParameter(node: UParameter): Boolean {
      super.visitParameter(node)

      if (node.sourcePsi == null) {
        return false
      }

      checkVariableInitializer(node)
      if (settings.inherit) {
        val parameter = node as UAnnotated
        val hierarchyAnnotations = evaluator.getAllAnnotations(parameter, true)
        val hierarchyConstraints = InferredConstraints.create(
          this@InferAnnotations, parameter.getConstraintAnchor(), annotations = hierarchyAnnotations,
          readOnly = false, ignore = getConstraints(parameter).ignore
        )
        val filter: (String) -> Boolean = ::inheritParameterAnnotation
        addConstraints(node, hierarchyConstraints, filter) { "because it extends a method with that parameter annotated or inferred" }
      }
      return false
    }

    override fun visitField(node: UField): Boolean {
      checkVariableInitializer(node)
      return super.visitField(node)
    }

    override fun visitLocalVariable(node: ULocalVariable): Boolean {
      if (settings.annotateLocalVariables) {
        checkVariableInitializer(node)
      }
      return super.visitLocalVariable(node)
    }

    private fun checkVariableInitializer(node: UVariable) {
      val initializer = node.uastInitializer ?: return
      val resourceTypeMap = initializer.getResourceTypes()
      if (resourceTypeMap.isNotEmpty()) {
        addResourceAnnotations(node, resourceTypeMap) { type ->
          explain("because it's assigned", "", resourceTypeMap, type, initializer)
        }
      }
    }
  }

  /**
   * Creates an explanation text for why a given AST [element] implied a
   * given resource [type], by consulting the [resourceTypeMap] returned by
   * [UElement#getResourceTypes] and by using the given [prefix] and [suffix]
   * fragments.
   */
  private fun explain(prefix: String, suffix: String, resourceTypeMap: Map<String, UElement>, type: String, element: UElement): String {
    val typeText = resourceTypeMap[type]?.sourcePsi?.text ?: element.sourcePsi?.text ?: element.asSourceString()
      .replace("\n", " ").take(40)

    val sb = StringBuilder()
    sb.append(prefix).append(' ').append(typeText)
    if (!typeText.contains(type.substringAfterLast('.').removeSuffix(RES_SUFFIX), ignoreCase = true)) {
      val description = describeResource(type)
      if (suffix.isNotEmpty()) {
        sb.append(" (").append(description).append(')')
      } else {
        sb.append(", ").append(description)
      }
    }

    if (suffix.isNotEmpty()) {
      sb.append(' ').append(suffix)
    }

    return sb.toString()
  }

  companion object {
    const val HEADER = "" +
      "INFER SUPPORT ANNOTATIONS REPORT\n" +
      "================================\n\n"

    /**
     * Whether to look for @hide markers in the javadocs and skip annotation
     * generation from hidden APIs. This is primarily used when this action is
     * invoked on the framework itself.
     */
    private const val FILTER_HIDDEN = true

    private const val MAX_PASSES = 10

    @JvmStatic
    fun nothingFoundMessage(project: Project?) {
      ApplicationManager.getApplication()
        .invokeLater { Messages.showInfoMessage(project, "Did not infer any new annotations", "Infer Support Annotation Results") }
    }

    @JvmStatic
    fun apply(settings: InferAnnotationsSettings, project: Project, info: UsageInfo) {
      if (info is ConstraintUsageInfo) {
        annotateConstraints(settings, project, info.constraints, info.getElement())
      }
    }

    private fun isHidden(owner: PsiElement): Boolean {
      if (FILTER_HIDDEN) {
        // TODO: KDoc support
        var curr = owner.getParentOfType<PsiDocCommentOwner>(false) ?: return false
        while (true) {
          val docComment = curr.docComment
          if (docComment != null) {
            // We can't just look for a PsiDocTag with name "hide" from docComment.getTags()
            // because that method only works for "@hide", not "{@hide}" which is used in a bunch
            // of places; we'd need to search for PsiInlineDocTags too
            val text = docComment.text
            if (text.contains("@hide")) {
              return true
            }
          }
          curr = PsiTreeUtil.getParentOfType(curr, PsiDocCommentOwner::class.java, true) ?: break
        }
      }
      return false
    }

    private fun annotateConstraints(
      settings: InferAnnotationsSettings,
      project: Project,
      constraints: InferredConstraints,
      element: PsiElement?
    ) {
      if (!constraints.modified || constraints.readOnly || element == null || ModuleUtilCore.findModuleForPsiElement(element) == null) {
        return
      }
      if (FILTER_HIDDEN) {
        val doc = PsiTreeUtil.getParentOfType(element, PsiDocCommentOwner::class.java, false)
        if (doc != null && isHidden(doc)) {
          return
        }
      }
      // Insert in reverse alphabetical order because the annotation utility appears to prepend
      for (code in constraints.getRemovedAnnotations(false)) {
        removeAnnotation(project, element, code)
      }
      for (code in constraints.getAddedAnnotations(false).reversed()) {
        insertAnnotation(settings, project, element, code)
      }
      for (code in constraints.getPermissionAnnotations()) {
        insertAnnotation(settings, project, element, code)
      }
    }

    private fun insertAnnotation(
      settings: InferAnnotationsSettings,
      project: Project,
      element: PsiElement,
      code: String
    ) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) {
        return
      }

      val end = code.indexOf('(').let { if (it == -1) code.length else it }
      assert(code.startsWith("@")) { code }
      val fqn = code.substring(1, end)

      when (element) {
        is PsiModifierListOwner -> {
          insertJavaAnnotation(project, code, element, fqn)
        }
        is KtModifierListOwner -> {
          val sites = mutableListOf<AnnotationUseSiteTarget?>()
          val fqName = FqName(fqn)

          if (element is KtParameter && element.hasValOrVar() && !element.isPrivate()) {
            // Constructor property.
            // Insert in reverse order that we want in file: getter, setter, parameter
            if (InferredConstraints.annotationAppliesToParameters(fqn)) {
              sites.add(AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER)
            }
            if (element.isMutable) {
              sites.add(AnnotationUseSiteTarget.PROPERTY_SETTER)
            }
            sites.add(AnnotationUseSiteTarget.PROPERTY_GETTER)
          } else if (element is KtProperty &&
            element.parent is KtClassBody &&
            element.modifierList?.node?.findChildByType(KtTokens.CONST_KEYWORD) == null &&
            (!element.isPrivate() || element.hasDelegate())
          ) {
            // Class property
            // Insert in reverse order that we want in file: field, setter, parameter
            if (InferredConstraints.annotationAppliesToFields(fqn) && !element.hasDelegate()) {
              sites.add(AnnotationUseSiteTarget.FIELD)
            }

            if (element.isVar) {
              val setter = element.setter
              if (setter == null || !(settings.publicOnly && setter.isPrivate())) {
                sites.add(AnnotationUseSiteTarget.PROPERTY_SETTER)
              }
            }

            val getter = element.getter
            if (getter == null || !(settings.publicOnly && getter.isPrivate())) {
              sites.add(AnnotationUseSiteTarget.PROPERTY_GETTER)
            }
          } else if (element is KtPropertyAccessor && element.property.findAnnotationWithUsageSite(
              fqName,
              if (element.isGetter) AnnotationUseSiteTarget.PROPERTY_GETTER else AnnotationUseSiteTarget.PROPERTY_SETTER
            ) != null
          ) {
            // We're attempting to annotate the getter or setter of a property where the property itself has already been
            // annotated with @get: or @set: for that accessor.
            return
          } else if (element is KtPropertyAccessor && settings.publicOnly && element.isPrivate()) {
            // Private accessor and we're not annotating private elements
            return
          } else {
            sites.add(null)
          }

          if (element.findAnnotationWithUsageSite(fqName, null) != null) {
            // Already annotated with a default-use site annotation
            return
          }

          for (site in sites) {
            insertKotlinAnnotation(code, project, element, fqName, site, site != sites.first())
          }
        }
        else -> {
          error("Unsupported PSI element: " + element.javaClass.name)
        }
      }
    }

    private fun insertJavaAnnotation(
      project: Project,
      code: String,
      element: PsiModifierListOwner,
      fqn: String
    ) {
      val elementFactory = JavaPsiFacade.getInstance(project).elementFactory
      val newAnnotation = elementFactory.createAnnotationFromText(code, element)
      val values = newAnnotation.parameterList.attributes
      WriteCommandAction.runWriteCommandAction(project) {
        AddAnnotationFix(fqn, element, values).invoke(project, null, element.containingFile)
      }
    }

    private fun insertKotlinAnnotation(
      code: String,
      project: Project,
      element: KtModifierListOwner,
      fqn: FqName,
      useSite: AnnotationUseSiteTarget?,
      sameLine: Boolean
    ): Boolean {
      val index = code.indexOf('(')
      val inner = if (index != -1) code.substring(index + 1, code.lastIndexOf(')')) else null
      var added = false
      WriteCommandAction.runWriteCommandAction(project) {
        // Ideally, we'd just call element.addAnnotation(FqName, ...) here, from modifierListModifactor.kt. Unfortunately,
        // it does *not* handle annotation use sites correctly so we have a patched version here.
        added = element.addAnnotationWithUsageSite(
          fqn, inner, useSiteTarget = useSite,
          whiteSpaceText = if (!sameLine && element.isNewLineNeededForAnnotation()) "\n" else " "
        )
      }
      return added
    }

    private fun removeAnnotation(
      project: Project,
      element: PsiElement,
      code: String
    ) {
      var end = code.indexOf('(')
      if (end == -1) {
        end = code.length
      }
      assert(code.startsWith("@")) { code }
      val fqn = code.substring(1, end)
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) {
        return
      }
      when (element) {
        is PsiModifierListOwner -> {
          WriteCommandAction.runWriteCommandAction(project) {
            element.getAnnotation(fqn)?.delete()
          }
        }
        is KtModifierListOwner -> {
          WriteCommandAction.runWriteCommandAction(project) {
            element.findAnnotation(FqName(fqn))?.delete()
          }
        }
        else -> {
          error("Unsupported PSI element: " + element.javaClass.name)
        }
      }
    }

    // Checks that a class type matches a given parameter type, e.g.
    //     Class<Integer> matches int
    private fun typesMatch(argumentType: PsiType?, parameterType: PsiType): Boolean {
      if (argumentType == parameterType) {
        return true
      }
      return if (argumentType is PsiClassType) {
        val typeParameters = argumentType.parameters
        if (typeParameters.size != 1) {
          return false
        }
        val unboxedParameter = PsiPrimitiveType.getUnboxedType(parameterType) ?: parameterType
        val unboxedArgument = PsiPrimitiveType.getUnboxedType(typeParameters[0]) ?: typeParameters[0]
        unboxedParameter == unboxedArgument
      } else {
        false
      }
    }

    val signatureSorter = Comparator<String> { o1, o2 ->
      // Sort outer classes higher than inner classes; this means that "}" beats other characters
      val l1 = o1.length
      val l2 = o2.length
      for (i in 0 until min(l1, l2)) {
        val c1 = o1[i]
        val c2 = o2[i]
        if (c1 == c2) {
          continue
        }
        return@Comparator when {
          c1 == '}' -> -1
          c2 == '}' -> 1
          else -> c1 - c2
        }
      }
      l1 - l2
    }

    @JvmStatic
    fun generateReport(infos: Array<UsageInfo>): String {
      return run {
        val sb = StringBuilder(1000)

        fun indent(levels: Int) {
          for (i in 0 until levels) {
            sb.append("  ")
          }
        }

        sb.append(HEADER)
        val list: MutableList<String> = Lists.newArrayList()
        for (info in infos) {
          list.addAll((info as ConstraintUsageInfo).inferred)
        }
        list.sortWith(signatureSorter)
        var lastClass: String? = null
        var lastMethod: String? = null
        var lastLine: String? = null
        for (s in list) {
          if (s == lastLine) {
            // Some inferences are duplicated
            continue
          }
          lastLine = s
          var cls: String? = null
          var method: String? = null
          var field: String? = null
          var parameter: String? = null
          var property: String? = null
          var index: Int
          // Decode the signature written by InferredConstraints#addExplanation
          index = s.indexOf("Class{")
          if (index != -1) {
            cls = s.substring(index + "Class{".length, s.indexOf('}', index))
          }
          index = s.indexOf("Method{")
          if (index != -1) {
            method = s.substring(index + "Method{".length, s.indexOf('}', index))
            index = s.indexOf("Parameter{")
            if (index != -1) {
              parameter = s.substring(index + "Parameter{".length, s.indexOf('}', index))
            }
          } else {
            index = s.indexOf("Field{")
            if (index != -1) {
              field = s.substring(index + "Field{".length, s.indexOf('}', index))
            } else {
              index = s.indexOf("Property{")
              if (index != -1) {
                property = s.substring(index + "Property{".length, s.indexOf('}', index))
              }
            }
          }
          var printedMethod = false

          if (cls != null && cls != lastClass) {
            lastClass = cls
            lastMethod = null
            sb.append("\n")
            if (s.startsWith("[ReadOnly]")) {
              sb.append("[Read Only] ")
            }
            sb.append("Class ").append(cls).append(":\n")
          }

          val indent = if (cls != null) 1 else 0
          if (method != null && method != lastMethod) {
            lastMethod = method
            indent(indent)
            if (cls != null) {
              sb.append("Method ")
            } else {
              sb.append("Function ")
            }
            sb.append(method).append(":\n")
            printedMethod = true
          } else if (field != null) {
            indent(indent)
            sb.append("Field ").append(field).append(":\n")
          } else if (property != null) {
            indent(indent)
            sb.append("Property ").append(property).append(":\n")
          }
          if (parameter != null) {
            if (!printedMethod && cls != null) {
              indent(indent)
              sb.append("Method ").append(method).append(":\n")
            }
            indent(indent + 1)
            sb.append("Parameter ")
            sb.append(parameter).append(":\n")
          }
          val message = s.substring(s.indexOf(':') + 1)
          indent(indent + if (parameter != null) 2 else 1)
          sb.append(message).append("\n")
        }
        if (list.isEmpty()) {
          sb.append("Nothing found.")
        }
        sb.toString()
      }
    }
  }
}

private fun PsiElement.getSignature(): String {
  val unwrapped = this.unwrapped
  if (unwrapped != null && unwrapped !== this) {
    return unwrapped.getSignature()
  }
  when (this) {
    is PsiMethod -> {
      val cls = this.containingClass
        ?: return name
      val className = cls.getSignature()
      if (isConstructor) {
        return className
      }
      return "$className#$name"
    }
    is PsiField -> {
      val cls = this.containingClass ?: return name
      return cls.getSignature() + "#" + name
    }
    is PsiClass -> {
      return name ?: ("anonymous class extending " + (superClass?.name ?: superTypes.firstOrNull()?.name))
    }
    is KtClass -> {
      return name ?: ("anonymous class extending " + getSuperNames())
    }
    is KtObjectDeclaration -> {
      return name ?: ("anonymous class extending " + getSuperNames())
    }
    is KtFunction -> {
      val name = this.name
      val containingClass = this.containingClass()?.getSignature()
      return if (containingClass != null) {
        "$containingClass#$name"
      } else {
        name ?: "<unknown function>"
      }
    }
    is KtParameter -> {
      val function = this.getParentOfType<KtFunction>(true)
      val name = this.name ?: "parameter ${this.parameterIndex()}"
      if (function != null) {
        return function.getSignature() + " parameter " + name
      }
      return name
    }
    is PsiParameter -> {
      val method = this.getParentOfType<PsiMethod>(true)
      val name = this.name
      if (method != null) {
        return method.getSignature() + " parameter " + name
      }
      return name
    }
    is KtPropertyAccessor -> {
      return property.getSignature() + if (isGetter) " (getter)" else if (isSetter) " (setter)" else ""
    }
    is KtProperty -> {
      val name = this.name ?: "unknown property name"
      val cls = this.getParentOfType<KtClass>(true)
      return if (cls != null) {
        cls.getSignature() + "#" + name
      } else {
        name
      }
    }
    is PsiLocalVariable -> {
      val parent = this.getParentOfType<PsiMethod>(true)
      val desc = "variable $name"
      return if (parent != null) {
        "${parent.getSignature()} $desc"
      } else {
        desc
      }
    }
    else -> {
      error("Unexpected signature element class ${this.javaClass.name}: ${this.text} in ${this.parent.text} in ${this.containingFile.virtualFile.path}")
    }
  }
}

private fun UMethod.getSignature(): String {
  val psi = sourcePsi ?: javaPsi
  return psi.getSignature()
}

// There's no @MipMapRes annotation; use @Drawable
fun ResourceType.mapType() = if (this == ResourceType.MIPMAP) ResourceType.DRAWABLE else this