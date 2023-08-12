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
package com.android.tools.adtui.model.trackgroup

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.BoxSelectionModel
import com.android.tools.adtui.model.DragAndDropListModel
import java.util.concurrent.atomic.AtomicInteger

/**
 * Data model for TrackGroup, a collapsible UI component that contains a list of Tracks.
 */
class TrackGroupModel private constructor(builder: Builder) : DragAndDropListModel<TrackModel<*, *>>() {
  val title: String = builder.title
  val titleHelpText: String? = builder.titleHelpText
  val titleHelpLinkText: String? = builder.titleHelpLinkText
  val titleHelpLinkUrl: String? = builder.titleHelpLinkUrl
  val isCollapsedInitially: Boolean = builder.collapsedInitially
  val hideHeader: Boolean = builder.hideHeader
  private val selector: Selector? = builder.selector
  val boxSelectionModel: BoxSelectionModel? = builder.boxSelectionModel
  val allDisplayToggles: List<String> = builder.toggles.keys.toList()
  private val displayToggleChangeListeners: Map<String, Runnable> = builder.toggles.mapValues { (_, rec) -> rec.second }
  var activeDisplayToggles: Set<String> = builder.toggles.filterValues { it.first }.keys.toSet()

  private val observer = AspectObserver()
  val isTrackSelectable: Boolean // whether the tracks inside this track group are selectable.
    get() = selector != null
  private val actionListenerList = mutableListOf<TrackGroupActionListener>()
  val actionListeners: List<TrackGroupActionListener> get() = actionListenerList

  // track models paired with functions that dynamically decide if they should display based on active toggles
  private val trackModelConfigs = mutableListOf<Pair<TrackModel<*, *>, (Set<String>) -> Boolean>>()

  /**
   * Add a [TrackModel] to the group.
   *
   * @param builder    to build the [TrackModel] to add
   * @param shouldPresent whether the track should be presented given the active tags
   * @param <M>        data model type
   * @param <R>        renderer enum type
   */
  @JvmOverloads
  fun <M, R : Enum<*>> addTrackModel(builder: TrackModel.Builder<M, R>,
                                     shouldPresent: (Set<String>) -> Boolean = { true }) {
    // add() is disabled in DragAndDropListModel to support dynamically reordering elements. Use insertOrderedElement() instead.
    val trackModel = builder.setId(TRACK_ID_GENERATOR.getAndIncrement()).build()
    trackModelConfigs += trackModel to shouldPresent

    if (shouldPresent(activeDisplayToggles)) {
      insertOrderedElement(trackModel)
    }

    // Listen to track's collapse state change.
    trackModel.aspectModel.addDependency(observer).onChange(TrackModel.Aspect.COLLAPSE_CHANGE) {
      val index = indexOf(trackModel)
      if (index != -1) {
        fireContentsChanged(this, index, index)
      }
    }
  }

  fun setDisplayTag(tag: String, active: Boolean) = when {
    tag !in allDisplayToggles -> throw IllegalArgumentException("Unrecognized tag $tag")
    active != tag in activeDisplayToggles -> {
      activeDisplayToggles = if (active) (activeDisplayToggles + tag) else (activeDisplayToggles - tag)
      clearOrderedElements()
      trackModelConfigs.forEach { (track, shouldPresent) ->
        if (shouldPresent(activeDisplayToggles)) {
          insertOrderedElement(track)
        }
      }
      fireContentsChanged(this, 0, size)
      displayToggleChangeListeners[tag]!!.run()
    }
    else -> { /* no state change */ }
  }

  /**
   * Add [TrackGroupActionListener] to be fired when a track group action, e.g. moving up, is performed.
   */
  fun addActionListener(actionListener: TrackGroupActionListener) = actionListenerList.add(actionListener)

  fun <M: Any> select(models: Set<TrackModel<M, *>>): Iterable<Map.Entry<Any, Set<M>>> = selector!!.apply(models)

  class Builder {
    internal var title = ""
    internal var titleHelpText: String? = null
    internal var titleHelpLinkText: String? = null
    internal var titleHelpLinkUrl: String? = null
    internal var collapsedInitially = false
    internal var hideHeader = false
    internal var selector: Selector? = null
    internal var boxSelectionModel: BoxSelectionModel? = null
    internal val toggles = mutableMapOf<String, Pair<Boolean, Runnable>>()

    /**
     * @param title string to be displayed in the header
     */
    fun setTitle(title: String) = this.also { this.title = title }

    /**
     * @param titleHelpText string to be displayed as tooltip next to the header. Supports HTML tags.
     */
    fun setTitleHelpText(titleHelpText: String) = this.also { this.titleHelpText = titleHelpText }

    /**
     * A link to be displayed as tooltip next after the help text.
     *
     * @param titleHelpLinkText link text
     * @param titleHelpLinkUrl  link URL
     */
    fun setTitleHelpLink(titleHelpLinkText: String, titleHelpLinkUrl: String) = this.also {
      this.titleHelpLinkText = titleHelpLinkText
      this.titleHelpLinkUrl = titleHelpLinkUrl
    }

    fun setCollapsedInitially(collapsedInitially: Boolean) = this.also { this.collapsedInitially = collapsedInitially }
    fun setHideHeader(hideHeader: Boolean) = this.also { this.hideHeader = hideHeader }

    /**
     * @param selector how this model handles selection, or null if it is not supposed to be selectable.
     */
    fun setSelector(selector: Selector?) = this.also { this.selector = selector }

    fun setBoxSelectionModel(rangeSelectionModel: BoxSelectionModel?) = this.also { boxSelectionModel = rangeSelectionModel }

    /**
     * Add a display toggle that can be dynamically turned on or off, affecting which tracks are displayed
     */
    @JvmOverloads
    fun addDisplayToggle(title: String, isOnByDefault: Boolean, onChanged: Runnable = Runnable {}) =
      this.also { toggles += title to (isOnByDefault to onChanged) }
    fun build() = TrackGroupModel(this)
  }

  companion object {
    // Use this to generate unique (within this group) IDs for [TrackModel]s in this group.
    private val TRACK_ID_GENERATOR = AtomicInteger()

    @JvmStatic
    fun newBuilder() = Builder()

    @JvmStatic
    fun makeBatchSelector(id: Any) = object : Selector {
      override fun <M: Any> apply(selections: Set<TrackModel<M, *>>) =
        listOf(entry(id, selections.mapTo(mutableSetOf()) { it.dataModel }))
    }

    fun makeItemSelector() = object : Selector {
      override fun <M: Any> apply(selections: Set<TrackModel<M, *>>) = selections.map { entry(it.id, setOf(it.dataModel)) }
    }

    private fun <K, V> entry(k: K, v: V) = object : Map.Entry<K, V> {
      override val key get() = k
      override val value get() = v
    }
  }

  /**
   * Function that takes selected tracks and returns pairs of keys and selections.
   */
  interface Selector {
    fun <M: Any> apply(selections: Set<TrackModel<M, *>>): Iterable<Map.Entry<Any, Set<M>>>
  }
}