# AndroidSQL language support

Prerequisites:

* [PsiElement](https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/psi_elements.html)

* [PsiReference](https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/psi_references.html)

* [Custom Languages Support](../README.md)

## PsiReferences

For every token in a Room query that is defined as a column (column_name/AndroidSqlColumnName) in [grammar](./parser/androidSql.bnf), we
create a [AndroidSqlColumnPsiReference](./resolution/References.kt). `AndroidSqlColumnPsiReference` can be one of two types:
`UnqualifiedColumnPsiReference` or `QualifiedColumnPsiReference`. It depends on whether we know the table a given column belongs to. If we
use column in query like this: `TableName.columnName` we say that we know the table and use `QualifiedColumnPsiReference`.

For tables there are two types of references: `AndroidSqlSelectedTablePsiReference` and `AndroidSqlDefinedTablePsiReference`.

`AndroidSqlSelectedTablePsiReference` for table that is already in scope for the current query. In practice it means for table name inside
 column reference i.e. **selected_table_name '.' column_name**.

`AndroidSqlDefinedTablePsiReference` for table defined in the schema or using a WITH clause. It's used everywhere where we refer to table
except 'full' columns names.

## Resolution process

In order to resolve `AndroidSqlColumnPsiReference` we need to find a `SqlColumn` that corresponds to a given reference. This logic is inside
`resolveColumn` method.

For `QualifiedColumnPsiReference` it's pretty simple. We know `tableName` and try to find a `SqlTable` that corresponds to it. If we found
 the `SqlTable` we just call `SqlTable.processColumn(Processor)`.

For `UnqualifiedColumnPsiReference` we need to find all possible sources (see **SqlTable/ Column sources**) of columns in given query.
And do `SqlTable.processColumn(Processor)` for each of them.

#### Let's see example

We have a Room Entity ```kotlin @Entity class User { val age:String } ``` We have a RoomQuery `"SELECT age FROM User"` and by clicking on
`age` or `User` we want to find defining element for it (actual field or Class). The process of finding that element calls resolution
process. All classes that help to do it are in [./resolution](./resolution) directory.

Let's look into example for `age` field.
Steps of parsing text file and creating PsiElements/Reference are described [here](../README.md) and in Intellij SDK Documentation

Start with the step when we already have `AndroidSqlColumnPsiReference` for `age` field. In this example we have
`UnqualifiedColumnPsiReference` because in the query from language perspective we didn't specify the source table like `User.age`.

`UnqualifiedColumnPsiReference.resolve()` should return PsiElement that corresponds to `age` field in `User` class. In order to do that we
try to find `SqlColumn` with name `age` in one of `SqlTable`s in the given query. In our simple example it will be just `User` table. Our
next step is to obtain from the query all `SqlTable`s, and call `SqlTable.processColumn` on each of them. That is exactly what
`processSelectedSqlTables` method in [Resolution.kt](./resolution/Resolution.kt) does.

Resolution process for `UnqualifiedColumnPsiReference`:
1. Inside `processSelectedSqlTables` we traverse PSI tree in DFS style
2. For every `PsiElement` that implements `AndroidSqlTableElement` we get `SqlTable`. Note that in general obtaining `SqlTable` from
`AndroidSqlTableElement` runs another resolution process.
3. For every `SqlTable` we run `SqlTable.processColumn` that processes every column in table. Be aware that sometimes
(e.g for [SubqueryTable](./resolution/SubqueryTable.kt)) `processColumn` starts another resolution process.
4. Continue until `SqlTable.processColumn` returns false, or we traverse all the PSI tree.

## SqlTable and AndroidSqlTableElement

`SqlTable` is a class that represents the source of columns, the key functionality of `SqlTable` is that it can feed its column to the
processor. Also `SqlTable` has a link to the defining element e.g Room Entity, SELECT statement, `AndroidSqlFakePsiElement`.

`AndroidSqlTableElement` is a PsiElement that is created during parsing of a SQL query. `AndroidSqlTableElement.sqlTable` contains link to
corresponding `SqlTable`. For different subclasses of `AndroidSqlTableElement` there are different implementations of
`AndroidSqlTableElement.sqlTable` see [PsiImplUtil.getSqlTable](./psi/PsiImplUtil.kt). In most cases `AndroidSqlTableElement.sqlTable` runs
resolution process for the table, see `resolve` method in `AndroidSqlDefinedTablePsiReference`and `AndroidSqlSelectedTablePsiReference`.

## Column sources

During the resolution process, we need to traverse all valid sources for a certain column. Everything that can be source of column
implements `SqlTable` interface. Examples:

* [SubqueryTable](./resolution/SubqueryTable.kt) - In query `SELECT * FROM (SELECT column1, column2 FROM MyTable)` inner SELECT creates
`SubqueryTable` for toplevel SELECT.
* [RoomTable](./room/RoomSchema.kt) - table based on Room Entity class
* [AliasColumnTable](./resolution/AliasColumnsTable.kt) - In query `SELECT id as aliasId, name as aliasName FROM User` part
`id as aliasId, name as aliasName` creates `AliasColumnTable` with two columns **aliasId** and **aliasName**.
* [WithClauseTable](./resolution/WithClauseTable.kt)
* Table from `SqliteSchema` see SqliteTable.convertToSqlTable in `SqliteSchemaContext`.

If `SqlTable` is not defined in a query (e.g. WITH close, [AliasColumnTable](./resolution/AliasColumnsTable.kt)) we find it by calling
`processTables` on a known [AndroidSqlContext](./AndroidSqlContext.kt). You can provide `AndroidSqlContext` for `PsiFile` that contains sql
query through **com.android.tools.idea.lang.androidSql.contextProvider** extension point.

## RoomSchema

`RoomSchema` is the schema of the database defined in Java/Kotlin classes. We build and store `RoomSchema` per module in
`RoomSchemaManager`. Every module can contain 3 schemas depends on visibility scope:

* MAIN
* UNIT TEST
* ANDROID TEST

For more about scopes see `TestArtifactSearchScopes`.

If Room is present in a module we traverse all files in current scope and build `RoomTables` (implementation of SqlTable) with
`RoomColumns`. In most cases `RoomColumn` is defined by class field, but in order to support
[rowid](https://sqlite.org/lang_createtable.html#rowid) when a user hasn't specified integer primary key we have `PsiElementForFakeColumn`.

## sqlTablesInProcess

SqlTable.processColumns takes `sqlTablesInProcess: MutableSet<PsiElement>` as the second argument. We need this set to avoid infinite
recursion. In query Columns of **A** SqlTable can be defined through **B** SqlTable and at the same time Columns of **B** SqlTable can be
defined through **A** SqlTable. When we try to process all columns of **A** we can end up in infinite recursion. Just like in simple DFS we
store table that currently in process to avoid it.

Example of **valid** recursive query:

 ``` WITH recTable AS (SELECT 1 AS level UNION ALL SELECT level + 1 FROM recTable WHERE level < 10) SELECT level FROM recTable ```

Example of **invalid** recursive query:

 ``` WITH t1 AS (SELECT * FROM t2), t2 AS (select * from t1 WHERE <caret>) SELECT * FROM t1" ```
