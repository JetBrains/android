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
package com.android.tools.inspectors.common.api.ide.stacktrace

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.adtui.stdui.StandardColors.DEFAULT_CONTENT_BACKGROUND_COLOR
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.codenavigation.CodeLocation.INVALID_LINE_NUMBER
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.inspectors.common.api.stacktrace.CodeElement
import com.android.tools.inspectors.common.api.stacktrace.StackElement
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel.Aspect.SELECTED_LOCATION
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel.Aspect.STACK_FRAMES
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel.Type.INVALID
import com.android.tools.inspectors.common.api.stacktrace.ThreadElement
import com.android.tools.inspectors.common.api.stacktrace.ThreadId.INVALID_THREAD_ID
import com.android.tools.inspectors.common.ui.ContextMenuInstaller
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys.COPY_PROVIDER
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.asCoroutineDispatcher
import java.awt.Insets
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel.SINGLE_SELECTION
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingUtilities
import javax.swing.event.ListSelectionListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LIST_ROW_INSETS: Insets = JBUI.insets(2, 10, 0, 0)

class IntelliJStackTraceView
@VisibleForTesting
internal constructor(
  private val project: Project,
  private val model: StackTraceModel,
  parentDisposable: Disposable,
  private val generator: (Project, CodeLocation) -> CodeElement,
) : AspectObserver(), StackTraceView, DataProvider, CopyProvider {

  private val copyPasteManager = CopyPasteManager.getInstance()
  private val scrollPane: JBScrollPane
  private val listModel = DefaultListModel<StackElement>()
  @get:VisibleForTesting val listView = JBList(listModel)
  private val renderer: StackElementRenderer

  constructor(
    project: Project,
    model: StackTraceModel,
    parentDisposable: Disposable,
  ) : this(
    project,
    model,
    parentDisposable,
    { p: Project, l: CodeLocation -> IntelliJCodeElement(p, l) }
  )

  init {
    listView.selectionMode = SINGLE_SELECTION
    listView.background = DEFAULT_CONTENT_BACKGROUND_COLOR
    renderer = StackElementRenderer()
    listView.setCellRenderer(renderer)
    scrollPane = JBScrollPane(listView)
    scrollPane.horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
    scrollPane.verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED

    DataManager.registerDataProvider(listView, this)

    listView.addListSelectionListener {
      if (listView.selectedValue == null) {
        model.clearSelection()
      }
    }

    val navigationHandler: () -> Boolean = {
      val index = listView.selectedIndex
      if (index >= 0 && index < listView.itemsCount) {
        model.selectedIndex = index
        true
      } else {
        false
      }
    }

    listView.addKeyListener(
      object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          // On Windows we don't get a KeyCode so checking the getKeyCode doesn't work. Instead, we
          // get the code from the char
          // we are given.
          val keyCode = KeyEvent.getExtendedKeyCodeForChar(e.keyChar.code)
          if (keyCode == KeyEvent.VK_ENTER) {
            if (navigationHandler()) {
              e.consume()
            }
          }
        }
      }
    )

    object : DoubleClickListener() {
        override fun onDoubleClick(event: MouseEvent): Boolean {
          return navigationHandler()
        }
      }
      .installOn(listView)

    listView.addMouseListener(
      object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isRightMouseButton(e)) {
            val row = listView.locationToIndex(e.point)
            if (row != -1) {
              listView.selectedIndex = row
            }
          }
        }
      }
    )

    // A scope that we will be using to load the list model in the background
    val scope = AndroidCoroutineScope(parentDisposable)
    val dispatcher = MoreExecutors.newSequentialExecutor(AndroidExecutors.getInstance().workerThreadExecutor).asCoroutineDispatcher()
    model
      .addDependency(this)
      .onChange(STACK_FRAMES) {
        scope.launch(dispatcher) {
          val elements = model.codeLocations.map { generator(project, it) }
          withContext(uiThread) {
            listModel.removeAllElements()
            listView.clearSelection()
            listModel.addAll(elements)
            val threadId = model.threadId
            if (threadId != INVALID_THREAD_ID) {
              listModel.addElement(ThreadElement(threadId))
            }
          }
        }
      }
      .onChange(SELECTED_LOCATION) {
        val index = model.selectedIndex
        if (model.selectedType == INVALID) {
          if (listView.selectedIndex != -1) {
            listView.clearSelection()
          }
        } else if (index >= 0 && index < listView.itemsCount) {
          if (listView.selectedIndex != index) {
            listView.selectedIndex = index
          }
        } else {
          throw IndexOutOfBoundsException(
            "View has " +
              listView.itemsCount +
              " elements while aspect is changing to index " +
              index
          )
        }
      }
  }

  fun installNavigationContextMenu(contextMenuInstaller: ContextMenuInstaller) {
    contextMenuInstaller.installNavigationContextMenu(listView, model.codeNavigator) lambda@{
      val index = listView.selectedIndex
      if (index >= 0 && index < listView.itemsCount) {
        return@lambda model.codeLocations[index]
      }
      null
    }
  }

  fun installGenericContextMenu(installer: ContextMenuInstaller, contextMenuItem: ContextMenuItem) {
    installer.installGenericContextMenu(listView, contextMenuItem)
  }

  override fun getModel() = model

  override fun getComponent() = scrollPane

  fun addListSelectionListener(listener: ListSelectionListener) {
    listView.addListSelectionListener(listener)
  }

  fun clearSelection() {
    listView.clearSelection()
  }

  override fun getActionUpdateThread() = BGT

  override fun isCopyEnabled(dataContext: DataContext) = true

  override fun isCopyVisible(dataContext: DataContext) = true

  /**
   * Copies the selected list item to the clipboard. The copied text rendering is the same as the
   * list rendering.
   */
  override fun performCopy(dataContext: DataContext) {
    val selectedIndex = listView.selectedIndex
    if (selectedIndex >= 0 && selectedIndex < listView.itemsCount) {
      renderer.getListCellRendererComponent(
        listView,
        listModel.getElementAt(selectedIndex),
        selectedIndex,
        true,
        false
      )
      val data = renderer.getCharSequence(false).toString()
      copyPasteManager.setContents(StringSelection(data))
    }
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      COPY_PROVIDER.name -> this
      else -> null
    }
  }

  /** Renderer for a JList of [StackElement] instances. */
  private class StackElementRenderer : ColoredListCellRenderer<StackElement>() {
    private val iconManager = IconManager.getInstance()

    override fun customizeCellRenderer(
      list: JList<out StackElement>,
      value: StackElement?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      if (value == null) {
        return
      }

      ipad = LIST_ROW_INSETS
      when (value) {
        is ThreadElement -> renderThreadElement(value, selected)
        is CodeElement -> renderCodeElement(value, selected)
        else -> append(value.toString(), SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
    }

    private fun renderCodeElement(element: CodeElement, selected: Boolean) {
      when (element.codeLocation.isNativeCode) {
        true -> renderNativeStackFrame(element, selected)
        false -> renderJavaStackFrame(element, selected)
      }
    }

    private fun renderJavaStackFrame(codeElement: CodeElement, selected: Boolean) {
      @Suppress("UnstableApiUsage")
      icon = iconManager.getPlatformIcon(PlatformIcons.Method)
      val textAttribute =
        if (selected || codeElement.isInUserCode) REGULAR_ATTRIBUTES else GRAY_ATTRIBUTES
      val location = codeElement.codeLocation
      val methodBuilder = StringBuilder(codeElement.methodName)
      if (location.lineNumber != INVALID_LINE_NUMBER) {
        methodBuilder.append(":")
        methodBuilder.append(location.lineNumber + 1)
      }
      methodBuilder.append(", ")
      methodBuilder.append(codeElement.simpleClassName)
      val methodName = methodBuilder.toString()
      append(methodName, textAttribute, methodName)
      val packageName = " (" + codeElement.packageName + ")"
      append(
        packageName,
        if (selected) REGULAR_ITALIC_ATTRIBUTES else GRAYED_ITALIC_ATTRIBUTES,
        packageName
      )
    }

    private fun renderNativeStackFrame(codeElement: CodeElement, selected: Boolean) {
      @Suppress("UnstableApiUsage")
      icon = iconManager.getPlatformIcon(PlatformIcons.Method)
      val location = codeElement.codeLocation

      val methodName = buildString {
        if (!location.className.isNullOrEmpty()) {
          append(location.className)
          append("::")
        }
        append(location.methodName)
        val params = location.methodParameters?.joinToString(",") { it } ?: ""
        append("($params) ")
      }
      append(methodName, REGULAR_ATTRIBUTES, methodName)

      val fileName = location.fileName ?: ""
      if (fileName.isNotEmpty()) {
        val sourceLocation = buildString {
          append(Paths.get(fileName).fileName.toString())
          if (location.lineNumber != INVALID_LINE_NUMBER) {
            append(":${(location.lineNumber + 1)}")
          }
        }
        append(sourceLocation, REGULAR_ATTRIBUTES, sourceLocation)
      }

      val nativeModuleName = location.nativeModuleName ?: "unknown"
      val moduleName = " " + Paths.get(nativeModuleName).fileName.toString()
      append(
        moduleName,
        if (selected) REGULAR_ITALIC_ATTRIBUTES else GRAYED_ITALIC_ATTRIBUTES,
        moduleName
      )
    }

    private fun renderThreadElement(threadElement: ThreadElement, selected: Boolean) {
      icon = AllIcons.Debugger.ThreadSuspended
      val text = threadElement.threadId.toString()
      append(text, if (selected) REGULAR_ATTRIBUTES else GRAY_ATTRIBUTES, text)
    }
  }
}
