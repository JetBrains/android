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
package com.android.tools.idea.compose.annotator

import com.android.tools.idea.compose.annotator.check.common.BadType
import com.android.tools.idea.compose.annotator.check.common.CheckRule
import com.android.tools.idea.compose.annotator.check.common.Failure
import com.android.tools.idea.compose.annotator.check.common.IssueReason
import com.android.tools.idea.compose.annotator.check.common.Missing
import com.android.tools.idea.compose.annotator.check.common.ParameterRule
import com.android.tools.idea.compose.annotator.check.common.Repeated
import com.android.tools.idea.compose.annotator.check.common.Unknown
import com.android.tools.idea.compose.annotator.check.device.DeviceSpecRule
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.Preview
import com.android.tools.idea.compose.preview.getContainingComposableUMethod
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.highlighter.isAnnotationClass
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElement

private val PreviewCheckResultKey = Key.create<Pair<String, CheckResult>>(PreviewAnnotationCheck::class.java.canonicalName)

/**
 * Singleton that provides methods to verify the correctness of the Compose @Preview annotation.
 */
internal object PreviewAnnotationCheck {
  private val Passed: CheckResult = CheckResult(emptyList(), null)

  private fun failedCheck(text: String) = CheckResult(listOf(Failure(text)), null)

  /**
   * Takes a [KtAnnotationEntry] element that should correspond to a reference of Compose @Preview annotation.
   *
   * Returns a [CheckResult] that contains a list of issues found in the annotation, so an empty list means that the annotation is
   * syntactically correct. The [CheckResult] is cached into the PsiElement given, and it's refreshed based on the contents of the
   * annotation.
   */
  fun checkPreviewAnnotationIfNeeded(annotationEntry: KtAnnotationEntry): CheckResult {
    if (!ApplicationManager.getApplication().isReadAccessAllowed) return failedCheck("No read access")
    val annotation = annotationEntry.toUElement() as? UAnnotation ?: return failedCheck("Can't get annotation UElement")
    if (!hasValidTarget(annotation)) return failedCheck(
      "Preview target must be a composable function${if (StudioFlags.COMPOSE_MULTIPREVIEW.get()) " or an annotation class" else ""}"
    )
    val deviceValueExpression = annotation.findDeclaredAttributeValue(PARAMETER_DEVICE) ?: return Passed
    val deviceValue = deviceValueExpression.evaluateString() ?: return failedCheck("Can't get string literal of 'device' value")

    // We only check for the device spec string, so a non 'spec:' string is considered to 'pass' the check
    if (!deviceValue.startsWith(Preview.DeviceSpec.PREFIX)) return Passed

    synchronized(PreviewAnnotationCheck) { // Protect reading/writing the cached result from asynchronous calls
      annotationEntry.getUserData(PreviewCheckResultKey)?.also { existingResult ->
        if (existingResult.first == deviceValue) {
          Logger.getInstance(PreviewAnnotationCheck::class.java).debug("Found existing CheckResult")
          return existingResult.second
        }
        else {
          annotationEntry.putUserData(PreviewCheckResultKey, null)
        }
      }
    }

    val configString = deviceValue.substringAfter(Preview.DeviceSpec.PREFIX)

    val rule = if (configString.contains(Preview.DeviceSpec.PARAMETER_SHAPE) || !StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.get()) {
      DeviceSpecRule.Legacy
    }
    else {
      DeviceSpecRule.LanguageBased
    }
    val result = doCheckDeviceParams(listParameters(configString), rule)
    synchronized(PreviewAnnotationCheck) {
      annotationEntry.putUserData(PreviewCheckResultKey, Pair(deviceValue, result))
    }
    return result
  }
}

private fun hasValidTarget(annotation: UAnnotation) =
  annotation.getContainingComposableUMethod() != null ||
  (StudioFlags.COMPOSE_MULTIPREVIEW.get() && annotation.getContainingUClass()?.isAnnotationClass() == true)

/**
 * Regex to match a string with the [Preview.DeviceSpec.OPERATOR] between two other non-empty strings. E.g: name=value, n=v
 */
private val paramValueRegex: Regex by lazy { Regex("(.+)${Preview.DeviceSpec.OPERATOR}(.+)") }

/**
 * Converts the original [configString] using the basic supported format: `[parameter0,value0],[parameter1,value1],[...]`
 */
private fun listParameters(configString: String): Collection<Pair<String, String>> =
  configString.split(Preview.DeviceSpec.SEPARATOR).map { paramString ->
    val capturedValues = paramValueRegex.matchEntire(paramString)?.groupValues

    if (capturedValues != null && capturedValues.size == 3) {
      // 0-index corresponds to the original string, the others are the capture groups (1 -> parameter name, 2 -> parameter value)
      Pair(capturedValues[1], capturedValues[2])
    }
    else {
      // Preserve invalid sections, all information is needed for a correct check
      Pair(paramString, "")
    }
  }

/**
 * Checks the given collection of param-value pairs for correctness, it should match the pattern used to describe a custom device
 * specification: "spec:shape=<enum>,width=<integer>,height=<integer>,unit=<enum>,dpi=<integer>". With no particular order enforced.
 *
 * The issues that the returned [CheckResult] may report:
 * - [Repeated]: There should only be one of each parameter
 * - [BadType]: A parameter has a value that does not correspond to the expected type (a float instead of an integer for example)
 * - [Unknown]: Unknown/unsupported parameter found
 * - [Missing]: An expected parameter is missing.
 *
 * Every issue will have the related parameter name in the [IssueReason.parameterName] message.
 *
 * [CheckResult.proposedFix] is a proposed string to fix the issues found, based on the original input.
 */
private fun doCheckDeviceParams(originalParams: Collection<Pair<String, String>>, rule: CheckRule): CheckResult {
  val issues = mutableListOf<IssueReason>()

  // Set of parameters confirmed in the original parameter list
  val appliedParams = mutableSetOf<String>()

  // Set of parameters present more than once
  val repeated = mutableSetOf<String>()

  // Simplified set used to confirm required parameters, we'll remove elements from this set as they appear on the original parameters
  // collection, so if this Set is not empty at the end, there are missing parameters
  val requiredParamsCheckList = rule.requiredParameters.map { it.name }.toMutableSet()

  // Create a mapping of all supported parameters with their respective rules
  val namesToParamRule = mutableListOf<ParameterRule>().apply {
    addAll(rule.requiredParameters)
    addAll(rule.optionalParameters)
  }.associateBy { it.name }

  // A copy based on the original collection that removes any unsupported parameter, this may be modified to represent a complete and
  // correct param-value map
  val fixableParams =
    originalParams.filter { namesToParamRule.contains(it.first) }.associate { it }.toMutableMap()

  originalParams.forEach { (paramName, value) ->
    if (!namesToParamRule.contains(paramName)) {
      // Unsupported parameter for the current CheckRule
      issues.add(Unknown(paramName))
    }
    else {
      if (appliedParams.contains(paramName)) {
        // If we've already traversed this parameter, it's repeated
        repeated.add(paramName)
        return@forEach
      }
      else {
        appliedParams.add(paramName)
        requiredParamsCheckList.remove(paramName)
      }

      val paramRule = namesToParamRule[paramName]!!
      if (!paramRule.valueCheck(value)) {
        // If the value is not valid, update the fixable params mapping with a correct value, and register the issue
        fixableParams[paramName] = paramRule.defaultValue
        issues.add(BadType(paramName, paramRule.expectedType))
      }
    }
  }
  repeated.forEach { issues.add(Repeated(it)) }

  requiredParamsCheckList.forEach { missingParamName ->
    // Add missing parameters with their default value, and register the issue
    fixableParams[missingParamName] = namesToParamRule[missingParamName]!!.defaultValue
    issues.add(Missing(missingParamName))
  }
  return CheckResult(issues, fixableParams.buildDeviceSpecString())
}

/**
 * Contains any Issues found by the check, if the issues can be resolved, [proposedFix] will be a not-null string that can be applied to
 * resolve the issues.
 *
 * So when [issues] is empty, the check completed successfully and [proposedFix] should be null.
 */
internal data class CheckResult(
  val issues: List<IssueReason>,
  val proposedFix: String?
) {
  val hasIssues: Boolean = issues.isNotEmpty()
}

/**
 * Returns the map in a format that matches a string that describes a device based on screen specifications differentiated by the 'spec:'
 * prefix, where every name-value pair is comma (,) separated and are expressed as a value assignment (<name>=<value>).
 *
 * i.e: "spec:<name0>=<value0>,<name1>=<value1>,...,<nameN>=<valueN>"
 */
private fun Map<String, String>.buildDeviceSpecString(): String {
  val result = StringBuffer()
  this.map { "${it.key}${Preview.DeviceSpec.OPERATOR}${it.value}" }.joinTo(
    buffer = result,
    prefix = Preview.DeviceSpec.PREFIX,
    separator = Preview.DeviceSpec.SEPARATOR.toString()
  )
  return result.toString()
}