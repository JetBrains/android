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
package com.android.tools.idea.lang.androidSql.room

import com.android.tools.idea.lang.androidSql.AndroidSqlContext
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumn
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.android.tools.idea.lang.androidSql.resolution.BindParameter
import com.android.tools.idea.lang.androidSql.resolution.FtsSqlType
import com.android.tools.idea.lang.androidSql.resolution.JavaFieldSqlType
import com.android.tools.idea.lang.androidSql.resolution.PRIMARY_KEY_NAMES
import com.android.tools.idea.lang.androidSql.resolution.PsiElementPointer
import com.android.tools.idea.lang.androidSql.resolution.SqlType
import com.android.tools.idea.lang.androidSql.room.RoomTable.Type
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getUastParentOfType

typealias PsiClassPointer = SmartPsiElementPointer<out PsiClass>
typealias PsiMemberPointer = SmartPsiElementPointer<out PsiMember>

data class RoomDatabase(
  /** Annotated class. */
  val psiClass: PsiClassPointer,

  /** Classes mentioned in the `entities` annotation parameter. These may not actually be `@Entities` if the code is wrong.  */
  val entities: Set<PsiClassPointer>,

  /** Classes mentioned in the `views` annotation parameter. These may not actually be `@DatabaseView` if the code is wrong.  */
  val views: Set<PsiClassPointer>,

  val daos: Set<PsiClassPointer>
)

/**
 * An [AndroidSqlTable] defined by a Room `@Entity`.
 */
data class RoomTable(
  /** Annotated class. */
  val psiClass: PsiClassPointer,

  /** [Type] of the table. */
  val type: Type,

  /** Name of the table: take from the class name or the annotation parameter. */
  override val name: String,

  /**
   * [PsiElement] that determines the table name and should be the destination of references from SQL.
   *
   * This can be either the class itself or the annotation element.
   */
  val nameElement: PsiElementPointer = psiClass,

  /** Columns present in the table representing this entity. */
  val columns: Set<AndroidSqlColumn> = emptySet()
) : AndroidSqlTable {
  override fun processColumns(processor: Processor<AndroidSqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>) = ContainerUtil.process(columns, processor)
  override val definingElement: PsiElement get() = psiClass.element!!
  override val resolveTo: PsiElement get() = nameElement.element!!
  override val isView = type == Type.VIEW

  enum class Type {
    /** Created from a class annotated with `@Entity`. */
    ENTITY,

    /** Created from a class annotated with `@DatabaseView`. */
    VIEW,
  }
}

/**
 * An [AndroidSqlColumn] defined by a field in a Room `@Entity`.
 */
data class RoomMemberColumn(
  /** Field that defines this column. */
  val psiMember: PsiMemberPointer,

  /** Effective name of the column, either taken from the field or from `@ColumnInfo`. */
  override val name: String,

  /** The [PsiElement] that defines the column name. */
  val nameElement: PsiElementPointer = psiMember,
  override val isPrimaryKey: Boolean = false,
  override val alternativeNames: Set<String> = emptySet()
) : AndroidSqlColumn {
  override val type: SqlType? = when(definingElement) {
      is PsiField -> (definingElement as PsiField).type.presentableText.let(::JavaFieldSqlType)
      is PsiMethod -> (definingElement as PsiMethod).returnType?.presentableText?.let(::JavaFieldSqlType)
      else -> null
  }
  override val definingElement: PsiElement get() = psiMember.element!!
  override val resolveTo: PsiElement get() = nameElement.element!!
}

/**
 * Represents column that equals table name in Fts table.
 */
data class RoomFtsTableColumn(
  override val definingElement: PsiElement,
  override val name: String
) : AndroidSqlColumn {
  override val type: SqlType get() = FtsSqlType
}

/**
 * Represents the special SQLite `rowid` column which is not explicitly defined in Room entities.
 *
 * See [https://sqlite.org/lang_createtable.html#rowid]
 */
data class RoomRowidColumn(
  override val definingElement: PsiElement,
  override val alternativeNames: Set<String> = PRIMARY_KEY_NAMES
) : AndroidSqlColumn {
  override val type: SqlType = JavaFieldSqlType("int")
  override val isPrimaryKey: Boolean get() = true
  override val isImplicit: Boolean get() = super.isImplicit
  override val name: String? get() = null
}

/**
 * Represent PsiElement that is used for [RoomRowidColumn]
 *
 * There is no real field for [RoomRowidColumn] so we resolve it to [PsiElementForFakeColumn] that navigate to table column belongs to.
 */
data class PsiElementForFakeColumn(val tablePsiElement: PsiClass): PsiElement by tablePsiElement {
  override fun getNavigationElement(): PsiElement {
    return tablePsiElement
  }

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return another is PsiElementForFakeColumn && another.tablePsiElement == tablePsiElement
  }
}

/** Represents a Room `@Dao` class. */
data class Dao(val psiClass: PsiClassPointer)

/**
 * Schema defined using Room annotations in Java/Kotlin code.
 */
data class RoomSchema(
  val databases: Set<RoomDatabase>,
  val tables: Set<RoomTable>,
  val daos: Set<Dao>
) {
  fun findTable(psiClass: PsiClass) = tables.find { it.psiClass.element == psiClass }
}

/**
 * [AndroidSqlContext] for queries in Room's `@Query` annotations.
 */
class RoomSqlContext(private val query: AndroidSqlFile) : AndroidSqlContext {

  class Provider : AndroidSqlContext.Provider {
    override fun getContext(query: AndroidSqlFile) = RoomSqlContext(query).takeIf { it.findHostRoomAnnotation() != null }
  }

  override val bindParameters: Map<String, BindParameter>
    get() {
      return (findHostRoomAnnotation()
        ?.takeIf { RoomAnnotations.QUERY.isEquals(it.qualifiedName) }
        ?.getParentOfType<UAnnotated>()
        as? UMethod)
        ?.uastParameters
        ?.mapNotNull { uParameter ->
          when (val name = uParameter.name) {
            null -> null
            else -> BindParameter(name, uParameter.sourcePsi)
          }
        }
        ?.associateBy { it.name }
        .orEmpty()
    }

  private fun findHostRoomAnnotation(): UAnnotation? {
    return findHost()
      ?.getUastParentOfType<UAnnotation>()
      ?.takeIf {
        val qualifiedName = it.qualifiedName
        when {
          RoomAnnotations.QUERY.isEquals(qualifiedName) -> true
          RoomAnnotations.DATABASE_VIEW.isEquals(qualifiedName) -> true
          else -> false
        }
      }
  }

  private fun findHost(): PsiLanguageInjectionHost? {
    // InjectedLanguageUtil is deprecated, but works in more cases than InjectedLanguageManager, e.g. when using [QuickEditAction] ("Edit
    // RoomSql fragment" intention) it navigates from the created light VirtualFile back to the original host string. We start with the
    // recommended method, fall back to a known solution to the situation described above and eventually fall back to the deprecated method
    // which seems to handle even more cases.
    return InjectedLanguageManager.getInstance(query.project).getInjectionHost(query)
           ?: query.context as? PsiLanguageInjectionHost
           ?: InjectedLanguageUtil.findInjectionHost(query)
  }

  override fun processTables(processor: Processor<AndroidSqlTable>): Boolean {
    val hostRoomAnnotation = findHostRoomAnnotation()
    if (hostRoomAnnotation != null) {
      // We are inside a Room annotation, let's use the Room schema.
      // If we are inside Editing Fragment query does not belong to module. We need to use original file.
      val module = ModuleUtil.findModuleForPsiElement(query.originalFile) ?: return true
      return ContainerUtil.process(
        findTablesApplicableToContext(module, hostRoomAnnotation),
        amendProcessor(hostRoomAnnotation, processor)
      )
    }

    return true
  }

  private fun findTablesApplicableToContext(module: Module, hostRoomAnnotation: UAnnotation):Collection<AndroidSqlTable> {
    val schema = RoomSchemaManager.getInstance(module).getSchema(query) ?: return emptySet()
    val allTables = schema.tables
    val databases = schema.databases
    var databasesHostBelongsTo = emptyList<RoomDatabase>()
    val hostClass = hostRoomAnnotation.getContainingUClass()?.sourceElement ?: return allTables
    if (RoomAnnotations.DATABASE_VIEW.isEquals(hostRoomAnnotation.qualifiedName)) {
      databasesHostBelongsTo = databases.filter { database -> database.views.any { it.element == hostClass } }
    }
    if (RoomAnnotations.QUERY.isEquals(hostRoomAnnotation.qualifiedName)) {
      databasesHostBelongsTo = databases.filter { database -> database.daos.any { it.element == hostClass } }
    }
    if (databasesHostBelongsTo.isEmpty()) {
      return allTables
    }
    return allTables.filter { table -> databasesHostBelongsTo.any { it.entities.contains(table.psiClass) } }
  }

  /**
   * Picks the right [Processor] for tables in the schema. If [query] belongs to a `@DatabaseView` definition, skips the view
   * being defined from completion, to avoid recursive definitions.
   */
  private fun amendProcessor(
    hostRoomAnnotation: UAnnotation,
    processor: Processor<AndroidSqlTable>
  ): Processor<in AndroidSqlTable> {
    return hostRoomAnnotation.takeIf { RoomAnnotations.DATABASE_VIEW.isEquals(it.qualifiedName) }
             ?.getParentOfType<UClass>()
             ?.let { IgnoreClassProcessor(it.javaPsi, processor) }
           ?: processor
  }
}

class IgnoreClassProcessor(private val toSkip: PsiClass, private val delegate: Processor<AndroidSqlTable>) : Processor<AndroidSqlTable> {
  private val psiManager: PsiManager = PsiManager.getInstance(toSkip.project)

  override fun process(t: AndroidSqlTable?): Boolean {
    val definingClass = (t as? RoomTable)?.psiClass?.element ?: return true
    // During code completion the two classes may not be equal, because the file being edited is copied for completion purposes. But they
    // are equivalent according to the PsiManager.
    return psiManager.areElementsEquivalent(definingClass, toSkip) || delegate.process(t)
  }
}