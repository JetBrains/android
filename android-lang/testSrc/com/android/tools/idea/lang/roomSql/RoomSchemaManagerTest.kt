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

import com.android.tools.idea.lang.roomSql.resolution.Dao
import com.android.tools.idea.lang.roomSql.resolution.PRIMARY_KEY_NAMES
import com.android.tools.idea.lang.roomSql.resolution.PRIMARY_KEY_NAMES_FOR_FTS
import com.android.tools.idea.lang.roomSql.resolution.PsiElementForFakeColumn
import com.android.tools.idea.lang.roomSql.resolution.RoomDatabase
import com.android.tools.idea.lang.roomSql.resolution.RoomRowidColumn
import com.android.tools.idea.lang.roomSql.resolution.RoomFieldColumn
import com.android.tools.idea.lang.roomSql.resolution.RoomFtsTableColumn
import com.android.tools.idea.lang.roomSql.resolution.RoomSchema
import com.android.tools.idea.lang.roomSql.resolution.RoomSchemaManager
import com.android.tools.idea.lang.roomSql.resolution.RoomTable
import com.android.tools.idea.lang.roomSql.resolution.RoomTable.Type.ENTITY
import com.android.tools.idea.lang.roomSql.resolution.RoomTable.Type.VIEW
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

class RoomSchemaManagerTest : RoomLightTestCase() {
  private fun getSchema(element: PsiElement) = RoomSchemaManager.getInstance(project)!!.getSchema(element.containingFile)!!

  fun testEntities() {
    myFixture.addRoomEntity("User")
    myFixture.addRoomEntity("com.example.Address")

    val psiClass = myFixture.addClass(
        """
          package com.example;

          public class NormalClass {}
          """.trimIndent())

    assertThat(getSchema(psiClass)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(myFixture.classPointer("User"), ENTITY, "User", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("User").element!!))
            )),
            RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Address").element!!))
            ))),
          databases = emptySet(),
          daos = emptySet()))
  }


  fun testEntities_tableNameOverride() {
    val address = myFixture.addRoomEntity("com.example.Address", tableNameOverride = "addresses")

    val entity = getSchema(address).tables.single()
    assertThat(entity.psiClass).isSameAs(myFixture.classPointer("com.example.Address"))
    assertThat(entity.name).isEqualTo("addresses")
    assertThat(entity.nameElement).isNotSameAs(entity.psiClass)
  }

  fun testEntities_tableNameOverride_expression() {
    val address = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;

        @Entity(tableName = "addresses" + Address.SUFFIX)
        public class Address {
          public static final String SUFFIX = "_table";
        }
        """.trimIndent())

    val entity = getSchema(address).tables.single()
    assertThat(entity.psiClass).isSameAs(myFixture.classPointer("com.example.Address"))
    assertThat(entity.name).isEqualTo("addresses_table")
    assertThat(entity.nameElement).isNotSameAs(entity.psiClass)
  }

  fun testEntities_addEntity() {
    val addressClass = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;

        public class Address {}
        """.trimIndent())

    assertThat(getSchema(addressClass)).isEqualTo(
        RoomSchema(
          tables = emptySet(),
          databases = emptySet(),
          daos = emptySet()))

    myFixture.openFileInEditor(addressClass.containingFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.getLineStartOffset(4))
    myFixture.type("@Entity ")

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assertThat(getSchema(addressClass)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Address").element!!))
            ))),
          databases = emptySet(),
          daos = emptySet()))
  }
  
  fun testViews_addView() {
    val idsClass = myFixture.addClass(
        """
        package com.example;

        import androidx.room.DatabaseView;

        public class Ids {}
        """.trimIndent())

    assertThat(getSchema(idsClass)).isEqualTo(
      RoomSchema(
        tables = emptySet(),
        databases = emptySet(),
        daos = emptySet()))

    myFixture.openFileInEditor(idsClass.containingFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.getLineStartOffset(4))
    myFixture.type("@DatabaseView ")

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val schema = getSchema(idsClass)
    assertThat(schema).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(myFixture.classPointer("com.example.Ids"), VIEW, "Ids")),
        databases = emptySet(),
        daos = emptySet()))

    assertThat(schema.tables.single().isView).isTrue()
  }

  fun testEntities_AddTableNameOverride() {
    val addressClass = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;

        @Entity
        public class Address {}
        """.trimIndent())

    assertThat(getSchema(addressClass)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Address").element!!))
            ))),
          databases = emptySet(),
          daos = emptySet()))

    myFixture.openFileInEditor(addressClass.containingFile.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.getLineEndOffset(4))
    myFixture.type("""(tableName = "addresses")""")

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val entity = getSchema(addressClass).tables.single()
    assertThat(entity.psiClass).isSameAs(myFixture.classPointer("com.example.Address"))
    assertThat(entity.name).isEqualTo("addresses")
    assertThat(entity.nameElement).isNotSameAs(entity.psiClass)
  }

  fun testDatabases_single() {
    myFixture.addRoomEntity("com.example.User")
    myFixture.addRoomEntity("com.example.Address")
    myFixture.addRoomEntity("com.example.Order")

    val database = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Database;

        @Database(tables = {User.class, Address.class}, version = 1)
        public class AppDatabase {}
        """.trimIndent())

    assertThat(getSchema(database)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Address").element!!))
            )),
            RoomTable(myFixture.classPointer("com.example.User"), ENTITY, "User", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
            )),
            RoomTable(myFixture.classPointer("com.example.Order"), ENTITY, "Order", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Order").element!!))
            ))),
          databases = setOf(
                RoomDatabase(
                    myFixture.classPointer("com.example.AppDatabase"),
                    entities = setOf(
                        myFixture.classPointer("com.example.Address"),
                        myFixture.classPointer("com.example.User")))),
          daos = emptySet()))
  }

  fun testDatabases_multiple() {
    myFixture.addRoomEntity("com.example.User")
    myFixture.addRoomEntity("com.example.Address")
    myFixture.addRoomEntity("com.example.Order")

    val psiClass = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Database;

        @Database(tables = {User.class, Address.class}, version = 1)
        public class UserDatabase {}
        """.trimIndent())

    myFixture.addClass(
        """
        package com.example;

        import androidx.room.Database;

        @Database(tables = {Order.class, Address.class}, version = 1)
        public class OrderDatabase {}
        """.trimIndent())

    assertThat(getSchema(psiClass)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Address").element!!))
            )),
            RoomTable(myFixture.classPointer("com.example.User"), ENTITY, "User", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
            )),
            RoomTable(myFixture.classPointer("com.example.Order"), ENTITY, "Order", columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Order").element!!))
            ))
        ),
          databases = setOf(
                RoomDatabase(
                    myFixture.classPointer("com.example.UserDatabase"),
                    entities = setOf(
                        myFixture.classPointer("com.example.Address"),
                        myFixture.classPointer("com.example.User"))),
                RoomDatabase(
                    myFixture.classPointer("com.example.OrderDatabase"),
                    entities = setOf(
                        myFixture.classPointer("com.example.Address"),
                        myFixture.classPointer("com.example.Order")))),
          daos = emptySet()))
  }

  fun testDaos() {
    myFixture.addRoomEntity("com.example.User")

    val dao = myFixture.addClass(
      """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public class UserDao {
          @Query("SELECT * FROM User") List<User> getAll();
        }
        """.trimIndent())

    assertThat(getSchema(dao)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(
            myFixture.classPointer("com.example.User"),
            ENTITY,
            "User",
            columns = setOf(
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
            ))),
        databases = emptySet(),
        daos = setOf(
          Dao(myFixture.classPointer("com.example.UserDao")))))
  }

  fun testRoomMissing() {
    ApplicationManager.getApplication().runWriteAction {
      myFixture.findClass("androidx.room.Dao").containingFile.virtualFile.delete(this)
      myFixture.findClass("androidx.room.Database").containingFile.virtualFile.delete(this)
      myFixture.findClass("androidx.room.Entity").containingFile.virtualFile.delete(this)
    }

    val psiClass = myFixture.addClass("class SomeClass {}")

    assertThat(RoomSchemaManager.getInstance(project)!!.getSchema(psiClass.containingFile)).isNull()
  }

  fun testColumns() {
    val user = myFixture.addRoomEntity("com.example.User", "name" ofType "String", "age" ofType "int")


    assertThat(getSchema(user)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(
              myFixture.classPointer("com.example.User"),
              name = "User",
              type = ENTITY,
              columns = setOf(
                RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!)),
                RoomFieldColumn(myFixture.fieldPointer("com.example.User", "name"), "name"),
                RoomFieldColumn(myFixture.fieldPointer("com.example.User", "age"), "age")))),
          databases = emptySet(),
          daos = emptySet()))
  }

  fun testColumns_ignore() {
    val user = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;
        import androidx.room.Ignore;

        @Entity
        public class User {
          private String name;
          @Ignore private int age;
        }
        """.trimIndent())

    assertThat(getSchema(user)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(
                    myFixture.classPointer("com.example.User"),
                    name = "User",
                    type = ENTITY,
                    columns = setOf(
                      RoomFieldColumn(myFixture.fieldPointer("com.example.User", "name"), "name"),
                      RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
                    )
            )
          ),
          databases = emptySet(),
          daos = emptySet()))
  }

  fun testColumns_static() {
    val user = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;
        import androidx.room.Ignore;

        @Entity
        public class User {
          private static final int MY_CONST = 12;
          private String name;
        }
        """.trimIndent())

    assertThat(getSchema(user)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(
                    myFixture.classPointer("com.example.User"),
                    name = "User",
                    type = ENTITY,
                    columns = setOf(
                      RoomFieldColumn(myFixture.fieldPointer("com.example.User", "name"), "name"),
                      RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
                    ))),
          databases = emptySet(),
          daos = emptySet()))
  }

  fun testColumns_inheritance() {
    val psiClass = myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;

        public abstract class NamedBase {
          private String name;
        }
        """.trimIndent())

    myFixture.addClass(
        """
        package com.example;

        import androidx.room.Entity;

        @Entity
        public class User extends NamedBase {
          private int age;
        }
        """.trimIndent())

    assertThat(getSchema(psiClass)).isEqualTo(
        RoomSchema(
          tables = setOf(
            RoomTable(
                    myFixture.classPointer("com.example.User"),
                    name = "User",
                    type = ENTITY,
                    columns = setOf(
                      RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!)),
                      RoomFieldColumn(myFixture.fieldPointer("com.example.User", "name", checkBases = true), "name"),
                      RoomFieldColumn(myFixture.fieldPointer("com.example.User", "age"), "age")))),
          databases = emptySet(),
          daos = emptySet()))
  }
  
  fun testFts() {
    val psiClass = myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import androidx.room.Fts4;

      @Entity
      @Fts4
      public class Mail {
        String body;
      }
      """.trimIndent()
    )

    assertThat(getSchema(psiClass)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(
            myFixture.classPointer("com.example.Mail"),
            name = "Mail",
            type = ENTITY,
            columns = setOf(
              RoomFieldColumn(myFixture.fieldPointer("com.example.Mail", "body"), "body"),
              RoomFtsTableColumn(myFixture.classPointer("com.example.Mail").element!!, "Mail"),
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.Mail").element!!), PRIMARY_KEY_NAMES_FOR_FTS)
            )
          )),
        databases = emptySet(),
        daos = emptySet()
      )
    )
  }
}
