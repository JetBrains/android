/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.meshgradient

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

private const val FUN_MESH_PAINTER = "MeshGradientPainter"
private const val FUN_SET_VERTEX = "setVertex"
private const val FUN_OFFSET = "Offset"
private const val FUN_COLOR = "Color"

private const val ARG_ROWS = "rows"
private const val ARG_COLUMNS = "columns"
private const val ARG_X = "x"
private const val ARG_Y = "y"
private const val ARG_RED = "red"
private const val ARG_GREEN = "green"
private const val ARG_BLUE = "blue"
private const val ARG_ALPHA = "alpha"

private const val FORMAT_OFFSET = "Offset(%.4ff, %.4ff)"

class MeshGradientPsiManager(private val project: Project) {

  data class ParsedVertex(val row: Int, val col: Int, val offset: Offset, val color: Color)

  data class ParsedMesh(val rows: Int, val cols: Int, val vertices: List<ParsedVertex>)

  /** Finds the first [KtCallExpression] for "MeshGradientPainter" in the file. */
  fun findMeshPainterCall(file: KtFile): KtCallExpression? {
    return PsiTreeUtil.findChildrenOfType(file, KtCallExpression::class.java).firstOrNull { it.isValidMeshGradientCall() }
  }

  /** Parses the [KtCallExpression] of MeshGradientPainter to extract rows, cols, and vertices. */
  fun parseMesh(callExpr: KtCallExpression): ParsedMesh? {
    val rowsExpr = findArgumentExpression(callExpr, ARG_ROWS, 0) ?: return null
    val colsExpr = findArgumentExpression(callExpr, ARG_COLUMNS, 1) ?: return null

    val rows = rowsExpr.text.toIntOrNull() ?: return null
    val cols = colsExpr.text.toIntOrNull() ?: return null

    val actualRows = rows + 1
    val actualCols = cols + 1

    val lambdaArg = callExpr.valueArguments.lastOrNull() as? KtLambdaArgument ?: return null
    val body = lambdaArg.getLambdaExpression()?.bodyExpression ?: return null

    val vertices = parseVertices(body)

    return ParsedMesh(actualRows, actualCols, vertices)
  }

  private fun findArgumentExpression(callExpr: KtCallExpression, name: String, index: Int): KtExpression? {
    val namedArg = callExpr.valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == name }
    if (namedArg != null) return namedArg.getArgumentExpression()

    val args = callExpr.valueArguments
    if (index < args.size) {
      val arg = args[index]
      if (arg.getArgumentName() == null) {
        return arg.getArgumentExpression()
      }
    }
    return null
  }

  private fun parseVertices(body: KtBlockExpression): List<ParsedVertex> {
    val vertices = mutableListOf<ParsedVertex>()
    val setVertexCalls =
      PsiTreeUtil.findChildrenOfType(body, KtCallExpression::class.java).filter { it.calleeExpression?.text == FUN_SET_VERTEX }

    for (call in setVertexCalls) {
      val args = call.valueArguments
      if (args.size < 4) continue

      val row = args[0].getArgumentExpression()?.text?.toIntOrNull() ?: continue
      val col = args[1].getArgumentExpression()?.text?.toIntOrNull() ?: continue

      val offsetExpr = args[2].getArgumentExpression() ?: continue
      val offset = parseOffset(offsetExpr) ?: continue

      val colorExpr = args[3].getArgumentExpression() ?: continue
      val color = parseColor(colorExpr) ?: continue

      vertices.add(ParsedVertex(row, col, offset, color))
    }
    return vertices
  }

  private fun parseOffset(expr: KtExpression): Offset? {
    val call = expr as? KtCallExpression ?: return null
    if (call.calleeExpression?.text != FUN_OFFSET) return null

    val xExpr = findArgumentExpression(call, ARG_X, 0) ?: return null
    val yExpr = findArgumentExpression(call, ARG_Y, 1) ?: return null

    val x = xExpr.text.removeSuffix("f").removeSuffix("F").toFloatOrNull() ?: return null
    val y = yExpr.text.removeSuffix("f").removeSuffix("F").toFloatOrNull() ?: return null
    return Offset(x, y)
  }

  private fun parseColor(expr: KtExpression): Color? {
    if (expr is KtCallExpression) {
      if (expr.calleeExpression?.text != FUN_COLOR) return null
      val args = expr.valueArguments
      if (args.isEmpty()) return null

      if (args.size == 1) {
        // Single argument: hex or decimal Long/Int
        val argExpr = args[0].getArgumentExpression() ?: return null
        val text = argExpr.text.removeSuffix(".toInt()").removeSuffix(".toLong()")
        val longValue = parseLong(text) ?: return null
        return Color(longValue)
      } else if (args.size >= 3) {
        // 3 or 4 arguments: RGB or RGBA
        val rExpr = findArgumentExpression(expr, ARG_RED, 0) ?: return null
        val gExpr = findArgumentExpression(expr, ARG_GREEN, 1) ?: return null
        val bExpr = findArgumentExpression(expr, ARG_BLUE, 2) ?: return null
        val aExpr = findArgumentExpression(expr, ARG_ALPHA, 3) // Optional

        val r = parseColorComponent(rExpr) ?: return null
        val g = parseColorComponent(gExpr) ?: return null
        val b = parseColorComponent(bExpr) ?: return null
        val a = if (aExpr != null) parseColorComponent(aExpr) ?: 1f else 1f

        return Color(r, g, b, a)
      }
    } else if (expr is KtDotQualifiedExpression) {
      // Constant like Color.Red
      val text = expr.text
      if (text.startsWith("Color.")) {
        val colorName = text.removePrefix("Color.").uppercase()
        return mapConstantColor(colorName)
      }
    }
    return null
  }

  private fun parseLong(text: String): Long? {
    return if (text.startsWith("0x") || text.startsWith("0X")) {
      text.substring(2).toLongOrNull(16)
    } else {
      text.toLongOrNull()
    }
  }

  private fun parseColorComponent(expr: KtExpression): Float? {
    val text = expr.text.removeSuffix("f").removeSuffix("F")
    if (text.contains(".") || expr.text.endsWith("f") || expr.text.endsWith("F")) {
      // Float: 0f .. 1f
      return text.toFloatOrNull()?.coerceIn(0f, 1f)
    } else {
      // Int: 0 .. 255
      val intVal = text.toIntOrNull() ?: return null
      return (intVal.coerceIn(0, 255).toFloat() / 255f)
    }
  }

  private fun mapConstantColor(name: String): Color? {
    return when (name) {
      "BLACK" -> Color.Black
      "DARKGRAY" -> Color.DarkGray
      "GRAY" -> Color.Gray
      "LIGHTGRAY" -> Color.LightGray
      "WHITE" -> Color.White
      "RED" -> Color.Red
      "GREEN" -> Color.Green
      "BLUE" -> Color.Blue
      "YELLOW" -> Color.Yellow
      "CYAN" -> Color.Cyan
      "MAGENTA" -> Color.Magenta
      "TRANSPARENT" -> Color.Transparent
      else -> null
    }
  }

  /**
   * Updates the color of a specific vertex in-place.
   *
   * Note: Preserved as internal for potential future granular/partial AST updates and standalone programmatic API usage. The main editor
   * dialog uses regenerateLambdaBody to rewrite the entire block cleanly.
   */
  internal fun updateVertexColor(painterCall: KtCallExpression, row: Int, col: Int, newColor: Color): Boolean {
    val lambda = painterCall.valueArguments.lastOrNull() as? KtLambdaArgument ?: return false
    val body = lambda.getLambdaExpression()?.bodyExpression ?: return false
    val setVertexCall = findSetVertexCall(body, row, col) ?: return false
    val args = setVertexCall.valueArguments
    if (args.size < 4) return false

    val colorArg = args[3]
    val colorExpr = colorArg.getArgumentExpression() ?: return false

    val psiFactory = KtPsiFactory(project)
    val newColorExpr = psiFactory.createExpression("Color(${newColor.toComposeHexLiteral()})")

    colorExpr.replace(newColorExpr)
    return true
  }

  /**
   * Updates the offset of a specific vertex in-place.
   *
   * Note: Preserved as internal for potential future granular/partial AST updates and standalone programmatic API usage. The main editor
   * dialog uses regenerateLambdaBody to rewrite the entire block cleanly.
   */
  internal fun updateVertexOffset(painterCall: KtCallExpression, row: Int, col: Int, newOffset: Offset): Boolean {
    val lambda = painterCall.valueArguments.lastOrNull() as? KtLambdaArgument ?: return false
    val body = lambda.getLambdaExpression()?.bodyExpression ?: return false
    val setVertexCall = findSetVertexCall(body, row, col) ?: return false
    val args = setVertexCall.valueArguments
    if (args.size < 3) return false

    val offsetArg = args[2]
    val offsetExpr = offsetArg.getArgumentExpression() ?: return false

    val psiFactory = KtPsiFactory(project)
    val newOffsetExpr = psiFactory.createExpression(String.format(Locale.US, FORMAT_OFFSET, newOffset.x, newOffset.y))

    offsetExpr.replace(newOffsetExpr)
    return true
  }

  /** Updates the rows and columns constructor arguments of the painter call. */
  fun updateConstructorArguments(painterCall: KtCallExpression, newRows: Int, newCols: Int): Boolean {
    val rowsExpr = findArgumentExpression(painterCall, ARG_ROWS, 0) ?: return false
    val colsExpr = findArgumentExpression(painterCall, ARG_COLUMNS, 1) ?: return false

    val psiFactory = KtPsiFactory(project)
    val newRowsExpr = psiFactory.createExpression((newRows - 1).toString())
    val newColsExpr = psiFactory.createExpression((newCols - 1).toString())

    rowsExpr.replace(newRowsExpr)
    colsExpr.replace(newColsExpr)
    return true
  }

  /** Completely clears and regenerates all setVertex statements inside the lambda body block. */
  fun regenerateLambdaBody(painterCall: KtCallExpression, meshPoints: List<List<Pair<Offset, Color>>>): Boolean {
    val lambda = painterCall.valueArguments.lastOrNull() as? KtLambdaArgument ?: return false
    val body = lambda.getLambdaExpression()?.bodyExpression ?: return false

    // Delete all existing statements inside lambda body
    body.statements.forEach { it.delete() }

    val psiFactory = KtPsiFactory(project)

    meshPoints.forEachIndexed { r, row ->
      row.forEachIndexed { c, (offset, color) ->
        val statementStr =
          String.format(
            Locale.US,
            "setVertex(%d, %d, Offset(%.4ff, %.4ff), Color(%s))",
            r,
            c,
            offset.x,
            offset.y,
            color.toComposeHexLiteral(),
          )
        val statementExpr = psiFactory.createExpression(statementStr)
        body.add(statementExpr)
        body.add(psiFactory.createNewLine())
      }
    }

    return true
  }

  private fun findSetVertexCall(body: KtBlockExpression, row: Int, col: Int): KtCallExpression? {
    return PsiTreeUtil.findChildrenOfType(body, KtCallExpression::class.java)
      .filter { it.calleeExpression?.text == FUN_SET_VERTEX }
      .firstOrNull { call ->
        val args = call.valueArguments
        if (args.size < 2) return@firstOrNull false
        val r = args[0].getArgumentExpression()?.text?.toIntOrNull()
        val c = args[1].getArgumentExpression()?.text?.toIntOrNull()
        r == row && c == col
      }
  }
}
