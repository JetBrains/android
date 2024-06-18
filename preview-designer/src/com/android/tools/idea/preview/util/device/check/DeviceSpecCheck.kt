/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.util.device.check

import com.android.annotations.TestOnly
import com.android.sdklib.devices.Device
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.util.AvailableDevicesKey
import com.android.tools.idea.preview.util.getSdkDevices
import com.android.tools.preview.config.DEFAULT_DEVICE_ID
import com.android.tools.preview.config.DEFAULT_DEVICE_ID_WITH_PREFIX
import com.android.tools.preview.config.DEVICE_BY_ID_PREFIX
import com.android.tools.preview.config.DEVICE_BY_NAME_PREFIX
import com.android.tools.preview.config.DEVICE_BY_SPEC_PREFIX
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.Preview.DeviceSpec
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.sourcePsiElement

private val PreviewCheckResultKey =
  Key.create<Pair<String, CheckResult>>(DeviceSpecCheck::class.java.canonicalName)

/**
 * Singleton that provides methods to verify the correctness of the `device` parameter within an
 * annotation. This class assumes that the validity of the annotation is verified upstream.
 */
object DeviceSpecCheck {
  /** [CheckResult] for a check that did not identify any issues. */
  private val Passed: CheckResult = CheckResult(emptyList(), null)

  /**
   * Creates a [CheckResult] for failures that are not dependent on user input. Ie: System failures
   */
  private fun failedCheck(description: String) = CheckResult(listOf(Failure(description)), null)

  /**
   * Checks if a [UAnnotation] element that has a `device` attribute has any issues with that
   * attribute.
   *
   * @see checkDeviceSpec
   */
  fun UAnnotation.hasIssues(): Boolean {
    return checkDeviceSpec(this).hasIssues
  }

  /**
   * Takes a [UAnnotation] element that has a `device` attribute and returns a [ProblemDescriptor]
   * if any issue is found.
   *
   * @see checkDeviceSpec
   */
  fun checkAnnotation(
    annotation: UAnnotation,
    inspectionManager: InspectionManager,
    isOnTheFly: Boolean,
  ): ProblemDescriptor? {
    val result = checkDeviceSpec(annotation)

    if (!result.hasIssues) return null

    val messageBuffer = StringBuffer()
    val groupedIssues = result.issues.groupBy { it::class.java.kotlin }

    groupedIssues.entries.forEach { entry ->
      val messagePrefix =
        when (entry.key) {
          BadType::class -> message("preview.device.spec.lint.error.type")
          Missing::class -> message("preview.device.spec.lint.error.missing")
          Unknown::class -> message("preview.device.spec.lint.error.unknown")
          Repeated::class -> message("preview.device.spec.lint.error.repeated")
          Failure::class -> {
            val failureMessage =
              entry.value.filterIsInstance<Failure>().joinToString("\n") { it.failureMessage }
            Logger.getInstance(DeviceSpecCheck::class.java)
              .warn("Failed when checking annotation: $failureMessage")
            return@forEach
          }
          else -> return@forEach
        } + ": "
      addSimpleMessage(messagePrefix, entry.value, messageBuffer)

      if (entry.key == BadType::class) {
        addMessageForBadTypeParameters(entry.value.map { it as BadType }, messageBuffer)
      }
      messageBuffer.append('\n')
    }

    if (messageBuffer.isNotEmpty() && result.proposedFix != null) {
      val message = messageBuffer.toString().trim()

      val deviceValueExpression =
        annotation.findDeclaredAttributeValue("device")?.sourcePsiElement ?: return null
      val deviceValueElement =
        if (deviceValueExpression is KtOperationExpression) {
          // If the expression is a string concatenation, highlight the entire expression
          deviceValueExpression
        } else {
          // Otherwise, highlight the string literal itself.
          PsiTreeUtil.findChildOfType(
            deviceValueExpression,
            KtLiteralStringTemplateEntry::class.java,
            false,
          ) ?: return null
        }

      return inspectionManager.createProblemDescriptor(
        deviceValueElement,
        message,
        DeviceParameterQuickFix(deviceValueElement, result.proposedFix),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        isOnTheFly,
      )
    }
    return null
  }

  /**
   * Takes a [UAnnotation] element that has a `device` parameter.
   *
   * Returns a [CheckResult] that contains a list of issues found in the parameter, so an empty list
   * means that the annotation is syntactically correct. The [CheckResult] is cached into the
   * PsiElement given, and it's refreshed based on the contents of the annotation.
   */
  @TestOnly
  internal fun checkDeviceSpec(annotation: UAnnotation): CheckResult {
    if (!ApplicationManager.getApplication().isReadAccessAllowed)
      return failedCheck("No read access")

    val deviceValueExpression =
      annotation.findDeclaredAttributeValue(PARAMETER_DEVICE) ?: return Passed
    val deviceValue =
      deviceValueExpression.evaluateString()
        ?: return failedCheck("Can't get string literal of 'device' value")

    val sourcePsi = annotation.sourcePsi ?: return failedCheck("Can't get sourcePsi")
    synchronized(
      DeviceSpecCheck
    ) { // Protect reading/writing the cached result from asynchronous calls
      sourcePsi.getUserData(PreviewCheckResultKey)?.also { existingResult ->
        if (existingResult.first == deviceValue) {
          Logger.getInstance(DeviceSpecCheck::class.java).debug("Found existing CheckResult")
          return existingResult.second
        } else {
          sourcePsi.putUserData(PreviewCheckResultKey, null)
        }
      }
    }

    return synchronized(DeviceSpecCheck) {
      checkDeviceParameter(deviceValue, sourcePsi.module).also { checkResult ->
        sourcePsi.putUserData(PreviewCheckResultKey, Pair(deviceValue, checkResult))
      }
    }
  }

  /**
   * Checks the value of the `device` parameter of the Preview annotation.
   *
   * Looks for issues in the syntax that'll result in failure or unexpected behavior when defining
   * the Device for the Preview panel.
   */
  private fun checkDeviceParameter(deviceParameterValue: String, module: Module?): CheckResult =
    when {
      // Check the device_id in "id:<device_id>
      deviceParameterValue.startsWith(DEVICE_BY_ID_PREFIX) -> {
        if (module == null) {
          failedCheck("Couldn't obtain Module")
        } else {
          checkDeviceId(deviceParameterValue.substringAfter(DEVICE_BY_ID_PREFIX), module)
        }
      }
      // Check the device_id in "name:<device_name>
      deviceParameterValue.startsWith(DEVICE_BY_NAME_PREFIX) -> {
        if (module == null) {
          failedCheck("Couldn't obtain Module")
        } else {
          checkDeviceName(deviceParameterValue.substringAfter(DEVICE_BY_NAME_PREFIX), module)
        }
      }
      // Check the DeviceSpec parameters in "spec:..."
      deviceParameterValue.startsWith(DEVICE_BY_SPEC_PREFIX) -> {
        val deviceSpecParams =
          toParameterList(deviceParameterValue.substringAfter(DEVICE_BY_SPEC_PREFIX))

        val rule =
          when {
            deviceParameterValue.contains(DeviceSpec.PARAMETER_SHAPE) -> {
              // The Legacy format is the only one with a `shape` parameter
              DeviceSpecRule.Legacy
            }
            deviceParameterValue.contains(DeviceSpec.PARAMETER_PARENT + DeviceSpec.OPERATOR) -> {
              // The `parent` parameter requires a specific rule
              DeviceSpecRule.LanguageWithParentId
            }
            // All other cases should be covered by the base DeviceSpec Language rule
            else -> DeviceSpecRule.LanguageBased
          }
        checkDeviceSpecParams(deviceSpecParams, rule, module)
      }
      // Blank value is ok, uses default Device
      deviceParameterValue.isBlank() -> Passed
      else ->
        // Unsupported input, default to an error proposing the Default device
        // TODO(b/236383162): Improve the messaging for general issues in the Device value, should
        // not use the base message for an Unknown
        //  parameter
        CheckResult(
          issues = listOf(Unknown(message("preview.device.spec.lint.error.unsupported"))),
          proposedFix = DEFAULT_DEVICE_ID_WITH_PREFIX,
        )
    }

  /**
   * Checks the given collection of param-value pairs for correctness, it should match the pattern
   * used to describe a custom device specification:
   * "spec:shape=<enum>,width=<integer>,height=<integer>,unit=<enum>,dpi=<integer>". With no
   * particular order enforced.
   *
   * The issues that the returned [CheckResult] may report:
   * - [Repeated]: There should only be one of each parameter
   * - [BadType]: A parameter has a value that does not correspond to the expected type (a float
   *   instead of an integer for example)
   * - [Unknown]: Unknown/unsupported parameter found
   * - [Missing]: An expected parameter is missing.
   *
   * Every issue will have the related parameter name in the [IssueReason.parameterName] message.
   *
   * [CheckResult.proposedFix] is a proposed string to fix the issues found, based on the original
   * input.
   */
  private fun checkDeviceSpecParams(
    originalParams: Collection<Pair<String, String>>,
    rule: CheckRule,
    module: Module?,
  ): CheckResult {
    val issues = mutableListOf<IssueReason>()

    // Set of parameters confirmed in the original parameter list
    val appliedParams = mutableMapOf<String, String>()

    // Set of parameters present more than once
    val repeated = mutableSetOf<String>()

    // Simplified set used to confirm required parameters, we'll remove elements from this set as
    // they appear on the original parameters
    // collection, so if this Set is not empty at the end, there are missing parameters
    val requiredParamsCheckList = rule.requiredParameters.map { it.name }.toMutableSet()

    // Create a mapping of all supported parameters with their respective rules
    val namesToParamRule =
      mutableListOf<ParameterRule>()
        .apply {
          addAll(rule.requiredParameters)
          addAll(rule.optionalParameters)
        }
        .associateBy { it.name }

    // A copy based on the original collection that removes any unsupported parameter, this may be
    // modified to represent a complete and
    // correct param-value map
    val fixableParams: MutableMap<String, String> =
      originalParams.filter { namesToParamRule.contains(it.first) }.associate { it }.toMutableMap()

    // Used by DeviceSpecLanguage ParameterRules, should persist through the entire check process
    val deviceSpecState = DeviceSpecCheckState()

    // DataProvider for context-dependent checks
    val dataProvider = DataProvider {
      when (it) {
        AvailableDevicesKey.name -> module?.let { getSdkDevices(module) } ?: emptyList<Device>()
        DeviceSpecCheckStateKey.name -> deviceSpecState
        else -> null
      }
    }

    // List to collect all parameters with syntax issues
    val paramsToFix: MutableList<String> = ArrayList(fixableParams.size)

    originalParams.forEach { (paramName, value) ->
      if (!namesToParamRule.contains(paramName)) {
        // Unsupported parameter for the current CheckRule
        issues.add(Unknown(paramName))
      } else {
        if (appliedParams.contains(paramName)) {
          // If we've already traversed this parameter, it's repeated
          repeated.add(paramName)
          return@forEach
        }

        val paramRule = namesToParamRule[paramName]!!
        if (!paramRule.checkValue(value, dataProvider)) {
          // Register the issue and prepare to fix the parameter's value
          paramsToFix.add(paramName)
          issues.add(BadType(paramName, paramRule.expectedType))
        }

        appliedParams[paramName] = value
        requiredParamsCheckList.remove(paramName)
      }
    }
    repeated.forEach { issues.add(Repeated(it)) }

    requiredParamsCheckList.forEach { missingParamName ->
      // Add missing parameters with their default value, and register the issue
      val missingParamRule = namesToParamRule[missingParamName]!!

      val candidate = missingParamRule.defaultValue
      if (!missingParamRule.checkValue(candidate, dataProvider)) {
        // Check the validity of the default value, since a parameterRule may have a
        // context-dependent check
        paramsToFix.add(missingParamName)
      }
      fixableParams[missingParamName] = candidate
      issues.add(Missing(missingParamName))
    }

    paramsToFix.forEach {
      // Lastly, obtain the fixes, it's expected that the inspection should not fail again if the
      // proposed fix is applied
      fixableParams[it] =
        namesToParamRule[it]!!.getFixedOrDefaultValue(fixableParams[it]!!, dataProvider)
    }
    return CheckResult(issues = issues, proposedFix = fixableParams.buildDeviceSpecString())
  }

  /**
   * Finds the default device in the list and, if found, returns a [CheckResult], flagging the given
   * [unknownParameterValue] as the error and the default device as proposed fix.
   */
  private fun List<Device>.findDefaultDeviceAndReturnFix(
    unknownParameterValue: String
  ): CheckResult =
    if (any { it.id == DEFAULT_DEVICE_ID }) {
      // TODO(b/236383162): Improve the messaging for issues in the DeviceId
      CheckResult(
        issues = listOf(Unknown(unknownParameterValue)),
        proposedFix = DEFAULT_DEVICE_ID_WITH_PREFIX,
      )
    } else {
      // Expected default device not in Sdk
      failedCheck("Default Device: $DEFAULT_DEVICE_ID not found")
    }

  /** Check that the given [deviceId] is the ID of an actual device in the Sdk. */
  private fun checkDeviceId(deviceId: String, module: Module): CheckResult {
    val sdkDevices = getSdkDevices(module)
    val isValid = sdkDevices.any { it.id == deviceId }
    return if (isValid) {
      Passed
    } else sdkDevices.findDefaultDeviceAndReturnFix(deviceId)
  }

  /** Check that the given [deviceName] is the name of an actual device in the Sdk. */
  private fun checkDeviceName(deviceName: String, module: Module): CheckResult {
    val sdkDevices = getSdkDevices(module)
    val isValid = sdkDevices.any { it.name == deviceName }
    return if (isValid) {
      Passed
    } else sdkDevices.findDefaultDeviceAndReturnFix(deviceName)
  }
}

/**
 * Regex to match a string with the [DeviceSpec.OPERATOR] between two other non-empty strings. E.g:
 * name=value, n=v
 */
private val paramValueRegex: Regex by lazy { Regex("(.+)${DeviceSpec.OPERATOR}(.+)") }

/**
 * Converts the original [configString] using the basic supported format:
 * `[parameter0,value0],[parameter1,value1],[...]`
 */
private fun toParameterList(configString: String): Collection<Pair<String, String>> =
  configString.split(DeviceSpec.SEPARATOR).map { paramString ->
    val capturedValues = paramValueRegex.matchEntire(paramString)?.groupValues

    if (capturedValues != null && capturedValues.size == 3) {
      // 0-index corresponds to the original string, the others are the capture groups (1 ->
      // parameter name, 2 -> parameter value)
      Pair(capturedValues[1].trim(), capturedValues[2].trim())
    } else {
      // Preserve invalid sections, all information is needed for a correct check
      Pair(paramString, "")
    }
  }

/**
 * Returns the map in a format that matches a string that describes a device based on screen
 * specifications differentiated by the 'spec:' prefix, where every name-value pair is comma (,)
 * separated and are expressed as a value assignment (<name>=<value>).
 *
 * i.e: "spec:<name0>=<value0>,<name1>=<value1>,...,<nameN>=<valueN>"
 */
private fun Map<String, String>.buildDeviceSpecString(): String {
  val result = StringBuffer()
  this.map { "${it.key}${DeviceSpec.OPERATOR}${it.value}" }
    .joinTo(
      buffer = result,
      prefix = DeviceSpec.PREFIX,
      separator = DeviceSpec.SEPARATOR.toString(),
    )
  return result.toString()
}

/**
 * QuickFix implementation for the `device` parameter of the Preview Annotation.
 *
 * Whenever there's an incorrect value for the `device` parameter, suggests replacing the expression
 * of the original value to [resultingString]. Which should be a correct value for the parameter.
 */
private class DeviceParameterQuickFix(
  deviceValueElement: PsiElement,
  private val resultingString: String,
) : LocalQuickFixOnPsiElement(deviceValueElement) {
  override fun getText(): String = message("preview.device.spec.fix.replace", resultingString)

  override fun getFamilyName(): String = message("inspection.quick.fix.family")

  override fun invoke(
    project: Project,
    file: PsiFile,
    startElement: PsiElement,
    endElement: PsiElement,
  ) {
    try {
      // Find the element that corresponds to the Argument value, this is needed in case the
      // original expression is composed by more than
      // one element. E.g: device = "spec:width=100dp," + "height=" + heightDp
      var replaceableElement = startElement
      while (replaceableElement.parent !is KtValueArgument) {
        replaceableElement = replaceableElement.parent
      }
      replaceableElement.replace(
        KtPsiFactory(project = project, markGenerated = true)
          .createExpression("\"$resultingString\"")
      )
    } catch (e: IncorrectOperationException) {
      Logger.getInstance(DeviceParameterQuickFix::class.java)
        .error("Unable to apply fix to @Preview 'device' parameter", e)
    }
  }
}

private fun addMessageForBadTypeParameters(issues: List<BadType>, messageBuffer: StringBuffer) {
  val parametersByType =
    issues.groupBy { it.expected }.mapValues { it.value.map(BadType::parameterName) }
  val messagePrefix = message("preview.device.spec.lint.error.type.prefix")

  parametersByType.entries.forEach { entry ->
    @Suppress(
      "MoveVariableDeclarationIntoWhen"
    ) // The suggested pattern is harder to read/understand
    val expectedType = entry.key
    when (expectedType) {
      is OpenEndedValueType -> {
        val messagePostfix =
          message("preview.device.spec.lint.error.type.open", expectedType.valueTypeName)
        entry.value.joinTo(
          buffer = messageBuffer,
          separator = ", ",
          prefix = "$messagePrefix: ",
          postfix = " $messagePostfix\n",
        )
      }
      is MultipleChoiceValueType -> {
        val valuesExamples = expectedType.acceptableValues.joinToString(", ")
        val messagePostfix = message("preview.device.spec.lint.error.type.options", valuesExamples)
        entry.value.joinTo(
          buffer = messageBuffer,
          separator = ", ",
          prefix = "$messagePrefix: ",
          postfix = " $messagePostfix\n",
        )
      }
    }
  }
}

private fun addSimpleMessage(
  prefix: String,
  issues: List<IssueReason>,
  messageBuffer: StringBuffer,
) {
  val allParameters = issues.map(IssueReason::parameterName)
  if (allParameters.isEmpty()) return

  allParameters.joinTo(buffer = messageBuffer, separator = ", ", prefix = prefix, postfix = ".\n\n")
}
