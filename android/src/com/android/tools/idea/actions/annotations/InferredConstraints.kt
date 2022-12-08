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

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_VALUE
import com.android.AndroidXConstants.INT_DEF_ANNOTATION
import com.android.SdkConstants.KOTLIN_SUPPRESS
import com.android.AndroidXConstants.LONG_DEF_ANNOTATION
import com.android.AndroidXConstants.STRING_DEF_ANNOTATION
import com.android.AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.resources.ResourceType
import com.android.support.AndroidxName
import com.android.tools.idea.actions.annotations.InferredConstraints.Companion.annotationNames
import com.android.tools.lint.checks.ANY_THREAD_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector
import com.android.tools.lint.checks.BINDER_THREAD_ANNOTATION
import com.android.tools.lint.checks.CHECK_RESULT_ANNOTATION
import com.android.tools.lint.checks.CallSuperDetector.Issues.CALL_SUPER_ANNOTATION
import com.android.tools.lint.checks.DiscouragedDetector.Companion.DISCOURAGED_ANNOTATION
import com.android.tools.lint.checks.FLOAT_RANGE_ANNOTATION
import com.android.tools.lint.checks.GRAVITY_INT_ANNOTATION
import com.android.tools.lint.checks.HALF_FLOAT_ANNOTATION
import com.android.tools.lint.checks.INT_RANGE_ANNOTATION
import com.android.tools.lint.checks.MAIN_THREAD_ANNOTATION
import com.android.tools.lint.checks.ObjectAnimatorDetector.Companion.KEEP_ANNOTATION
import com.android.tools.lint.checks.PERMISSION_ANNOTATION
import com.android.tools.lint.checks.PERMISSION_ANNOTATION_READ
import com.android.tools.lint.checks.PERMISSION_ANNOTATION_WRITE
import com.android.tools.lint.checks.REQUIRES_FEATURE_ANNOTATION
import com.android.tools.lint.checks.RESTRICT_TO_ANNOTATION
import com.android.tools.lint.checks.SIZE_ANNOTATION
import com.android.tools.lint.checks.UI_THREAD_ANNOTATION
import com.android.tools.lint.checks.VISIBLE_FOR_TESTING_ANNOTATION
import com.android.tools.lint.checks.WORKER_THREAD_ANNOTATION
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.ResourceEvaluator.ANIMATOR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ANIM_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ANY_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ARRAY_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ATTR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.BOOL_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMEN_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DRAWABLE_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.FONT_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.FRACTION_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.ID_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.INTEGER_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.INTERPOLATOR_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.LAYOUT_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.MENU_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.NAVIGATION_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.PLURALS_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.PX_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.RAW_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.RES_SUFFIX
import com.android.tools.lint.detector.api.ResourceEvaluator.STRING_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.STYLEABLE_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.STYLE_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.TRANSITION_RES_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.XML_RES_ANNOTATION
import com.android.tools.lint.detector.api.VersionChecks.Companion.CHECKS_SDK_INT_AT_LEAST_ANNOTATION
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_API_ANNOTATION
import com.android.tools.lint.detector.api.isKotlin
import com.google.common.collect.Sets
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiAnnotationOwner
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPostfixExpression
import com.intellij.psi.PsiPrefixExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UDeclarationEx
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UUnknownExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import java.util.Locale

/**
 * Represents the constraints known about a particular [UAnnotated] element
 */
class InferredConstraints private constructor(
  /**
   * The [InferAnnotations] driving the inference, providing settings, PSI
   * context for lookup etc
   */
  private val inferrer: InferAnnotations,

  /** Associated PSI element */
  val psi: PsiElement? = null,

  /**
   * A read-only element is for example a reference to code in a library or
   * the platform; we can make inferences about these, but we won't write
   * back out the annotations we've inferred.
   */
  var readOnly: Boolean,
  /**
   * An ignored element is one explicitly opted out of inference; in this
   * case, we won't even add any inferred constraints for it. (This allows
   * users to specifically exempt via a `@Suppress` annotation a particular
   * element where the inference is incorrect. This is important because you
   * can't just delete from the Usages view an incorrect inference since
   * that inference could then have flowed further to make other invalid
   * inferences.)
   */
  var ignore: Boolean
) {
  /** Applicable settings */
  private val settings: InferAnnotationsSettings get() = inferrer.settings

  /**
   * Whether we've made any inferences on this element beyond those directly
   * annotated on it originally.
   */
  var modified = false

  /**
   * A set of descriptions of the inferences we've made (in a special syntax
   * where the prefix before `:` denotes the class and member attributes)
   */
  private var inferences: MutableList<String>? = null

  /**
   * A bitmask for all the no-argument annotations that were originally
   * present on this element. -1 means "initializing".
   */
  var originalAnnotations: Long = -1

  /**
   * A bitmask for all the no-argument annotations that were either
   * explicitly annotated originally or have since been inferred.
   */
  var annotations: Long = 0

  /**
   * Required permission names; these are arguments to the
   * `@RequiresPermission` attribute in the bit mask. Contains either actual
   * string values for the permission names, or PSI references to the name
   * constant fields.
   */
  var permissionReferences: MutableSet<Any>? = null
    private set

  /**
   * Whether *all* the permission names in [permissionReferences] are
   * required, as opposed to *any*
   */
  var requireAllPermissions = false
    private set

  /** Annotation arguments, keyed by qualified name. */
  var arguments: MutableMap<String, List<UNamedExpression>>? = null

  /**
   * Adds a specific [annotation] into the constraint set, and returns true
   * if the annotation was not previously present
   */
  fun addAnnotation(annotation: AndroidxName): Boolean {
    return addAnnotation(annotation.newName())
  }

  private fun skip(qualifiedName: String): Boolean {
    if (ignore) {
      return true
    }
    if (!settings.resources && isResourceAnnotation(qualifiedName)) {
      return true
    }
    if (!settings.reflection && qualifiedName == KEEP_ANNOTATION.newName()) {
      return true
    }

    return false
  }

  /**
   * Adds a specific [qualifiedName] annotation into the constraint set, and
   * returns true if the annotation was not previously present.
   */
  fun addAnnotation(qualifiedName: String): Boolean {
    if (skip(qualifiedName)) {
      return false
    }
    val mask = getAnnotationMask(qualifiedName)
    val new = annotations or mask
    if (new != annotations) {
      if (originalAnnotations != -1L) {
        // Check whether we've turned off analyzing annotations of this type
        if (!settings.resources) {
          if (isResourceAnnotation(qualifiedName)) {
            return false
          }
        }
        if (!settings.permissions) {
          if (qualifiedName.startsWith(PERMISSION_ANNOTATION.newName())) {
            return false
          }
        }
        if (!settings.threads) {
          when (qualifiedName) {
            MAIN_THREAD_ANNOTATION.newName(),
            UI_THREAD_ANNOTATION.newName(),
            BINDER_THREAD_ANNOTATION.newName(),
            WORKER_THREAD_ANNOTATION.newName(),
            ANY_THREAD_ANNOTATION.newName() -> return false
          }
        }
        if (!settings.ranges) {
          when (qualifiedName) {
            INT_RANGE_ANNOTATION.newName(),
            FLOAT_RANGE_ANNOTATION.newName(),
            SIZE_ANNOTATION.newName() -> return false
          }
        }
        if (!settings.reflection && qualifiedName == KEEP_ANNOTATION.newName()) {
          return false
        }
      }
      annotations = new
      modified = true
      return true
    }
    return false
  }

  /**
   * Removes a specific [qualifiedName] annotation from the constraint set,
   * and returns true if the annotation was previously present.
   */
  fun removeAnnotation(qualifiedName: String): Boolean {
    if (skip(qualifiedName)) {
      return false
    }
    val original = annotations
    annotations = annotations and getAnnotationMask(qualifiedName).inv()
    val removed = annotations != original
    if (removed) {
      modified = true
    }
    return modified
  }

  /** Returns true if the annotation is present in the [annotations] bitmask */
  private fun hasAnnotation(qualifiedName: String): Boolean {
    return (annotations and getAnnotationMask(qualifiedName)) != 0L
  }

  /**
   * Returns a list of all annotations; if [namesOnly] it will only use
   * simple names
   */
  fun getAllAnnotations(namesOnly: Boolean): List<String> {
    return getAnnotations(annotations, namesOnly)
  }

  /**
   * Returns a list of the new annotations added since the original
   * annotations; if [namesOnly] it will only use simple names
   */
  fun getAddedAnnotations(namesOnly: Boolean): List<String> {
    val shared = annotations and originalAnnotations
    return getAnnotations(annotations - shared, namesOnly)
  }

  /**
   * Returns a list of any annotations that were removed since the original
   * annotations; if [namesOnly] it will only use simple names
   */
  fun getRemovedAnnotations(namesOnly: Boolean): List<String> {
    val shared = annotations and originalAnnotations
    return getAnnotations(originalAnnotations - shared, namesOnly)
  }

  /**
   * Given an annotation [qualifiedName], returns this as annotation source
   * (e.g. including the leading `@` and possibly additional parenthesized
   * arguments). If [namesOnly] is true, will only use simple names instead
   * of fully qualified names. Like [Companion.getAnnotationSource], but
   * will also include specific arguments to annotations stored in this
   * constraint.
   */
  fun getAnnotationSource(qualifiedName: String, namesOnly: Boolean): String {
    return getAnnotationSource(qualifiedName, namesOnly, arguments?.get(qualifiedName), isKotlin(psi))
  }

  /** Whether this constraint set contains a `@Keep` annotation */
  fun keep(): Boolean {
    return hasAnnotation(KEEP_ANNOTATION.newName())
  }

  /**
   * Returns the set of resource type annotations. Note that this includes
   * more than the plain resource annotations; it also contains related
   * annotations that are treated the same way such as `@ColorInt`, `@Px` and
   * `@HalfFloat`.
   */
  fun getResourceTypes(): ResourceTypeSet {
    return ResourceTypeSet(annotations and resourceTypeMask)
  }

  /**
   * Given a set of [constraints] from another element, adds any applicable
   * constraints into this constraint set and invokes the given [callback]
   * passing back each annotation name, and returns whether at least one
   * annotation was added.
   */
  fun addConstraints(constraints: InferredConstraints, filter: (String) -> Boolean, callback: (String) -> Unit): Boolean {
    if (ignore) {
      return false
    } else if (hasAnnotation(ANY_RES_ANNOTATION.newName())) {
      return false
    } else if (constraints.hasAnnotation(ANY_RES_ANNOTATION.newName())) {
      clearResourceTypes()
    }
    var added = false
    val shared = annotations and constraints.annotations
    if (constraints.annotations != shared) {
      for (i in annotationNames.indices) {
        val mask = getAnnotationMask(i)
        if (constraints.annotations and mask != 0L && annotations and mask == 0L) {
          val qualifiedName = annotationNames[i]
          if (skip(qualifiedName) || !filter(qualifiedName)) {
            continue
          }
          // If it's an annotation with arguments (e.g. not just a marker annotation) and we cannot
          // merge in its arguments (such as the range parameters for an @IntRange), skip this annotation
          if (!isMarkerAnnotation(qualifiedName) && !mergeArguments(qualifiedName, constraints)) {
            continue
          }
          if (addAnnotation(qualifiedName)) {
            callback(qualifiedName)
          }
          added = true
        }
      }
    }

    return added
  }

  /**
   * Attempt to merge the arguments belonging to the given [qualifiedName]
   * annotation, and return true if successful.
   */
  private fun mergeArguments(qualifiedName: String, constraints: InferredConstraints): Boolean {
    when (qualifiedName) {
      INT_RANGE_ANNOTATION.newName(),
      FLOAT_RANGE_ANNOTATION.newName(),
      SIZE_ANNOTATION.newName(),
      CHECK_RESULT_ANNOTATION.newName() -> {
        val arguments = arguments ?: HashMap<String, List<UNamedExpression>>().also { arguments = it }
        val existing = arguments[qualifiedName]
        if (existing != null) {
          // For now, can't merge ranges, we just transfer if there's no conflict. We can allow equal, but then the merging is already done.
          return false
        }
        constraints.arguments?.get(qualifiedName)?.let { arguments[qualifiedName] = it }
        return true
      }
    }

    return false
  }

  /**
   * Given a set of annotation [qualifiedNames], adds any resource types
   * into this constraint set and invokes the given [callback] passing back
   * each resource type annotation name, and returns whether at least one
   * annotation was added.
   */
  fun addResourceTypes(qualifiedNames: Collection<String>, callback: (String) -> Unit): Boolean {
    if (ignore) {
      return false
    } else if (hasAnnotation(ANY_RES_ANNOTATION.newName())) {
      return false
    } else if (qualifiedNames.contains(ANY_RES_ANNOTATION.newName())) {
      clearResourceTypes()
    }
    var added = false
    for (qualifiedName in qualifiedNames) {
      if (skip(qualifiedName)) {
        continue
      }
      if (addAnnotation(qualifiedName)) {
        callback(qualifiedName)
        added = true
        modified = true
      }
    }

    return added
  }

  /**
   * Adds the given annotation to this constraint set; similar to
   * [addAnnotation] but specifically used for resource type annotations
   * where we want special handling of the `@AnyRes` annotation.
   */
  fun addResourceAnnotation(annotation: String): Boolean {
    if (skip(annotation)) {
      return false
    } else if (hasAnnotation(ANY_RES_ANNOTATION.newName())) {
      return false
    } else if (annotation == ANY_RES_ANNOTATION.newName()) {
      // Clear other resource types
      clearResourceTypes()
    }

    if (addAnnotation(annotation)) {
      modified = true
      return true
    }

    return false
  }

  /**
   * Removes all resource-related annotations from the annotations set.
   * This is used when `@AnyRes` is encountered, and we want to remove all
   * incompatible or implicit other annotations such as `@StringRes` or
   * `@DimenRes`.
   */
  fun clearResourceTypes() {
    if (ignore) {
      return
    }
    annotations = annotations and (resourceTypeMask.inv())
  }

  /**
   * Returns the annotations from the given bitmask. If [namesOnly] is true,
   * returns the simple names instead of the fully qualified names.
   */
  private fun getAnnotations(annotations: Long, namesOnly: Boolean): List<String> {
    if (annotations == 0L) {
      return emptyList()
    }
    val list = mutableListOf<String>()
    for (index in annotationNames.indices) {
      val mask = getAnnotationMask(index)
      if ((mask and annotations) != 0L) {
        list.add(getAnnotationSource(annotationNames[index], namesOnly))
      }
    }

    return list
  }

  // For debugging only
  override fun toString(): String {
    val sb = StringBuilder()
    if (modified) {
      sb.append("[modified ]")
    }
    if (readOnly) {
      sb.append("[readOnly] ")
    }
    val append: (String) -> Unit = { sb.append(it).append('\n') }
    inferences?.forEach(append)
    getAllAnnotations(true).forEach(append)
    getPermissionAnnotations().forEach(append)
    return sb.toString()
  }

  /**
   * Adds the given [names] as required permissions (and if [all] is set to
   * true, they're all required) to this constraint set. Returns true if at
   * least one new name was added to the constraint.
   */
  fun addPermissionRequirement(names: Collection<Any>, all: Boolean): Boolean {
    if (ignore) {
      return false
    } else if (!settings.permissions) {
      return false
    }
    val permissionReferences = permissionReferences ?: mutableSetOf<Any>().also {
      permissionReferences = it
    }
    val countBefore = permissionReferences.size
    var added = false
    for (name in names) {
      if (name is String && name.startsWith("android.permission.")) {
        val field = findAndroidPermissionField(inferrer.project, name)
        if (field != null) {
          if (permissionReferences.add(field)) {
            added = true
            modified = true
          }
        }
        continue
      }

      if (permissionReferences.add(name)) {
        added = true
        modified = true
      }
    }
    if (countBefore == 0) {
      requireAllPermissions = all
    } else if (permissionReferences.size > 1) {
      requireAllPermissions = all
    }

    return added
  }

  /**
   * Returns a list of descriptions of the inferences we've made (in a
   * special syntax where the prefix before `:` denotes the class and member
   * attributes)
   */
  fun getExplanations(): List<String> {
    return inferences ?: emptyList()
  }

  private fun UClass.getClassName(): String? {
    val cls = this
    if (sourcePsi == null) {
      return null // top level function
    }
    val qualifiedName = this.qualifiedName
    if (qualifiedName != null) {
      return qualifiedName
    }
    val name = this.name ?: "<Anonymous ${cls.superTypes.firstOrNull()?.name}>"
    val parent = this.getParentOfType<UClass>(true) ?: return name
    return parent.getClassName() + "." + name
  }

  private fun UMethod.getMethodName(): String {
    // Try to access the KtFunction mae first such that we don't pick up mangled names for internal elements
    val name = (this.sourcePsi as? KtFunction)?.name ?: this.name
    var containingClass = getParentOfType<UClass>(true) ?: return name
    while (true) {
      val outer = containingClass.getParentOfType<UClass>(true) ?: break
      containingClass = outer
    }
    if (containingClass.sourcePsi == null) {
      // Top level function: include package
      val className = containingClass.name
      if (className != null) { // not anonymous class
        val qualifiedName = containingClass.qualifiedName
        if (qualifiedName != null && qualifiedName.length > className.length && qualifiedName.endsWith(className)) {
          return qualifiedName.removeSuffix(className) + name
        }
      }
    }
    return name
  }

  /** Adds an explanation for an inference added to this constraint set. */
  fun addExplanation(annotated: UAnnotated, message: String) {
    var cls: UClass? = null
    var method: UMethod? = null
    var field: UField? = null
    var parameter: UParameter? = null
    when (annotated) {
      is UClass -> cls = annotated
      is UMethod -> {
        method = annotated
        cls = method.getContainingUClass()
      }
      is UField -> {
        field = annotated
        cls = field.getContainingUClass()
      }
      is UParameter -> {
        parameter = annotated
        method = parameter.getParentOfType()
        cls = method?.getContainingUClass()
      }
    }
    val sb = StringBuilder()
    if (readOnly) {
      sb.append("[ReadOnly]")
    }
    val className = cls?.getClassName()
    if (className != null) {
      sb.append("Class{").append(className)
      if (isHidden(cls as UElement)) {
        sb.append(" (Hidden)")
      }
      sb.append('}')
    }
    if (method != null) {
      val psi = method.javaPsi
      val unwrapped = psi.unwrapped
      if (unwrapped is KtProperty) {
        sb.append(" Property{").append(unwrapped.name)
        val sourcePsi = method.sourcePsi
        if (sourcePsi is KtPropertyAccessor) {
          if (sourcePsi.isGetter) {
            sb.append(" (getter)")
          } else if (sourcePsi.isSetter) {
            sb.append(" (setter)")
          }
        }
      } else {
        sb.append(" Method{").append(method.getMethodName())
        sb.append('(')
        sb.append(
          method.uastParameters.joinToString(",") {
            val type = it.type.canonicalText
            type.substringAfterLast('.')
          }
        )
        sb.append(')')
      }
      if (isHidden(method as UElement)) {
        sb.append(" (Hidden)")
      }
      sb.append('}')
    }
    if (field != null) {
      val psi = field.javaPsi
      val unwrapped = psi?.unwrapped
      if (unwrapped is KtProperty) {
        sb.append(" Property{").append(unwrapped.name)
      } else {
        sb.append("Field{").append(field.name)
      }
      if (isHidden(field as UElement)) {
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

  /**
   * Returns true if the given [element] is documented with `@hide` or within
   * an outer block annotated with `@hide`.
   */
  private fun isHidden(element: UElement): Boolean {
    if (settings.filterHidden && element.sourcePsi !is PsiCompiledElement) {
      var curr = element
      while (true) {
        for (comment in curr.comments) {
          if (comment.text.contains("@hide")) {
            return true
          }
        }
        curr = curr.uastParent ?: break
      }
    }
    return false
  }

  /** Returns all the permission annotations in this constraint. */
  fun getPermissionAnnotations(kotlin: Boolean = isKotlin(psi)): List<String> {
    val permissionReferences = this.permissionReferences
    return if (permissionReferences != null && permissionReferences.isNotEmpty()) {
      if (permissionReferences.size == 1) {
        val permission = permissionReferences.iterator().next()
        val sb = StringBuilder()
        sb.append('@').append(PERMISSION_ANNOTATION.newName()).append('(')
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
        sb.append('@').append(PERMISSION_ANNOTATION.newName()).append('(')
        if (requireAllPermissions) {
          sb.append(AnnotationDetector.ATTR_ALL_OF)
        } else {
          sb.append(AnnotationDetector.ATTR_ANY_OF)
        }
        sb.append("=")
        sb.append(if (kotlin) '[' else '{')
        sb.append(
          permissionReferences.map { permission ->
            when (permission) {
              is String -> {
                "\"$permission\""
              }
              is PsiField -> {
                val name = permission.name
                val qualifiedName = permission.containingClass?.qualifiedName
                if (qualifiedName != null) {
                  "$qualifiedName.$name"
                } else {
                  name
                }
              }
              else -> {
                error("Unexpected permission reference class ${permission.javaClass}")
              }
            }
          }.sorted().joinToString(",")
        )
        sb.append(if (kotlin) ']' else '}')
        sb.append(')')
        val annotation = sb.toString()
        listOf(annotation.replace(PERMISSION_ANNOTATION.oldName(), PERMISSION_ANNOTATION.newName()))
      }
    } else emptyList()
  }

  /** Summarizes the permission requirements from this constraint. */
  fun getPermissionAnnotationsString(): String {
    val annotations: List<String> = getPermissionAnnotations()
    return if (annotations.isNotEmpty()) {
      annotations.joinToString("\n") {
        it.replace(SUPPORT_ANNOTATIONS_PREFIX.newName(), "").replace("android.Manifest", "Manifest")
      }
    } else ""
  }

  companion object {
    /**
     * Creates an [InferredConstraints] object from the given [annotated]
     * element
     */
    fun create(
      inferrer: InferAnnotations,
      evaluator: JavaEvaluator,
      annotated: UAnnotated,
      element: PsiElement
    ): InferredConstraints {
      val annotations =
        when (element) {
          is KtAnnotated -> element.annotationEntries.mapNotNull { UastFacade.convertElement(it, null) as? UAnnotation }.toList()
          is PsiAnnotationOwner -> element.annotations.mapNotNull { UastFacade.convertElement(it, null) as? UAnnotation }.toList()
          else -> evaluator.getAllAnnotations(annotated, false)
        }
      val ignore = annotated.uAnnotations.any {
        val qualifiedName = it.qualifiedName
        (qualifiedName == KOTLIN_SUPPRESS || qualifiedName == "java.lang.SuppressWarnings") &&
          it.sourcePsi?.text?.contains("InferAnnotations") == true
      }
      // TODO: Consider looking up @RestrictTo annotations and taking that into consideration for public-ness as well
      val readOnly = inferrer.settings.publicOnly && !isPublic(annotated, element)
      return create(inferrer, element, annotations, readOnly, ignore = ignore)
    }

    private fun isPublic(annotated: UAnnotated, element: PsiElement): Boolean {
      if (element is PsiModifierListOwner) {
        return element.hasModifierProperty(PsiModifier.PUBLIC)
      }
      if (annotated is UDeclarationEx) {
        return annotated.javaPsi.hasModifierProperty(PsiModifier.PUBLIC)
      }

      return false
    }

    /** Creates an [InferredConstraints] object from the given [element] */
    fun create(
      inferrer: InferAnnotations,
      evaluator: JavaEvaluator,
      element: PsiModifierListOwner // Arguably should have used PsiAnnotationOwner instead
    ): InferredConstraints {
      val annotations = evaluator.getAnnotations(element, false)
      val readOnly = element is PsiCompiledElement ||
        inferrer.settings.publicOnly && !element.hasModifierProperty(PsiModifier.PUBLIC)
      return create(inferrer, element, annotations, readOnly, false).apply {
        // R fields have implicit resource types
        if (element is PsiField) {
          val containing = element.containingClass
          val outer = containing?.containingClass
          if (outer != null && SdkConstants.R_CLASS == outer.name) {
            val name = containing.name
            if (name != null) {
              val type = ResourceType.fromClassName(name)?.mapType()
              if (type != null) {
                this.readOnly = true // Normally true but helpful in our tests where we provide it as source

                val annotation = SUPPORT_ANNOTATIONS_PREFIX.newName() + StringUtil.capitalize(type.getName()) + RES_SUFFIX
                if (annotationToIndex[annotation] != null) {
                  addResourceAnnotation(annotation)
                }

                modified = false
              }
            }
          }
        }
      }
    }

    /**
     * Creates an [InferredConstraints] object from the given list of
     * [annotations]
     */
    fun create(
      inferrer: InferAnnotations,
      element: PsiElement,
      annotations: List<UAnnotation>,
      readOnly: Boolean,
      ignore: Boolean
    ): InferredConstraints {
      // We set ignore=false here even if ignore=true was passed in because we *do* want all the original
      // annotations that are on the element to be processed below; we'll update the ignore flag at the end
      // after initializing.
      val constraints = InferredConstraints(inferrer, element, readOnly, false)

      // Initialize existing annotations
      for (annotation in annotations) {
        val qualifiedName = getAnnotationName(annotation) ?: continue

        if (qualifiedName == PERMISSION_ANNOTATION.newName()) {
          val permissions: MutableList<Any> = mutableListOf()
          val value = annotation.findAttributeValue(null) ?: annotation.findAttributeValue(ATTR_VALUE)
          val project = inferrer.project
          addPermissions(project, value, permissions)
          if (permissions.isNotEmpty()) {
            constraints.permissionReferences = Sets.newHashSet(permissions)
          } else {
            val anyOf = annotation.findAttributeValue(AnnotationDetector.ATTR_ANY_OF)
            addPermissions(project, anyOf, permissions)
            if (permissions.isNotEmpty()) {
              constraints.permissionReferences = Sets.newHashSet(permissions)
            } else {
              val allOf = annotation.findAttributeValue(AnnotationDetector.ATTR_ALL_OF)
              addPermissions(project, allOf, permissions)
              if (permissions.isNotEmpty()) {
                constraints.permissionReferences = Sets.newHashSet(permissions)
                constraints.requireAllPermissions = true
              }
            }
          }
        }

        if (annotationToIndex[qualifiedName] != null) {
          constraints.addAnnotation(qualifiedName)

          val attributeValues = annotation.attributeValues
          if (attributeValues.isNotEmpty()) {
            val arguments = constraints.arguments ?: HashMap<String, List<UNamedExpression>>()
              .also { constraints.arguments = it }
            arguments[qualifiedName] = attributeValues
          }
        }
      }

      constraints.modified = false
      constraints.originalAnnotations = constraints.annotations
      constraints.ignore = ignore
      return constraints
    }

    private fun addPermissions(project: Project, value: UExpression?, names: MutableList<Any>) {
      if (value == null) {
        return
      }
      if (value is ULiteralExpression) {
        val name = value.evaluate() as? String
        if (name != null && name.isNotEmpty()) {
          names.add(findAndroidPermissionField(project, name) ?: name)
        }
        // empty is just the default: means not specified
      } else if (value is UReferenceExpression) {
        val resolved = value.resolve()
        if (resolved is PsiField) {
          names.add(resolved)
        }
      } else if (value is UCallExpression) { // array syntax
        for (memberValue in value.valueArguments) {
          addPermissions(project, memberValue, names)
        }
      }
    }

    private fun findAndroidPermissionField(project: Project, permissionName: String): PsiField? {
      // When we get permission requirements from for example jar files, we just
      // get the string literal instead of a reference to the permission field,
      // but in annotations we prefer logical references to string literals. Permissions
      // are *usually* built-in ones from the platform, so attempt to
      // pick those up.
      val facade = JavaPsiFacade.getInstance(project)
      val scope = GlobalSearchScope.allScope(project)
      val permissions = facade.findClass("android.Manifest.permission", scope) ?: return null

      // First try direct lookup
      permissions.findFieldByName(permissionName.removePrefix("android.permission."), false)?.let {
        return it
      }

      for (field in permissions.fields) {
        if (field.computeConstantValue() == permissionName) {
          return field
        }
      }

      return null
    }

    /**
     * Given an [annotation], returns the annotation qualified name to use in
     * the constraint set maps
     */
    private fun getAnnotationName(annotation: UAnnotation): String? {
      val qualifiedName = annotation.qualifiedName ?: return null

      if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX.oldName())) {
        return SUPPORT_ANNOTATIONS_PREFIX.newName() + qualifiedName.substring(
          SUPPORT_ANNOTATIONS_PREFIX.oldName().length)
      }

      if (qualifiedName == DIMENSION_ANNOTATION.newName()) {
        val unit = annotation.findAttributeValue("unit")
        if (unit is UReferenceExpression) {
          val resolved = unit.resolve()
          if (resolved is PsiNamedElement) {
            val name = resolved.name
            if ("DP" == name) {
              return DIMENSION_DP_PLACEHOLDER
            } else if ("SP" == name) {
              return DIMENSION_SP_PLACEHOLDER
            }
            // else: default to PX (DIMENSION_MARKER_TYPE)
          }
        }
      }

      return qualifiedName
    }

    /** Special marker for `@Dimension(unit=Dimension.SP)` */
    private const val DIMENSION_SP_PLACEHOLDER = "@sp"

    /** Special marker for `@Dimension(unit=Dimension.DP)` */
    private const val DIMENSION_DP_PLACEHOLDER = "@dp"

    /**
     * Given an annotation [qualifiedName], returns this as annotation source
     * (e.g. including the leading `@` and possibly additional parenthesized
     * arguments). If [namesOnly] is true, will only use simple names instead
     * of fully qualified names.
     */
    fun getAnnotationSource(qualifiedName: String, namesOnly: Boolean, arguments: List<UNamedExpression>? = null, kotlin: Boolean = true): String {
      return if (qualifiedName == DIMENSION_SP_PLACEHOLDER || qualifiedName == DIMENSION_DP_PLACEHOLDER) {
        val unit = if (qualifiedName == DIMENSION_DP_PLACEHOLDER) "DP" else "SP"
        if (namesOnly) {
          "@Dimension($unit)"
        } else {
          "@${DIMENSION_ANNOTATION.newName()}(${DIMENSION_ANNOTATION.newName()}.$unit)"
        }
      } else if (qualifiedName == DIMENSION_ANNOTATION.newName()) {
        // Map @Dimension to @Px (when not explicitly setting a different unit)
        return getAnnotationSource(PX_ANNOTATION.newName(), namesOnly, arguments, kotlin)
      } else {
        if (namesOnly) {
          "@${qualifiedName.substringAfterLast('.')}${getAnnotationArgumentSource(arguments, false, kotlin)}"
        } else {
          "@$qualifiedName${getAnnotationArgumentSource(arguments, true, kotlin)}".let {
            // In Kotlin, we have to convert constant references in the Float class up to Double and ditto for Integer to Long
            // because that's the expected type of these annotations' attributes.
            if (kotlin && (
              qualifiedName == INT_RANGE_ANNOTATION.newName() || qualifiedName == FLOAT_RANGE_ANNOTATION.newName() ||
                qualifiedName == SIZE_ANNOTATION.newName()
              )
            ) {
              it.replace("Float.MIN_VALUE", "Float.MIN_VALUE.toDouble()")
                .replace("Float.MAX_VALUE", "Float.MAX_VALUE.toDouble()")
                .replace("Integer.MIN_VALUE", "Integer.MIN_VALUE.toLong()")
                .replace("Integer.MAX_VALUE", "Integer.MAX_VALUE.toLong()")
            } else {
              it
            }
          }
        }
      }
    }

    private fun getAnnotationArgumentSource(arguments: List<UNamedExpression>?, fqn: Boolean, kotlin: Boolean): String {
      if (arguments.isNullOrEmpty()) return ""
      return "(" + arguments.joinToString {
        val psi = it.sourcePsi
        if (!fqn && psi != null && psi !is PsiCompiledElement) {
          psi.text
        } else {
          val sb = StringBuilder()
          sb.append(it.name).append('=')
          if (psi is PsiCompiledElement && psi is PsiNameValuePair) {
            val value = psi.value
            if (value is PsiExpression) {
              sb.append(value.toSource(kotlin))
            } else {
              error("Unsupported annotation literal type $value")
            }
          } else {
            sb.append(it.expression.toSource(kotlin))
          }
          sb.toString()
        }
      } + ")"
    }

    private fun Any?.literalSource(kotlin: Boolean): String {
      return when (this) {
        is String -> {
          (
            '"' + replace("\\", "\\\\")
              .replace("\r", "\\r").replace("\n", "\\n")
              .replace("\t", "\\t").replace("\b", "\\b")
              .replace("\"", "\\\"") + '"'
            ).let {
            if (kotlin) it.replace("$", "\\$") else it
          }
        }
        is Char -> if (this == '\'') "'\''" else "'$this'"
        is Int -> toConstantReference(this) ?: toString()
        is Double -> toConstantReference(this)
          // because @FloatRange(to = Float.MAX_VALUE) is valid and gets stored in a double
          ?: toConstantReference(this.toFloat())
          ?: toString()
        is Boolean, is Short, is Byte -> toString()
        is Long -> toConstantReference(this)
          // because @IntRange(to = Integer.MAX_VALUE) is valid and gets stored in a long
          ?: toConstantReference(this.toInt())
          ?: (toString() + "L")
        is Float -> toConstantReference(this) ?: (toString() + "f")
        null -> "null"
        else -> error("Unexpected literal type")
      }
    }

    private fun toConstantReference(int: Int): String? {
      return when (int) {
        Integer.MIN_VALUE -> {
          "java.lang.Integer.MIN_VALUE"
        }
        Integer.MAX_VALUE -> {
          "java.lang.Integer.MAX_VALUE"
        } else -> null
      }
    }

    private fun toConstantReference(long: Long): String? {
      return when (long) {
        Long.MIN_VALUE -> {
          "java.lang.Long.MIN_VALUE"
        }
        Long.MAX_VALUE -> {
          "java.lang.Long.MAX_VALUE"
        } else -> toConstantReference(long.toInt())
      }
    }

    private fun toConstantReference(float: Float): String? {
      // We don't need to qualify the constants; java.lang. classes can be referenced unqualified from both Java and Kotlin
      return when (float) {
        Float.MIN_VALUE -> {
          "Float.MIN_VALUE"
        }
        Float.MAX_VALUE -> {
          "Float.MAX_VALUE"
        }
        else -> null
      }
    }

    private fun toConstantReference(double: Double): String? {
      when (double) {
        Double.MIN_VALUE -> {
          return "Double.MIN_VALUE"
        }
        Double.MAX_VALUE -> {
          return "Double.MAX_VALUE"
        }
        Double.NaN -> {
          return "Double.NaN"
        }
        Double.NEGATIVE_INFINITY -> {
          return "Double.NEGATIVE_INFINITY"
        }
        Double.POSITIVE_INFINITY -> {
          return "Double.POSITIVE_INFINITY"
        }
        else -> return null
      }
    }

    /**
     * Generate source code to use for annotation argument initialization. This
     * is not a general UAST to source facility; annotations are limited to
     * only allow some very basic data types -- primitives, constants and class
     * literals, and that's all we support here.
     */
    private fun UExpression.toSource(kotlin: Boolean): String {
      when (this) {
        is ULiteralExpression -> return value.literalSource(kotlin)
        is UCallExpression -> {
          val sourcePsi = sourcePsi
          if (sourcePsi is PsiAnnotation) {
            val annotation = UastFacade.convertElement(sourcePsi, null) as? UAnnotation
              ?: return sourcePsi.toSource(kotlin).let { if (kotlin) it.removePrefix("@") else it }
            val parameters = annotation.attributeValues.joinToString {
              it.name + "=" + it.expression.toSource(kotlin)
            }.let { if (it.isNotBlank()) "($it)" else "" }
            return "${if (kotlin) "" else "@"}${annotation.qualifiedName}$parameters"
          }
          val arguments = valueArguments.joinToString { it.toSource(kotlin) }
          return if (kotlin) "[$arguments]" else "{$arguments}"
        }
        is UPrefixExpression -> {
          return operator.toString() + operand.toSource(kotlin)
        }
        is UPostfixExpression -> {
          return operand.toSource(kotlin) + operator.toString()
        }
        is UReferenceExpression -> {
          val resolved = resolve()
          if (resolved is PsiField) {
            val name = resolved.name
            val cls = resolved.containingClass?.qualifiedName
              ?: return name
            return "$cls.$name"
          }
          val psi = sourcePsi
          if (psi != null && psi !is PsiCompiledElement) {
            return psi.text
          }
        }
        is UNamedExpression -> {
          return this.name + "=" + this.expression.toSource(kotlin)
        }
        is UClassLiteralExpression -> {
          val qualifiedName = this.type?.canonicalText ?: error("Unresolved class expression")
          return qualifiedName + if (kotlin) "::class.java" else ".class"
        }
        is UUnknownExpression -> {
          val sourcePsi = sourcePsi
          if (sourcePsi is PsiAnnotationMemberValue) {
            return sourcePsi.toSource(kotlin)
          }
        }
      }
      error("Could not generate source for expression $this")
    }

    // Used for annotations from binary files; UAST doesn't represent these
    // properly (instead we just get an org.jetbrains.uast.java.UnknownJavaExpression)
    private fun PsiAnnotationMemberValue.toSource(kotlin: Boolean): String {
      when (this) {
        is PsiLiteral -> return value.literalSource(kotlin)
        is PsiArrayInitializerMemberValue -> {
          val arguments = initializers.joinToString { it.toSource(kotlin) }
          return if (kotlin) "[$arguments]" else "{$arguments}"
        }
        is PsiAnnotation -> {
          val parameters = parameterList.attributes.joinToString {
            it.name + "=" + it.value?.toSource(kotlin)
          }.let { if (it.isNotBlank()) "($it)" else "" }
          return "@$qualifiedName$parameters"
        }
        is PsiPrefixExpression -> {
          return text + operand?.toSource(kotlin)
        }
        is PsiPostfixExpression -> {
          return operand.toSource(kotlin) + text
        }
        is PsiReference -> {
          val resolved = resolve()
          if (resolved is PsiField) {
            val name = resolved.name
            val cls = resolved.containingClass?.qualifiedName
              ?: return name
            return "$cls.$name"
          }
          error("Could not resolve reference $this")
        }
        is PsiClassObjectAccessExpression -> {
          val qualifiedName = this.type.canonicalText
          return qualifiedName + if (kotlin) "::class.java" else ".class"
        }
      }
      error("Could not generate source for expression $this")
    }

    /**
     * Returns a lower case description (including an indefinite article) of
     * this [ResourceType].
     */
    fun describeResource(qualifiedName: String): String {
      return when (qualifiedName) {
        ANY_RES_ANNOTATION.newName() -> "a resource of any type"
        COLOR_INT_ANNOTATION.newName() -> "a color int"
        HALF_FLOAT_ANNOTATION.newName() -> "a half-precision float"
        PX_ANNOTATION.newName(), DIMENSION_ANNOTATION.newName() -> "a pixel dimension"
        DIMENSION_DP_PLACEHOLDER -> "a density-independent (dp) pixel dimension"
        DIMENSION_SP_PLACEHOLDER -> "a scale-independent (sp) pixel dimension"
        INT_RANGE_ANNOTATION.newName(), FLOAT_RANGE_ANNOTATION.newName() -> "a range"
        SIZE_ANNOTATION.newName() -> "a size"
        XML_RES_ANNOTATION.newName() -> "an XML resource"
        RAW_RES_ANNOTATION.newName() -> "a raw resource"
        PLURALS_RES_ANNOTATION.newName() -> "a quantity string"
        NAVIGATION_RES_ANNOTATION.newName() -> "a navigation resource"
        else -> {
          val type = ResourceEvaluator.getTypeFromAnnotation(qualifiedName)
          val name = (type?.displayName ?: qualifiedName.substringAfterLast('.')).lowercase(Locale.US)
          val article = when (name[0]) {
            // it's more complicated in reality but works for the limited set of ResourceType descriptions
            'a', 'e', 'i', 'o' -> "an"
            else -> "a"
          }
          "$article $name"
        }
      }
    }

    /**
     * The index of the first resource-related annotation in the
     * [annotationNames] array
     */
    val firstResourceAnnotation: Int

    /**
     * The (inclusive) index of the last resource-related annotation in the
     * [annotationNames] array
     */
    val lastResourceAnnotation: Int

    /**
     * A bitmask which includes all the bits between [firstResourceAnnotation]
     * and [lastResourceAnnotation], inclusive.
     */
    val resourceTypeMask: Long

    /** All annotations that [InferredConstraints] concerns itself with */
    val annotationNames: Array<String> = arrayOf(
      // The order matters; this is the order in which we'll list and add annotations into the source,
      // so make sure it's logical.

      // We also want resource type annotations to all be clustered together since we'll process them
      // as a particular range. That's why for example the resource types are generally alphabetical but
      // we list @AnyRes before @AnimatorRes.

      ANY_RES_ANNOTATION.newName(), // first resource annotation, see firstResourceAnnotation below.
      ANIMATOR_RES_ANNOTATION.newName(),
      ANIM_RES_ANNOTATION.newName(),
      ARRAY_RES_ANNOTATION.newName(),
      ATTR_RES_ANNOTATION.newName(),
      BOOL_RES_ANNOTATION.newName(),
      COLOR_RES_ANNOTATION.newName(),
      DIMEN_RES_ANNOTATION.newName(),
      DRAWABLE_RES_ANNOTATION.newName(),
      FONT_RES_ANNOTATION.newName(),
      FRACTION_RES_ANNOTATION.newName(),
      ID_RES_ANNOTATION.newName(),
      INTEGER_RES_ANNOTATION.newName(),
      INTERPOLATOR_RES_ANNOTATION.newName(),
      LAYOUT_RES_ANNOTATION.newName(),
      MENU_RES_ANNOTATION.newName(),
      NAVIGATION_RES_ANNOTATION.newName(),
      PLURALS_RES_ANNOTATION.newName(),
      RAW_RES_ANNOTATION.newName(),
      STRING_RES_ANNOTATION.newName(),
      STYLEABLE_RES_ANNOTATION.newName(),
      STYLE_RES_ANNOTATION.newName(),
      TRANSITION_RES_ANNOTATION.newName(),
      XML_RES_ANNOTATION.newName(),
      COLOR_INT_ANNOTATION.newName(),
      PX_ANNOTATION.newName(),
      DIMENSION_ANNOTATION.newName(),
      DIMENSION_SP_PLACEHOLDER,
      DIMENSION_DP_PLACEHOLDER,
      GRAVITY_INT_ANNOTATION.newName(),
      HALF_FLOAT_ANNOTATION.newName(), // last resource annotation, see lastResourceAnnotation below

      KEEP_ANNOTATION.newName(),
      CALL_SUPER_ANNOTATION.newName(),

      CHECK_RESULT_ANNOTATION.newName(),
      UI_THREAD_ANNOTATION.newName(),
      MAIN_THREAD_ANNOTATION.newName(),
      WORKER_THREAD_ANNOTATION.newName(),
      BINDER_THREAD_ANNOTATION.newName(),
      ANY_THREAD_ANNOTATION.newName(),
      VISIBLE_FOR_TESTING_ANNOTATION.newName(),
      SIZE_ANNOTATION.newName(),
      FLOAT_RANGE_ANNOTATION.newName(),
      INT_RANGE_ANNOTATION.newName(),
      RESTRICT_TO_ANNOTATION.newName(),
      PERMISSION_ANNOTATION.newName(),
      PERMISSION_ANNOTATION_READ.newName(),
      PERMISSION_ANNOTATION_WRITE.newName(),
      INT_DEF_ANNOTATION.newName(),
      LONG_DEF_ANNOTATION.newName(),

      CHECKS_SDK_INT_AT_LEAST_ANNOTATION,
      DISCOURAGED_ANNOTATION,
      REQUIRES_API_ANNOTATION.newName(),
      REQUIRES_FEATURE_ANNOTATION.newName()
    )

    /**
     * A map from qualified annotation name back to the corresponding index in
     * the [annotationNames] array
     */
    val annotationToIndex: MutableMap<String, Int> = mutableMapOf()

    private fun isResourceAnnotation(qualifiedName: String): Boolean {
      val index = annotationToIndex[qualifiedName] ?: return false
      // Inherit resource type annotations
      return index in firstResourceAnnotation..lastResourceAnnotation
    }

    /**
     * Whether this annotation is one that does not take any arguments. This
     * is true for most annotations. These can be treated more simply; we can
     * simply record their presence in the [annotations] bit array; others
     * require storing and possibly merging arguments (such as conflicting
     * `@IntRange` bounds and so on).
     */
    fun isMarkerAnnotation(qualifiedName: String): Boolean {
      return when (qualifiedName) {
        // Consider
        // GUARDED_BY.newName(),
        // INSPECTABLE_PROPERTY.newName(),

        // The following are NOT marker annotations:
        RESTRICT_TO_ANNOTATION.newName(),
        INT_RANGE_ANNOTATION.newName(),
        FLOAT_RANGE_ANNOTATION.newName(),
        INT_DEF_ANNOTATION.newName(),
        LONG_DEF_ANNOTATION.newName(),
        STRING_DEF_ANNOTATION.newName(),
        PERMISSION_ANNOTATION.newName(),
        PERMISSION_ANNOTATION_READ.newName(),
        PERMISSION_ANNOTATION_WRITE.newName(),
        CHECK_RESULT_ANNOTATION.newName(),
        CHECKS_SDK_INT_AT_LEAST_ANNOTATION,
        DISCOURAGED_ANNOTATION,
        REQUIRES_API_ANNOTATION.newName(),
        REQUIRES_FEATURE_ANNOTATION.newName(),
        VISIBLE_FOR_TESTING_ANNOTATION.newName() -> false

        // @Dimension is special; it takes arguments, but there are only
        // 3 possible values and we track these using the DIMENSION_*_PLACEHOLDER
        // values
        else -> true
      }
    }

    /**
     * Whether the given annotation, if returned from a method, implies that we
     * should place it on the method itself.
     */
    fun transferReturnToMethod(qualifiedName: String): Boolean {
      if (isResourceAnnotation(qualifiedName)) {
        return true
      }

      return when (qualifiedName) {
        INT_DEF_ANNOTATION.newName(),
        INT_RANGE_ANNOTATION.newName(),
        FLOAT_RANGE_ANNOTATION.newName(),
        SIZE_ANNOTATION.newName(),
        CHECK_RESULT_ANNOTATION.newName() -> true
        else -> false
      }
    }

    /**
     * Whether the given annotation, if inferred for an argument, implies that
     * we should place it on the corresponding parameter itself.
     */
    fun transferArgumentToParameter(qualifiedName: String): Boolean {
      if (isResourceAnnotation(qualifiedName)) {
        if (qualifiedName == ANY_RES_ANNOTATION.newName()) {
          return false
        }
        return true
      }

      return when (qualifiedName) {
        INT_RANGE_ANNOTATION.newName(),
        FLOAT_RANGE_ANNOTATION.newName(),
        SIZE_ANNOTATION.newName() -> true
        else -> false
      }
    }

    fun inheritMethodAnnotation(qualifiedName: String): Boolean {
      if (isResourceAnnotation(qualifiedName)) {
        return true
      }

      return when (qualifiedName) {
        INT_RANGE_ANNOTATION.newName(),
        FLOAT_RANGE_ANNOTATION.newName(),
        SIZE_ANNOTATION.newName(),
        CHECK_RESULT_ANNOTATION.newName(),
        PERMISSION_ANNOTATION.newName() -> true

        // Don't inherit the rest. This includes all the threading annotations (because they're implicitly inherited anyway;
        // lint will look at the hierarchy), and ditto for the @CallSuper annotation, etc.

        else -> false
      }
    }

    fun inheritParameterAnnotation(qualifiedName: String): Boolean {
      if (isResourceAnnotation(qualifiedName)) {
        return true
      }

      return when (qualifiedName) {
        SIZE_ANNOTATION.newName(),
        FLOAT_RANGE_ANNOTATION.newName(),
        INT_RANGE_ANNOTATION.newName() -> true
        else -> false
      }
    }

    fun annotationAppliesToParameters(qualifiedName: String): Boolean {
      return when (qualifiedName) {
        VISIBLE_FOR_TESTING_ANNOTATION.newName(),
        MAIN_THREAD_ANNOTATION.newName(),
        UI_THREAD_ANNOTATION.newName(),
        BINDER_THREAD_ANNOTATION.newName(),
        VISIBLE_FOR_TESTING_ANNOTATION.newName(),
        REQUIRES_FEATURE_ANNOTATION.newName(),
        PERMISSION_ANNOTATION.newName(),
        PERMISSION_ANNOTATION_READ.newName(),
        PERMISSION_ANNOTATION_WRITE.newName() -> false
        else -> true
      }
    }

    fun annotationAppliesToFields(qualifiedName: String): Boolean {
      return annotationAppliesToParameters(qualifiedName)
    }

    init {
      for (i in annotationNames.indices) {
        assert(annotationToIndex[annotationNames[i]] == null) { annotationNames[i] } // no duplicates
        annotationToIndex[annotationNames[i]] = i
      }
      assert(annotationNames.size < 64) // storing in a long

      // Look up resource annotation block; we use these indices when producing Set<ResourceType>, when
      // clearing out annotations superfluous with @AnyRes, etc.
      firstResourceAnnotation = annotationToIndex[ANY_RES_ANNOTATION.newName()]!!
      lastResourceAnnotation = annotationToIndex[HALF_FLOAT_ANNOTATION.newName()]!!

      var resMask = getAnnotationMask(firstResourceAnnotation)
      for (i in firstResourceAnnotation until lastResourceAnnotation) {
        resMask = resMask shl 1 or resMask
      }
      // Validity check
      assert(resMask and (getAnnotationMask(firstResourceAnnotation - 1)) == 0L)
      assert(resMask and (getAnnotationMask(firstResourceAnnotation)) != 0L)
      assert(resMask and (getAnnotationMask(lastResourceAnnotation)) != 0L)
      assert(resMask and (getAnnotationMask(lastResourceAnnotation + 1)) == 0L)
      resourceTypeMask = resMask
    }

    /**
     * The single bit mask which corresponds to an annotation (identified by
     * qualified name) in a bitmask such as [InferredConstraints.annotations].
     */
    fun getAnnotationMask(qualifiedName: String): Long {
      val index = annotationToIndex[qualifiedName] ?: error("Unsupported annotation $qualifiedName")
      return getAnnotationMask(index)
    }

    /**
     * The single bit mask which corresponds to an annotation (identified
     * by the annotation's index in [annotationNames]) in a bitmask such as
     * [InferredConstraints.annotations].
     */
    fun getAnnotationMask(annotationIndex: Int): Long {
      return 1L shl annotationIndex
    }
  }
}

/**
 * A set of resource types, using the same bitmask as [InferredConstraints]
 * such that we can easily compare and add resources.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class ResourceTypeSet(val bits: Long) : Iterable<String> {
  override fun toString(): String {
    return annotations().joinToString(", ") { "@${it.replace(SUPPORT_ANNOTATIONS_PREFIX.newName(), "")}" }
  }

  /**
   * Returns true if the annotation identified by [qualifiedName] is in this
   * bit set
   */
  fun contains(qualifiedName: String): Boolean {
    return (bits and InferredConstraints.getAnnotationMask(qualifiedName)) != 0L
  }

  /** Returns the number of annotations in this set */
  val size: Int
    get() {
      // Brian Kernighan’s Algorithm
      var n: Long = bits
      var size = 0
      while (n > 0) {
        n = n and n - 1
        size++
      }
      return size
    }

  /** Returns the list of annotations in this set */
  fun annotations(): List<String> {
    if (bits == 0L) {
      return emptyList()
    }

    val list = mutableListOf<String>()
    for (i in InferredConstraints.firstResourceAnnotation..InferredConstraints.lastResourceAnnotation) {
      if ((bits and InferredConstraints.getAnnotationMask(i)) != 0L) {
        list.add(annotationNames[i])
      }
    }

    return list
  }

  /** Returns an iterator over the annotations in this set */
  override fun iterator(): Iterator<String> = annotations().iterator()
}
