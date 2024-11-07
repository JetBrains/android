/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.ModCommandLintQuickFix
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.lint.checks.PermissionDetector
import com.android.tools.lint.checks.PermissionRequirement
import com.android.tools.lint.detector.api.LintFix
import com.google.common.collect.Maps
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiTypes
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlTag
import kotlin.collections.plus
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStatementExpression

class AndroidLintMissingPermissionInspection :
  AndroidLintInspectionBase(
    message("android.lint.inspections.missing.permission"),
    PermissionDetector.MISSING_PERMISSION,
  ) {

  override fun getQuickFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    message: String,
    quickfixData: LintFix?,
  ): Array<LintIdeQuickFix> {
    if (quickfixData is LintFix.DataMap) {
      val names =
        quickfixData.getStringList(PermissionDetector.KEY_MISSING_PERMISSIONS)
          ?: return super.getQuickFixes(startElement, endElement, message, quickfixData)

      // Create quickfix(es) that can add or update the @RequiresPermission annotation.
      val operator = parseOperator(quickfixData.getString(PermissionDetector.KEY_OPERATOR, "&"))
      val requiresPermissionFixes =
        AddRequiresPermissionAnnotationFix.create(startElement, names.toSet(), operator)
          .map(::ModCommandLintQuickFix)

      // [missing permissions: Set<String>, maxSdkVersion: Integer]
      // Add quickfixes for the missing permissions
      val lastApplicableApi = quickfixData.getInt(PermissionDetector.KEY_LAST_API, -1)
      if (lastApplicableApi != -1) {
        return (names.map { ModCommandLintQuickFix(AddPermissionFix(it, lastApplicableApi)) } +
            requiresPermissionFixes)
          .toTypedArray()
      }

      // [revocable permissions: Set<String>, requirement: PermissionRequirement] :
      // Add quickfix for requesting permissions
      quickfixData
        .getString(PermissionDetector.KEY_REQUIREMENT, null)
        ?.let(PermissionRequirement::deserialize)
        ?.let {
          return (listOf(ModCommandLintQuickFix(AddCheckPermissionFix(it, startElement, names))) +
              requiresPermissionFixes)
            .toTypedArray()
        }
    }

    return super.getQuickFixes(startElement, endElement, message, quickfixData)
  }

  internal class AddPermissionFix(private val permissionName: String, private val maxVersion: Int) :
    ModCommandAction {
    private val name =
      "Add Permission ${permissionName.substring(permissionName.lastIndexOf('.') + 1)}"

    override fun getFamilyName() = "AddPermissionFix"

    override fun getPresentation(context: ActionContext) = Presentation.of(name)

    override fun perform(context: ActionContext): ModCommand {
      val facet = AndroidFacet.getInstance(context.file) ?: return ModCommand.nop()

      // I tried manipulating the file using DOM apis, using this:
      //    Permission permission = manifest.addPermission();
      //    permission.getName().setValue(myPermissionName);
      // (which required adding
      //      Permission addPermission();
      // to org.jetbrains.android.dom.manifest.Manifest).
      //
      // However, that will append a <permission name="something"/> element to the
      // *end* of the manifest, which is not right (and will trigger a lint warning).
      // So, instead we manipulate the XML document directly via PSI. (This is
      // incidentally also how the AndroidModuleBuilder#configureManifest method works.)
      val manifestTag = Manifest.getMainManifest(facet)?.xmlTag ?: return ModCommand.nop()

      return ModCommand.psiUpdate(context) { updater ->
        addPermission(updater.getWritable(manifestTag), facet)?.let { updater.moveCaretTo(it) }
      }
    }

    private fun addPermission(manifestTag: XmlTag, facet: AndroidFacet): XmlTag? {
      var permissionTag =
        manifestTag.createChildTag(TAG_USES_PERMISSION, "", null, false) ?: return null

      // Find best insert position:
      //   (1) attempt to insert alphabetically among any permission tags
      //   (2) if no permission tags are found, put it before the application tag
      val before =
        manifestTag.subTags.firstOrNull { tag ->
          when (tag.name) {
            TAG_APPLICATION -> true
            TAG_USES_PERMISSION,
            TAG_USES_PERMISSION_SDK_23,
            TAG_USES_PERMISSION_SDK_M -> {
              tag.getAttributeValue(ATTR_NAME, ANDROID_URI)?.let { it > permissionName } ?: false
            }
            else -> false
          }
        }

      permissionTag =
        if (before == null) {
          manifestTag.addSubTag(permissionTag, false)
        } else {
          manifestTag.addBefore(permissionTag, before) as XmlTag
        }

      // Do this *after* adding the tag to the document; otherwise, setting the
      // namespace prefix will not work correctly
      permissionTag.setAttribute(ATTR_NAME, ANDROID_URI, permissionName)

      // Some permissions only apply for a range of API levels - for example,
      // the MANAGE_ACCOUNTS permission is only needed pre Marshmallow. In that
      // case set a maxSdkVersion attribute on the uses-permission element.
      if (
        maxVersion != Int.MAX_VALUE &&
          maxVersion >= StudioAndroidModuleInfo.getInstance(facet).minSdkVersion.apiLevel
      ) {
        permissionTag.setAttribute("maxSdkVersion", ANDROID_URI, maxVersion.toString())
      }
      return permissionTag
    }
  }

  @Suppress("UnstableApiUsage")
  private class AddCheckPermissionFix(
    private val myRequirement: PermissionRequirement,
    call: PsiElement,
    private val myRevocablePermissions: Collection<String>,
  ) : PsiUpdateModCommandAction<PsiElement>(call) {

    override fun getFamilyName() = "Add permission check"

    override fun invoke(context: ActionContext, call: PsiElement, updater: ModPsiUpdater) {
      val project = call.project
      val isKotlin = call.language is KotlinLanguage

      // Find the statement containing the method call
      val statement =
        if (isKotlin)
          call.parents(false).filterIsInstance<KtExpression>().firstOrNull {
            it.parent is KtStatementExpression
          }
        else PsiTreeUtil.getParentOfType(call, PsiStatement::class.java, true)

      val parent = statement?.parent ?: return // highly unlikely

      val facade = JavaPsiFacade.getInstance(project)
      val module = ModuleUtilCore.findModuleForPsiElement(call) ?: return
      val moduleScope = GlobalSearchScope.moduleWithLibrariesScope(module)
      val manifest = facade.findClass("android.Manifest.permission", moduleScope)
      val permissionNames: Map<String, PsiField>

      if (manifest != null) {
        val fields = manifest.fields
        permissionNames = Maps.newHashMapWithExpectedSize(fields.size)
        for (field in fields) {
          val initializer = field.initializer
          if (initializer is PsiLiteralExpression) {
            val value = initializer.value
            if (value is String) {
              permissionNames.put(value, field)
            }
          }
        }
      } else {
        permissionNames = emptyMap()
      }

      // Look up the operator combining the requirements, and *reverse it*.
      // That's because we're adding a check to exit if the permissions are *not* met.
      // For example, take the case of location permissions: you need COARSE OR FINE.
      // In that case, we check that you do not have COARSE, *and* that you do not have FINE,
      // before we exit.
      var operator = myRequirement.operator
      if (operator == null || operator === JavaTokenType.ANDAND) {
        operator = JavaTokenType.OROR
      } else if (operator === JavaTokenType.OROR) {
        operator = JavaTokenType.ANDAND
      }

      val activityCompat =
        facade.findClass("androidx.core.app.ActivityCompat", moduleScope)
          ?: facade.findClass("android.support.v4.app.ActivityCompat", moduleScope)

      // If using an older version of appcompat than 23.0.1, revert to platform version.
      val usingAppCompat =
        activityCompat?.findMethodsByName("requestPermissions", false)?.isNotEmpty() ?: false

      // TODO(b/319287252): rewrite with IfSurrounder APIs when available (KTIJ-29939)
      val code = buildString {
        append("if (")
        var first = true

        for (permission in myRevocablePermissions) {
          if (first) {
            first = false
          } else {
            append(' ')
            when (operator) {
              JavaTokenType.ANDAND -> append("&&")
              JavaTokenType.OROR -> append("||")
              JavaTokenType.XOR -> append("^")
            }
            append(' ')
          }
          if (usingAppCompat) {
            activityCompat?.let { append("${activityCompat.qualifiedName}.") }
          }
          append("checkSelfPermission(")
          if (usingAppCompat) {
            append("this, ")
          }

          // Try to map permission strings back to field references!
          val field = permissionNames[permission]
          if (field != null && field.containingClass != null) {
            append(field.containingClass!!.qualifiedName).append('.').append(field.name)
          } else {
            append('"').append(permission).append('"')
          }
          append(") != android.content.pm.PackageManager.PERMISSION_GRANTED")
        }
        appendLine(") {")
        appendLine(" // TODO: Consider calling")
        append(" //    Activity")
          .append(if (usingAppCompat) "Compat" else "")
          .append(
            """#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity
            """
              .trimIndent()
          )
          .append(if (usingAppCompat) "Compat" else "")
          .append("#requestPermissions for more details.\n")

        // TODO: Add additional information here, perhaps pointing to
        // http://android-developers.blogspot.com/2015/09/google-play-services-81-and-android-60.html
        // or ask our assistant for suggestions.
        val method =
          if (isKotlin) null else PsiTreeUtil.getParentOfType(call, PsiMethod::class.java, true)
        if (method != null && PsiTypes.voidType() != method.returnType) {
          append("return TODO")
        } else {
          append("return")
        }
        if (!isKotlin) {
          append(';')
        }
        append("\n}\n")
      }

      if (isKotlin) {
        val factory = KtPsiFactory(project)
        val check = factory.createBlock(code)
        for (child in check.children) {
          if (child is KtIfExpression) {
            val added = parent.addBefore(child, statement)
            parent.addBefore(factory.createNewLine(), statement)
            if (added is KtElement) {
              ShortenReferencesFacility.Companion.getInstance().shorten(added)
            }
            break
          }
        }
      } else {
        val factory = facade.elementFactory
        val check = factory.createStatementFromText(code, call)
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(check)
        parent.addBefore(check, statement)
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private class AddRequiresPermissionAnnotationFix
  private constructor(
    private val elementToAnnotate: PsiElement,
    private val oldPermissionNames: Set<String>,
    private val newPermissionNames: Set<String>,
    private val isAnd: Boolean = true,
    private val showAdditions: Boolean = false,
  ) : PsiUpdateModCommandAction<PsiElement>(elementToAnnotate) {

    override fun getFamilyName() = "Add @RequiresPermission annotation"

    override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
      when (val writable = updater.getWritable(elementToAnnotate)) {
        is KtAnnotated -> {
          addOrUpdateKotlinAnnotation(writable)
          ShortenReferencesFacility.Companion.getInstance().shorten(writable)
        }
        is PsiModifierListOwner -> {
          addOrUpdateJavaAnnotation(writable)
          JavaCodeStyleManager.getInstance(writable.project).shortenClassReferences(writable)
        }
        else -> return
      }
    }

    private fun addOrUpdateKotlinAnnotation(ktAnnotated: KtAnnotated) {
      val existing =
        analyze(ktAnnotated) {
          ktAnnotated.annotationEntries.firstOrNull { it.fqNameMatches(REQUIRES_PERMISSION, this) }
        }
      if (existing == null) {
        ktAnnotated.addAnnotation(
          ClassId.topLevel(FqName(REQUIRES_PERMISSION)),
          getKotlinInnerText(),
          false,
        )
        return
      }
      // Find the corresponding argument that has the permission(s), if any, and delete it
      // as we will be replacing it.
      existing.valueArguments
        .firstOrNull {
          it.getArgumentName()?.asName?.asString()?.let(ARGUMENT_NAMES::contains) ?: true
        }
        ?.asElement()
        ?.delete()

      val combinedNames = oldPermissionNames + newPermissionNames.map { it.toCanonicalPermission() }

      val internals =
        when {
          combinedNames.size == 1 -> combinedNames.single()
          isAnd -> combinedNames.joinToString(prefix = "allOf = [", postfix = "]")
          else -> combinedNames.joinToString(prefix = "anyOf = [", postfix = "]")
        }

      // Create the new first argument.
      val newFirstArg = KtPsiFactory(ktAnnotated.project).createArgument(internals)
      // If there are other arguments, we will need to add this one before those.
      val existingFirstArg = existing.valueArgumentList?.arguments?.firstOrNull()
      existing.valueArgumentList?.addArgumentBefore(newFirstArg, existingFirstArg)
    }

    private fun getKotlinInnerText(): String {
      val combinedNames = oldPermissionNames + newPermissionNames.map { it.toCanonicalPermission() }
      return when {
        combinedNames.size == 1 -> combinedNames.single()
        isAnd -> combinedNames.joinToString(prefix = "allOf = [", postfix = "]")
        else -> combinedNames.joinToString(prefix = "anyOf = [", postfix = "]")
      }
    }

    private fun addOrUpdateJavaAnnotation(psiModifierListOwner: PsiModifierListOwner) {
      val annotation =
        psiModifierListOwner.getAnnotation(REQUIRES_PERMISSION)
          ?: psiModifierListOwner.modifierList?.addAnnotation(REQUIRES_PERMISSION)
          ?: return

      // Clear any existing stuff
      for (argumentName in ARGUMENT_NAMES) {
        annotation.setDeclaredAttributeValue(argumentName, null)
      }
      val newArgName =
        when {
          oldPermissionNames.size + newPermissionNames.size == 1 -> "value"
          isAnd -> "allOf"
          else -> "anyOf"
        }
      val combinedNames = oldPermissionNames + newPermissionNames.map { it.toCanonicalPermission() }
      val innerText =
        when {
          combinedNames.size == 1 -> combinedNames.single()
          else -> combinedNames.joinToString(prefix = "{", postfix = "}")
        }
      val newValue =
        JavaPsiFacade.getInstance(elementToAnnotate.project)
          .elementFactory
          .createExpressionFromText(innerText, elementToAnnotate)
      annotation.setDeclaredAttributeValue(newArgName, newValue)
    }

    override fun getPresentation(context: ActionContext, element: PsiElement): Presentation {
      val namesToDisplay = newPermissionNames.map { it.toCanonicalPermissionShort() }
      val targetName = (elementToAnnotate as? PsiNamedElement)?.name ?: "Enclosing Element"
      val str =
        when {
          oldPermissionNames.isEmpty() ->
            message("android.lint.fix.add.requires.permission", targetName)
          showAdditions ->
            message(
              "android.lint.fix.update.requires.permission.with",
              targetName,
              namesToDisplay.joinToString(),
            )
          else -> message("android.lint.fix.update.requires.permission", targetName)
        }
      return Presentation.of(str)
    }

    private fun String.toCanonicalPermissionField(): PsiField? =
      elementToAnnotate.module?.getManifestPermissionsMap()?.get(this)

    private fun String.toCanonicalPermission(): String =
      toCanonicalPermissionField()?.kotlinFqName?.asString() ?: "\"$this\""

    private fun String.toCanonicalPermissionShort(): String =
      toCanonicalPermissionField()?.name ?: "\"$this\""

    companion object {
      private const val REQUIRES_PERMISSION = "androidx.annotation.RequiresPermission"

      private val KOTLIN_TARGETS =
        listOf(KtFunction::class, KtPropertyAccessor::class, KtClass::class, KtConstructor::class)

      private val JAVA_TARGETS = listOf(PsiMethod::class, PsiField::class, PsiClass::class)

      private val ARGUMENT_NAMES = listOf("value", "anyOf", "allOf")

      private fun createKotlinParams(element: PsiElement): Pair<PsiElement, Set<String>>? {
        val elementToAnnotate =
          element
            .parents(withSelf = false)
            .filterIsInstance<KtAnnotated>()
            .filter { candidate -> KOTLIN_TARGETS.any { it.isInstance(candidate) } }
            .firstOrNull() ?: return null

        val argument =
          analyze(elementToAnnotate) {
              elementToAnnotate.annotationEntries.firstOrNull {
                it.getQualifiedName(this) == REQUIRES_PERMISSION
              }
            }
            ?.valueArguments
            ?.firstOrNull {
              it.getArgumentName()?.asName?.asString()?.let(ARGUMENT_NAMES::contains) ?: true
            }

        val existingPermissions =
          when (val expr = argument?.getArgumentExpression()) {
            null -> setOf()
            is KtCollectionLiteralExpression ->
              expr.innerExpressions.map(PsiElement::getText).toSet()
            else -> setOf(expr.text)
          }
        // We cannot add to an existing "OR" annotation.
        if (
          existingPermissions.size > 1 && argument?.getArgumentName()?.asName?.asString() == "anyOf"
        )
          return null
        return elementToAnnotate to existingPermissions
      }

      private fun createJavaParams(element: PsiElement): Pair<PsiElement, Set<String>>? {
        val elementToAnnotate =
          element
            .parents(withSelf = false)
            .filterIsInstance<PsiModifierListOwner>()
            .filter { candidate -> JAVA_TARGETS.any { it.isInstance(candidate) } }
            .firstOrNull() ?: return null

        val argument =
          elementToAnnotate.modifierList
            ?.findAnnotation(REQUIRES_PERMISSION)
            ?.attributes
            ?.firstOrNull { it.attributeName in ARGUMENT_NAMES } as? PsiNameValuePair
        val existingPermissions =
          when (val attributeValue = argument?.value) {
            null -> setOf()
            is PsiArrayInitializerMemberValue ->
              attributeValue.initializers.map(PsiElement::getText).toSet()
            else -> setOf(attributeValue.text)
          }
        // We cannot extend an existing "OR" annotation.
        if (existingPermissions.size > 1 && argument?.attributeName == "anyOf") return null

        return elementToAnnotate to existingPermissions
      }

      fun create(
        element: PsiElement,
        newPermissions: Set<String>,
        operator: IElementType,
      ): List<AddRequiresPermissionAnnotationFix> {
        val (elementToAnnotate, existingPermissions) =
          when (element.language) {
            is KotlinLanguage -> createKotlinParams(element)
            else -> createJavaParams(element)
          } ?: return listOf()

        // If the existing permissions are an AND and the new ones are an OR, then we can just
        // offer to add each of them individually.
        if (existingPermissions.isNotEmpty() && operator != JavaTokenType.ANDAND) {
          return newPermissions.map {
            AddRequiresPermissionAnnotationFix(
              elementToAnnotate,
              existingPermissions,
              setOf(it),
              showAdditions = true,
            )
          }
        }

        return listOf(
          AddRequiresPermissionAnnotationFix(
            elementToAnnotate,
            existingPermissions,
            newPermissions,
            operator == JavaTokenType.ANDAND,
          )
        )
      }
    }
  }
}

/** Parse a [String] representation of an operator that can join permissions. */
private fun parseOperator(s: String?): IElementType =
  when (s) {
    "&" -> JavaTokenType.ANDAND
    "|" -> JavaTokenType.OROR
    "^" -> JavaTokenType.XOR
    else -> throw IllegalArgumentException("Unsupported operator: $s")
  }

/**
 * Gets a map from [String] permission values to the canonical [PsiField] that holds them for
 * `android.Manifest` permissions.
 *
 * E.g. `"android.permission.CAMERA"` -> `android.Manifest.permission.CAMERA`
 */
private fun Module.getManifestPermissionsMap(): Map<String, PsiField> =
  CachedValuesManager.getManager(project).getCachedValue(this) {
    val facade = JavaPsiFacade.getInstance(project)
    val moduleScope = GlobalSearchScope.moduleWithLibrariesScope(this)
    val manifest = facade.findClass("android.Manifest.permission", moduleScope)
    if (manifest == null) {
      CachedValueProvider.Result(emptyMap())
    } else {
      @Suppress("UNCHECKED_CAST")
      CachedValueProvider.Result(
        (manifest.fields.associateBy {
          (it.initializer as? PsiLiteralExpression)?.value as? String
        } - null) // Remove null from the mapping
          as Map<String, PsiField>,
        manifest,
      )
    }
  }
