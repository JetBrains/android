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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDocumentManager

class RoomSchemaManagerTest : LightRoomTestCase() {

  fun testEntities() {
    myFixture.addRoomEntity("User")
    myFixture.addRoomEntity("com.example.Address")

    myFixture.addClass(
        """
          package com.example;

          public class NormalClass {}
          """.trimIndent())

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(myFixture.findClass("User"), "User"),
                Entity(myFixture.findClass("com.example.Address"), "Address")),
            databases = emptySet(),
            daos = emptySet()))
  }

  fun testEntities_tableNameOverride() {
    myFixture.addRoomEntity("com.example.Address", tableNameOverride = "addresses")

    val entity = RoomSchemaManager.getInstance(myModule)!!.getSchema()!!.entities.single()
    assertThat(entity.psiClass).isSameAs(myFixture.findClass("com.example.Address"))
    assertThat(entity.name).isEqualTo("addresses")
    assertThat(entity.nameElement).isNotSameAs(entity.psiClass)
  }

  fun testEntities_tableNameOverride_expression() {
    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Entity;

        @Entity(tableName = "addresses" + Address.SUFFIX)
        public class Address {
          public static final String SUFFIX = "_table";
        }
        """.trimIndent())

    val entity = RoomSchemaManager.getInstance(myModule)!!.getSchema()!!.entities.single()
    assertThat(entity.psiClass).isSameAs(myFixture.findClass("com.example.Address"))
    assertThat(entity.name).isEqualTo("addresses_table")
    assertThat(entity.nameElement).isNotSameAs(entity.psiClass)
  }

  fun testEntities_addEntity() {
    val addressClass = myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Entity;

        public class Address {}
        """.trimIndent())

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = emptySet(),
            databases = emptySet(),
            daos = emptySet()))

    myFixture.openFileInEditor(addressClass.containingFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.getLineStartOffset(4))
    myFixture.type("@Entity ")

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(myFixture.findClass("com.example.Address"), "Address")),
            databases = emptySet(),
            daos = emptySet()))
  }

  fun testEntities_AddTableNameOverride() {
    val addressClass = myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Entity;

        @Entity
        public class Address {}
        """.trimIndent())

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(myFixture.findClass("com.example.Address"), "Address")),
            databases = emptySet(),
            daos = emptySet()))

    myFixture.openFileInEditor(addressClass.containingFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.getLineEndOffset(4))
    myFixture.type("""(tableName = "addresses")""")

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val entity = RoomSchemaManager.getInstance(myModule)!!.getSchema()!!.entities.single()
    assertThat(entity.psiClass).isSameAs(myFixture.findClass("com.example.Address"))
    assertThat(entity.name).isEqualTo("addresses")
    assertThat(entity.nameElement).isNotSameAs(entity.psiClass)
  }

  fun testDatabases_single() {
    myFixture.addRoomEntity("com.example.User")
    myFixture.addRoomEntity("com.example.Address")
    myFixture.addRoomEntity("com.example.Order")

    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Database;

        @Database(entities = {User.class, Address.class}, version = 1)
        public class AppDatabase {}
        """.trimIndent())

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(myFixture.findClass("com.example.Address"), "Address"),
                Entity(myFixture.findClass("com.example.User"), "User"),
                Entity(myFixture.findClass("com.example.Order"), "Order")),
            databases = setOf(
                Database(
                    myFixture.findClass("com.example.AppDatabase"),
                    entities = setOf(
                        myFixture.findClass("com.example.Address"),
                        myFixture.findClass("com.example.User")))),
            daos = emptySet()))
  }

  fun testDatabases_multiple() {
    myFixture.addRoomEntity("com.example.User")
    myFixture.addRoomEntity("com.example.Address")
    myFixture.addRoomEntity("com.example.Order")

    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Database;

        @Database(entities = {User.class, Address.class}, version = 1)
        public class UserDatabase {}
        """.trimIndent())

    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Database;

        @Database(entities = {Order.class, Address.class}, version = 1)
        public class OrderDatabase {}
        """.trimIndent())

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(myFixture.findClass("com.example.Address"), "Address"),
                Entity(myFixture.findClass("com.example.User"), "User"),
                Entity(myFixture.findClass("com.example.Order"), "Order")),
            databases = setOf(
                Database(
                    myFixture.findClass("com.example.UserDatabase"),
                    entities = setOf(
                        myFixture.findClass("com.example.Address"),
                        myFixture.findClass("com.example.User"))),
                Database(
                    myFixture.findClass("com.example.OrderDatabase"),
                    entities = setOf(
                        myFixture.findClass("com.example.Address"),
                        myFixture.findClass("com.example.Order")))),
            daos = emptySet()))
  }

  fun testDaos() {
    myFixture.addRoomEntity("com.example.User")

    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;

        @Dao
        public class UserDao {
          @Query("SELECT * FROM User") List<User> getAll();
        }
        """.trimIndent())

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(myFixture.findClass("com.example.User"), "User")),
            databases = emptySet(),
            daos = setOf(
                Dao(myFixture.findClass("com.example.UserDao")))))
  }

  fun testRoomMissing() {
    ApplicationManager.getApplication().runWriteAction {
      myFixture.findClass("android.arch.persistence.room.Dao").containingFile.virtualFile.delete(this)
      myFixture.findClass("android.arch.persistence.room.Database").containingFile.virtualFile.delete(this)
      myFixture.findClass("android.arch.persistence.room.Entity").containingFile.virtualFile.delete(this)
    }

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isNull()
  }

  fun testColums() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String", "age" ofType "int")


    val userClass = myFixture.findClass("com.example.User")

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(
                    userClass,
                    name = "User",
                    columns = setOf(
                        Column(userClass.findFieldByName("name", false)!!, "name"),
                        Column(userClass.findFieldByName("age", false)!!, "age")))),
            databases = emptySet(),
            daos = emptySet()))
  }

  fun testColums_ignore() {
    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Entity;
        import android.arch.persistence.room.Ignore;

        @Entity
        public class User {
          private String name;
          @Ignore private int age;
        }
        """.trimIndent())

    val userClass = myFixture.findClass("com.example.User")

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(
                    userClass,
                    name = "User",
                    columns = setOf(
                        Column(userClass.findFieldByName("name", false)!!, "name")))),
            databases = emptySet(),
            daos = emptySet()))
  }

  fun testColums_inheritance() {
    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Entity;

        public abstract class NamedBase {
          private String name;
        }
        """.trimIndent())

    myFixture.addClass(
        """
        package com.example;

        import android.arch.persistence.room.Entity;

        @Entity
        public class User extends NamedBase {
          private int age;
        }
        """.trimIndent())

    val userClass = myFixture.findClass("com.example.User")

    assertThat(RoomSchemaManager.getInstance(myModule)!!.getSchema()).isEqualTo(
        RoomSchema(
            entities = setOf(
                Entity(
                    userClass,
                    name = "User",
                    columns = setOf(
                        Column(userClass.findFieldByName("name", true)!!, "name"),
                        Column(userClass.findFieldByName("age", false)!!, "age")))),
            databases = emptySet(),
            daos = emptySet()))
  }
}
