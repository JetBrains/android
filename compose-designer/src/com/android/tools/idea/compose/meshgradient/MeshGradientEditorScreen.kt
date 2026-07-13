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

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.android.tools.idea.compose.meshgradient.components.ColorSwatch
import com.android.tools.idea.compose.meshgradient.components.DimensionInputField
import com.android.tools.idea.ui.resourcechooser.util.createAndShowColorPickerPopup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import java.awt.Color as AwtColor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.ScrollPaneConstants
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography
import org.jetbrains.kotlin.idea.KotlinLanguage

@OptIn(ExperimentalLayoutApi::class)
@Suppress("UseJBColor")
@Composable
fun MeshGradientEditorScreen(project: Project, state: MeshGeneratorState, isEditingExisting: Boolean = false) {
  val scrollState = rememberScrollState()
  var canvasSize by remember { mutableStateOf(IntSize.Zero) }
  var showColorPickerForVertex by remember { mutableStateOf<Pair<Int, Int>?>(null) }

  // Only create heavy editor instances in Generator fallback mode
  val document =
    remember(isEditingExisting) {
      if (isEditingExisting) return@remember null
      EditorFactory.getInstance().createDocument("")
    }

  val editor =
    remember(project, document, isEditingExisting) {
      if (isEditingExisting || document == null) return@remember null
      val fileType = KotlinLanguage.INSTANCE.getAssociatedFileType() ?: FileTypes.PLAIN_TEXT
      val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
      (EditorFactory.getInstance().createViewer(document, project) as EditorEx).apply {
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        setBorder(null)
        this.highlighter = highlighter

        val scheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
        colorsScheme = scheme
        setBackgroundColor(scheme.defaultBackground)
        scrollPane.background = scheme.defaultBackground
        scrollPane.viewport.background = scheme.defaultBackground
        component.background = scheme.defaultBackground
        contentComponent.background = scheme.defaultBackground
        settings.isCaretRowShown = false
      }
    }

  if (editor != null) {
    DisposableEffect(editor) {
      val connection = project.messageBus.connect()
      connection.subscribe(
        EditorColorsManager.TOPIC,
        EditorColorsListener { newScheme ->
          val scheme = newScheme ?: EditorColorsManager.getInstance().schemeForCurrentUITheme
          WriteIntentReadAction.run {
            editor.colorsScheme = scheme
            editor.setBackgroundColor(scheme.defaultBackground)
            editor.scrollPane.background = scheme.defaultBackground
            editor.scrollPane.viewport.background = scheme.defaultBackground
            editor.component.background = scheme.defaultBackground
            editor.contentComponent.background = scheme.defaultBackground
          }
        },
      )
      onDispose {
        connection.disconnect()
        WriteIntentReadAction.run { EditorFactory.getInstance().releaseEditor(editor) }
      }
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Column(
      modifier =
        if (isEditingExisting) {
          Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)
        } else {
          Modifier.fillMaxWidth().verticalScroll(scrollState)
        },
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // 1. Canvas Preview
      Box(modifier = Modifier.fillMaxWidth().height(220.dp).onGloballyPositioned { canvasSize = it.size }) {
        GradientCanvas(
          meshPoints = state.meshPoints,
          showPoints = state.showPoints,
          constrainEdgePoints = state.constrainEdgePoints,
          onTogglePoints = { state.showPoints = !state.showPoints },
          onPointDrag = { row, col, offset -> state.updateMeshPoint(row, col, offset) },
          onPointClick = { row, col -> showColorPickerForVertex = Pair(row, col) },
        )

        showColorPickerForVertex?.let { (row, col) ->
          val point = state.meshPoints[row][col]
          val relativeOffset = point.position

          // Calculate absolute pixel coordinates inside the Box container
          val xOffset = (relativeOffset.x * canvasSize.width).toInt()
          val yOffset = (relativeOffset.y * canvasSize.height).toInt()

          Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(xOffset, yOffset),
            onDismissRequest = { showColorPickerForVertex = null },
          ) {
            Row(
              modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                  .background(JewelTheme.globalColors.panelBackground)
                  .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                  .padding(6.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              state.availableColors.forEach { color ->
                ColorSwatch(
                  color = color,
                  modifier =
                    Modifier.clickable {
                      state.updateVertexColor(row, col, color)
                      showColorPickerForVertex = null
                    },
                )
              }
            }
          }
        }
      }

      Divider(orientation = Orientation.Horizontal)

      // 2. Grid Config
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        DimensionInputField(
          value = state.rows,
          min = 2,
          max = 10,
          paramName = "Rows",
          onUpdate = { state.updateRows(it) },
          modifier = Modifier.width(100.dp),
        )
        DimensionInputField(
          value = state.cols,
          min = 2,
          max = 10,
          paramName = "Cols",
          onUpdate = { state.updateCols(it) },
          modifier = Modifier.width(100.dp),
        )
        Spacer(Modifier.width(8.dp))
        DefaultButton(onClick = { state.distributeMeshPointsEvenly() }) { Text("Reset Points") }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        CheckboxRow(text = "Show points", checked = state.showPoints, onCheckedChange = { state.showPoints = it })
        CheckboxRow(text = "Constrain edge", checked = state.constrainEdgePoints, onCheckedChange = { state.constrainEdgePoints = it })
      }

      Divider(orientation = Orientation.Horizontal)

      // 4. Color Palette
      Text("Color Palette", style = JewelTheme.typography.h4TextStyle, fontWeight = FontWeight.SemiBold)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        state.availableColors.forEachIndexed { index, color ->
          ContextMenuArea(
            items = {
              listOf(
                ContextMenuItem("Delete") {
                  if (state.availableColors.size > 1) {
                    state.availableColors.remove(color)
                    val fallbackColor = state.availableColors.first()
                    state.updateAllPoints { offset, currentColor ->
                      if (currentColor == color) {
                        MeshGradientPoint(offset, fallbackColor)
                      } else {
                        MeshGradientPoint(offset, currentColor)
                      }
                    }
                  }
                }
              )
            }
          ) {
            ColorSwatch(
              color = color,
              Modifier.clickable {
                var lastColor = color
                createAndShowColorPickerPopup(
                  initialColor = AwtColor(color.toArgb(), true),
                  initialColorResource = null,
                  facet = null,
                  contextFile = null,
                  resourceResolver = null,
                  resourcePickerSources = listOf(),
                  restoreFocusComponent = null,
                  locationToShow = null,
                  colorPickedCallback = { newAwtColor ->
                    val newColor = Color(newAwtColor.rgb)
                    state.updatePaletteAndMeshColor(lastColor, newColor)
                    lastColor = newColor
                  },
                  colorResourcePickedCallback = null,
                )
              },
            )
          }
        }
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
              .size(16.dp)
              .background(JewelTheme.globalColors.panelBackground)
              .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(4.dp))
              .clickable {
                val placeholderColor = Color.White
                state.availableColors.add(placeholderColor)
                val newIndex = state.availableColors.lastIndex

                createAndShowColorPickerPopup(
                  initialColor = AwtColor.WHITE,
                  initialColorResource = null,
                  facet = null,
                  contextFile = null,
                  resourceResolver = null,
                  resourcePickerSources = listOf(),
                  restoreFocusComponent = null,
                  locationToShow = null,
                  colorPickedCallback = { newAwtColor ->
                    val newColor = Color(newAwtColor.rgb)
                    state.availableColors[newIndex] = newColor
                  },
                  colorResourcePickedCallback = null,
                )
              },
        ) {
          Icon(
            key = AllIconsKeys.General.InlineAdd,
            iconClass = AllIconsKeys::class.java,
            contentDescription = "Add Color",
            modifier = Modifier.size(10.dp),
          )
        }
      }
    }

    if (!isEditingExisting) {
      Divider(orientation = Orientation.Horizontal)

      // 5. Generated Code Header
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
    }

    if (!isEditingExisting && editor != null && document != null) {
      SwingPanel(
        factory = { editor.component },
        modifier = Modifier.fillMaxWidth().weight(1f),
        update = {
          if (document.text != state.generatedCode) {
            WriteIntentReadAction.run { ApplicationManager.getApplication().runWriteAction { document.setText(state.generatedCode) } }
          }
        },
      )
    }
  }
}
