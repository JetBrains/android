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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.idea.compose.meshgradient.components.ColorDropdown
import com.android.tools.idea.compose.meshgradient.components.ColorSwatch
import com.android.tools.idea.compose.meshgradient.components.DimensionInputField
import com.android.tools.idea.compose.meshgradient.components.OffsetInputField
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeshGradientGeneratorScreen(state: MeshGeneratorState = remember { MeshGeneratorState() }) {
  val scrollState = rememberScrollState()
  val defaultColors = remember {
    listOf(
      Color(0xFFF44336),
      Color(0xFFE91E63),
      Color(0xFF9C27B0),
      Color(0xFF673AB7),
      Color(0xFF3F51B5),
      Color(0xFF2196F3),
      Color(0xFF03A9F4),
      Color(0xFF00BCD4),
      Color(0xFF009688),
      Color(0xFF4CAF50),
    )
  }
  val availableColors = remember { mutableStateListOf<Color>().apply { addAll(defaultColors) } }
  val customColorText = remember { TextFieldState() }

  Column(
    modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(16.dp).verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Canvas Preview
    Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
      GradientCanvas(
        resolution = state.resolution,
        blurLevel = state.blurLevel,
        meshPoints = state.meshPoints,
        showPoints = state.showPoints,
        onTogglePoints = { state.showPoints = !state.showPoints },
        onPointDrag = { row, col, offset -> state.updateMeshPoint(row, col, offset) },
      )
    }

    Divider(orientation = Orientation.Horizontal)

    // 2. Grid Config
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      DimensionInputField(
        value = state.rows,
        min = 2,
        max = 10,
        paramName = "Rows",
        onUpdate = { state.updateRows(it) },
        modifier = Modifier.weight(1f),
      )
      DimensionInputField(
        value = state.cols,
        min = 2,
        max = 10,
        paramName = "Cols",
        onUpdate = { state.updateCols(it) },
        modifier = Modifier.weight(1f),
      )
    }

    // Checkboxes
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
      CheckboxRow(text = "Show points", checked = state.showPoints, onCheckedChange = { state.showPoints = it })
      CheckboxRow(text = "Constrain edge", checked = state.constrainEdgePoints, onCheckedChange = { state.constrainEdgePoints = it })
      DefaultButton(onClick = { state.distributeMeshPointsEvenly() }) { Text("Reset Points") }
    }

    // Sliders
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Blur: ", modifier = Modifier.width(80.dp))
        Slider(value = state.blurLevel, onValueChange = { state.blurLevel = it }, modifier = Modifier.weight(1f))
        Text(String.format(Locale.US, "%.2f", state.blurLevel), modifier = Modifier.padding(start = 8.dp))
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Resolution: ", modifier = Modifier.width(80.dp))
        Slider(
          value = (state.resolution - 1).toFloat() / 19f, // 1 to 20
          onValueChange = { state.resolution = (it * 19).roundToInt() + 1 },
          modifier = Modifier.weight(1f),
        )
        Text("${state.resolution}", modifier = Modifier.padding(start = 8.dp))
      }
    }

    Divider(orientation = Orientation.Horizontal)

    // 3. Color Palette
    Text("Color Palette", style = JewelTheme.typography.h4TextStyle, fontWeight = FontWeight.SemiBold)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      TextField(state = customColorText, placeholder = { Text("Hex (e.g. FF0000)") }, modifier = Modifier.weight(1f))
      IconButton(
        onClick = {
          try {
            val color = customColorText.text.toString().toColor()
            if (color !in availableColors) {
              availableColors.add(color)
            }
            customColorText.edit { replace(0, length, "") }
          } catch (e: Exception) {
            // Handle error (e.g. invalid hex)
          }
        }
      ) {
        Icon(key = AllIconsKeys.General.InlineAdd, iconClass = AllIconsKeys::class.java, contentDescription = "Add Color")
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      availableColors.forEach { color ->
        ColorSwatch(
          color = color,
          modifier =
            Modifier.clickable {
              if (color !in defaultColors) {
                availableColors.remove(color)
                state.updateAllPoints { offset, currentColor ->
                  if (currentColor == color) {
                    Pair(offset, defaultColors[0])
                  } else {
                    Pair(offset, currentColor)
                  }
                }
              }
            },
        )
      }
    }

    Divider(orientation = Orientation.Horizontal)

    // 4. Points List
    Text("Edit Points", style = JewelTheme.typography.h4TextStyle, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      state.meshPoints.forEachIndexed { rowIdx, rowPoints ->
        Text("Row ${rowIdx + 1}", fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          rowPoints.forEachIndexed { colIdx, point ->
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("P(${rowIdx},${colIdx})", modifier = Modifier.width(50.dp))
              ColorDropdown(
                selectedColor = point.second,
                colors = availableColors,
                onSelected = { state.updateMeshPointColor(rowIdx, colIdx, it) },
              )
              OffsetInputField(
                value = point.first.x,
                enabled = !(state.constrainEdgePoints && (colIdx == 0 || colIdx == rowPoints.size - 1)),
                paramName = "X",
                onUpdate = { state.updateMeshPoint(rowIdx, colIdx, Offset(it, point.first.y)) },
                modifier = Modifier.weight(1f),
              )
              OffsetInputField(
                value = point.first.y,
                enabled = !(state.constrainEdgePoints && (rowIdx == 0 || rowIdx == state.meshPoints.size - 1)),
                paramName = "Y",
                onUpdate = { state.updateMeshPoint(rowIdx, colIdx, Offset(point.first.x, it)) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    Divider(orientation = Orientation.Horizontal)

    // 5. Generated Code
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Generated Code", style = JewelTheme.typography.h4TextStyle, fontWeight = FontWeight.SemiBold)
      DefaultButton(
        onClick = {
          val clipboard = Toolkit.getDefaultToolkit().systemClipboard
          clipboard.setContents(StringSelection(state.generatedCode), null)
        }
      ) {
        Text("Copy Code")
      }
    }

    SelectionContainer {
      Text(
        text = state.generatedCode,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.05f)).padding(8.dp),
      )
    }
  }
}
