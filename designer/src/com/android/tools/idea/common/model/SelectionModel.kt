/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Interface that provides the functionality to support a secondary selection in the design surface.
 */
interface SecondarySelectionModel {
  /**
   * Returns the secondary selection. Users of this api should check the type and treat it as null
   * if the type is not known.
   */
  val secondarySelection: Any?

  /** Clears the secondary selection. */
  fun clearSecondary()

  /**
   * Set the secondary selection. Secondary selections must be associated with a NlComponent which
   * is considered to be selected
   *
   * @param component the parent component of the secondary selection
   * @param secondary the secondary selection the object can be of any type but should implement
   *   equals
   */
  fun setSecondarySelection(component: NlComponent?, secondary: Any?)

  /** Returns true if the given [secondary] is currently selected. */
  fun isSecondarySelected(secondary: Any?): Boolean = secondarySelection == secondary
}

/**
 * List of components that are selected for highlighting only. It is used for references which are
 * used by constriant helpers in ConstraintLayout.
 */
interface HighlightSelectionModel {

  /** Returns true if the given component is Highlighted. False otherwise. */
  fun isHighlighted(component: NlComponent): Boolean

  /**
   * Set list of components to be highlighted. Highlighted components will behave like unselected,
   * normal components except they will be drawn highlighted.
   *
   * If a component is both selected and highlight-selected, normal selection behaviour will take
   * higher priority.
   *
   * It is used for references which are used by constriant helpers in ConstraintLayout.
   *
   * When [SelectionModel.setSelection] is called, highlighted selection must be cleared.
   *
   * @param highlighted - components selected for highlighting
   * @param selected - normally selected components. It can bey empty.
   */
  fun setHighlightSelection(highlighted: List<NlComponent>, selected: List<NlComponent>)
}

/** Represents a selection of [NlComponent]s. */
interface SelectionModel : SecondarySelectionModel, HighlightSelectionModel {
  /** The current list of selected [NlComponent]s. */
  val selection: List<NlComponent>

  /** The primary selection or null if there is no primary selection. */
  val primary: NlComponent?

  val isEmpty: Boolean
    get() = selection.isEmpty()

  /** Adds a new [SelectionListener] to the model. */
  fun addListener(selectionListener: SelectionListener)

  /** Removes a [SelectionListener] to the model. */
  fun removeListener(selectionListener: SelectionListener)

  /** Clears the current selection. */
  fun clear()

  /** Returns true if the given component is part of the selection. */
  fun isSelected(component: NlComponent): Boolean = selection.contains(component)

  /** Sets the selection to the given components and additionally sets the primary selection. */
  fun setSelection(components: List<NlComponent>, primary: NlComponent?)

  /**
   * Sets the selection to the given components. The primary selection will be set to the first
   * component in the given list.
   */
  fun setSelection(components: List<NlComponent>) =
    setSelection(components, components.firstOrNull())

  /**
   * Switches the selection for the given [component]. If the component is not selected, it will
   * select it. If it is, it will be removed from the selection.
   */
  fun toggle(component: NlComponent)
}

/**
 * Base implementation for Selection Models. This is used as a workaround for Java classes not
 * working with the default methods provided by a Kotlin interface. By using this class for Java
 * implementations, users are not forced to re-implement the default implementations from the
 * interface.
 *
 * Kotlin implementations do not need to inherit from this class.
 */
abstract class BaseSelectionModel : SelectionModel

/** Empty implementation of [SelectionModel] to be used when no selection management is needed. */
object NopSelectionModel : BaseSelectionModel() {
  override fun setSelection(components: List<NlComponent>, primary: NlComponent?) {}

  override val selection: List<NlComponent> = emptyList()
  override val primary: NlComponent? = null

  override fun addListener(selectionListener: SelectionListener) {}

  override fun removeListener(selectionListener: SelectionListener) {}

  override fun clear() {}

  override fun toggle(component: NlComponent) {}

  override val secondarySelection: Any? = null

  override fun clearSecondary() {}

  override fun setSecondarySelection(component: NlComponent?, secondary: Any?) {}

  override fun isHighlighted(component: NlComponent): Boolean {
    return false
  }

  override fun setHighlightSelection(highlighted: List<NlComponent>, selected: List<NlComponent>) {}
}
