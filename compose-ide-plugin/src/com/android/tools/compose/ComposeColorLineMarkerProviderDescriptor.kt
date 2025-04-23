/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.compose

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.MaterialColorPaletteProvider
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.elementType
import com.intellij.ui.colorpicker.ColorPickerBuilder
import com.intellij.ui.colorpicker.MaterialGraphicalColorPipetteProvider
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.ColorIcon
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.inspections.AbstractRangeInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.toUElement
import java.awt.Color
import java.awt.MouseInfo
import java.awt.event.MouseEvent
import java.util.Locale

private const val ICON_SIZE = 8

class ComposeColorLineMarkerProviderDescriptor : LineMarkerProviderDescriptor() {
  override fun getName() = ComposeBundle.message("compose.color.picker.name")

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element.elementType != KtTokens.IDENTIFIER || !isComposeEnabled(element)) return null

    val uElement =
      (element.parent.parent as? KtCallExpression)?.toUElement(UCallExpression::class.java)
        ?: return null
    if (!uElement.isColorCall()) return null

    val color = getColor(uElement) ?: return null
    val iconRenderer = ColorIconRenderer(uElement, color)
    return LineMarkerInfo(
      element,
      element.textRange,
      iconRenderer.icon,
      { ComposeBundle.message("compose.color.picker.tooltip") },
      iconRenderer,
      GutterIconRenderer.Alignment.RIGHT,
      { ComposeBundle.message("compose.color.picker.tooltip") },
    )
  }

  private fun UCallExpression.isColorCall() =
    kind == UastCallKind.METHOD_CALL &&
      returnType == PsiTypes.longType() &&
      COLOR_METHOD == methodName &&
      // Resolve the MethodCall expression after the faster checks
      resolve()?.containingClass?.qualifiedName == COMPOSE_COLOR_CLASS

  private fun getColor(uElement: UCallExpression): Color? {
    val arguments = (uElement.sourcePsi as? KtCallExpression)?.valueArguments ?: return null
    return when (getConstructorType(uElement.valueArguments)) {
      ComposeColorConstructor.INT -> getColorInt(arguments)
      ComposeColorConstructor.LONG -> getColorLong(arguments)
      ComposeColorConstructor.INT_X3 -> getColorIntX3(arguments)
      ComposeColorConstructor.INT_X4 -> getColorIntX4(arguments)
      ComposeColorConstructor.FLOAT_X3 -> getColorFloatX3(arguments)
      ComposeColorConstructor.FLOAT_X4 -> getColorFloatX4(arguments)
      // TODO: Provide the color preview for ComposeColorConstructor.FLOAT_X4_COLORSPACE
      // constructor.
      ComposeColorConstructor.FLOAT_X4_COLORSPACE -> null
      else -> null
    }
  }
}

/**
 * Simplified version of [AndroidAnnotatorUtil.ColorRenderer] that does not work on
 * [ResourceReference] but still displays the same color picker.
 *
 * TODO(lukeegan): Implement for ComposeColorConstructor.FLOAT_X4_COLORSPACE Color parameter
 */
data class ColorIconRenderer(val element: UCallExpression, val color: Color) :
  GutterIconNavigationHandler<PsiElement> {

  val icon = ColorIcon(ICON_SIZE, color)

  override fun navigate(e: MouseEvent?, elt: PsiElement?) {
    val project = element.sourcePsi?.project ?: return
    val setColorTask: (Color) -> Unit = getSetColorTask() ?: return

    val pickerListener = ColorListener { color, _ ->
      ApplicationManager.getApplication()
        .invokeLater(
          {
            WriteCommandAction.runWriteCommandAction(
              project,
              "Change Color",
              null,
              { setColorTask.invoke(color) },
            )
          },
          project.disposed,
        )
    }

    val dialog = LightCalloutPopup()
    val colorPicker =
      ColorPickerBuilder(showAlpha = true, showAlphaAsPercent = false)
        .setOriginalColor(color)
        .addSaturationBrightnessComponent()
        .addColorAdjustPanel(MaterialGraphicalColorPipetteProvider())
        .addColorValuePanel()
        .withFocus()
        .addSeparator()
        .addCustomComponent(MaterialColorPaletteProvider)
        .addColorListener(pickerListener)
        .focusWhenDisplay(true)
        .setFocusCycleRoot(true)
        .build()
    dialog.show(colorPicker.content, null, MouseInfo.getPointerInfo().location)
  }

  @VisibleForTesting
  fun getSetColorTask(): ((Color) -> Unit)? {
    val ktCallExpression = element.sourcePsi as? KtCallExpression ?: return null
    val constructorType = getConstructorType(element.valueArguments) ?: return null
    // No matter what the original format is, we make the format become one of:
    // - (0xAARRGGBB)
    // - (color = 0xAARRGGBB)
    // or
    // - ([0..255], [0..255], [0..255], [0.255])
    // - (red = [0..255], green = [0..255], blue = [0..255], alpha = [0.255])
    // or
    // - ([0x00..0xFF], [0x00..0xFF], [0x00..0xFF], [0x00..0xFF])
    // - (red = [0x00..0xFF], green =[0x00..0xFF], blue = [0x00..0xFF], alpha = [0x00..0xFF])
    // or
    // - ([0.0f..1.0f], [0.0f..1.0f], [0.0f..1.0f], [0.0f..1.0f])
    // - (red = [0.0f..1.0f], green = [0.0f..1.0f], blue = [0.0f..1.0f], alpha = [0.0f..1.0f])
    // , depends on the original value type and numeral system.
    return when (constructorType) {
      ComposeColorConstructor.INT,
      ComposeColorConstructor.LONG -> { color: Color ->
          val valueArgumentList = ktCallExpression.valueArgumentList
          if (valueArgumentList != null) {
            val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
            val hexString = "0x${String.format("%08X", color.rgb)}"
            val argumentText = if (needsArgumentName) "(color = $hexString)" else "($hexString)"
            valueArgumentList.replace(
              KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText)
            )
          }
        }
      ComposeColorConstructor.INT_X3,
      ComposeColorConstructor.INT_X4 -> { color: Color ->
          val valueArgumentList = ktCallExpression.valueArgumentList
          if (valueArgumentList != null) {
            val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
            val hasHexFormat =
              valueArgumentList.arguments.any {
                it.getArgumentExpression()?.text?.startsWith("0x") ?: false
              }
            val red = if (hasHexFormat) color.red.toHexString() else color.red.toString()
            val green = if (hasHexFormat) color.green.toHexString() else color.green.toString()
            val blue = if (hasHexFormat) color.blue.toHexString() else color.blue.toString()
            val alpha = if (hasHexFormat) color.alpha.toHexString() else color.alpha.toString()

            val argumentText =
              if (needsArgumentName) "(red = $red, green = $green, blue = $blue, alpha = $alpha)"
              else "($red, $green, $blue, $alpha)"
            valueArgumentList.replace(
              KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText)
            )
          }
        }
      ComposeColorConstructor.FLOAT_X3,
      ComposeColorConstructor.FLOAT_X4 -> { color: Color ->
          val valueArgumentList = ktCallExpression.valueArgumentList
          if (valueArgumentList != null) {
            val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
            val red = (color.red / 255f).toRoundString()
            val green = (color.green / 255f).toRoundString()
            val blue = (color.blue / 255f).toRoundString()
            val alpha = (color.alpha / 255f).toRoundString()

            val argumentText =
              if (needsArgumentName)
                "(red = ${red}f, green = ${green}f, blue = ${blue}f, alpha = ${alpha}f)"
              else "(${red}f, ${green}f, ${blue}f, ${alpha}f)"
            valueArgumentList.replace(
              KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText)
            )
          }
        }
      ComposeColorConstructor.FLOAT_X4_COLORSPACE ->
        null // TODO: support ComposeColorConstructor.FLOAT_X4_COLORSPACE in the future.
    }
  }
}

private const val COLOR_METHOD = "Color"
private const val COMPOSE_COLOR_CLASS = "androidx.compose.ui.graphics.ColorKt"

private const val ARG_NAME_RED = "red"
private const val ARG_NAME_GREEN = "green"
private const val ARG_NAME_BLUE = "blue"
private const val ARG_NAME_ALPHA = "alpha"

private val ARGS_RGB = listOf(ARG_NAME_RED, ARG_NAME_GREEN, ARG_NAME_BLUE)
private val ARGS_RGBA = listOf(ARG_NAME_RED, ARG_NAME_GREEN, ARG_NAME_BLUE, ARG_NAME_ALPHA)

enum class ComposeColorConstructor {
  INT,
  LONG,
  INT_X3,
  INT_X4,
  FLOAT_X3,
  FLOAT_X4,
  FLOAT_X4_COLORSPACE,
}

private fun getColorInt(arguments: List<KtValueArgument>): Color? {
  val colorValue =
    arguments.first().getArgumentExpression()?.evaluateToConstantOrNull<Int>() ?: return null
  return Color(colorValue, true)
}

private fun getColorLong(arguments: List<KtValueArgument>): Color? {
  val colorValue =
    arguments.first().getArgumentExpression()?.evaluateToConstantOrNull<Long>() ?: return null
  return Color(colorValue.toInt(), true)
}

private fun getColorIntX3(arguments: List<KtValueArgument>): Color? {
  val rgbValues = getNamedValues<Int>(ARGS_RGB, arguments) ?: return null
  return intColorMapToColor(rgbValues)
}

private fun getColorIntX4(arguments: List<KtValueArgument>): Color? {
  val rgbaValues = getNamedValues<Int>(ARGS_RGBA, arguments) ?: return null
  return intColorMapToColor(rgbaValues)
}

private fun getColorFloatX3(arguments: List<KtValueArgument>): Color? {
  val rgbValues = getNamedValues<Float>(ARGS_RGB, arguments) ?: return null
  return floatColorMapToColor(rgbValues)
}

private fun getColorFloatX4(arguments: List<KtValueArgument>): Color? {
  val rgbaValues = getNamedValues<Float>(ARGS_RGBA, arguments) ?: return null
  return floatColorMapToColor(rgbaValues)
}

/**
 * This function return the name-value pair for the request arguments names by extracting the given
 * ktValueArguments.
 */
private inline fun <reified T> getNamedValues(
  requestArgumentNames: List<String>,
  ktValueArgument: List<KtValueArgument>,
): Map<String, T>? {
  val namedValues = mutableMapOf<String, T>()

  val unnamedValue = mutableListOf<T>()
  for (argument in ktValueArgument) {
    val (name, value) = getArgumentNameValuePair<T>(argument) ?: return null
    if (name != null) {
      namedValues[name] = value
    } else {
      unnamedValue.add(value)
    }
  }

  val unnamedArgument = requestArgumentNames.filterNot { it in namedValues.keys }.toList()
  if (unnamedArgument.size != unnamedValue.size) {
    // The number of argument values doesn't match the given KtValueArgument.
    return null
  }

  for (index in unnamedArgument.indices) {
    // Fill the unnamed argument value from KtValueArgument.
    namedValues[unnamedArgument[index]] = unnamedValue[index]
  }
  if (namedValues.keys != requestArgumentNames.toSet()) {
    // Has the redundant or missed argument(s).
    return null
  }
  return namedValues
}

private inline fun <reified T> getArgumentNameValuePair(
  valueArgument: KtValueArgument
): Pair<String?, T>? {
  val name = valueArgument.getArgumentName()?.asName?.asString()
  val value = valueArgument.getArgumentExpression()?.evaluateToConstantOrNull<T>() ?: return null
  return name to value
}

private inline fun <reified T> KtExpression.evaluateToConstantOrNull(): T? {
  return if (KotlinPluginModeProvider.isK2Mode()) {
    analyze(this) {
      evaluate()?.value as? T ?: return null
    }
  } else {
    constantValueOrNull()?.value as? T ?: return null
  }
}

private fun Int.toHexString(): String =
  "0x${(Integer.toHexString(this)).uppercase(Locale.getDefault())}"

// Note: toFloat() then toString() is for removing the tail zero(s).
private fun Float.toRoundString(decimals: Int = 3): String =
  "%.${decimals}f".format(this).toFloat().toString()

private typealias IntColorMap = Map<String, Int>

private fun intColorMapToColor(intColorMap: IntColorMap): Color? {
  val red = intColorMap[ARG_NAME_RED] ?: return null
  val green = intColorMap[ARG_NAME_GREEN] ?: return null
  val blue = intColorMap[ARG_NAME_BLUE] ?: return null
  val alpha = intColorMap[ARG_NAME_ALPHA]
  return if (alpha == null) Color(red, green, blue) else Color(red, green, blue, alpha)
}

private typealias FloatColorMap = Map<String, Float>

private fun floatColorMapToColor(floatColorMap: FloatColorMap): Color? {
  val red = floatColorMap[ARG_NAME_RED] ?: return null
  val green = floatColorMap[ARG_NAME_GREEN] ?: return null
  val blue = floatColorMap[ARG_NAME_BLUE] ?: return null
  val alpha = floatColorMap[ARG_NAME_ALPHA]
  return if (alpha == null) Color(red, green, blue) else Color(red, green, blue, alpha)
}

private fun getConstructorType(arguments: List<UExpression>): ComposeColorConstructor? {
  val paramType = arguments.firstOrNull()?.getExpressionType() ?: return null
  return when (arguments.size) {
    1 ->
      if (PsiTypes.intType() == paramType) ComposeColorConstructor.INT
      else ComposeColorConstructor.LONG
    3 ->
      if (PsiTypes.intType() == paramType) ComposeColorConstructor.INT_X3
      else ComposeColorConstructor.FLOAT_X3
    4 ->
      if (PsiTypes.intType() == paramType) ComposeColorConstructor.INT_X4
      else ComposeColorConstructor.FLOAT_X4
    5 -> ComposeColorConstructor.FLOAT_X4_COLORSPACE
    else -> null
  }
}
