/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.util.text.StringUtil

private const val STATE_ENABLED = "state_enabled"
internal const val STATE_NAME_PREFIX = "state_"

/**
 * Stores the information contained in a resource state list.
 */
class StateList(val fileName: String, val dirName: String) {
  private val myStates: MutableList<StateListState>

  val folderType: ResourceFolderType get() = ResourceFolderType.getFolderType(dirName)!!
  val states: List<StateListState> get() = myStates

  /**
   * @return the type of state list, can be [ResourceType.COLOR] or [ResourceType.DRAWABLE]
   */
  val type: ResourceType get() = ResourceType.fromFolderName(folderType.getName())!!


  /**
   * @return a list of all the states in this state list that have explicitly or implicitly state_enabled = false
   */
  val disabledStates: ImmutableList<StateListState>
    get() {
      val disabledStatesBuilder = ImmutableList.builder<StateListState>()
      var remainingObjectStates =
        ImmutableSet.of(
          ImmutableMap.of(STATE_ENABLED, true),
          ImmutableMap.of(STATE_ENABLED, false)
        )
      // An object state is a particular assignment of boolean values to all possible state list flags.
      // For example, in a world where there exists only three flags (a, b and c), there are 2^3 = 8 possible object state.
      // {a : true, b : false, c : true} is one such state.
      // {a : true} is not an object state, since it does not have values for b or c.
      // But we can use {a : true} as a representation for the set of all object states that have true assigned to a.
      // Since we do not know a priori how many different flags there are, that is how we are going to represent a set of object states.
      // We are using a set of maps, where each map represents a set of object states, and the overall set is their union.
      // For example, the set S = { {a : true} , {b : true, c : false} } is the union of two sets of object states.
      // The first one, described by {a : true} contains 4 object states, and the second one, described by {b : true, c : false} contains 2.
      // Overall this set S represents: {a : true, b : true, c : true}, {a : true, b : true, c : false}, {a : true, b : false, c : true}
      // {a : true, b : false, c : false} and {a : false, b : true, c : false}.
      // It is only 5 object states since {a : true, b : true, c : false} is described by both maps.
      // remainingObjects is going to represent all the object states that have not been matched in the state list until now.
      // So before we start we want to initialise it to represents all possible object states. One easy way to do so is to pick a flag
      // and make two representations {flag : true} and {flag : false} and take their union. We pick "state_enabled" as that flag but any
      // flag could have been used.
      // We now go through the state list state by state.
      // For each state list state, we ask the question : does there exist an object state that could reach this state list state,
      // and match it, and have "state_enabled = true"? If that object state exists, it has to be represented in remainingObjectStates.
      // if there is no such object state, then all the object states that would match this state list state would have
      // "state_enabled = false", so this state list state is considered disabled.
      // Before looking at the next state list state, we recompute remainingObjectStates so that it does not represent any more
      // the object states that match this state list state.
      for (state in myStates) {
        if (!state.matchesWithEnabledObjectState(remainingObjectStates)) {
          disabledStatesBuilder.add(state)
        }
        remainingObjectStates = removeState(state, remainingObjectStates)
      }
      return disabledStatesBuilder.build()
    }

  init {
    myStates = ArrayList()
  }

  fun addState(state: StateListState) {
    myStates.add(state)
  }

  /**
   * Returns a representation of all the object states that were in allowed states but do not match the state list state
   */
  private fun removeState(
    state: StateListState,
    allowedStates: ImmutableSet<ImmutableMap<String, Boolean>>
  ): ImmutableSet<ImmutableMap<String, Boolean>> {
    val remainingStates = ImmutableSet.builder<ImmutableMap<String, Boolean>>()
    val stateAttributes = state.attributes
    for (attribute in stateAttributes.keys) {
      for (allowedState in allowedStates) {
        if (!allowedState.containsKey(attribute)) {
          // This allowed state does not have a constraint for attribute. So it represents object states that can take either value
          // for it. We restrict this representation by adding to it explicitly the opposite constraint to the one in the state list state
          // so that we remove from this representation all the object states that match the state list state while keeping all the ones
          // that do not.
          val newAllowedState = ImmutableMap.builder<String, Boolean>()
          newAllowedState.putAll(allowedState).put(attribute, stateAttributes[attribute]!!.not())
          remainingStates.add(newAllowedState.build())
        } else if (allowedState[attribute] !== stateAttributes[attribute]) {
          // None of the object states represented by allowedState match the state list state. So we keep them all by keeping
          // the same representation.
          remainingStates.add(allowedState)
        }
      }
    }
    return remainingStates.build()
  }
}

/**
 * Stores information about a particular state of a resource state list.
 */
class StateListState(var value: String?, val attributes: Map<String, Boolean>, var alpha: String?) {

  val description: String
    get() = Joiner.on(", ").join(getAttributesNames(true))

  /**
   * @return a list of all the attribute names. Names are capitalized is capitalize is true
   */
  private fun getAttributesNames(capitalize: Boolean): ImmutableList<String> {
    val attributes = attributes

    if (attributes.isEmpty()) {
      return ImmutableList.of(if (capitalize) "Default" else "default")
    }

    val attributeDescriptions = ImmutableList.builder<String>()
    for ((key, value1) in attributes) {
      var description = key.substring(STATE_NAME_PREFIX.length)
      if (!value1) {
        description = "not $description"
      }
      attributeDescriptions.add(if (capitalize) StringUtil.capitalize(description) else description)
    }

    return attributeDescriptions.build()
  }

  /**
   * Checks if there exists an object state that matches this state list state, has state_enabled = true,
   * and is represented in allowedObjectStates.
   * @param allowedObjectStates
   */
  internal fun matchesWithEnabledObjectState(allowedObjectStates: ImmutableSet<ImmutableMap<String, Boolean>>): Boolean {
    if (attributes.containsKey(STATE_ENABLED) && attributes[STATE_ENABLED]!!.not()) {
      // This state list state has state_enabled = false, so no object state with state_enabled = true could match it
      return false
    }
    for (allowedAttributes in allowedObjectStates) {
      if (allowedAttributes.containsKey(STATE_ENABLED) && allowedAttributes[STATE_ENABLED]!!.not()) {
        // This allowed object state representation has explicitly state_enabled = false, so it does not represent any object state
        // with state_enabled = true
        continue
      }
      var match = true
      for (attribute in attributes.keys) {
        if (allowedAttributes.containsKey(attribute) && attributes[attribute] !== allowedAttributes[attribute]) {
          // This state list state does not match any of the object states represented by allowedAttributes, since they explicitly
          // disagree on one particular flag.
          match = false
          break
        }
      }
      if (match) {
        // There is one object state represented in allowedAttributes, that has state_enabled = true, and that matches this
        // state list state.
        return true
      }
    }
    return false
  }
}
