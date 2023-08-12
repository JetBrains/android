/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard.dynamic

import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key
import com.google.common.base.Function
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.lang.ref.WeakReference

/**
 * Used by [DynamicWizard], [DynamicWizardPath], and [DynamicWizardStep] to store their state.
 * Each state store is part of an ancestry chain of state stores. Each level of the chain has a scope associated with it.
 * The ancestry chain must be ordered in value order of scopes, that is the scope's ordinal value must increase as the chain
 * of stores is traversed upwards from child to parent. Each stored value is associated with a scope, and values are only stored
 * in a store of the same scope as the value. If a given store is asked to store a value of a different scope, it will pass the value
 * up the chain until a store of appropriate scope is found. When searching for a value, each store will first search in its own value
 * map before inquiring up the chain, so keys of a lower scope will "shadow" the same keys which have higher scopes.
 *
 * The store can have a listener associated with it which will be notified of each update of the store.
 * The update will contain the key associated with the change as well as the scope of the change.
 * The store also allows for pulling change notifications rather than these pushed notifications via the
 * [recentUpdates] and [clearRecentUpdates].
 *
 * The currently defined scopes are STEP, PATH, and WIZARD. So, each DynamicWizardStep has a ScopedStateStore associated with it which
 * has a scope of STEP, which only stores key/value pairs which also have a scope of STEP.
 * The store declares a [Key] class which must be used to store and retrieve values. A Key is parametrized with the class of the
 * associated value. Each Key has a Scope. A null scope may be used if the client does not care where the data associated with the
 * Key is retrieved from or stored. If a scope is specified, then calling get() will only return a value with a matching scope.
 *
 * ## Choosing Scopes
 * Most keys should be scoped at the PATH level, since they are shared with the rest of that modular workflow.
 * Each instance of DynamicWizardPath maintains its own ScopedStateStore instance. If you wish a key to be shared between
 * multiple paths then it should be scoped at the WIZARD level. A STEP scoped key is only available to the instance object of the
 * STEP in which it is registered, and thus behaves like a local variable.
 */
class ScopedStateStore(
  private val scope: Scope, private val parent: ScopedStateStore?, listener: ScopedStoreListener?
) : Function<ScopedStateStore.Key<*>?, Any?> { // Map of the current state
  private val state = Maps.newHashMap<Key<*>, Any>()
  // Set of changed key/scope pairs which have been modified since the last call to clearRecentUpdates()
  private val recentlyUpdated = Sets.newHashSet<Key<*>>()
  private val listeners = Lists.newArrayListWithCapacity<WeakReference<ScopedStoreListener>>(4)
  // Note that this should live as long as the store instance. Otherwise it gets GCed and no notifications are propagated.
  private val parentListener = ScopedStoreListenerImpl()

  private inner class ScopedStoreListenerImpl: ScopedStoreListener {
    override fun <T> invokeUpdate(changedKey: Key<T>?) {
      notifyListeners(changedKey)
    }
  }

  /**
   * Get the map of keys and scopes representing changes to the store since the last call to clearRecentUpdates()
   * @return a non-null, possibly empty map of keys to scopes
   */
  val recentUpdates: Set<Key<*>> get() = recentlyUpdated

  /**
   * @return a all keys from all scopes that were set in this store.
   */
  private val allKeys: Set<Key<*>>
    get() = if (parent == null) {
      state.keys.toSet()
    }
    else {
      Iterables.concat<Key<*>>(state.keys, parent.allKeys).toSet()
    }

  interface ScopedStoreListener {
    fun <T> invokeUpdate(changedKey: Key<T>?)
  }

  init {
    require(!(parent != null && scope.isGreaterThan(parent.scope))) {
      "Attempted to add store of scope $scope as child of lesser scope ${parent!!.scope}"
    }
    if (listener != null) {
      addListener(listener)
    }
    parent?.addListener(parentListener)
  }

  /**
   * Adds a listener that gets notified when stored values change.
   */
  fun addListener(listener: ScopedStoreListener) {
    listeners.add(WeakReference(listener))
  }

  /**
   * Get a value from our state store attempt to cast to the given type.
   * Will first check this state for the matching value, and if not found, will query the parent scope.
   * If the object returned is not-null, but cannot be cast to the required type, an exception is thrown.
   * @param key the unique id for the value to retrieve.
   * @return The requested value. Will return null if no value exists in the state for the given key.
   */
  operator fun <T> get(key: Key<T>): T? = when {
    scope == key.scope && state.containsKey(key) -> try {
      key.cast(state[key])
    }
    catch (e: ClassCastException) {
      null
    }
    parent != null -> parent[key]
    else -> null
  }

  /**
   * Get a value from our state store. If value in the store is missing or is null, defaultValue is returned.
   *
   * @param key the unique id for the value to retrieve.
   * @param defaultValue value to be returned if the value for the key is missing.
   * @return a pair where the first object is the requested value and the second is the scoped key of that value.
   * will return Pair<null></null>, null> if no value exists in the state for the given key.
   */
  fun <T> getNotNull(key: Key<T>, defaultValue: T): T = get(key) ?: defaultValue

  /**
   * Store a value in the state for the given key. If the given scope matches this state's scope, it will be stored
   * in this state store. If the given scope is larger than this store's scope, it will be delegated to the parent scope if possible.
   * @param key the unique id for the value to store.
   * @param value the value to store.
   * @return true iff the state changed as a result of this operation
   */
  fun <T> put(key: Key<T>, value: T?): Boolean {
    val stateChanged: Boolean
    require(!scope.isGreaterThan(key.scope)) { "Attempted to store a value of scope ${key.scope.name} in greater scope of ${scope.name}" }
    when {
      scope == key.scope -> {
        stateChanged = !state.containsKey(key) || state[key] != value
        state[key] = value
        if (stateChanged) {
          notifyListeners(key)
        }
      }
      key.scope.isGreaterThan(scope) && parent != null -> stateChanged = parent.put(key, value)
      else -> throw IllegalArgumentException(
        "Attempted to store a value of scope ${key.scope} in lesser scope of $scope which does not have a parent of the proper scope"
      )
    }
    return stateChanged
  }

  private fun <T> notifyListeners(key: Key<T>?) {
    recentlyUpdated.add(key)
    val iterator = listeners.iterator()
    while (iterator.hasNext()) {
      val listener = iterator.next().get()
      if (listener == null) {
        iterator.remove()
      }
      else {
        listener.invokeUpdate(key)
      }
    }
  }

  /**
   * Push the given value onto a list. If no list is present for the given key it will be created.
   * @return true iff the state changed as a result of this operation.
   */
  fun <T> listPush(key: Key<MutableList<T>>, value: T): Boolean {
    var list: MutableList<T>? = null
    if (containsKey(key)) {
      list = get(key)
    }
    if (list == null) {
      list = Lists.newArrayList()
    }
    // TODO(qumeric): this is always true. Either this or [listPush] comment is wrong
    val stateChanged = list!!.add(value)
    put(key, list)

    if (stateChanged) {
      notifyListeners(key)
    }
    return stateChanged
  }

  fun <T : List<*>> listSize(key: Key<T>): Int {
    if (containsKey(key)) {
      return get(key)?.size ?: 0
    }
    return 0
  }

  /**
   * Remove the given value from a list.
   * @return true iff the state changed as a result of this operation.
   */
  fun <T : MutableList<V>, V> listRemove(key: Key<T>, value: V): Boolean {
    var stateChanged = false
    if (containsKey(key)) {
      val list = get(key)
      if (list != null) {
        stateChanged = list.remove(value)
      }
    }
    if (stateChanged) {
      notifyListeners(key)
    }
    return stateChanged
  }

  /**
   * Insert value into the context performing the type check at runtime.
   * This is needed for cases when we don't know the type of the key parameter at compile time.
   *
   * @throws ClassCastException if the value is not null and cannot be casted to the key type
   */
  fun <T> unsafePut(key: Key<T>, `object`: Any?) {
    put(key, key.expectedClass.cast(`object`))
  }

  /**
   * Store a set of values into the store with the given scope according to the rules laid out in [put].
   */
  fun <T> putAll(map: Map<Key<T>, T>) {
    for (key in map.keys) {
      put(key, map[key])
    }
  }

  /**
   * Adds all the keys from `store` to `this` at the wizard level.
   */
  fun putAllInWizardScope(store: ScopedStateStore) {
    for (key in store.allKeys) {
      copyValue(store, key)
    }
  }

  /**
   * Typesafe copy operation for copying key value between stores.
   */
  private fun <T> copyValue(store: ScopedStateStore, key: Key<T>) {
    val newKey = Key(key.name, Scope.WIZARD, key.expectedClass)
    put(newKey, store[key])
  }

  /**
   * Remove the value in the state for the given key. If the given scope matches this state's scope, it will be removed
   * in this state store. If the given scope is larger than this store's scope, it will be delegated to the parent scope if possible.
   * @param key the unique id for the value to store.
   * @return true iff the remove operation caused the state of this store to change (ie something was actually removed)
   */
  fun <T> remove(key: Key<T>): Boolean {
    val stateChanged: Boolean
    require(!scope.isGreaterThan(key.scope)) { "Attempted to remove a value of scope ${key.scope} from greater scope of ${scope.name}" }
    if (scope == key.scope) {
      stateChanged = state.containsKey(key)
      state.remove(key)
    }
    else if (key.scope.isGreaterThan(scope) && parent != null) {
      stateChanged = parent.remove(key)
    }
    else {
      throw IllegalArgumentException(
        "Attempted to remove a value of scope ${key.scope} from lesser scope of $scope which does not have a parent of the proper scope"
      )
    }
    if (stateChanged) {
      notifyListeners(key)
    }
    return stateChanged
  }

  /**
   * Check to see if the given key is contained in this state store.
   * @return true iff the given key corresponds to a value in the state store.
   */
  fun <T> containsKey(key: Key<T>): Boolean = when {
    scope == key.scope -> state.containsKey(key)
    parent != null && key.scope.isGreaterThan(scope) -> parent.containsKey(key)
    else -> false
  }

  /**
   * Return a single map of the values "visible" from this store, that is the values contained in this store as well as
   * all values from the ancestor chain that are not overridden by values set at this scope level.
   */
  fun flatten(): MutableMap<String, Any> {
    val toReturn: MutableMap<String, Any> = parent?.flatten() ?: Maps.newHashMapWithExpectedSize(state.size)
    for (key in state.keys) {
      toReturn[key.name] = state[key]!!
    }
    return toReturn
  }

  /**
   * Notify the store that the client is done with with current record of modifications and that they can be deleted.
   */
  fun clearRecentUpdates() {
    recentlyUpdated.clear()
  }

  /**
   * Get a key to allow storage in the state store. The created key will be scoped at the same level as this state store.
   */
  fun <T> createKey(name: String, clazz: Class<T>): Key<T> = createKey(name, scope, clazz)

  /**
   * This allows using this store as a [Function] when working with Guava utilities.
   *
   * E.g. this allows to use [Maps.asMap]
   * to create a views on this context that implement [Map] interface.
   */
  override fun apply(input: Key<*>?): Any? = get(input!!)

  data class Key<T>(
    @JvmField
    val name: String,
    @JvmField
    val scope: Scope,
    @JvmField
    val expectedClass: Class<T>
  ) {
    @Suppress("UNCHECKED_CAST")
    fun cast(obj: Any?): T? =
      when {
        // can't cast java boxed types to kotlin primitives using cast(), and we'll get boxed types due to nullability
        expectedClass.isPrimitive -> obj as T?
        else -> expectedClass.cast(obj)
      }
  }

  enum class Scope {
    STEP,
    PATH,
    WIZARD;

    fun isGreaterThan(other: Scope?): Boolean = other != null && this.ordinal > other.ordinal
  }

  companion object {
    /**
     * Get a key to allow storage in the state store.
     */
    @JvmStatic
    fun <T> createKey(name: String, scope: Scope, clazz: Class<T>): Key<T> = Key(name, scope, clazz)
  }
}
