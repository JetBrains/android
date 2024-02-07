/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.actions.PasteWithIdOptionAction.Companion.PASTE_WITH_NEW_IDS_KEY
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.addComponentsAndSelectedIfCreated
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.DeleteProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException

abstract class DesignSurfaceActionHandler
protected constructor(
  protected val mySurface: DesignSurface<*>,
  private val myCopyPasteManager: CopyPasteManager,
) : DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  constructor(surface: DesignSurface<*>) : this(surface, CopyPasteManager.getInstance())

  protected abstract val flavor: DataFlavor

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun performCopy(dataContext: DataContext) {
    if (!mySurface.selectionModel.isEmpty) {
      myCopyPasteManager.setContents(mySurface.selectionAsTransferable)
    }
  }

  override fun isCopyEnabled(dataContext: DataContext): Boolean {
    return hasNonEmptySelection()
  }

  override fun isCopyVisible(dataContext: DataContext): Boolean {
    return true
  }

  override fun performCut(dataContext: DataContext) {
    if (!mySurface.selectionModel.isEmpty) {
      val transferable = mySurface.selectionAsTransferable
      try {
        val transferItem = transferable.getTransferData(flavor) as DnDTransferItem
        transferItem.setIsCut()
        myCopyPasteManager.setContents(transferable)
      } catch (e: UnsupportedFlavorException) {
        performCopy(dataContext) // Fallback to simple copy/delete
      }
      deleteElement(dataContext)
    }
  }

  override fun isCutEnabled(dataContext: DataContext): Boolean {
    return hasNonEmptySelection()
  }

  override fun isCutVisible(dataContext: DataContext): Boolean {
    return true
  }

  override fun deleteElement(dataContext: DataContext) {
    val model = mySurface.model ?: return
    val selectionModel = mySurface.selectionModel
    model.treeWriter.delete(selectionModel.selection)
    selectionModel.clear()
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean {
    return hasNonEmptySelection()
  }

  @get:VisibleForTesting abstract val pasteTarget: NlComponent?

  @VisibleForTesting
  abstract fun canHandleChildren(component: NlComponent, pasted: List<NlComponent>): Boolean

  override fun performPaste(dataContext: DataContext) {
    val generateNewIds = PASTE_WITH_NEW_IDS_KEY.getData(dataContext) ?: true
    pasteOperation(checkOnly = false, generateNewIds)
  }

  /** returns true if the action should be shown. */
  override fun isPastePossible(dataContext: DataContext): Boolean {
    return pasteTarget != null && clipboardData != null
  }

  /**
   * Called by [com.intellij.ide.actions.PasteAction] to check if pasteOperation() should be called
   */
  override fun isPasteEnabled(dataContext: DataContext): Boolean {
    return pasteOperation(true, /* check only */ false /* generate new ides */)
  }

  private fun hasNonEmptySelection(): Boolean {
    return !mySurface.selectionModel.isEmpty
  }

  private fun pasteOperation(checkOnly: Boolean, generateNewIds: Boolean): Boolean {
    var receiver = pasteTarget ?: return false
    val model = receiver.model

    val transferItem = clipboardData ?: return false

    val dragType = if (transferItem.isCut) DragType.MOVE else DragType.PASTE
    val insertType =
      model.treeWriter.determineInsertType(dragType, transferItem, checkOnly, generateNewIds)

    val pasted = model.treeWriter.createComponents(transferItem, insertType)

    var before: NlComponent? = null
    if (canHandleChildren(receiver, pasted)) {
      before = receiver.getChild(0)
    } else {
      while (!canHandleChildren(receiver, pasted)) {
        before = receiver.nextSibling
        receiver = receiver.parent ?: return false
      }
    }

    if (!model.treeWriter.canAddComponents(pasted, receiver, before, checkOnly)) {
      return false
    }
    if (checkOnly) {
      return true
    }
    transferItem.consumeCut()
    model.treeWriter.addComponentsAndSelectedIfCreated(
      pasted,
      receiver,
      before,
      insertType,
      mySurface.selectionModel,
    )
    if (insertType.isPasteOperation()) {
      mySurface.selectionModel.setSelection(pasted)
    }
    return true
  }

  private val clipboardData: DnDTransferItem?
    get() {
      val contents = myCopyPasteManager.contents
      return if (contents != null) DnDTransferItem.getTransferItem(contents, false) else null
    }
}
