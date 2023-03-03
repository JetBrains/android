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
package com.android.tools.idea.naveditor.scene

import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ScreenOrientation
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.model.AndroidLength
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.model.scaledAndroidLength
import com.android.tools.idea.common.scene.DefaultSceneManagerHierarchyProvider
import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.model.effectiveDestinationId
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.idPath
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.isSelfAction
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.model.supportsActions
import com.android.tools.idea.naveditor.scene.decorator.NavSceneDecoratorFactory
import com.android.tools.idea.naveditor.scene.hitproviders.NavRegularActionHitProvider
import com.android.tools.idea.naveditor.scene.hitproviders.NavActionSourceHitProvider
import com.android.tools.idea.naveditor.scene.hitproviders.NavDestinationHitProvider
import com.android.tools.idea.naveditor.scene.hitproviders.NavHorizontalActionHitProvider
import com.android.tools.idea.naveditor.scene.hitproviders.NavSelfActionHitProvider
import com.android.tools.idea.naveditor.scene.layout.ElkLayeredLayoutAlgorithm
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm
import com.android.tools.idea.naveditor.scene.layout.NewDestinationLayoutAlgorithm
import com.android.tools.idea.naveditor.scene.targets.NavScreenTargetProvider
import com.android.tools.idea.naveditor.scene.targets.NavigationTargetProvider
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.CompletableFuture
import kotlin.math.max

@NavCoordinate
private val SCREEN_LONG = JBUIScale.scale(256f)

@NavCoordinate
val SUBNAV_WIDTH = JBUIScale.scale(140)
@NavCoordinate
val SUBNAV_HEIGHT = JBUIScale.scale(38)

@SwingCoordinate
private val PAN_LIMIT = JBUIScale.scale(150)
@NavCoordinate
private val BOUNDING_BOX_PADDING = JBUIScale.scale(100)

private val ACTION_HEIGHT = ACTION_ARROW_PERPENDICULAR
private val ACTION_VERTICAL_PADDING = scaledAndroidLength(6f)
private val POP_ICON_VERTICAL_PADDING = scaledAndroidLength(10f)

private val ACTION_LINE_LENGTH = scaledAndroidLength(14f)
private val ACTION_WIDTH = ACTION_ARROW_PARALLEL + ACTION_LINE_LENGTH
private val ACTION_HORIZONTAL_PADDING = scaledAndroidLength(8f)

/**
 * [SceneManager] for the navigation editor.
 */
open class NavSceneManager(
  model: NlModel,
  surface: NavDesignSurface
) : SceneManager(model, surface, NavSceneComponentHierarchyProvider(), null) {

  private val layoutAlgorithms = listOf(
    NewDestinationLayoutAlgorithm(),
    ManualLayoutAlgorithm(model.module, this),
    ElkLayeredLayoutAlgorithm())

  private val savingLayoutAlgorithm = layoutAlgorithms.find { algorithm -> algorithm.canSave() }

  val isEmpty: Boolean
    get() = designSurface.currentNavigation.children.none { it.isDestination }

  init {
    createSceneView()
    updateHierarchy(getModel(), null)
    getModel().addListener(ModelChangeListener())
    designSurface.selectionModel.addListener(SelectionListener { _, _ -> scene.needsRebuildList() })
    designSurface.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent?) {
        update()
      }
    })
  }

  override fun getDesignSurface() = super.getDesignSurface() as NavDesignSurface

  override fun doCreateSceneView(): NavView = NavView(designSurface, this)

  override fun update() {
    val rootBounds: Rectangle? = scene.root?.fillDrawRect(0, null)

    super.update()

    scene.root?.let {
      it.updateTargets()
      layoutAll(it)
    }

    updateRootBounds(rootBounds)
  }

  private fun updateRootBounds(@NavCoordinate prevRootBounds: Rectangle?) {
    val root = scene.root ?: return

    @SwingCoordinate val extentSize = designSurface.extentSize
    @NavCoordinate val extentWidth = Coordinates.getAndroidDimension(designSurface, extentSize.width)
    @NavCoordinate val extentHeight = Coordinates.getAndroidDimension(designSurface, extentSize.height)

    @NavCoordinate val rootBounds: Rectangle

    if (isEmpty) {
      rootBounds = Rectangle(0, 0, extentWidth, extentHeight)
    }
    else {
      @NavCoordinate val panLimit = Coordinates.getAndroidDimension(designSurface, PAN_LIMIT)
      rootBounds = getBoundingBox(root)
      rootBounds.grow(max(0, extentWidth - panLimit), max(0, extentHeight - panLimit))
    }

    root.setPosition(rootBounds.x, rootBounds.y)
    root.setSize(rootBounds.width, rootBounds.height)
    scene.needsRebuildList()

    designSurface.focusedSceneView?.let {
      @SwingCoordinate val deltaX = Coordinates.getSwingDimension(it, root.drawX - (prevRootBounds?.x ?: 0))
      @SwingCoordinate val deltaY = Coordinates.getSwingDimension(it, root.drawY - (prevRootBounds?.y ?: 0))

      @SwingCoordinate val point = designSurface.scrollPosition
      designSurface.setScrollPosition(point.x - deltaX, point.y - deltaY)
    }
  }

  override fun getRoot() = designSurface.currentNavigation

  override fun getSceneScalingFactor() = 1f

  private fun findAndCreateExitActionComponents(component: NlComponent): List<SceneComponent> {
    return component.flatten()
      .filter { it.isAction && it.actionDestination?.parent == root }
      .map { c ->
        scene.getSceneComponent(c) ?: SceneComponent(scene, c, getHitProvider(c))
      }
      .toList()
  }

  private fun shouldCreateHierarchy(component: NlComponent) = when {
    component.isAction -> true
    component.isDestination -> (component == root || component.parent == root)
    else -> false
  }

  /**
   * Global actions are children of the root navigation in the NlComponent tree, but we want their scene components to be children of
   * the scene component of their destination. This method re-parents the scene components of the global actions.
   *
   *
   * TODO: in SceneManager.createHierarchy we try to reuse SceneComponents if possible. Moving SceneComponents in this way prevents that
   * from working.
   */
  private fun moveGlobalActions(root: SceneComponent) {
    val destinationMap = root.children.filter { it.nlComponent.isDestination }.associateBy { it.id }

    val rootNlComponent = root.nlComponent
    val globalActions = root.children.filter { it.nlComponent.isAction && it.nlComponent.parent == rootNlComponent }

    for (globalAction in globalActions) {
      val destination = globalAction.nlComponent.actionDestinationId
      val parent = destinationMap[destination]
      if (parent == null) {
        scene.removeComponent(globalAction)
      }
      else {
        parent.addChild(globalAction)
      }
    }
  }

  override fun createTemporaryComponent(component: NlComponent) = TemporarySceneComponent(scene, component)

  override fun requestRenderAsync(): CompletableFuture<Void> {
    val wasEmpty = scene.root == null || scene.root?.childCount == 0
    update()
    if (wasEmpty) {
      designSurface.zoomToFit()
    }

    return CompletableFuture.completedFuture(null)
  }

  private fun layoutAll(root: SceneComponent) {
    var destinations = root.children.filter { it.nlComponent.isDestination }.toMutableList()

    for (algorithm in layoutAlgorithms) {
      val remaining = algorithm.layout(destinations)
      destinations.removeAll(remaining)
      // If the algorithm that laid out the component can't persist the position, assume the position hasn't been persisted and
      // needs to be
      if (!algorithm.canSave()) {
        save(destinations)
      }
      if (remaining.isEmpty()) {
        break
      }
      destinations = remaining
    }

    val connectedActionSources = mutableSetOf<String>()
    val connectedActionDestinations = mutableSetOf<String>()

    getConnectedActions(root.nlComponent, connectedActionSources, connectedActionDestinations)

    for (component in root.children.filter { it.nlComponent.isDestination }) {
      val globalActions = mutableListOf<SceneComponent?>()
      val exitActions = mutableListOf<SceneComponent?>()

      for (child in component.children) {
        when (child.nlComponent.getActionType(getRoot())) {
          ActionType.GLOBAL -> globalActions.add(child)
          ActionType.EXIT -> exitActions.add(child)
          else -> {
          }
        }
      }

      val id = component.nlComponent.id

      layoutGlobalActions(component, globalActions, connectedActionDestinations.contains(id))
      layoutExitActions(component, exitActions, connectedActionSources.contains(id))
    }
  }

  fun save(components: List<SceneComponent>) {
    for (component in components) {
      savingLayoutAlgorithm?.save(component)
    }
  }

  fun getPositionData(component: SceneComponent): Any? = savingLayoutAlgorithm?.getPositionData(component)

  fun restorePositionData(path: List<String>, positionData: Any) {
    savingLayoutAlgorithm?.restorePositionData(path, positionData)
  }

  private fun isHorizontalAction(component: NlComponent): Boolean {
    val actionType = component.getActionType(root)
    return actionType == ActionType.GLOBAL || actionType == ActionType.EXIT
  }

  override fun requestLayoutAsync(animate: Boolean): CompletableFuture<Void> {
    var bounds: Rectangle? = scene.root?.fillDrawRect(0, null)

    updateRootBounds(bounds)

    return CompletableFuture.completedFuture(null)
  }

  override fun layout(animate: Boolean) {
    requestLayoutAsync(animate)
  }

  override fun getSceneDecoratorFactory() = NavSceneDecoratorFactory

  override fun getDefaultProperties() = mapOf<Any, Map<ResourceReference, ResourceValue>>()

  override fun getDefaultStyles() = mapOf<Any, ResourceReference>()

  private inner class ModelChangeListener : ModelListener {
    override fun modelDerivedDataChanged(model: NlModel) {
    }

    override fun modelChanged(model: NlModel) {
      updateHierarchy(model, model)
      designSurface.refreshRoot()
      requestRenderAsync()
      model.notifyListenersModelDerivedDataChanged()
    }

    override fun modelChangedOnLayout(model: NlModel, animate: Boolean) {
      val previous = scene.isAnimated
      UIUtil.invokeLaterIfNeeded {
        scene.isAnimated = animate
        update()
        scene.isAnimated = previous
      }
    }
  }

  public override fun getHitProvider(component: NlComponent): HitProvider {
    return when {
      component.supportsActions -> NavActionSourceHitProvider
      component.isAction -> getActionHitProvider(component)
      else -> NavDestinationHitProvider
    }
  }

  private fun getActionHitProvider(component: NlComponent): HitProvider {
    return when (component.getActionType(root)) {
      ActionType.GLOBAL, ActionType.EXIT -> NavHorizontalActionHitProvider
      ActionType.SELF -> NavSelfActionHitProvider
      else -> NavRegularActionHitProvider
    }
  }

  fun performUndoablePositionAction(component: NlComponent) {
    val sceneComponent = scene.getSceneComponent(component) ?: return
    val positionData = getPositionData(sceneComponent)
    val path = component.idPath

    UndoManager.getInstance(designSurface.project).undoableActionPerformed(
      object : BasicUndoableAction(model.file.virtualFile) {
        override fun undo() {
          if (positionData == null) {
            return
          }
          restorePositionData(path.mapNotNull { it }, positionData)
        }

        override fun redo() {
        }
      })
  }

  /**
   * Regular actions are children of a destination in the NlComponent tree, but we want their scene components to be children of
   * the root. This method re-parents the scene components of the regular actions.
   *
   *
   * TODO: decide if this is also what we should do for other action types, and if so restore clips for components (remove custom
   * NavScreenDecorator#buildListChildren).
   */
  private fun moveRegularActions(root: SceneComponent) {
    for (destination in root.children.filter { it.nlComponent.isDestination }) {
      for (action in destination.children.filter { it.nlComponent.getActionType(root.nlComponent) == ActionType.REGULAR }) {
        action.removeFromParent()
        root.addChild(action)
      }
    }
  }

  /**
   * Builds up a list of ids of sources and destinations for all actions
   * whose source and destination are currently visible
   * These are used to layout the global and exit actions properly
   */
  private fun getConnectedActions(root: NlComponent,
                                  connectedActionSources: MutableSet<String>,
                                  connectedActionDestinations: MutableSet<String>) {
    val children = root.children.mapNotNull { it.id }

    for (component in root.children.filter { it.isDestination }) {
      // TODO: Handle duplicate ids
      for (action in component.flatten().filter { it.isAction }) {
        val destinationId = action.effectiveDestinationId ?: continue
        if (children.contains(destinationId)) {
          component.id?.let { connectedActionSources.add(it) }
          if (!action.isSelfAction) {
            connectedActionDestinations.add(destinationId)
          }
        }
      }
    }
  }

  private fun layoutGlobalActions(destination: SceneComponent,
                                  globalActions: MutableList<SceneComponent?>,
                                  skip: Boolean) {
    layoutActions(destination, globalActions, skip,
                  (AndroidLength(destination.drawX.toFloat()) - ACTION_WIDTH - ACTION_HORIZONTAL_PADDING).toInt())
  }

  private fun layoutExitActions(source: SceneComponent, exitActions: MutableList<SceneComponent?>, skip: Boolean) {
    layoutActions(source, exitActions, skip, source.drawX + source.drawWidth + ACTION_HORIZONTAL_PADDING.toInt())
  }

  private fun layoutActions(component: SceneComponent, actions: MutableList<SceneComponent?>, skip: Boolean, @NavCoordinate x: Int) {
    var count = actions.size

    if (count == 0) {
      return
    }

    var popIconCount = 0

    for (i in 0 until (count + 1) / 2) {
      if (actions[i]?.nlComponent?.popUpTo != null) {
        popIconCount++
      }
    }

    if (skip) {
      // Insert a null element to indicate that we need space for regular actions
      actions.add((count + 1) / 2, null)
      count++
    }

    @NavCoordinate var y = (component.drawY + component.drawHeight / 2
                            - ACTION_HEIGHT.toInt() / 2 - count / 2 * (ACTION_HEIGHT + ACTION_VERTICAL_PADDING).toInt()
                            - popIconCount * POP_ICON_VERTICAL_PADDING.toInt())

    for (action in actions) {
      if (action != null) {
        if (action.nlComponent.popUpTo != null) {
          y += POP_ICON_VERTICAL_PADDING.toInt()
        }

        action.setPosition(x, y)
      }
      y += (ACTION_HEIGHT + ACTION_VERTICAL_PADDING).toInt()
    }
  }

  private class NavSceneComponentHierarchyProvider: DefaultSceneManagerHierarchyProvider() {
    override fun createHierarchy(manager: SceneManager, component: NlComponent): List<SceneComponent> {
      val navSceneManager = manager as NavSceneManager

      if (!navSceneManager.shouldCreateHierarchy(component)) {
        return listOf()
      }

      val hierarchy = super.createHierarchy(manager, component)

      if (component == navSceneManager.root) {
        for (child in hierarchy) {
          navSceneManager.moveGlobalActions(child)
          navSceneManager.moveRegularActions(child)
        }
      }
      else if (component.isNavigation) {
        return hierarchy.plus(navSceneManager.findAndCreateExitActionComponents(component))
      }

      return hierarchy
    }

    override fun syncFromNlComponent(sceneComponent: SceneComponent) {
      super.syncFromNlComponent(sceneComponent)

      val nlComponent = sceneComponent.nlComponent

      if ((sceneComponent.scene.sceneManager as NavSceneManager).isHorizontalAction(nlComponent)) {
        sceneComponent.setSize(ACTION_WIDTH.toInt(), ACTION_HEIGHT.toInt())
        return
      }

      val designSurface = sceneComponent.scene.designSurface as NavDesignSurface
      val type = nlComponent.destinationType
      if (type != null) {
        sceneComponent.setTargetProvider(if (sceneComponent.nlComponent == designSurface.currentNavigation)
                                           NavigationTargetProvider
                                         else
                                           NavScreenTargetProvider)
        sceneComponent.updateTargets()

        if (type == NavigationSchema.DestinationType.NAVIGATION) {
          if (sceneComponent.nlComponent == designSurface.currentNavigation) {
            // done in post
            sceneComponent.setSize(-1, -1)
          }
          else {
            sceneComponent.setSize(SUBNAV_WIDTH, SUBNAV_HEIGHT)
          }
        }
        else {
          val state = nlComponent.model.configuration.deviceState!!
          val screen = state.hardware.screen
          @NavCoordinate var x = SCREEN_LONG
          @NavCoordinate var y = SCREEN_LONG
          val ratio = screen.xDimension / screen.yDimension.toFloat()
          if (ratio > 1) {
            y /= ratio
          }
          else {
            x *= ratio
          }
          if (ratio < 1.1 && ratio > 0.9) {
            // If it's approximately square make it smaller, otherwise it takes up too much space.
            x /= 2
            y /= 2
          }
          if (state.orientation == ScreenOrientation.LANDSCAPE == ratio < 1) {
            val tmp = x

            x = y
            y = tmp
          }
          sceneComponent.setSize(x.toInt(), y.toInt())
        }
      }
    }
  }

  override fun activate(source: Any): Boolean = super.activate(source).also {
    if (it) {
      updateHierarchy(model, model)
      requestRenderAsync()
    }
  }
}

// TODO: this should be moved somewhere model-specific, since it is relevant even absent a Scene
fun updateHierarchy(model: NlModel, newModel: NlModel?) {
  var roots: List<NlModel.TagSnapshotTreeNode> = listOf()
  var newRoot = AndroidPsiUtils.getRootTagSafely(model.file)

  newModel?.let {
    newRoot = AndroidPsiUtils.getRootTagSafely(it.file)
    roots = buildTree(it.components.map { c -> c.tagDeprecated })
  }

  newRoot?.let {
    // TODO error handling (if newRoot is null)
    model.syncWithPsi(it, roots)
  }
}

private fun buildTree(roots: List<XmlTag>): List<NlModel.TagSnapshotTreeNode> {
  return roots.map {
    object : NlModel.TagSnapshotTreeNode {
      override fun getTagSnapshot(): TagSnapshot {
        return TagSnapshot.createTagSnapshot(it, null)
      }

      override fun getChildren(): List<NlModel.TagSnapshotTreeNode> {
        return buildTree(it.subTags.toList())
      }
    }
  }
}

@NavCoordinate
fun getBoundingBox(root: SceneComponent): Rectangle {
  return getBoundingBox(root.children)
}

@NavCoordinate
fun getBoundingBox(components: List<SceneComponent>): Rectangle {
  @NavCoordinate val boundingBox = Rectangle(0, 0, -1, -1)
  @NavCoordinate val childRect = Rectangle()

  for (child in components.filter { c -> c.nlComponent.isDestination }) {
    child.fillDrawRect(0, childRect)
    if (boundingBox.width < 0) {
      boundingBox.bounds = childRect
    }
    else {
      boundingBox.add(childRect)
    }
  }

  boundingBox.grow(BOUNDING_BOX_PADDING, BOUNDING_BOX_PADDING)

  return boundingBox
}

