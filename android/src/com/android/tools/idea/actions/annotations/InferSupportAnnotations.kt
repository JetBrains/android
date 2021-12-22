/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations

import com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.resources.ResourceType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_ALL_OF
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_ANY_OF
import com.android.tools.lint.checks.BINDER_THREAD_ANNOTATION
import com.android.tools.lint.checks.MAIN_THREAD_ANNOTATION
import com.android.tools.lint.checks.PERMISSION_ANNOTATION
import com.android.tools.lint.checks.UI_THREAD_ANNOTATION
import com.android.tools.lint.checks.WORKER_THREAD_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_MARKER_TYPE
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_MARKER_TYPE
import com.android.tools.lint.detector.api.ResourceEvaluator.PX_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.RES_SUFFIX
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.PsiThrowStatement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.ArrayUtil
import org.jetbrains.android.refactoring.getNameInProject
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.EnumSet

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
 * * Look at return statements to figure out more constraints
 * * Look into inferring range restrictions
 * * Setting for whether we should look at callsites for some annotations
 *   and use that for inference. E.g. if we know
 *   nothing about foo(int) but somebody calls it with
 *   foo(R.string.name), then we have foo(@StringRes int)
 * * Add setting for intdef inference (since it won't be accurate)
 */
class InferSupportAnnotations(private val myAnnotateLocalVariables: Boolean, private val myProject: Project) {
  private var numAnnotationsAdded = 0
  private val myConstraints: MutableMap<SmartPsiElementPointer<out PsiModifierListOwner>, Constraints> =
    Maps.newHashMapWithExpectedSize(400)
  private val myPointerManager: SmartPointerManager = SmartPointerManager.getInstance(myProject)

  class Constraints {
    var inferences: MutableList<String>? = null
    var readOnly = false
    var types: EnumSet<ResourceType>? = null
    var permissionReferences: MutableSet<Any>? = null
    var requireAllPermissions = false
    var keep = false

    fun addResourceType(type: ResourceType?) {
      if (type != null) {
        if (types == null) {
          types = EnumSet.of(type)
        } else {
          types?.add(type)
        }
      }
    }

    fun addResourceTypes(types: EnumSet<ResourceType>?) {
      if (types != null) {
        if (this.types == null) {
          this.types = EnumSet.copyOf(types)
        } else {
          this.types?.addAll(types)
        }
      }
    }

    fun addReport(annotated: PsiModifierListOwner?, message: String?) {
      if (CREATE_INFERENCE_REPORT) {
        var cls: PsiClass? = null
        var member: PsiMember? = null
        var parameter: PsiParameter? = null
        if (annotated is PsiClass) {
          cls = annotated
        } else if (annotated is PsiMethod || annotated is PsiField) {
          member = annotated as PsiMember
          cls = member.containingClass
        } else if (annotated is PsiParameter) {
          parameter = annotated
          val method = PsiTreeUtil.getParentOfType(parameter, PsiMethod::class.java, true)
          if (method != null) {
            member = method
            cls = method.containingClass
          }
        }
        val sb = StringBuilder()
        if (cls != null) {
          sb.append("Class{").append(cls.name)
          if (isHidden(cls)) {
            sb.append(" (Hidden)")
          }
          sb.append('}')
        }
        if (member is PsiMethod) {
          sb.append(" Method{").append(member.name)
          if (isHidden(member)) {
            sb.append(" (Hidden)")
          }
          sb.append('}')
        }
        if (member is PsiField) {
          sb.append("Field{").append(member.name)
          if (isHidden(member)) {
            sb.append(" (Hidden)")
          }
          sb.append('}')
        }
        if (parameter != null) {
          sb.append("Parameter")
          sb.append("{")
          sb.append(parameter.type.canonicalText).append(" ").append(parameter.name)
          sb.append("}")
        }
        sb.append(":")
        sb.append(message)
        val inferences = inferences ?: mutableListOf<String>().also { inferences = it }
        inferences.add(sb.toString())
      }
    }

    fun merge(other: Constraints): Int {
      var added = 0
      val otherTypes = other.types
      if (otherTypes != null) {
        if (types == null) {
          types = otherTypes
          added++
        } else {
          if (types!!.addAll(otherTypes)) {
            added++
          }
        }
      }
      val otherPermissions = other.permissionReferences
      if (otherPermissions != null) {
        if (permissionReferences == null) {
          permissionReferences = otherPermissions
          added++
        } else {
          if (permissionReferences!!.addAll(otherPermissions)) {
            added++
          }
        }
        if (otherPermissions.size > 1) {
          requireAllPermissions = other.requireAllPermissions
        }
      }
      if (!keep && other.keep) {
        keep = true
        added++
      }
      val otherInferences = other.inferences
      if (otherInferences != null) {
        if (inferences == null) {
          inferences = otherInferences
        } else {
          for (inference in otherInferences) {
            if (!inferences!!.contains(inference)) {
              inferences!!.add(inference)
            }
          }
        }
      }
      return added
    }

    val resourceTypeAnnotations: List<String>
      get() {
        val types = this.types
        if (types != null && !types.isEmpty()) {
          val annotations: MutableList<String> = Lists.newArrayList()
          for (originalType in types) {
            var type = originalType
            val oldAnnotation = StringBuilder()
            val newAnnotation = StringBuilder()
            oldAnnotation.append('@')
            newAnnotation.append('@')
            if (type == COLOR_INT_MARKER_TYPE) {
              oldAnnotation.append(COLOR_INT_ANNOTATION.oldName())
              newAnnotation.append(COLOR_INT_ANNOTATION.newName())
            } else if (type == DIMENSION_MARKER_TYPE) {
              oldAnnotation.append(PX_ANNOTATION.oldName())
              newAnnotation.append(PX_ANNOTATION.newName())
            } else {
              if (type == ResourceType.MIPMAP) {
                type = ResourceType.DRAWABLE
              } else if (type == ResourceType.STYLEABLE) {
                continue
              }
              oldAnnotation.append(SUPPORT_ANNOTATIONS_PREFIX.oldName())
              oldAnnotation.append(StringUtil.capitalize(type.getName()))
              oldAnnotation.append(RES_SUFFIX)
              newAnnotation.append(SUPPORT_ANNOTATIONS_PREFIX.newName())
              newAnnotation.append(StringUtil.capitalize(type.getName()))
              newAnnotation.append(RES_SUFFIX)
            }
            annotations.add(oldAnnotation.toString())
            annotations.add(newAnnotation.toString())
          }
          return annotations
        }
        return emptyList()
      }

    val permissionAnnotations: List<String>
      get() {
        val permissionReferences = this.permissionReferences
        return if (permissionReferences != null && permissionReferences.isNotEmpty()) {
          if (permissionReferences.size == 1) {
            val permission = permissionReferences.iterator().next()
            val sb = StringBuilder()
            sb.append('@').append(PERMISSION_ANNOTATION.oldName()).append('(')
            if (permission is String) {
              sb.append('"')
              sb.append(permission)
              sb.append('"')
            } else if (permission is PsiField) {
              val containingClass = permission.containingClass
              if (containingClass != null) {
                val qualifiedName = containingClass.qualifiedName
                if (qualifiedName != null) {
                  sb.append(qualifiedName)
                  sb.append('.')
                }
              }
              sb.append(permission.name)
            }
            sb.append(')')
            listOf(sb.toString())
          } else {
            val sb = StringBuilder()
            sb.append('@').append(PERMISSION_ANNOTATION.oldName()).append('(')
            if (requireAllPermissions) {
              sb.append(ATTR_ALL_OF)
            } else {
              sb.append(ATTR_ANY_OF)
            }
            sb.append("={")
            var first = true
            for (permission in permissionReferences) {
              if (first) {
                first = false
              } else {
                sb.append(',')
              }
              if (permission is String) {
                sb.append('"')
                sb.append(permission)
                sb.append('"')
              } else if (permission is PsiField) {
                val containingClass = permission.containingClass
                if (containingClass != null) {
                  val qualifiedName = containingClass.qualifiedName
                  if (qualifiedName != null) {
                    sb.append(qualifiedName)
                    sb.append('.')
                  }
                }
                sb.append(permission.name)
              }
            }
            sb.append("}")
            sb.append(')')
            val annotation = sb.toString()
            ImmutableList.of(
              annotation,
              StringUtil.replace(annotation, PERMISSION_ANNOTATION.oldName(), PERMISSION_ANNOTATION.newName())
            )
          }
        } else emptyList()
      }

    val resourceTypeAnnotationsString: String
      get() {
        val annotations: List<String> = resourceTypeAnnotations
        return if (annotations.isNotEmpty()) {
          Joiner.on('\n').join(annotations)
            .replace(SUPPORT_ANNOTATIONS_PREFIX.oldName(), "")
            .replace(SUPPORT_ANNOTATIONS_PREFIX.newName(), "")
        } else ""
      }

    val permissionAnnotationsString: String
      get() {
        val annotations: List<String> = permissionAnnotations
        return if (annotations.isNotEmpty()) {
          Joiner.on('\n').join(annotations)
            .replace(SUPPORT_ANNOTATIONS_PREFIX.oldName(), "")
            .replace(SUPPORT_ANNOTATIONS_PREFIX.newName(), "").replace("android.Manifest", "Manifest")
        } else ""
      }

    fun getKeepAnnotationsString(project: Project): String {
      return if (keep) {
        "@" + SUPPORT_ANNOTATIONS_PREFIX.getNameInProject(project) + "Keep"
      } else ""
    }

    // public boolean callSuper;
    // public boolean checkResult;
    // TODO ranges and sizes
    // TODO typedefs
    // TODO threads
  }

  class ConstraintUsageInfo constructor(element: PsiElement, val constraints: Constraints) : UsageInfo(element) {

    fun addInferenceExplanations(list: MutableList<String>) {
      if (CREATE_INFERENCE_REPORT && constraints.inferences != null) {
        list.addAll(constraints.inferences!!)
      }
    }
  }

  @TestOnly
  fun apply(project: Project) {
    for ((owner, value) in myConstraints) {
      val element = owner.element
      if (element != null) {
        annotateConstraints(project, value, element)
      }
    }
    if (myConstraints.isEmpty()) {
      throw RuntimeException("Nothing found to infer")
    }
  }

  fun collect(usages: MutableList<UsageInfo>, scope: AnalysisScope) {
    for ((pointer, constraints) in myConstraints) {
      val element = pointer.element
      if (element != null && scope.contains(element) && !shouldIgnore(element)) {
        usages.add(ConstraintUsageInfo(element, constraints))
      }
    }
  }

  private fun shouldIgnore(element: PsiModifierListOwner): Boolean {
    if (!myAnnotateLocalVariables) {
      if (element is PsiLocalVariable) return true
      if (element is PsiParameter && element.declarationScope is PsiForeachStatement) return true
    }
    return false
  }

  private fun registerPermissionRequirement(owner: PsiModifierListOwner, all: Boolean, vararg permissions: Any): Constraints? {
    val pointer = myPointerManager.createSmartPsiElementPointer(owner)
    var constraints = myConstraints[pointer]
    if (constraints == null) {
      constraints = Constraints()
      constraints.permissionReferences = Sets.newHashSet(*permissions)
      constraints.requireAllPermissions = all
      storeConstraint(owner, pointer, constraints)
      numAnnotationsAdded++
    } else if (constraints.permissionReferences == null) {
      constraints.permissionReferences = Sets.newHashSet(*permissions)
      constraints.requireAllPermissions = all
      numAnnotationsAdded++
    } else {
      val set = constraints.permissionReferences!!
      if (Collections.addAll(set, *permissions)) {
        if (set.size > 1) {
          constraints.requireAllPermissions = all
        }
        numAnnotationsAdded++
      } else {
        return null
      }
    }
    return constraints
  }

  private fun storeConstraint(
    owner: PsiModifierListOwner,
    pointer: SmartPsiElementPointer<PsiModifierListOwner>,
    constraints: Constraints
  ) {
    constraints.readOnly = ModuleUtilCore.findModuleForPsiElement(owner) == null
    if (ApplicationManager.getApplication().isUnitTestMode) {
      constraints.readOnly = false
    }
    myConstraints[pointer] = constraints
  }

  fun collect(file: PsiFile) {
    // This isn't quite right; this does iteration for a single file, but
    // really newly added annotations can change previously visited files'
    // inferred data too. We should do it in a more global way.
    var prevNumAnnotationsAdded: Int
    var pass = 0
    do {
      val visitor = InferenceVisitor()
      prevNumAnnotationsAdded = numAnnotationsAdded
      file.accept(visitor)
      pass++
    }
    while (prevNumAnnotationsAdded < numAnnotationsAdded && pass < MAX_PASSES)
  }

  private fun getResourceTypeConstraints(owner: PsiModifierListOwner, inHierarchy: Boolean): Constraints? {
    var constraints: Constraints? = null
    for (annotation in AnnotationUtil.getAllAnnotations(owner, inHierarchy, null)) {
      val qualifiedName = annotation.qualifiedName ?: continue
      var type: ResourceType? = null
      if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX.oldName()) && qualifiedName.endsWith(RES_SUFFIX)) {
        val name = qualifiedName.substring(
          SUPPORT_ANNOTATIONS_PREFIX.oldName().length,
          qualifiedName.length - RES_SUFFIX.length
        )
        type = ResourceType.fromClassName(StringUtil.toLowerCase(name))
      } else if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX.newName()) && qualifiedName.endsWith(RES_SUFFIX)) {
        val name = qualifiedName.substring(
          SUPPORT_ANNOTATIONS_PREFIX.newName().length,
          qualifiedName.length - RES_SUFFIX.length
        )
        type = ResourceType.fromClassName(StringUtil.toLowerCase(name))
      } else if (COLOR_INT_ANNOTATION.isEquals(qualifiedName)) {
        type = COLOR_INT_MARKER_TYPE
      } else if (PX_ANNOTATION.isEquals(qualifiedName)) {
        type = DIMENSION_MARKER_TYPE
      }
      if (type != null) {
        if (constraints == null) {
          constraints = Constraints()
        }
        constraints.addResourceType(type)
      }
    }
    val pointer = myPointerManager.createSmartPsiElementPointer(owner)
    val existing = myConstraints[pointer]
    if (existing != null) {
      if (constraints != null) {
        constraints.merge(existing)
        return constraints
      }
      return existing
    }
    return constraints
  }

  private fun findReflectiveReference(call: PsiMethodCallExpression): PsiModifierListOwner? {
    val methodExpression = call.methodExpression
    if ("invoke" != methodExpression.referenceName) {
      return null
    }
    var qualifier = methodExpression.qualifier
    var methodCall: PsiMethodCallExpression? = null
    if (qualifier is PsiMethodCallExpression) {
      methodCall = qualifier
    } else if (qualifier is PsiReferenceExpression) {
      val methodVar = qualifier.resolve() ?: return null
      // Now find the assignment of the method --
      // TODO: make this smarter to handle assignment separate from declaration of variable etc
      if (methodVar is PsiLocalVariable) {
        val initializer = methodVar.initializer
        if (initializer is PsiMethodCallExpression) {
          methodCall = initializer
        }
      }
    } else {
      return null
    }
    if (methodCall != null) {
      var methodCallMethodExpression = methodCall.methodExpression
      var declarationName = methodCallMethodExpression.referenceName
      if ("getDeclaredMethod" != declarationName && "getMethod" != declarationName) {
        return null
      }
      val arguments = methodCall.argumentList.expressions
      if (arguments.isEmpty()) {
        return null
      }
      var o: Any? = JavaConstantExpressionEvaluator.computeConstantExpression(arguments[0], false) as? String ?: return null
      val methodName = o as String
      var className: String? = null
      qualifier = methodCallMethodExpression.qualifier
      if (qualifier is PsiReferenceExpression) {
        val clsVar = qualifier.resolve() ?: return null
        if (clsVar is PsiLocalVariable) {
          qualifier = clsVar.initializer
        }
      }
      if (qualifier is PsiMethodCallExpression) {
        methodCall = qualifier
        methodCallMethodExpression = methodCall.methodExpression
        declarationName = methodCallMethodExpression.referenceName
        if ("loadClass" != declarationName && "forName" != declarationName) {
          return null
        }
        val arguments2 = methodCall.argumentList.expressions
        if (arguments2.isEmpty()) {
          return null
        }
        o = JavaConstantExpressionEvaluator.computeConstantExpression(arguments2[0], false)
        if (o !is String) {
          return null
        }
        className = o
      } else if (qualifier is PsiClassObjectAccessExpression) {
        val operand = qualifier.operand
        if (operand != null) {
          className = operand.type.canonicalText
        }
      } else {
        return null
      }
      if (className != null) {
        val psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject))
          ?: return null
        val methods = psiClass.findMethodsByName(methodName, true)
        if (methods.size == 1) {
          return methods[0]
        } else if (methods.isEmpty()) {
          return null
        }
        for (method in methods) {
          // Try to match parameters
          val parameters = method.parameterList.parameters
          if (arguments.size == parameters.size + 1) {
            var allMatch = true
            for (i in parameters.indices) {
              val parameter = parameters[i]
              val argument = arguments[i + 1]
              val parameterType = parameter.type
              val argumentType = argument.type
              if (!typesMatch(argumentType, parameterType)) {
                allMatch = false
                break
              }
            }
            if (allMatch) {
              return method
            }
          }
        }
        return null
      }
    }

    // Also consider reflection libraries
    return null
  }

  private fun computeRequiredPermissions(owner: PsiModifierListOwner): Constraints? {
    var constraints: Constraints? = null
    for (annotation in AnnotationUtil.getAllAnnotations(owner, true, null)) {
      val qualifiedName = annotation.qualifiedName ?: continue
      if (PERMISSION_ANNOTATION.isPrefix(qualifiedName)) {
        if (constraints == null) {
          constraints = Constraints()
        }
        val permissions: MutableList<Any> = Lists.newArrayList()
        val value = annotation.findAttributeValue(null) // TODO: Or "value" ?
        addPermissions(value, permissions)
        if (permissions.isNotEmpty()) {
          constraints.permissionReferences = Sets.newHashSet(permissions)
        } else {
          val anyOf = annotation.findAttributeValue(ATTR_ANY_OF)
          addPermissions(anyOf, permissions)
          if (permissions.isNotEmpty()) {
            constraints.permissionReferences = Sets.newHashSet(permissions)
          } else {
            val allOf = annotation.findAttributeValue(ATTR_ALL_OF)
            addPermissions(allOf, permissions)
            if (permissions.isNotEmpty()) {
              constraints.permissionReferences = Sets.newHashSet(permissions)
              constraints.requireAllPermissions = true
            }
          }
        }
      } else if (UI_THREAD_ANNOTATION.isEquals(qualifiedName) ||
        MAIN_THREAD_ANNOTATION.isEquals(qualifiedName) ||
        BINDER_THREAD_ANNOTATION.isEquals(qualifiedName) ||
        WORKER_THREAD_ANNOTATION.isEquals(qualifiedName)
      ) {
        // TODO: Record thread here to pass to caller, BUT ONLY IF CONDITIONAL
      }
    }
    val pointer = myPointerManager.createSmartPsiElementPointer(owner)
    val existing = myConstraints[pointer]
    if (existing != null) {
      if (constraints != null) {
        constraints.merge(existing)
        return constraints
      }
      return existing
    }
    return constraints
  }

  private fun storeConstraints(owner: PsiModifierListOwner, constraints: Constraints): Constraints? {
    val pointer = myPointerManager.createSmartPsiElementPointer(owner)
    var existing = myConstraints[pointer]
    if (existing == null) {
      existing = getResourceTypeConstraints(owner, false)
      existing?.let { storeConstraint(owner, pointer, it) }
    }
    return if (existing == null) {
      storeConstraint(owner, pointer, constraints)
      numAnnotationsAdded++
      constraints
    } else {
      // Merge
      val added = existing.merge(constraints)
      numAnnotationsAdded += added
      if (added > 0) existing else null
    }
  }

  private inner class InferenceVisitor : JavaRecursiveElementWalkingVisitor() {
    override fun visitMethod(method: PsiMethod) {
      super.visitMethod(method)
      var constraints = getResourceTypeConstraints(method, true)
      val overridingMethods = OverridingMethodsSearch.search(method).findAll()
      for (overridingMethod in overridingMethods) {
        val additional = getResourceTypeConstraints(overridingMethod, true)
        if (additional != null) {
          if (constraints == null) {
            constraints = additional
          } else {
            constraints.addResourceTypes(additional.types)
          }
        }
      }
      if (constraints != null) {
        constraints = storeConstraints(method, constraints)
        if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
          constraints.addReport(
            method,
            constraints.resourceTypeAnnotationsString +
              " because it extends or is overridden by an annotated method"
          )
        }
      }
      val body = method.body
      body?.accept(object : JavaRecursiveElementWalkingVisitor() {
        private var myReturnedFromMethod = false
        override fun visitClass(aClass: PsiClass) {}
        override fun visitThrowStatement(statement: PsiThrowStatement) {
          myReturnedFromMethod = true
          super.visitThrowStatement(statement)
        }

        override fun visitLambdaExpression(expression: PsiLambdaExpression) {}
        override fun visitReturnStatement(statement: PsiReturnStatement) {
          val expression = statement.returnValue
          if (expression is PsiReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiModifierListOwner) {
              // TODO: Look up annotations on this method; here we're for example
              // returning a value that must have the same type as this method
              // e.g.
              //   int unknownReturnType() {
              //       return getKnownReturnType();
              //   }
              //   @DimenRes int getKnownReturnType() { ... }
            }
          }

          // TODO: Resolve expression: if's a resource type, use that
          myReturnedFromMethod = true
          super.visitReturnStatement(statement)
        }

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
          super.visitMethodCallExpression(expression)
          val calledMethod = expression.resolveMethod()
          if (calledMethod != null) {
            var permissionConstraint = computeRequiredPermissions(calledMethod)
            if (permissionConstraint?.permissionReferences != null && isUnconditionallyReachable(method, expression)) {
              val inferred: Constraints = permissionConstraint
              permissionConstraint = storeConstraints(method, permissionConstraint)
              if (CREATE_INFERENCE_REPORT && permissionConstraint != null && !permissionConstraint.readOnly) {
                val containingClass = calledMethod.containingClass
                val signature = (
                  (if (containingClass != null) containingClass.name + "#" else "") +
                    calledMethod.name
                  )
                val message: String = inferred.permissionAnnotationsString + " because it calls " + signature
                permissionConstraint.addReport(method, message)
              }
            }
          }
          val reflectiveReference = findReflectiveReference(expression)
          if (reflectiveReference != null) {
            val newConstraint = storeConstraints(reflectiveReference, Constraints().apply { keep = true })
            if (CREATE_INFERENCE_REPORT && newConstraint != null && !newConstraint.readOnly) {
              val containingClass = method.containingClass
              val signature = (
                (if (containingClass != null) containingClass.name + "#" else "") +
                  method.name
                )
              val message = newConstraint.getKeepAnnotationsString(myProject) + " because it is called reflectively from " + signature
              newConstraint.addReport(reflectiveReference, message)
            }
          }
          val name = expression.methodExpression.referenceName
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
            val args = expression.argumentList.expressions
            if (args.isNotEmpty()) {
              val first = args[0]
              val reference = first.reference
              if (reference != null) {
                val resolved = reference.resolve()
                if (resolved is PsiField) {
                  if (resolved.hasModifierProperty(PsiModifier.FINAL) && resolved.hasModifierProperty(PsiModifier.STATIC)) {
                    val permissionConstraint = registerPermissionRequirement(method, true, resolved)
                    if (CREATE_INFERENCE_REPORT && permissionConstraint != null && !permissionConstraint.readOnly) {
                      permissionConstraint.addReport(method, permissionConstraint.permissionAnnotationsString + " because it calls " + name)
                    }
                    return
                  }
                }
              }
              val v = JavaConstantExpressionEvaluator.computeConstantExpression(first, false)
              if (v is String) {
                val permissionConstraint = registerPermissionRequirement(method, true, v)
                if (CREATE_INFERENCE_REPORT && permissionConstraint != null && !permissionConstraint.readOnly) {
                  permissionConstraint.addReport(
                    method,
                    permissionConstraint.permissionAnnotationsString +
                      " because it calls " + name
                  )
                }
              }
            }
          }
        }

        private fun isUnconditionallyReachable(method: PsiMethod, expression: PsiElement): Boolean {
          if (myReturnedFromMethod) {
            return false
          }
          var curr = expression.parent
          var prev = curr
          while (curr != null) {
            if (curr === method) {
              return true
            }
            if (curr is PsiIfStatement || curr is PsiConditionalExpression || curr is PsiSwitchStatement) {
              return false
            }
            if (curr is PsiBinaryExpression) {
              // Check for short circuit evaluation:  A && B && C -- here A is unconditional, B and C is not
              val binaryExpression = curr
              if (prev !== binaryExpression.lOperand && binaryExpression.operationTokenType === JavaTokenType.ANDAND) {
                return false
              }
            }
            prev = curr
            curr = curr.parent
          }
          return true
        }
      })
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      super.visitMethodCallExpression(expression)
      val method = expression.resolveMethod()
      if (method != null) {
        val parameters = method.parameterList.parameters
        val arguments = expression.argumentList.expressions
        if (parameters.isNotEmpty() && arguments.size >= parameters.size) { // >: varargs
          for (i in arguments.indices) {
            val argument = arguments[i]
            val resourceType = AndroidPsiUtils.getResourceType(argument)
            if (resourceType != null) {
              val parameter = parameters[i]
              // If we see a call to some generic method, such as
              //    prettyPrint(R.id.foo)
              // or
              //    intent.putExtra(key, R.id.foo)
              // we shouldn't conclude that ALL calls to that method must also
              // use the same resource type! In other words, if we
              // see a method that takes non-integers, or an actual put method
              // (2 parameter method where our target is the second parameter and
              // the name begins with put) we ignore it.
              if (PsiType.INT != parameter.type || i == 1 && parameters.size == 2 && method.name.startsWith("put")) {
                continue
              }
              val newConstraint = Constraints()
              newConstraint.addResourceType(resourceType)
              val constraints = storeConstraints(parameter, newConstraint)
              if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                constraints.addReport(
                  parameter,
                  newConstraint.resourceTypeAnnotationsString +
                    " because it's passed " + argument.text + " in a call"
                )
              }
            }
          }
        }
      }
    }

    override fun visitReturnStatement(statement: PsiReturnStatement) {
      super.visitReturnStatement(statement)
      val returnValue = statement.returnValue ?: return
      val resourceType = AndroidPsiUtils.getResourceType(returnValue)
      if (resourceType != null) {
        val newConstraint = Constraints()
        newConstraint.addResourceType(resourceType)
        val method = PsiTreeUtil.getParentOfType(statement, PsiMethod::class.java)
        val constraints = storeConstraints(method!!, newConstraint)
        if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
          constraints.addReport(
            method,
            newConstraint.resourceTypeAnnotationsString +
              " because it returns " + returnValue.text
          )
        }
      } else if (returnValue is PsiReferenceExpression) {
        val resolved = returnValue.resolve()
        if (resolved is PsiModifierListOwner) {
          val newConstraint = getResourceTypeConstraints(resolved, true)
          if (newConstraint != null) {
            val method = PsiTreeUtil.getParentOfType(statement, PsiMethod::class.java)
            val constraints = storeConstraints(
              method!!, newConstraint
            )
            if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
              constraints.addReport(
                method,
                newConstraint.resourceTypeAnnotationsString +
                  " because it returns " + returnValue.getText()
              )
            }
          }
        }
      }
    }

    override fun visitParameter(parameter: PsiParameter) {
      super.visitParameter(parameter)
      val resourceTypeConstraints = getResourceTypeConstraints(parameter, true) ?: return
      val types = resourceTypeConstraints.types ?: return
      if (types.isNotEmpty()) {
        val constraints = storeConstraints(parameter, resourceTypeConstraints)
        if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
          constraints.addReport(
            parameter,
            constraints.resourceTypeAnnotationsString +
              " because it extends a method with that parameter annotated or inferred"
          )
        }
      }
      val grandParent = parameter.declarationScope
      if (grandParent is PsiMethod && grandParent.body != null) {
        for (reference in ReferencesSearch.search(parameter, LocalSearchScope(grandParent))) {
          val place = reference.element
          if (place is PsiReferenceExpression) {
            val parent = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression::class.java, PsiTypeCastExpression::class.java)
            if (processParameter(parameter, place, parent)) {
              return //  TODO: return? Shouldn't it be break?
            }
          }
        }
      }
    }

    private fun processParameter(parameter: PsiParameter, expr: PsiReferenceExpression, parent: PsiElement?): Boolean {
      if (PsiUtil.isAccessedForWriting(expr)) {
        return true // TODO: Move into super class
      }
      val call = PsiTreeUtil.getParentOfType(expr, PsiCall::class.java)
      if (call != null) {
        val argumentList = call.argumentList
        if (argumentList != null) {
          val args = argumentList.expressions
          val idx = ArrayUtil.find(args, expr)
          if (idx >= 0) {
            val resolvedMethod = call.resolveMethod()
            if (resolvedMethod != null) {
              val parameters = resolvedMethod.parameterList.parameters
              if (idx < parameters.size) { // not vararg
                val resolvedToParam = parameters[idx]
                var constraints = getResourceTypeConstraints(resolvedToParam, true)
                if (constraints?.types != null && !constraints.types!!.isEmpty() &&
                  !resolvedToParam.isVarArgs
                ) {
                  constraints = storeConstraints(parameter, constraints)
                  if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                    constraints.addReport(
                      parameter,
                      constraints.resourceTypeAnnotationsString +
                        " because it calls " +
                        (
                          if (resolvedMethod.containingClass != null) resolvedMethod.containingClass!!
                            .name +
                            "#"
                          else ""
                          ) +
                        resolvedMethod.name
                    )
                  }
                  return true
                }
              }
            }
          }
        }
      }
      return false
    }
  }

  companion object {
    const val CREATE_INFERENCE_REPORT = true

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
    fun apply(project: Project, info: UsageInfo) {
      if (info is ConstraintUsageInfo) {
        annotateConstraints(project, info.constraints, info.getElement() as PsiModifierListOwner?)
      }
    }

    private fun isHidden(owner: PsiDocCommentOwner): Boolean {
      var curr = owner
      if (FILTER_HIDDEN) {
        while (true) {
          val docComment = curr.docComment
          if (docComment != null) {
            // We cna't just look for a PsiDocTag with name "hide" from docComment.getTags()
            // because that method only works for "@hide", not "{@hide}" which is used in a bunch
            // of places; we'd need to search for PsiInlineDocTags too
            val text = docComment.text
            return text.contains("@hide")
          }
          curr = PsiTreeUtil.getParentOfType(curr, PsiDocCommentOwner::class.java, true) ?: break
        }
      }
      return false
    }

    private fun annotateConstraints(
      project: Project,
      constraints: Constraints,
      element: PsiModifierListOwner?
    ) {
      // TODO: Add some option for only annotating public/protected API methods, not private etc
      if (element == null) {
        return
      }
      if (constraints.readOnly || ModuleUtilCore.findModuleForPsiElement(element) == null) {
        return
      }
      if (FILTER_HIDDEN) {
        val doc = PsiTreeUtil.getParentOfType(element, PsiDocCommentOwner::class.java, false)
        if (doc != null && isHidden(doc)) {
          return
        }
      }
      for (code in constraints.resourceTypeAnnotations) {
        insertAnnotation(project, element, code)
      }
      for (code in constraints.permissionAnnotations) {
        insertAnnotation(project, element, code)
      }
      if (constraints.keep) {
        insertAnnotation(project, element, constraints.getKeepAnnotationsString(project))
      }
    }

    private fun insertAnnotation(
      project: Project,
      element: PsiModifierListOwner,
      code: String
    ) {
      val elementFactory = JavaPsiFacade.getInstance(project).elementFactory
      val newAnnotation = elementFactory.createAnnotationFromText(code, element)
      val attributes = newAnnotation.parameterList.attributes
      var end = code.indexOf('(')
      if (end == -1) {
        end = code.length
      }
      assert(code.startsWith("@")) { code }
      val fqn = code.substring(1, end)
      insertAnnotation(project, element, fqn, null, attributes)
    }

    private fun insertAnnotation(
      project: Project,
      element: PsiModifierListOwner,
      fqn: String,
      toRemove: String?,
      values: Array<PsiNameValuePair>
    ) {
      WriteCommandAction.runWriteCommandAction(project) {
        val toRemoveArray = toRemove?.let { arrayOf(it) } ?: ArrayUtil.EMPTY_STRING_ARRAY
        AddAnnotationFix(fqn, element, values, *toRemoveArray).invoke(project, null, element.containingFile)
      }
    }

    // Checks that a class type matches a given parameter type, e.g.
    //     Class<Integer> matches int
    private fun typesMatch(argumentType: PsiType?, parameterType: PsiType): Boolean {
      var argumentType = argumentType
      var parameterType = parameterType
      return if (argumentType is PsiClassType) {
        val typeParameters = argumentType.parameters
        if (typeParameters.size != 1) {
          return false
        }
        var unboxed = PsiPrimitiveType.getUnboxedType(parameterType)
        if (unboxed != null) {
          parameterType = unboxed
        }
        argumentType = typeParameters[0]
        unboxed = PsiPrimitiveType.getUnboxedType(argumentType)
        if (unboxed != null) {
          argumentType = unboxed
        }
        parameterType == argumentType
      } else {
        false
      }
    }

    private fun addPermissions(value: PsiAnnotationMemberValue?, names: MutableList<Any>) {
      if (value == null) {
        return
      }
      if (value is PsiLiteral) {
        val name = value.value as String?
        if (name != null && name.isNotEmpty()) {
          names.add(name)
        }
        // empty is just the default: means not specified
      } else if (value is PsiReferenceExpression) {
        val resolved = value.resolve()
        if (resolved is PsiField) {
          names.add(resolved)
        }
      } else if (value is PsiArrayInitializerMemberValue) {
        for (memberValue in value.initializers) {
          addPermissions(memberValue, names)
        }
      }
    }

    @JvmStatic
    fun generateReport(infos: Array<UsageInfo>): String {
      return if (CREATE_INFERENCE_REPORT) {
        val sb = StringBuilder(1000)
        sb.append("INFER SUPPORT ANNOTATIONS REPORT\n")
        sb.append("================================\n\n")
        val list: MutableList<String> = Lists.newArrayList()
        for (info in infos) {
          (info as ConstraintUsageInfo).addInferenceExplanations(list)
        }
        list.sort()
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
          var index: Int
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
            }
          }
          var printedMethod = false
          if (cls != null && cls != lastClass) {
            lastClass = cls
            lastMethod = null
            sb.append("\n")
            sb.append("Class ").append(cls).append(":\n")
          }
          if (method != null && method != lastMethod) {
            lastMethod = method
            sb.append("  Method ").append(method).append(":\n")
            printedMethod = true
          } else if (field != null) {
            sb.append("  Field ").append(field).append(":\n")
          }
          if (parameter != null) {
            if (!printedMethod) {
              sb.append("  Method ").append(method).append(":\n")
            }
            sb.append("    Parameter ")
            sb.append(parameter).append(":\n")
          }
          val message = s.substring(s.indexOf(':') + 1)
          sb.append("      ").append(message).append("\n")
        }
        if (list.isEmpty()) {
          sb.append("Nothing found.")
        }
        sb.toString()
      } else {
        ""
      }
    }
  }
}