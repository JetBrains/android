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
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.resolution.PRIMARY_KEY_NAMES_FOR_FTS
import com.android.tools.idea.lang.androidSql.room.Dao
import com.android.tools.idea.lang.androidSql.room.PsiElementForFakeColumn
import com.android.tools.idea.lang.androidSql.room.RoomDatabase
import com.android.tools.idea.lang.androidSql.room.RoomFtsTableColumn
import com.android.tools.idea.lang.androidSql.room.RoomMemberColumn
import com.android.tools.idea.lang.androidSql.room.RoomRowidColumn
import com.android.tools.idea.lang.androidSql.room.RoomSchema
import com.android.tools.idea.lang.androidSql.room.RoomSchemaManager
import com.android.tools.idea.lang.androidSql.room.RoomTable
import com.android.tools.idea.lang.androidSql.room.RoomTable.Type.ENTITY
import com.android.tools.idea.lang.androidSql.room.RoomTable.Type.VIEW
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase

class RoomSchemaManagerTest : JavaCodeInsightFixtureAdtTestCase() {

  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }

  private fun getSchema(element: PsiElement): RoomSchema {
    val module = ModuleUtil.findModuleForPsiElement(element)!!
    val schema = RoomSchemaManager.getInstance(module).getSchema(element.containingFile)
    return schema!!
  }

  fun testDifferentSchemasForTestAndNotTestScope() {
    myFixture.addRoomEntity("MainSourcesEntity")
    val mainSourceFile = myFixture.addFileToProject("MainSourcesFile.java", "")

    // Below we're adding a new source root, make sure we're not modifying a reusable light project.
    assertThat((project as? ProjectImpl)?.isLight ?: false).named("project is light").isFalse()
    val testSrc = myFixture.tempDirFixture.findOrCreateDir("testDir")
    PsiTestUtil.addSourceRoot(myFixture.module, testSrc, true)
    val testFile = myFixture.addFileToProject("${testSrc.name}/TestFile.java", "")

    myFixture.addFileToProject(
      "${testSrc.name}/TestEntity.java",
      """
      import androidx.room.Entity;

      @Entity
      public class TestEntity {}
      """.trimIndent())


    assertThat(getSchema(mainSourceFile)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(myFixture.classPointer("MainSourcesEntity"), ENTITY, "MainSourcesEntity",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("MainSourcesEntity").element!!))
                    ))
        ),
        databases = emptySet(),
        daos = emptySet())
    )

    assertThat(getSchema(testFile)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(myFixture.classPointer("MainSourcesEntity"), ENTITY, "MainSourcesEntity",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("MainSourcesEntity").element!!))
                    )),
          RoomTable(myFixture.classPointer("TestEntity"), ENTITY, "TestEntity", columns = setOf(
            RoomRowidColumn(
              PsiElementForFakeColumn(myFixture.classPointer("TestEntity").element!!))
          ))
        ),
        databases = emptySet(),
        daos = emptySet())
    )
  }

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
            RoomRowidColumn(
              PsiElementForFakeColumn(myFixture.classPointer("User").element!!))
          )),
          RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Address").element!!))
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
          RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Address").element!!))
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
          RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Address").element!!))
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

        @Database(entities  = {User.class, Address.class}, version = 1)
        public class AppDatabase {}
        """.trimIndent())

    assertThat(getSchema(database)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Address").element!!))
                    )),
          RoomTable(myFixture.classPointer("com.example.User"), ENTITY, "User", columns = setOf(
            RoomRowidColumn(
              PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
          )),
          RoomTable(myFixture.classPointer("com.example.Order"), ENTITY, "Order",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Order").element!!))
                    ))),
        databases = setOf(
          RoomDatabase(
            myFixture.classPointer("com.example.AppDatabase"),
            entities = setOf(
              myFixture.classPointer("com.example.Address"),
              myFixture.classPointer("com.example.User")),
            daos = emptySet(),
            views = emptySet(),
          )),
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

        @Database(entities  = {User.class, Address.class}, version = 1)
        public class UserDatabase {}
        """.trimIndent())

    myFixture.addClass(
      """
        package com.example;

        import androidx.room.Database;

        @Database(entities  = {Order.class, Address.class}, version = 1)
        public class OrderDatabase {}
        """.trimIndent())

    assertThat(getSchema(psiClass)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(myFixture.classPointer("com.example.Address"), ENTITY, "Address",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Address").element!!))
                    )),
          RoomTable(myFixture.classPointer("com.example.User"), ENTITY, "User", columns = setOf(
            RoomRowidColumn(
              PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
          )),
          RoomTable(myFixture.classPointer("com.example.Order"), ENTITY, "Order",
                    columns = setOf(
                      RoomRowidColumn(
                        PsiElementForFakeColumn(
                          myFixture.classPointer("com.example.Order").element!!))
                    ))
        ),
        databases = setOf(
          RoomDatabase(
            myFixture.classPointer("com.example.UserDatabase"),
            entities = setOf(
              myFixture.classPointer("com.example.Address"),
              myFixture.classPointer("com.example.User")),
            daos = emptySet(),
            views = emptySet(),
          ),
          RoomDatabase(
            myFixture.classPointer("com.example.OrderDatabase"),
            entities = setOf(
              myFixture.classPointer("com.example.Address"),
              myFixture.classPointer("com.example.Order")),
            daos = emptySet(),
            views = emptySet(),
          )),
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
              RoomRowidColumn(
                PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
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
    val module = ModuleUtil.findModuleForPsiElement(psiClass)!!
    assertThat(RoomSchemaManager.getInstance(module).getSchema(psiClass.containingFile)).isNull()
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
              RoomRowidColumn(
                PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!)),
              RoomMemberColumn(myFixture.fieldPointer("com.example.User", "name"), "name"),
              RoomMemberColumn(myFixture.fieldPointer("com.example.User", "age"), "age")))),
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
              RoomMemberColumn(myFixture.fieldPointer("com.example.User", "name"), "name"),
              RoomRowidColumn(
                PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
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
              RoomMemberColumn(myFixture.fieldPointer("com.example.User", "name"), "name"),
              RoomRowidColumn(
                PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!))
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
              RoomRowidColumn(
                PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!)),
              RoomMemberColumn(
                myFixture.fieldPointer("com.example.User", "name", checkBases = true), "name"),
              RoomMemberColumn(myFixture.fieldPointer("com.example.User", "age"), "age")))),
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
              RoomMemberColumn(myFixture.fieldPointer("com.example.Mail", "body"), "body"),
              RoomFtsTableColumn(myFixture.classPointer("com.example.Mail").element!!, "Mail"),
              RoomRowidColumn(
                PsiElementForFakeColumn(myFixture.classPointer("com.example.Mail").element!!),
                PRIMARY_KEY_NAMES_FOR_FTS)
            )
          )),
        databases = emptySet(),
        daos = emptySet()
      )
    )
  }

  fun testAutoValue() {
    myFixture.addFileToProject(
      "/src/com/google/auto/value/AutoValue.java",
      //language=JAVA
    """
      package com.google.auto.value;

      @interface AutoValue {}
    """.trimIndent())

    val psiClass = myFixture.addClass(
      """
      package com.example;

      import androidx.room.Entity;
      import com.google.auto.value.AutoValue;

      @AutoValue
      @Entity
      public class User {
        public abstract String getFirstName();

        public abstract String getLastName();
      }
      """.trimIndent()
    )

    assertThat(getSchema(psiClass)).isEqualTo(
      RoomSchema(
        tables = setOf(
          RoomTable(
            myFixture.classPointer("com.example.User"),
            name = "User",
            type = ENTITY,
            columns = setOf(
              RoomMemberColumn(myFixture.methodPointer("com.example.User", "getFirstName"), "firstName"),
              RoomMemberColumn(myFixture.methodPointer("com.example.User", "getLastName"), "lastName"),
              RoomRowidColumn(PsiElementForFakeColumn(myFixture.classPointer("com.example.User").element!!) )
            )
          )),
        databases = emptySet(),
        daos = emptySet()
      )
    )
  }

  fun testSchemaForKotlinFileWithAnnotation() {
    myFixture.configureByText("User.kt",
                              """
        package com.example

        import androidx.room.ColumnInfo
        import androidx.room.Entity

        @Entity
        class User() {
          const val name = "override_name"
          @ColumnInfo(name = name) val origi<caret>nalName: String?
        }
    """.trimIndent())

    val element = myFixture.elementAtCaret

    assertThat(getSchema(element).tables.iterator().next().columns.find { it.name == "override_name" }).isNotNull()
  }

  fun testEditFragmentFindsCorrectSchema() {
    myFixture.addRoomEntity("com.example.User", "name" ofType "String", "age" ofType "int")

    myFixture.configureByText(JavaFileType.INSTANCE, """
        package com.example;

        import androidx.room.Dao;
        import androidx.room.Query;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM U<caret>ser") List<User> getAll();
        }
    """.trimIndent())

    val injectionTestFixture = InjectionTestFixture(myFixture)

    val quickEditHandler = QuickEditAction().invokeImpl(project, injectionTestFixture.topLevelEditor, injectionTestFixture.topLevelFile)
    val fragmentFile = quickEditHandler.newFile

    myFixture.openFileInEditor(fragmentFile.virtualFile)
    myFixture.moveCaret("U|ser")

    val element = myFixture.elementAtCaret

    assertThat(element).isEqualTo(myFixture.findClass("com.example.User"))
  }
}
