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
package com.android.tools.idea.lang.roomSql

import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes
import com.google.common.truth.Truth.assertThat

class RoomSqlPsiFacadeTest : LightRoomTestCase() {

  private lateinit var facade : RoomSqlPsiFacade

  override fun setUp() {
    super.setUp()
    facade = RoomSqlPsiFacade.getInstance(project)!!
  }

  fun testCreateTableName() {
    val userTable = facade.createTableName("user")!!
    assertThat(userTable.text).isEqualTo("user")
    assertThat(userTable.node.elementType).isEqualTo(RoomPsiTypes.TABLE_NAME)
  }

  fun testCreateTableName_quoting() {
    assertThat(facade.createTableName("table")!!.text).isEqualTo("`table`")
  }

  fun testCreateColumnName() {
    val nameColumn = facade.createColumnName("name")!!
    assertThat(nameColumn.text).isEqualTo("name")
    assertThat(nameColumn.node.elementType).isEqualTo(RoomPsiTypes.COLUMN_NAME)
  }
}
