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
package com.android.tools.idea.journeys.view

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.idea.journeys.JourneysEditorViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import icons.StudioIconsCompose
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import javax.swing.JComponent
import com.android.tools.adtui.common.primaryContentBackground
import com.intellij.openapi.diagnostic.thisLogger
import icons.StudioIconsCompose.LayoutEditor.Palette.GridView
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.visibleItemsRange
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaMetrics
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldColors
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle
import kotlin.math.abs

class JourneysEditorViewImpl(
  parentDisposable: Disposable,
  private val model: JourneysEditorViewModel,
  private val listener: JourneysEditorViewListener,
) : Disposable {
  private val rootView: JComponent

  init {
    Disposer.register(parentDisposable, this)
    rootView = StudioComposePanel { EditorUI() }
  }

  override fun dispose() {}

  fun getComponent(): JComponent = rootView

  @Composable
  private fun EditorUI(modifier: Modifier = Modifier) {
    Column(
      modifier = modifier.fillMaxWidth()
        .background(primaryContentBackground.toComposeColor())
    ){
      JourneyTitle()
      JourneyDescription()
      Toolbar()
      JourneyActions()
    }
  }

  @Composable
  private fun JourneyTitle(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = JewelTheme.defaultTextStyle
  ) {
    val textState = remember(model.name.value) { TextFieldState(model.name.value)}
    LaunchedEffect(textState) {
      snapshotFlow { textState.text.toString() }.collectLatest {
        if (model.name.value != it) {
          listener.nameTextUpdated(it)
        }
      }
    }

    Row(
      modifier = modifier
        .background(Color.Transparent)
        .padding(vertical = 12.dp, horizontal = 16.dp)
        .fillMaxWidth()
    ) {
      TextField(
        state = textState,
        textStyle = textStyle.merge(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
        style = rememberBorderlessTextFieldStyle()
      )
    }

    Divider(orientation = Orientation.Horizontal, color = JewelTheme.globalColors.borders.normal)
  }

  @Composable
  private fun JourneyDescription(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = JewelTheme.defaultTextStyle
  ) {
    val textState = remember(model.description.value) { TextFieldState(model.description.value) }
    LaunchedEffect(textState) {
      snapshotFlow { textState.text.toString() }.collectLatest {
        if (model.description.value != it) {
          listener.descriptionTextUpdated(it)
        }
      }
    }

    Row(
      modifier = modifier
        .background(Color.Transparent)
        .padding(vertical = 12.dp, horizontal = 16.dp)
        .fillMaxWidth()
    ) {
        TextField(
          state = textState,
          textStyle = textStyle.merge(fontSize = 14.sp, lineHeight = 18.sp),
          style = rememberBorderlessTextFieldStyle()
        )
    }

    Divider(orientation = Orientation.Horizontal, color = JewelTheme.globalColors.borders.normal)
  }

  @Composable
  private fun Toolbar(modifier: Modifier = Modifier) {
    Row(
      modifier = modifier
        .background(Color.Transparent)
        .padding(vertical = 4.dp, horizontal = 7.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        modifier = Modifier.padding(start = 5.dp).width(16.dp).height(16.dp),
        key = StudioIconsCompose.Compose.Toolbar.RunOnDevice,
        contentDescription = null)

      Divider(orientation = Orientation.Vertical, modifier = Modifier.height(14.dp))

      Icon(
        modifier = Modifier.width(16.dp).height(16.dp),
        key = StudioIconsCompose.Common.Add,
        contentDescription = null)
    }

    Divider(orientation = Orientation.Horizontal, color = JewelTheme.globalColors.borders.normal)
  }

  @Composable
  private fun JourneyActions(modifier: Modifier = Modifier) {
    var offsetState by remember { mutableFloatStateOf(0f) }
    var indexDragged by remember { mutableIntStateOf(-1) }
    var localPosition by remember { mutableStateOf(Offset(0f, 0f)) }
    var cardHeights = remember { mutableStateListOf<Int>() }
    val state = rememberLazyListState()
    val focusRequesterList = mutableListOf<FocusRequester>()

    val nestedScrollConnection = remember {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (indexDragged != -1) {
            if (isPositionInView(state, localPosition.y + offsetState - available.y)) {
              offsetState -= available.y
            }
          }
          return Offset.Zero
        }
      }
    }

    VerticallyScrollableContainer(
      state
    ) {
      LazyColumn(
        state = state,
        modifier = modifier
          .background(Color.Transparent)
          .nestedScroll(nestedScrollConnection),
        //contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
      ) {
        itemsIndexed(items = model.actionValueList.value) { i, _ ->
          var hasFocus by remember { mutableStateOf(false) }
          val textState = remember(model.actionValueList.value[i].value, i) { TextFieldState(model.actionValueList.value[i].value) }
          val focusRequester = remember { FocusRequester() }
          val interactionSource = remember { MutableInteractionSource() }
          val isHovered by interactionSource.collectIsHoveredAsState()

          while (cardHeights.size <= i) {
            cardHeights.add(0)
          }

          focusRequesterList.add(focusRequester)

          LaunchedEffect(textState) {
            snapshotFlow { textState.text.toString() }.collectLatest {
              if (i < model.actionValueList.value.size && model.actionValueList.value[i].value != it)
                listener.actionValueUpdated(i, it)
            }
          }

          ActionCard(
            modifier = Modifier
              .onGloballyPositioned {
                // maybe we can do better here?
                if (cardHeights[i] != it.size.height) {
                  cardHeights[i] = it.size.height
                }

                if (i == indexDragged) {
                  localPosition = it.positionInParent()
                }
              }
              //.padding(4.dp)
              .offset { if (i == indexDragged) IntOffset(0, offsetState.toInt()) else IntOffset(0,0) }
              .zIndex(zIndex = if (i == indexDragged) model.actionValueList.value.size.toFloat() else 0f)
              .onFocusChanged { hasFocus = it.hasFocus }
              .hoverable(interactionSource = interactionSource)
              .pointerInput(Unit) {
                detectDragGestures(
                  onDragStart = {
                    if (hasFocus) {
                      indexDragged = i
                    }
                  },
                  onDragEnd = {
                    if (hasFocus) {
                      val isMovingUp = offsetState < 0
                      val movementOffset = if (isMovingUp) cardHeights[indexDragged] * .5 else -cardHeights[indexDragged] * .5
                      var currOffset = offsetState + movementOffset

                      var newIndex = indexDragged
                      while (newIndex >= 0 && newIndex < model.actionValueList.value.size && (isMovingUp && currOffset < -cardHeights[newIndex] / 2 || !isMovingUp && currOffset > cardHeights[newIndex] / 2)) {
                        if (isMovingUp) {
                          currOffset += cardHeights[newIndex]
                          newIndex--
                        } else  {
                          currOffset -= cardHeights[newIndex]
                          newIndex++
                        }
                      }

                      thisLogger().warn("newIndex: ${newIndex}")
                      if (indexDragged != -1 && newIndex != indexDragged) {
                        listener.moveAction(indexDragged, newIndex)
                        //focusRequesterList[indexDragged + changeOfIndex].requestFocus()
                      }
                      indexDragged = -1
                      offsetState = 0f
                    }
                  },
                  onDrag = { _, dragAmount ->
                    if (hasFocus) {
                      val movementOffset =  cardHeights[indexDragged] * .5f
                      if (isPositionInView(state, localPosition.y + offsetState + dragAmount.y + movementOffset)) {
                        offsetState += dragAmount.y
                      }
                    }
                  }
                )
              }
              .pointerInput(Unit) {
                detectTapGestures { focusRequester.requestFocus() }
              },
            textState = textState,
            focusRequester = focusRequester,
            isHovered = isHovered,
            hasFocus = hasFocus,
            isDraggable = true,
            onTapDelete = { listener.removeAction(i) }
          )
        }

        item {
          val textState = remember { TextFieldState("") }
          var hasCreatedAction by remember { mutableStateOf(false) }
          var hasFocus by remember { mutableStateOf(false) }
          val focusRequester = remember { FocusRequester() }

          LaunchedEffect(textState) {
            snapshotFlow { textState.text.toString() }.collect {
              if (it.isNotEmpty()) {
                if (hasCreatedAction) {
                  // Assume model hasn't updated
                  listener.actionValueUpdated(model.actionValueList.value.size, it)
                } else {
                  listener.addNewActionWithText(it)
                  hasCreatedAction = true
                }
              }
            }
          }

          ActionCard(
            modifier = Modifier
              //.padding(vertical = 8.dp)
              .onFocusChanged { hasFocus = it.hasFocus }
              .pointerInput(Unit) {
                detectTapGestures { focusRequester.requestFocus() }
              },
            textState = textState,
            focusRequester = focusRequester,
            isHovered = false,
            hasFocus = hasFocus,
            isDraggable = false
          )
        }
      }
    }
  }

  @Composable
  private fun ActionCard(
    modifier: Modifier = Modifier,
    textState: TextFieldState,
    focusRequester: FocusRequester,
    isHovered: Boolean,
    hasFocus: Boolean,
    isDraggable: Boolean,
    onTapDelete: () -> Unit = { }
  ) {
    val shape = RoundedCornerShape(12.dp)
    // background color should be some blue color (for light theme) and ??? for dark.
    Row(
      modifier = modifier
        .fillMaxWidth()
        .background(color = if (isHovered) JewelTheme.globalColors.panelBackground else JewelTheme.globalColors.borders.focused, shape)
        .border(
          width = 1.dp,
          color = if (hasFocus) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.borders.normal,
          shape = shape)
        .padding(vertical = 12.dp, horizontal = 16.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
      content = {
        val iconModifier = Modifier.width(16.dp).height(16.dp)
        ShowOrHideComponentWithSpace(show = (hasFocus || isHovered) && isDraggable, modifier = iconModifier) {
          Icon(
            modifier = iconModifier,
            key = GridView,
            contentDescription = null
          )
        }

        ContentFrame(textState = textState, focusRequester = focusRequester, showButtons = hasFocus || isHovered, onTapDelete = onTapDelete)
      }
    )
  }

  @Composable
  private fun ContentFrame(
    modifier: Modifier = Modifier,
    textState: TextFieldState,
    focusRequester: FocusRequester,
    showButtons: Boolean,
    onTapDelete: () -> Unit
  ) {
    var currentHeightDp by remember { mutableStateOf(Dp.Unspecified) }
    val density = LocalDensity.current
    Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
      horizontalAlignment = Alignment.Start
    ) {
      HeightAutoSizingTextArea(
        state = textState,
        modifier = Modifier
          .heightIn(
            min = 16.dp,
            max = 200.dp,
          )
          .animateContentSize(
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            alignment = Alignment.BottomCenter,
          )
          .onSizeChanged { currentHeightDp = with(density) { it.height.toDp() } }
          .focusRequester(focusRequester),
        style = rememberBorderlessTextAreaStyle(),
        placeholder = { Text("Add an action")}
      )

      ContentButtons(showButtons = showButtons, onTapDelete = onTapDelete)
    }
  }

  @Composable
  private fun ContentButtons(
    modifier: Modifier = Modifier,
    showButtons: Boolean,
    onTapDelete: () -> Unit
  ) {
    Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
      verticalAlignment = Alignment.Top
    ) {
      val placeHolderModifier = Modifier.width(16.dp).height(16.dp)
      ShowOrHideComponentWithSpace(showButtons, placeHolderModifier) {
        Icon(
          modifier = placeHolderModifier,
          key = StudioIconsCompose.LayoutEditor.Palette.Placeholder,
          contentDescription = null
        )
      }

      val deleteModifier = Modifier.size(16.dp)
      ShowOrHideComponentWithSpace(showButtons, deleteModifier) {
        IconButton(
          modifier = deleteModifier,
          onClick = onTapDelete,
          focusable = false,
          style = rememberBorderlessIconButtonStyle()
        ) {
          Icon(
            key = StudioIconsCompose.Common.Delete,
            contentDescription = null
          )
        }
      }
    }
  }

  @Composable
  private fun ShowOrHideComponentWithSpace(
    show: Boolean,
    modifier: Modifier,
    component: @Composable () -> Unit
  ) {
    if (show) {
      component()
    } else {
      Spacer(modifier = modifier)
    }
  }

  @Composable
  private fun rememberBorderlessTextAreaStyle(): TextAreaStyle {
    val baseStyle = JewelTheme.textAreaStyle
    return TextAreaStyle(
      colors = baseStyle.colors,
      metrics =
      TextAreaMetrics(
        borderWidth = baseStyle.metrics.borderWidth,
        contentPadding = PaddingValues(0.dp),
        cornerSize = CornerSize(0f),
        minSize = DpSize.Zero,
      ),
    )
  }

  @Composable
  private fun rememberBorderlessTextFieldStyle(): TextFieldStyle {
    val baseStyle = JewelTheme.textFieldStyle
    return TextFieldStyle(
      colors = TextFieldColors(
        background = Color.Transparent,
        backgroundDisabled = baseStyle.colors.backgroundDisabled,
        backgroundFocused = baseStyle.colors.backgroundFocused,
        backgroundPressed = baseStyle.colors.backgroundPressed,
        backgroundHovered = baseStyle.colors.backgroundHovered,
        content = baseStyle.colors.content,
        contentDisabled = baseStyle.colors.contentDisabled,
        contentFocused = baseStyle.colors.contentFocused,
        contentPressed = baseStyle.colors.contentPressed,
        contentHovered = baseStyle.colors.contentHovered,
        border = Color.Transparent,
        borderDisabled = Color.Transparent,
        borderFocused = Color.Transparent,
        borderPressed = Color.Transparent,
        borderHovered = Color.Transparent,
        caret = baseStyle.colors.caret,
        caretDisabled = baseStyle.colors.caretDisabled,
        caretFocused = baseStyle.colors.caretFocused,
        caretPressed = baseStyle.colors.caretPressed,
        caretHovered = baseStyle.colors.caretHovered,
        placeholder = baseStyle.colors.placeholder,
      ),
      metrics =
      TextFieldMetrics(
        borderWidth = 0.dp,
        contentPadding = PaddingValues(0.dp),
        cornerSize = CornerSize(0f),
        minSize = DpSize.Zero,
      ),
    )
  }

  @Composable
  private fun rememberBorderlessIconButtonStyle(): IconButtonStyle {
    val baseStyle = JewelTheme.iconButtonStyle

    return IconButtonStyle(
      colors = baseStyle.colors,
      metrics = IconButtonMetrics(
        cornerSize = CornerSize(0f),
        borderWidth = 0.dp,
        padding = PaddingValues(0.dp),
        minSize = DpSize.Zero,
      )
    )
  }

  private fun isPositionInView(state: LazyListState, position: Float): Boolean {
    // Assume the New Action card is at the very bottom (for now).
    val containerStart = state.layoutInfo.viewportStartOffset + state.layoutInfo.beforeContentPadding
    val containerEnd = state.layoutInfo.viewportEndOffset + state.layoutInfo.afterContentPadding
    val actionCardOffset = if (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index == state.layoutInfo.totalItemsCount - 1) {
      containerEnd - (state.layoutInfo.visibleItemsInfo.lastOrNull()?.offset ?: containerEnd)
    } else { 0 }

    //println("position ${position}, lastVisibleIndex: ${state.layoutInfo.visibleItemsInfo.lastOrNull()?.index}, size: ${state.layoutInfo.visibleItemsInfo.lastOrNull()?.size}, offset: ${state.layoutInfo.visibleItemsInfo.lastOrNull()?.offset}, ")
    //println("Action Card showing: ${actionCardOffset}")
    return position >= containerStart  && position <= containerEnd - actionCardOffset
  }

  class ActionData(val type: Action, val value: String) {}

  enum class Action(val value: String) {
    ACTION("action"), ASSERTION("assertion");

    companion object {
      fun enumValueOfOrNull(name: String): Action? {
        return entries.find { it.value == name }
      }

      val list = entries
    }
  }

}

