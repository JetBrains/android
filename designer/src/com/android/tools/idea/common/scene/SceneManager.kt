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
package com.android.tools.idea.common.scene

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.common.model.ChangeType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.rendering.RenderUtils
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.Companion.getInstance
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.uibuilder.model.viewInfo
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A facility for creating and updating [Scene]s based on [NlModel]s.
 *
 * @param model the [NlModel] linked to this [SceneManager].
 * @param designSurface the [DesignSurface] where the model will be rendered.
 * @param sceneComponentProvider a [SceneComponentHierarchyProvider] that will generate the
 *   [SceneComponent]s from the given [NlComponent].
 */
abstract class SceneManager(
  val model: NlModel,
  protected open val designSurface: DesignSurface<*>,
  private val sceneComponentProvider: SceneComponentHierarchyProvider,
) : Disposable, ResourceChangeListener {
  init {
    Disposer.register(model, this)
  }

  val scene: Scene = Scene(this, designSurface)
  private val hitProvider: HitProvider = DefaultHitProvider()
  private val activationLock = ReentrantLock()
  @GuardedBy("myActivationLock") private var isActivated = false

  // This will be initialized when constructor calls updateSceneView().
  protected var sceneView: SceneView? = null
    set(value) {
      field?.let { Disposer.dispose(it) }
      field = value
    }

  /** Optional secondary scene view. Null by default, but could be set by subclasses. */
  protected var secondarySceneView: SceneView? = null
    set(value) {
      field?.let { Disposer.dispose(it) }
      field = value
    }

  /**
   * Update the [SceneView]s of this [SceneManager]. The [SceneView]s will be recreated if needed.
   */
  abstract fun updateSceneViews()

  /**
   * A list of not null scene views. The first element is always the primary scene view, and the
   * second element, if present, will be the secondary scene view.
   */
  val sceneViews: List<SceneView>
    get() {
      checkNotNull(sceneView) { "updateSceneViews was not called" }
      val builder = ImmutableList.builder<SceneView>().add(sceneView!!)
      secondarySceneView?.let { builder.add(it) }
      return builder.build()
    }

  /**
   * In the layout editor, Scene uses [AndroidDpCoordinate]s whereas rendering is done in (zoomed
   * and offset) [AndroidCoordinate]s. The scaling factor between them is the ratio of the screen
   * density to the standard density (160).
   */
  abstract val sceneScalingFactor: Float

  /**
   * Returns the actual android.view.View (or child class) object. This can be used to query the
   * object properties that are not in the XML.
   */
  val viewObject: Any?
    get() = scene.root?.nlComponent?.viewInfo?.viewObject

  override fun dispose() {
    deactivate(this)
  }

  /**
   * Update the [Scene] with the components in the current [NlModel]. This method needs to be called
   * in the dispatch thread. This includes marking the display list as dirty.
   */
  open fun update() {
    val components: List<NlComponent> = model.treeReader.components
    if (components.isEmpty()) {
      scene.removeAllComponents()
      scene.root = null
      return
    }
    val usedComponents: MutableSet<SceneComponent> = HashSet()
    val oldComponents: MutableSet<SceneComponent> = HashSet(scene.sceneComponents)

    val modelRootComponent = root
    if (modelRootComponent == null) {
      Logger.getInstance(SceneManager::class.java).warn("Unexpected null model root component")
      return
    }
    if (scene.root != null && modelRootComponent !== scene.root!!.nlComponent) {
      scene.removeAllComponents()
      scene.root = null
    }

    val hierarchy = sceneComponentProvider.createHierarchy(this, modelRootComponent)
    val rootSceneComponent: SceneComponent? =
      when {
        hierarchy.isEmpty() -> null
        hierarchy.size == 1 -> hierarchy.first()
        else -> {
          val minX = hierarchy.minOf { it.drawX }
          val minY = hierarchy.minOf { it.drawY }
          val maxX = hierarchy.maxOf { it.drawX + it.drawWidth }
          val maxY = hierarchy.maxOf { it.drawY + it.drawHeight }
          SceneComponent(
              scene,
              modelRootComponent,
              scene.sceneManager.getHitProvider(modelRootComponent),
            )
            .apply {
              hierarchy.forEach { addChild(it) }
              setPosition(minX, minY)
              setSize(maxX - minX, maxY - minY)
            }
        }
      }
    scene.root = rootSceneComponent
    if (rootSceneComponent != null) {
      updateFromComponent(rootSceneComponent, usedComponents)
    }
    oldComponents.removeAll(usedComponents)
    // The temporary component are not present in the NLModel so won't be added to the used
    // component array
    oldComponents.removeIf { it is TemporarySceneComponent }
    oldComponents.forEach { scene.removeComponent(it) }

    scene.needsRebuildList()
  }

  protected open val root: NlComponent?
    get() = model.treeReader.components.firstOrNull()?.root

  /**
   * Update the SceneComponent paired to the given [NlComponent] and its children.
   *
   * @param component the root SceneComponent to update
   * @param seenComponents Collector of components that were seen during [NlComponent] tree
   *   traversal.
   */
  protected fun updateFromComponent(
    component: SceneComponent,
    seenComponents: MutableSet<SceneComponent>,
  ) {
    seenComponents.add(component)
    sceneComponentProvider.syncFromNlComponent(component)
    component.children.forEach { updateFromComponent(it, seenComponents) }
  }

  /**
   * Request a new render of the model and wait for the new render to finish.
   *
   * It shouldn't be used when it is not relevant for the caller to wait for the render to finish,
   * in those cases [requestRender] should be used instead.
   */
  abstract suspend fun requestRenderAndWait()

  /**
   * Request a new render of the model. This request may not be processed immediately, but it could
   * be scheduled for later instead. It is responsibility of the subclasses to define the exact
   * behaviour of this method.
   *
   * See also [requestRenderAndWait].
   */
  abstract fun requestRender()

  abstract fun requestLayoutAsync(animate: Boolean): CompletableFuture<Void>

  abstract val sceneDecoratorFactory: SceneDecoratorFactory

  open fun getHitProvider(component: NlComponent): HitProvider {
    return hitProvider
  }

  /**
   * Notify this [SceneManager] that is active. It will be active by default.
   *
   * @param source caller used to keep track of the references to this model. See [.deactivate]
   * @returns true if the [SceneManager] was not active before and was activated.
   */
  open fun activate(source: Any): Boolean {
    activationLock.withLock {
      if (!isActivated) {
        model.let {
          val manager = getInstance(it.project)
          manager.addListener(this, it.facet, it.virtualFile, it.configuration)
        }
        isActivated = true
      }
    }
    // NlModel handles the double activation/deactivation itself.
    return model.activate(source)
  }

  /**
   * Notify this [SceneManager] that it's not active. This means it can stop watching for events
   * etc. It may be activated again in the future.
   *
   * @param source the source is used to keep track of the references that are using this model.
   *   Only when all the sources have called deactivate(Object), the model will be really
   *   deactivated.
   * @returns true if the [SceneManager] was active before and was deactivated.
   */
  open fun deactivate(source: Any): Boolean {
    activationLock.withLock {
      if (isActivated) {
        model.let {
          val manager = getInstance(it.project)
          manager.removeListener(this, it.facet, it.virtualFile, it.configuration)
        }
        isActivated = false
      }
    }
    // NlModel handles the double activation/deactivation itself.
    return model.deactivate(source)
  }

  // ---- Implements ResourceNotificationManager.ResourceChangeListener ----
  override fun resourcesChanged(reasons: ImmutableSet<ResourceNotificationManager.Reason>) {
    val shouldClearRenderCache =
      reasons.any {
        getNlModelChangeType(it).let { changeType ->
          changeType == ChangeType.BUILD || changeType == ChangeType.RESOURCE_CHANGED
        }
      }
    if (shouldClearRenderCache) RenderUtils.clearCache(ImmutableList.of(model.configuration))
    // TODO(b/365124075): add support for using a set of reasons and not only the last one.
    reasons.lastOrNull()?.let { model.notifyModified(getNlModelChangeType(it)) }
  }

  private fun getNlModelChangeType(reason: ResourceNotificationManager.Reason): ChangeType {
    return when (reason) {
      ResourceNotificationManager.Reason.RESOURCE_EDIT -> ChangeType.RESOURCE_EDIT
      ResourceNotificationManager.Reason.EDIT -> ChangeType.EDIT
      ResourceNotificationManager.Reason.CONFIGURATION_CHANGED -> ChangeType.CONFIGURATION_CHANGE
      ResourceNotificationManager.Reason.IMAGE_RESOURCE_CHANGED -> ChangeType.RESOURCE_CHANGED
      else -> ChangeType.BUILD
    }
  }
}
