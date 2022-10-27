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
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerBuilder
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerListener
import com.android.tools.idea.ui.resourcechooser.colorpicker2.internal.MaterialColorPaletteProvider
import com.android.tools.idea.ui.resourcechooser.colorpicker2.internal.MaterialGraphicalColorPipetteProvider
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.ui.ColorIcon
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.inspections.AbstractRangeInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.toUElement
import java.awt.Color
import java.awt.MouseInfo
import java.util.Locale
import javax.swing.Icon

/**
 * [Annotator] to place color gutter icons for compose color declarations.
 * It does this by looking at the parameters of the Color() method and so does not work is the parameters are references.
 * It also does not work predefined colors. eg. Color.White
 */
class ComposeColorAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when {
      element.getModuleSystem()?.usesCompose != true -> return
      element is KtCallElement -> {
        val uElement = element.toUElement(UCallExpression::class.java) ?: return
        val returnType = uElement.returnType ?: return
        if (uElement.kind != UastCallKind.METHOD_CALL || returnType != PsiType.LONG || COLOR_METHOD != uElement.methodName) {
          return
        }

        // Resolve the MethodCall expression after the faster checks
        val fqName = uElement.resolve()?.containingClass?.qualifiedName ?: return
        if (fqName == COMPOSE_COLOR_CLASS) {
          val color = getColor(uElement) ?: return
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .gutterIconRenderer(ColorIconRenderer(uElement, color))
            .create()
        }
      }
    }
  }

  private fun getColor(uElement: UElement): Color? {
    val callElement = uElement as? UCallExpression ?: return null
    val arguments = (uElement.sourcePsi as? KtCallExpression)?.valueArguments ?: return null
    return when (getConstructorType(callElement.valueArguments)) {
      ComposeColorConstructor.INT -> getColorInt(arguments)
      ComposeColorConstructor.LONG -> getColorLong(arguments)
      ComposeColorConstructor.INT_X3 -> getColorIntX3(arguments)
      ComposeColorConstructor.INT_X4 -> getColorIntX4(arguments)
      ComposeColorConstructor.FLOAT_X3 -> getColorFloatX3(arguments)
      ComposeColorConstructor.FLOAT_X4 -> getColorFloatX4(arguments)
      // TODO: Provide the color preview for ComposeColorConstructor.FLOAT_X4_COLORSPACE constructor.
      ComposeColorConstructor.FLOAT_X4_COLORSPACE -> null
      else -> null
    }
  }
}

/**
 * Simplified version of [AndroidAnnotatorUtil.ColorRenderer] that does not work on [ResourceReference] but still displays the same color
 * picker.
 * TODO(lukeegan): Implement for ComposeColorConstructor.FLOAT_X4_COLORSPACE Color parameter
 */
data class ColorIconRenderer(val element: UCallExpression, val color: Color) : GutterIconRenderer() {
  private val ICON_SIZE = 8

  override fun getIcon(): Icon {
    return ColorIcon(ICON_SIZE, color)
  }

  override fun getClickAction(): AnAction? {
    val project = element.sourcePsi?.project ?: return null
    val setColorTask: (Color) -> Unit = getSetColorTask() ?: return null

    val pickerListener = ColorPickerListener { color, _ ->
      ApplicationManager.getApplication().invokeLater(Runnable {
        WriteCommandAction.runWriteCommandAction(project, "Change Color", null, Runnable { setColorTask.invoke(color) })
      }, project.disposed)
    }
    return object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
          val dialog = LightCalloutPopup()
          val colorPicker = ColorPickerBuilder()
            .setOriginalColor(color)
            .addSaturationBrightnessComponent()
            .addColorAdjustPanel(MaterialGraphicalColorPipetteProvider())
            .addColorValuePanel().withFocus()
            .addSeparator()
            .addCustomComponent(MaterialColorPaletteProvider)
            .addColorPickerListener(pickerListener)
            .focusWhenDisplay(true)
            .setFocusCycleRoot(true)
            .build()
          dialog.show(colorPicker, null, MouseInfo.getPointerInfo().location)
        }
      }
    }
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
    return when(constructorType) {
      ComposeColorConstructor.INT,
      ComposeColorConstructor.LONG -> { color: Color ->
        val valueArgumentList = ktCallExpression.valueArgumentList
        if (valueArgumentList != null) {
          val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
          val hexString = color.rgb.toHexString()
          val argumentText = if (needsArgumentName) "(color = $hexString)" else "($hexString)"
          valueArgumentList.replace(KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText))
        }
      }
      ComposeColorConstructor.INT_X3,
      ComposeColorConstructor.INT_X4 -> { color: Color ->
        val valueArgumentList = ktCallExpression.valueArgumentList
        if (valueArgumentList != null) {
          val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
          val hasHexFormat = valueArgumentList.arguments.any { it.getArgumentExpression()?.text?.startsWith("0x") ?: false }
          val red = if (hasHexFormat) color.red.toHexString() else color.red.toString()
          val green = if (hasHexFormat) color.green.toHexString() else color.green.toString()
          val blue = if (hasHexFormat) color.blue.toHexString() else color.blue.toString()
          val alpha = if (hasHexFormat) color.alpha.toHexString() else color.alpha.toString()

          val argumentText =
            if (needsArgumentName) "(red = $red, green = $green, blue = $blue, alpha = $alpha)" else "($red, $green, $blue, $alpha)"
          valueArgumentList.replace(KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText))
        }
      }
      ComposeColorConstructor.FLOAT_X3,
      ComposeColorConstructor.FLOAT_X4 -> { color: Color ->
        val valueArgumentList = ktCallExpression.valueArgumentList
        if (valueArgumentList != null) {
          val needsArgumentName = valueArgumentList.arguments.any { it.getArgumentName() != null }
          val red = (color.red / 255f).toRoundString(3)
          val green = (color.green / 255f).toRoundString(3)
          val blue = (color.blue / 255f).toRoundString(3)
          val alpha = (color.alpha / 255f).toRoundString(3)

          val argumentText =
            if (needsArgumentName) "(red = ${red}f, green = ${green}f, blue = ${blue}f, alpha = ${alpha}f)"
            else "(${red}f, ${green}f, ${blue}f, ${alpha}f)"
          valueArgumentList.replace(KtPsiFactory(ktCallExpression.project).createCallArguments(argumentText))
        }
      }
      ComposeColorConstructor.FLOAT_X4_COLORSPACE -> null // TODO: support ComposeColorConstructor.FLOAT_X4_COLORSPACE in the future.
    }
  }
}

private const val COLOR_METHOD = "Color"
private const val COMPOSE_COLOR_CLASS = "androidx.compose.ui.graphics.ColorKt"

private const val ARG_NAME_RED = "red"
private const val ARG_NAME_GREEN = "green"
private const val ARG_NAME_BLUE = "blue"
private const val ARG_NAME_ALPHA = "alpha"
private const val ARG_NAME_COLOR_SPACE = "colorSpace"

private val ARGS_RGB = listOf(ARG_NAME_RED, ARG_NAME_GREEN, ARG_NAME_BLUE)
private val ARGS_RGBA = listOf(ARG_NAME_RED, ARG_NAME_GREEN, ARG_NAME_BLUE, ARG_NAME_ALPHA)

enum class ComposeColorConstructor {
  INT, LONG, INT_X3, INT_X4, FLOAT_X3, FLOAT_X4, FLOAT_X4_COLORSPACE
}

private fun getColorInt(arguments: List<KtValueArgument>): Color? {
  val colorValue = arguments.first().getArgumentExpression()?.constantValueOrNull()?.value as? Int ?: return null
  return Color(colorValue, true)
}

private fun getColorLong(arguments: List<KtValueArgument>): Color? {
  val colorValue = arguments.first().getArgumentExpression()?.constantValueOrNull()?.value as? Long ?: return null
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

private fun getColorFloatX4ColorSpace(arguments: List<KtValueArgument>): Color? {
  // Filter the ColorSpace argument first.
  val argumentsWithoutColorSpace = arguments.filterNot { it.getArgumentName()?.asName?.asString() == ARG_NAME_COLOR_SPACE }.let {
    // If the color space is not a named argument, it must be at the end.
    if (it.size == arguments.size) arguments.subList(0, 4) else it
  }

  val rgbaValues = getNamedValues<Float>(ARGS_RGBA, argumentsWithoutColorSpace) ?: return null
  // TODO: adjust the color by the given ColorSpace.
  return floatColorMapToColor(rgbaValues)
}

/**
 * This function return the name-value pair for the request arguments names by extracting the given ktValueArguments.
 */
private inline fun <reified T> getNamedValues(requestArgumentNames: List<String>, ktValueArgument: List<KtValueArgument>): Map<String, T>? {
  val namedValues = mutableMapOf<String, T>()

  val unnamedValue = mutableListOf<T>()
  for (argument in ktValueArgument) {
    val (name, value) = getArgumentNameValuePair<T>(argument) ?: return null
    if (name != null) {
      namedValues[name] = value
    }
    else {
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

private inline fun <reified T> getArgumentNameValuePair(valueArgument: KtValueArgument): Pair<String?, T>? {
  val name = valueArgument.getArgumentName()?.asName?.asString()
  val value = valueArgument.getArgumentExpression()?.constantValueOrNull()?.value as? T ?: return null
  return name to value
}

private fun Int.toHexString(): String = "0x${(Integer.toHexString(this)).toUpperCase(Locale.getDefault())}"

// Note: toFloat() then toString() is for removing the tail zero(s).
private fun Float.toRoundString(decimals: Int = 3): String = "%.${decimals}f".format(this).toFloat().toString()

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
    1 -> if (PsiType.INT == paramType) ComposeColorConstructor.INT else ComposeColorConstructor.LONG
    3 -> if (PsiType.INT == paramType) ComposeColorConstructor.INT_X3 else ComposeColorConstructor.FLOAT_X3
    4 -> if (PsiType.INT == paramType) ComposeColorConstructor.INT_X4 else ComposeColorConstructor.FLOAT_X4
    5 -> ComposeColorConstructor.FLOAT_X4_COLORSPACE
    else -> null
  }
}
