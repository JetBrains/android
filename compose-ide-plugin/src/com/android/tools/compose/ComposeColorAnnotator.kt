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
import com.android.tools.idea.flags.StudioFlags
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
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ui.ColorIcon
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.evaluation.uValueOf
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
      !StudioFlags.COMPOSE_EDITOR_SUPPORT.get() -> return
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
          val color = getColor(uElement.valueArguments) ?: return
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .gutterIconRenderer(ColorIconRenderer(uElement, color))
            .create()
        }
      }
    }
  }

  private fun getColor(args: List<UExpression>): Color? {
    return try {
      when (getConstructorType(args)) {
        ComposeColorConstructor.INT -> {
          val rgbaInt = args[0].getInt() ?: return null
          Color(rgbaInt, true)
        }
        ComposeColorConstructor.LONG -> {
          val rgbaInt = args[0].getLong() ?: return null
          Color(rgbaInt, true)
        }
        ComposeColorConstructor.INT_X3 -> {
          val r = args[0].getInt() ?: return null
          val g = args[1].getInt() ?: return null
          val b = args[2].getInt() ?: return null
          Color(r, g, b)
        }
        ComposeColorConstructor.INT_X4 -> {
          val r = args[0].getInt() ?: return null
          val g = args[1].getInt() ?: return null
          val b = args[2].getInt() ?: return null
          val a = args[3].getInt() ?: return null
          Color(r, g, b, a)
        }
        ComposeColorConstructor.FLOAT_X3 -> {
          val r = args[0].getFloat() ?: return null
          val g = args[1].getFloat() ?: return null
          val b = args[2].getFloat() ?: return null
          Color(r, g, b)
        }
        ComposeColorConstructor.FLOAT_X4, ComposeColorConstructor.FLOAT_X4_COLORSPACE -> {
          val r = args[0].getFloat() ?: return null
          val g = args[1].getFloat() ?: return null
          val b = args[2].getFloat() ?: return null
          val a = args[3].getFloat() ?: return null
          Color(r, g, b, a)
        }
        else -> null
      }
    }
    catch (ignore: Exception) {
      return null
    }
  }
}

/**
 * Simplified version of [AndroidAnnotatorUtil.ColorRenderer] that does not work on [ResourceReference] but still displays the same color
 * picker.
 * Currently only updates the value of the Color declaration in the editor if it's using the [ComposeColorConstructor.INT] or
 * [ComposeColorConstructor.LONG].
 * TODO(lukeegan): Implement this for each of the Color parameter combinations
 */
data class ColorIconRenderer(val element: UCallExpression, val color: Color) : GutterIconRenderer() {
  private val ICON_SIZE = 8

  override fun getIcon(): Icon {
    return ColorIcon(ICON_SIZE, color)
  }

  override fun getClickAction(): AnAction? {
    val constructorType = getConstructorType(element.valueArguments) ?: return null
    if (!constructorType.canBeOverwritten()) {
      return null
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
            .addColorPickerListener(ColorPickerListener { color, _ -> setColorToAttribute(color) })
            .focusWhenDisplay(true)
            .setFocusCycleRoot(true)
            .build()
          dialog.show(colorPicker, null, MouseInfo.getPointerInfo().location)
        }
      }
    }
  }

  private fun ComposeColorConstructor.canBeOverwritten(): Boolean {
    return ComposeColorConstructor.INT == this || ComposeColorConstructor.LONG == this
  }

  fun setColorToAttribute(color: Color) {
    val constructorType = getConstructorType(element.valueArguments) ?: return
    if (!constructorType.canBeOverwritten()) {
      return
    }
    val runnable =
      Runnable {
        val hexString = "0x${(Integer.toHexString(color.rgb)).toUpperCase(Locale.getDefault())}"
        val firstArgument = element.valueArguments[0].sourcePsi as? KtConstantExpression ?: return@Runnable
        if ((firstArgument as PsiElement).isValid) {
          (firstArgument.node?.firstChildNode as? LeafPsiElement)?.replaceWithText(hexString)
        }
      }
    val project = element.sourcePsi?.project ?: return
    ApplicationManager.getApplication().invokeLater(Runnable {
      WriteCommandAction.runWriteCommandAction(project, "Change Color", null, runnable)
    }, project.disposed)
  }
}

private const val COLOR_METHOD = "Color"
private const val COMPOSE_COLOR_CLASS = "androidx.compose.ui.graphics.ColorKt"

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

private fun UExpression.getInt(): Int? {
  return this.getConstant() as? Int
}

private fun UExpression.getLong(): Int? {
  return (this.getConstant() as? Long)?.toInt()
}

private fun UExpression.getFloat(): Float? {
  return when (val argumentValue = this.getConstant()) {
    is Double -> argumentValue.toFloat()
    is Float -> argumentValue
    else -> null
  }
}

private fun UExpression.getConstant(): Any? {
  val value = this.uValueOf() ?: return null
  val constant = value.toConstant() ?: return null
  return constant.value
}

enum class ComposeColorConstructor {
  INT, LONG, INT_X3, INT_X4, FLOAT_X3, FLOAT_X4, FLOAT_X4_COLORSPACE
}
