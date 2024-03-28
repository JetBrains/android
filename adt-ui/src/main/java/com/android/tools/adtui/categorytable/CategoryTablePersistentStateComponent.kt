/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import javax.swing.SortOrder

/**
 * An abstract [PersistentStateComponent] that persists a [CategoryTable] when supplied with a
 * [CategoryTableStateSerializer].
 */
abstract class CategoryTablePersistentStateComponent :
  PersistentStateComponent<CategoryTableState> {
  abstract val serializer: CategoryTableStateSerializer
  var table: CategoryTable<*>? = null
    set(value) {
      field = value
      if (!state.isEqualToDefault() && value != null) {
        serializer.readState(value, state)
      }
    }

  @Volatile private var state = CategoryTableState()

  override fun getState(): CategoryTableState? {
    val table = table ?: return null
    serializer.writeState(table, state)
    return state
  }

  override fun loadState(state: CategoryTableState) {
    this.state = state
    table?.let { runInEdt { serializer.readState(it, state) } }
  }
}

/**
 * The persistable state of a CategoryTable. While BaseState is normally meant to be used as the
 * canonical store for the data, it's completely unsuitable for CategoryTable's state: CategoryTable
 * works extensively with generic values of arbitrary types supplied by clients. BaseState needs to
 * know the concrete types to serialize based on reflection, which is impossible with generics.
 *
 * Thus, we perform our own conversion to simple types, and store converted data in these BaseState
 * classes.
 */
class CategoryTableState : BaseState() {
  // These lists *must* be var or else they will fail to be detected as properties, even though we
  // never reassign them.
  var groupByAttributes by list<String>()
  var columnSorters by list<ColumnSorterState>()
  var collapsedNodes by list<CategoryListState>()

  class ColumnSorterState : BaseState() {
    var column by string()
    var order by enum<SortOrder>()
  }

  class CategoryState : BaseState() {
    var attribute by string()
    var value by string()
  }

  class CategoryListState : BaseState() {
    var categories by list<CategoryState>()
  }
}

/**
 * A bridge between [CategoryTable] and [CategoryTableState]. [CategoryTable] can be used without
 * any persistence support; serialization-related code is isolated to
 * [CategoryTableStateSerializer], which is built from [AttributeSerializer].
 */
class CategoryTableStateSerializer(val attributeSerializers: List<AttributeSerializer<*>>) {
  private fun findAttribute(name: String?): Attribute<*, *>? =
    attributeSerializers.find { it.name == name }?.attribute

  private fun Attribute<*, *>.name() = serializer()?.name

  private fun <T, C> Attribute<T, C>.serializer(): AttributeSerializer<C>? =
    attributeSerializers.find { it.attribute == this } as AttributeSerializer<C>?

  fun writeState(table: CategoryTable<*>, state: CategoryTableState) {
    state.columnSorters.clear()
    state.columnSorters.addAll(table.columnSorters.mapNotNull { it.toColumnSorterState() })

    state.collapsedNodes.clear()
    state.collapsedNodes.addAll(table.collapsedNodes.mapNotNull { it.toCategoryListState() })

    state.groupByAttributes.clear()
    state.groupByAttributes.addAll(table.groupByAttributes.mapNotNull { it.name() })
  }

  @UiThread
  fun <T : Any> readState(table: CategoryTable<T>, state: CategoryTableState) {
    table.setSortOrder(state.columnSorters.mapNotNull { it.toColumnSortOrder() })

    for (collapsedNodeState in state.collapsedNodes) {
      val categoryList = collapsedNodeState.toCategoryList<T>() ?: continue
      table.setCollapsed(categoryList, true)
    }

    for (groupByAttributeName in state.groupByAttributes) {
      val attribute = findAttribute(groupByAttributeName) as Attribute<T, *>? ?: continue
      table.addGrouping(attribute)
    }
  }

  fun <T, C> Category<T, C>.toCategoryState(): CategoryTableState.CategoryState? {
    val serializer = attribute.serializer() ?: return null
    return CategoryTableState.CategoryState().also {
      it.attribute = attribute.name()
      it.value = serializer.converter.toString(value)
    }
  }

  fun <T> CategoryTableState.CategoryState.toCategory(): Category<T, *>? {
    val attribute = findAttribute(attribute) as Attribute<T, *>? ?: return null
    return toCategory(attribute)
  }

  // Helper function split from toCategory() for type-inference reasons
  private fun <T, C> CategoryTableState.CategoryState.toCategory(
    attribute: Attribute<T, C>
  ): Category<T, C>? {
    val value = attribute.serializer()?.converter?.fromString(value ?: "") ?: return null
    return Category(attribute, value)
  }

  fun CategoryList<*>.toCategoryListState(): CategoryTableState.CategoryListState? {
    return CategoryTableState.CategoryListState().also {
      for (category in this) {
        val state = category.toCategoryState() ?: return null
        it.categories.add(state)
      }
    }
  }

  fun <T> CategoryTableState.CategoryListState.toCategoryList(): CategoryList<T>? {
    val result =
      categories.mapNotNull<CategoryTableState.CategoryState, Category<T, *>> { it.toCategory() }
    return result.takeIf { it.size == categories.size }
  }

  fun ColumnSortOrder<*>.toColumnSorterState(): CategoryTableState.ColumnSorterState? {
    val name = attribute.name() ?: return null
    return CategoryTableState.ColumnSorterState().also {
      it.column = name
      it.order = sortOrder
    }
  }

  fun <T> CategoryTableState.ColumnSorterState.toColumnSortOrder(): ColumnSortOrder<T>? {
    val attribute = findAttribute(column) ?: return null
    return ColumnSortOrder<T>(attribute as Attribute<T, *>, order ?: SortOrder.UNSORTED)
  }
}
