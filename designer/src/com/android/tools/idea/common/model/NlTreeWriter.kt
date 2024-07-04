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
package com.android.tools.idea.common.model

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * Helper function to wrapped [NlTreeWriter.addComponents] to select the added [NlComponent]s when
 * [insertType] is [InsertType.CREATE]. This happens when adding a new created [NlComponent]s into
 * [NlModel] but not moving the existing [NlComponent]s.
 *
 * We use [NlTreeWriter.addComponents] to create and moving [NlComponent]s, so we need to check the
 * [insertType].
 *
 * Note: Do not inline this function into [NlModel]. [NlModel] shouldn't depend on [SelectionModel].
 */
@JvmOverloads
fun NlTreeWriter.addComponentsAndSelectedIfCreated(
  toAdd: List<NlComponent>,
  receiver: NlComponent,
  before: NlComponent?,
  insertType: InsertType,
  selectionModel: SelectionModel,
  attributeUpdatingTask: Runnable? = null,
) {
  addComponents(
    toAdd,
    receiver,
    before,
    insertType,
    {
      if (insertType == InsertType.CREATE) {
        selectionModel.setSelection(toAdd)
      }
    },
    attributeUpdatingTask,
  )
}

/**
 * Helper function to wrapped [NlTreeWriter.addComponents] to add and selected the added
 * [NlComponent]s. This is used to add a new created [NlComponent]s into [NlModel] but not moving
 * the existing [NlComponent]s.
 *
 * Note: Do not inline this function into [NlModel]. [NlModel] shouldn't depend on [SelectionModel].
 */
@JvmOverloads
fun NlTreeWriter.createAndSelectComponents(
  toAdd: List<NlComponent>,
  receiver: NlComponent,
  before: NlComponent?,
  selectionModel: SelectionModel,
  attributeUpdatingTask: Runnable? = null,
) {
  addComponents(
    toAdd,
    receiver,
    before,
    InsertType.CREATE,
    { selectionModel.setSelection(toAdd) },
    attributeUpdatingTask,
  )
}

/**
 * Changes tree structure of [NlComponent] by adding and deleting components.
 *
 * @param facet the [AndroidFacet]
 * @param file the corresponding [XmlFile]
 * @param notifyModified notifies about changes made to the tree
 * @param createComponent creates new instance of [NlComponent] for given [XmlTag]
 */
class NlTreeWriter(
  private val facet: AndroidFacet,
  private val file: () -> XmlFile,
  private val notifyModified: (ChangeType) -> Unit,
  private val createComponent: (XmlTag) -> NlComponent,
) {

  val id: Long = System.nanoTime() xor file().name.hashCode().toLong()

  val pendingIds: MutableSet<String> = Sets.newHashSet()

  /** Looks up the existing set of id's reachable from this model. */
  val ids: Set<String>
    get() {
      val resources: ResourceRepository = StudioResourceRepositoryManager.getAppResources(facet)
      var ids: Set<String> =
        HashSet(resources.getResources(ResourceNamespace.TODO(), ResourceType.ID).keySet())
      val pendingIds = pendingIds
      if (pendingIds.isNotEmpty()) {
        val all: MutableSet<String> = HashSet(pendingIds.size + ids.size)
        all.addAll(ids)
        all.addAll(pendingIds)
        ids = all
      }
      return ids
    }

  fun determineInsertType(
    dragType: DragType,
    item: DnDTransferItem?,
    asPreview: Boolean,
    generateIds: Boolean,
  ): InsertType {
    if (item != null && item.isFromPalette) {
      return if (asPreview) InsertType.CREATE_PREVIEW else InsertType.CREATE
    }
    return when (dragType) {
      DragType.CREATE -> if (asPreview) InsertType.CREATE_PREVIEW else InsertType.CREATE
      DragType.MOVE -> if (item != null && id != item.modelId) InsertType.COPY else InsertType.MOVE
      DragType.COPY -> InsertType.COPY
      else -> if (generateIds) InsertType.PASTE_GENERATE_NEW_IDS else InsertType.PASTE
    }
  }

  /** Add tags component to the specified receiver before the given sibling. */
  fun addTags(
    added: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
  ) {
    NlWriteCommandActionUtil.run(added, generateAddComponentsDescription(added, insertType)) {
      for (component in added) {
        component.addTags(receiver, before, insertType)
      }
    }

    notifyModified(ChangeType.ADD_COMPONENTS)
  }

  /** Returns true if the specified components can be added to the specified receiver. */
  @JvmOverloads
  fun canAddComponents(
    toAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    ignoreMissingDependencies: Boolean = false,
  ): Boolean {
    if (before != null && before.parent !== receiver) {
      return false
    }
    if (toAdd.isEmpty()) {
      return false
    }
    if (toAdd.stream().anyMatch { c: NlComponent -> !c.canAddTo(receiver) }) {
      return false
    }

    // If the receiver is a (possibly indirect) child of any of the dragged components, then reject
    // the operation
    if (NlComponentUtil.isDescendant(receiver, toAdd)) {
      return false
    }

    return ignoreMissingDependencies || checkIfUserWantsToAddDependencies(toAdd)
  }

  /**
   * Adds components to the specified receiver before the given sibling. If insertType is a move the
   * components specified should be components from this model. The callback function
   * {@param #onComponentAdded} gives a chance to do additional task when components are added.
   */
  fun addComponents(
    toAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
    onComponentAdded: Runnable?,
  ) {
    addComponents(toAdd, receiver, before, insertType, onComponentAdded, null)
  }

  /**
   * Adds components to the specified receiver before the given sibling. If insertType is a move the
   * components specified should be components from this model. The callback function
   * [onComponentAdded] gives a chance to do additional task when components are added.
   */
  @JvmOverloads
  fun addComponents(
    componentToAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
    onComponentAdded: Runnable?,
    attributeUpdatingTask: Runnable?,
    groupId: String? = null,
  ) {
    // Fix for b/124381110
    // The components may be added by addComponentInWriteCommand after this method returns.
    // Make a copy of the components such that the caller can change the list without causing
    // problems.
    val toAdd = ImmutableList.copyOf(componentToAdd)

    // Note: we don't really need to check for dependencies if all we do is moving existing
    // components.
    if (!canAddComponents(toAdd, receiver, before, insertType == InsertType.MOVE)) {
      return
    }

    val callback = Runnable {
      addComponentInWriteCommand(
        toAdd,
        receiver,
        before,
        insertType,
        onComponentAdded,
        attributeUpdatingTask,
        groupId,
      )
    }
    if (insertType == InsertType.MOVE) {
      // The components are just moved, so there are no new dependencies.
      callback.run()
      return
    }

    ApplicationManager.getApplication().invokeLater {
      NlDependencyManager.getInstance().addDependencies(toAdd, facet, false, callback)
    }
  }

  /**
   * Creates a new component of the given type. It will optionally insert it as a child of the given
   * parent (and optionally right before the given sibling or null to append at the end.)
   *
   * Note: This operation can only be called when the caller is already holding a write lock. This
   * will be the case from [ViewHandler] callbacks such as [ViewHandler.onCreate] and
   * [DragHandler.commit].
   *
   * Note: The caller is responsible for calling [.notifyModified] if the creation completes
   * successfully.
   *
   * @param tag The XmlTag for the component.
   * @param parent The parent to add this component to.
   * @param before The sibling to insert immediately before, or null to append
   * @param insertType The reason for this creation.
   */
  fun createComponent(
    tag: XmlTag,
    parent: NlComponent?,
    before: NlComponent?,
    insertType: InsertType,
  ): NlComponent? {
    var addedTag = tag
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      val parentTag = parent.tagDeprecated
      addedTag =
        WriteAction.compute<XmlTag, RuntimeException> {
          if (before != null) {
            return@compute parentTag.addBefore(tag, before.tagDeprecated) as XmlTag
          }
          parentTag.addSubTag(tag, false)
        }
    }

    val child = createComponent(addedTag)

    parent?.addChild(child, before)
    if (child.postCreate(insertType)) {
      return child
    }
    return null
  }

  fun createComponents(item: DnDTransferItem, insertType: InsertType): List<NlComponent> {
    val components: MutableList<NlComponent> = ArrayList(item.components.size)
    for (dndComponent in item.components) {
      val tag = XmlTagUtil.createTag(facet.module.project, dndComponent.representation)
      val component =
        createComponent(tag, null, null, insertType)
          ?: // User may have cancelled
          return emptyList()
      component.postCreateFromTransferrable(dndComponent)
      components.add(component)
    }
    return components
  }

  fun delete(components: Collection<NlComponent?>) {
    // Group by parent and ask each one to participate
    WriteCommandAction.runWriteCommandAction(
      facet.module.project,
      "Delete Component",
      null,
      { handleDeletion(components) },
      file(),
    )
    notifyModified(ChangeType.DELETE)
  }

  private fun checkIfUserWantsToAddDependencies(toAdd: List<NlComponent>): Boolean {
    // May bring up a dialog such that the user can confirm the addition of the new dependencies:
    return NlDependencyManager.getInstance().checkIfUserWantsToAddDependencies(toAdd, facet)
  }

  private fun addComponentInWriteCommand(
    toAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
    onComponentAdded: Runnable?,
    attributeUpdatingTask: Runnable?,
    groupId: String?,
  ) {
    DumbService.getInstance(facet.module.project).runWhenSmart {
      NlWriteCommandActionUtil.run(
        toAdd,
        generateAddComponentsDescription(toAdd, insertType),
        groupId,
      ) {
        // Update the attribute before adding components, if need.
        attributeUpdatingTask?.run()
        handleAddition(toAdd, receiver, before, insertType)
      }
      notifyModified(ChangeType.ADD_COMPONENTS)
      onComponentAdded?.run()
    }
  }

  private fun handleAddition(
    added: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
  ) {
    for (component in added) {
      component.moveTo(receiver, before, insertType, ids)
    }
  }

  private fun handleDeletion(components: Collection<NlComponent?>) {
    // Segment the deleted components into lists of siblings
    val siblingLists = NlComponentUtil.groupSiblings(components)

    // Notify parent components about children getting deleted
    for (parent in siblingLists.keySet()) {
      if (parent == null) {
        continue
      }

      val children = siblingLists[parent]

      if (!parent.mixin!!.maybeHandleDeletion(children)) {
        for (component in children) {
          val p = component.parent
          p?.removeChild(component)

          val tag = component.tagDeprecated
          if (tag.isValid) {
            val parentTag = tag.parent
            tag.delete()
            if (parentTag is XmlTag) {
              parentTag.collapseIfEmpty()
            }
          }
        }
      }
    }
  }

  private fun generateAddComponentsDescription(
    toAdd: List<NlComponent>,
    insertType: InsertType,
  ): String {
    val dragType = insertType.dragType
    var componentType = ""
    if (toAdd.size == 1) {
      val tagName = toAdd[0].tagName
      componentType = tagName.substring(tagName.lastIndexOf('.') + 1)
    }
    return dragType.getDescription(componentType)
  }
}
