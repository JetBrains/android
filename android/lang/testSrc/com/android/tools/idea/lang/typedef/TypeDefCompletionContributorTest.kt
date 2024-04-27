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
package com.android.tools.idea.lang.typedef

import com.android.tools.idea.lang.typedef.CompletionType.JAVA_CONSTRUCTOR
import com.android.tools.idea.lang.typedef.CompletionType.JAVA_METHOD
import com.android.tools.idea.lang.typedef.CompletionType.KOTLIN_CONSTRUCTOR_NAMED
import com.android.tools.idea.lang.typedef.CompletionType.KOTLIN_CONSTRUCTOR_POSITIONAL
import com.android.tools.idea.lang.typedef.CompletionType.KOTLIN_FUNCTION_NAMED
import com.android.tools.idea.lang.typedef.CompletionType.KOTLIN_FUNCTION_POSITIONAL
import com.android.tools.idea.lang.typedef.SourceLanguage.JAVA
import com.android.tools.idea.lang.typedef.SourceLanguage.KOTLIN
import com.android.tools.idea.lang.typedef.TypeDef.Type.INT
import com.android.tools.idea.lang.typedef.TypeDef.Type.LONG
import com.android.tools.idea.lang.typedef.TypeDef.Type.STRING
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.onEdt
import com.google.common.base.CaseFormat
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

const val TYPE_DEF_USAGE_PACKAGE = "typedef.usage"
const val FUNCTION_DEFINITION_PACKAGE = "function.definition"
const val COMPLETION_PACKAGE = "completion.pkg"

/**
 * Tests both the Java and Kotlin flavors of [TypeDefCompletionContributor] for typedefs,
 * annotations, and usages that are all part of the project source.
 */
@RunWith(Parameterized::class)
class TypeDefCompletionContributorSourceTest(
  private val typeDefLanguage: SourceLanguage,
  private val usageLanguage: SourceLanguage,
  private val completionType: CompletionType,
) {
  init {
    require(!completionType.isNamedCompletion() || usageLanguage == KOTLIN) {
      "Cannot use named arguments with non-Kotlin methods."
    }
  }

  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }
  private val typeDefUsages = typeDefUsages(typeDefLanguage)
  private val addedFiles: MutableList<PsiFile> = mutableListOf()

  companion object {
    @Parameters(name = "sourceCompletion_{0}TypeDef_{1}Usage_{2}Completion")
    @JvmStatic
    fun data(): List<Array<Any>> {
      val langPairs =
        SourceLanguage.values().flatMap { typeDefLang ->
          SourceLanguage.values().map { usageLang -> typeDefLang to usageLang }
        }
      return langPairs.flatMap {
        CompletionType.values().mapNotNull { completionType ->
          if (completionType.isNamedCompletion() && it.second != KOTLIN) null
          else arrayOf(it.first, it.second, completionType)
        }
      }
    }
  }

  @Before
  fun setUp() {
    // Add source for the typedefs themselves (e.g. annotation class IntDef)
    for (type in TypeDef.Type.values()) {
      fixture.addTypeDefSource(type).also(addedFiles::add)
    }
    // Add source for the usages of those typedefs (e.g. @IntDef({APPLE, BANANA, CHERRY}) annotation
    // class Fruit)
    when (typeDefLanguage) {
      JAVA ->
        for (javaUsage in javaTypeDefUsages) {
          fixture.addJavaTypeDefUsage(javaUsage).also(addedFiles::add)
        }
      KOTLIN ->
        // In Kotlin make sure to create both indexed- and named-argument versions.
        for ((first, second) in kotlinTypeDefUsages.chunked(2)) {
          fixture.addKotlinTypeDefUsage(first).also(addedFiles::add)
          fixture.addKotlinTypeDefUsage(second, useNamedArgument = true).also(addedFiles::add)
        }
    }

    // Add source for the usages of the annotations (e.g. fun myFunction(@Fruit param: Int) )
    for (usage in typeDefUsages) {
      fixture
        .addFileToProject(
          usage.toAnnotationUsageFilename(usageLanguage),
          usage.toAnnotationUsage(usageLanguage, typeDefLanguage)
        )
        .also(addedFiles::add)
    }
  }

  @After
  fun tearDown() {
    addedFiles.map(PsiFile::getVirtualFile).forEach(VfsTestUtil::deleteFile)
    addedFiles.clear()
  }

  @Test
  fun completion() {
    fun TypeDefUsage.getResults(): Array<LookupElement> {
      val contents =
        when (completionType) {
          JAVA_METHOD -> toJavaMethodCompletionSrc(explicitCompanion = usageLanguage == KOTLIN)
          JAVA_CONSTRUCTOR -> toJavaConstructorCompletionSrc()
          KOTLIN_FUNCTION_POSITIONAL -> toKotlinFunctionCompletionSrc()
          KOTLIN_FUNCTION_NAMED -> toKotlinFunctionCompletionSrc(prefix = "param = ")
          KOTLIN_CONSTRUCTOR_POSITIONAL -> toKotlinConstructorCompletionSrc()
          KOTLIN_CONSTRUCTOR_NAMED -> toKotlinConstructorCompletionSrc(prefix = "param = ")
        }
      val file = fixture.loadNewFile(toCompletionFilename(completionType.language), contents)
      val results = fixture.completeBasic()
      // Clean up so the next time through the `forEach`, this file is gone.
      VfsTestUtil.deleteFile(file.virtualFile)
      return results
    }

    // 🤮 WARNING 🤮: This is a disgusting hack to work around
    // https://youtrack.jetbrains.com/issue/KTIJ-27420
    fun TypeDefUsage.getPassingResults(): Array<LookupElement> = runBlocking {
      withTimeout(2.minutes) {
        var results = getResults()
        while (!results.willPass(this@getPassingResults, TYPE_DEF_USAGE_PACKAGE)) {
          yield() // Allow for cancellation.
          tearDown()
          setUp()
          results = getResults()
        }
        results
      }
    }

    for (usage in typeDefUsages) {
      with(usage.getPassingResults()) {
        checkStartsWith(usage.annotationName, usage.names, TYPE_DEF_USAGE_PACKAGE)
        checkContainsOnce(usage.annotationName, usage.names, TYPE_DEF_USAGE_PACKAGE)
      }
    }
  }

  @Test
  fun noCompletionAfterDot() {
    typeDefUsages.forEach {
      val contents =
        when (completionType) {
          JAVA_METHOD ->
            it.toJavaMethodCompletionSrc(
              prefix = "Integer.",
              explicitCompanion = usageLanguage == KOTLIN
            )
          JAVA_CONSTRUCTOR -> it.toJavaConstructorCompletionSrc(prefix = "Integer.")
          KOTLIN_FUNCTION_POSITIONAL -> it.toKotlinFunctionCompletionSrc(prefix = "Int.")
          KOTLIN_FUNCTION_NAMED -> it.toKotlinFunctionCompletionSrc(prefix = "param = Int.")
          KOTLIN_CONSTRUCTOR_POSITIONAL -> it.toKotlinConstructorCompletionSrc(prefix = "Int.")
          KOTLIN_CONSTRUCTOR_NAMED -> it.toKotlinConstructorCompletionSrc(prefix = "param = Int.")
        }
      val file = fixture.loadNewFile(it.toCompletionFilename(completionType.language), contents)

      assertThat(
          fixture.completeBasic().map(LookupElement::getLookupString).filter(it.names::contains)
        )
        .isEmpty()

      // Clean up so the next time through the `forEach`, this file is gone.
      VfsTestUtil.deleteFile(file.virtualFile)
    }
  }
}

/**
 * Tests both the Java and Kotlin flavors of [TypeDefCompletionContributor] for typedefs,
 * annotations, and usages that are part of the source of a loaded JAR library.
 */
@RunWith(Parameterized::class)
class TypeDefCompletionContributorLibraryTest(private val completionType: CompletionType) {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }
  private val typeDefUsages = typeDefUsages(completionType.language)
  private val addedLibraries: MutableList<Library> = mutableListOf()

  companion object {
    @Parameters(name = "libraryCompletion_{0}")
    @JvmStatic
    fun data(): List<Array<Any>> = CompletionType.values().map { arrayOf(it) }
  }

  @Before
  fun setUp() {
    val classesRoot =
      JarFileSystem.getInstance()
        .refreshAndFindFileByPath(
          AndroidTestBase.getTestDataPath() + "/libs/lang/typedef/Library.jar!/"
        )
    PsiTestUtil.addProjectLibrary(fixture.module, "mylib", listOf(classesRoot), listOf(classesRoot))
      .also(addedLibraries::add)
  }

  @After
  fun tearDown() {
    for (library in addedLibraries) {
      PsiTestUtil.removeLibrary(fixture.module, library)
    }
    addedLibraries.clear()
  }

  @Test
  fun libraryCompletion() {
    val funcDefPkg = "library.$FUNCTION_DEFINITION_PACKAGE"

    fun TypeDefUsage.getResults(): Array<LookupElement> {
      val contents =
        when (completionType) {
          JAVA_METHOD -> toJavaMethodCompletionSrc(funcDefPkg)
          JAVA_CONSTRUCTOR -> toJavaConstructorCompletionSrc(funcDefPkg)
          KOTLIN_FUNCTION_POSITIONAL -> toKotlinFunctionCompletionSrc(funcDefPkg)
          KOTLIN_FUNCTION_NAMED -> toKotlinFunctionCompletionSrc(funcDefPkg, prefix = "param = ")
          KOTLIN_CONSTRUCTOR_POSITIONAL -> toKotlinConstructorCompletionSrc(funcDefPkg)
          KOTLIN_CONSTRUCTOR_NAMED ->
            toKotlinConstructorCompletionSrc(funcDefPkg, prefix = "param = ")
        }
      val file = fixture.loadNewFile(toCompletionFilename(completionType.language), contents)
      val results = fixture.completeBasic()
      // Clean up so the next time through the `forEach`, this file is gone.
      VfsTestUtil.deleteFile(file.virtualFile)
      return results
    }

    // 🤮 WARNING 🤮: This is a disgusting hack to work around
    // https://youtrack.jetbrains.com/issue/KTIJ-27420
    fun TypeDefUsage.getPassingResults(): Array<LookupElement> = runBlocking {
      withTimeout(2.minutes) {
        var results = getResults()
        while (!results.willPass(this@getPassingResults, "library.$TYPE_DEF_USAGE_PACKAGE")) {
          yield()
          tearDown()
          setUp()
          results = getResults()
        }
        results
      }
    }

    for (usage in typeDefUsages) {
      with(usage.getPassingResults()) {
        checkStartsWith(usage.annotationName, usage.names, "library.$TYPE_DEF_USAGE_PACKAGE")
        checkContainsOnce(usage.annotationName, usage.names, "library.$TYPE_DEF_USAGE_PACKAGE")
      }
    }
  }
}

private val guitar = TypeDefUsage(INT, "Guitar", "FENDER" to "0", "GIBSON" to "1", "MARTIN" to "2")
private val microphone =
  TypeDefUsage(INT, "Microphone", "SHURE" to "0", "BLUE" to "1", "RØDE" to "2")
private val amplifier =
  TypeDefUsage(INT, "Amplifier", "PEAVEY" to "0", "ORANGE" to "1", "MARSHALL" to "2")
private val fruit = TypeDefUsage(LONG, "Fruit", "APPLE" to "0L", "BANANA" to "1L", "CHERRY" to "2L")
private val mushroom =
  TypeDefUsage(LONG, "Mushroom", "PORTOBELLO" to "0L", "CHANTERELLE" to "1L", "MOREL" to "2L")
private val vegetable =
  TypeDefUsage(LONG, "Vegetable", "ASPARAGUS" to "0L", "BROCCOLI" to "1L", "CARROT" to "2L")
private val urbanistChannel =
  TypeDefUsage(
    STRING,
    "UrbanistChannel",
    "STRONG_TOWNS" to "Chuck Marohn",
    "CLIMATE_TOWN" to "Rollie Williams",
    "NOT_JUST_BIKES" to "Jason Slaughter"
  )
private val transportationMode =
  TypeDefUsage(
    STRING,
    "TransportationMode",
    "BIKE" to "bicycle",
    "BUS" to "bus",
    "LIGHT_RAIL" to "light-rail"
  )
private val electricUnicycle =
  TypeDefUsage(
    STRING,
    "ElectricUnicycle",
    "INMOTION" to "inmotion",
    "BEGODE" to "gotway",
    "KING_SONG" to "king-song",
    "VETERAN" to "leaperkim"
  )

// Arbitrarily chosen. Make sure this is even so we can do one with "value = " syntax and one
// without.
private val kotlinTypeDefUsages =
  listOf(guitar, microphone, fruit, mushroom, urbanistChannel, transportationMode)
private val javaTypeDefUsages = listOf(amplifier, vegetable, electricUnicycle)

private fun typeDefUsages(typeDefLanguage: SourceLanguage) =
  when (typeDefLanguage) {
    JAVA -> javaTypeDefUsages
    KOTLIN -> kotlinTypeDefUsages
  }

private fun Array<LookupElement>.willPass(usage: TypeDefUsage, typeDefUsagePkg: String): Boolean {
  val startsWith =
    slice(usage.names.indices).containsOnce(usage.annotationName, usage.names, typeDefUsagePkg)
  val containsOnce = toList().containsOnce(usage.annotationName, usage.names, typeDefUsagePkg)
  return startsWith && containsOnce
}

private fun List<LookupElement>.containsOnce(
  annotationName: String,
  elements: List<String>,
  typeDefUsagePkg: String
): Boolean {
  if ((elements - map(LookupElement::getLookupString).toSet()).isNotEmpty()) return false
  return filter { it.lookupString in elements }
    .map { LookupElementPresentation().also(it::renderElement) }
    .all {
      it.typeText == "@$annotationName" && it.tailText == " ($typeDefUsagePkg)" && it.isItemTextBold
    }
}

private fun List<LookupElement>.assertContainsOnce(
  annotationName: String,
  elements: List<String>,
  typeDefUsagePkg: String
) {
  // Check text of completions.
  assertThat(map(LookupElement::getLookupString).filter(elements::contains))
    .containsExactlyElementsIn(elements)
  // Check formatting.
  filter { it.lookupString in elements }
    .map { LookupElementPresentation().also(it::renderElement) }
    .forEach {
      assertThat(it.typeText).isEqualTo("@$annotationName")
      assertThat(it.tailText).isEqualTo(" ($typeDefUsagePkg)")
      assertThat(it.isItemTextBold).isTrue()
    }
}

private fun Array<LookupElement>.checkContainsOnce(
  annotationName: String,
  elements: List<String>,
  typeDefUsagePkg: String
) {
  toList().assertContainsOnce(annotationName, elements, typeDefUsagePkg)
}

private fun Array<LookupElement>.checkStartsWith(
  annotationName: String,
  elements: List<String>,
  typeDefUsagePkg: String
) {
  slice(elements.indices).assertContainsOnce(annotationName, elements, typeDefUsagePkg)
}

private fun CodeInsightTestFixture.addTypeDefSource(type: TypeDef.Type): PsiFile =
  addFileToProject(type.toFilename(), type.toSrc())

private fun CodeInsightTestFixture.addKotlinTypeDefUsage(
  usage: TypeDefUsage,
  useNamedArgument: Boolean = false
): PsiFile {
  val filename = "/src/${TYPE_DEF_USAGE_PACKAGE.replace('.', '/')}/${usage.annotationName}.kt"
  val contents = usage.toKotlinAnnotationDefinition(useNamedArgument)
  return addFileToProject(filename, contents)
}

private fun CodeInsightTestFixture.addJavaTypeDefUsage(usage: TypeDefUsage): PsiFile {
  val filename = "/src/${TYPE_DEF_USAGE_PACKAGE.replace('.', '/')}/${usage.annotationName}.java"
  val contents = usage.toJavaAnnotationDefinition()
  return addFileToProject(filename, contents)
}

private fun TypeDef.Type.toSrc() =
  // language=kotlin
  """
  package androidx.annotation
  @Retention(AnnotationRetention.SOURCE)
  @Target(AnnotationTarget.ANNOTATION_CLASS)
  annotation class $annotationName(
    /** Defines the allowed constants for this element. */
    vararg val value: $kotlinTypeName = [],
    /** Defines whether the constants can be used as a flag, or just as an enum (the default). */
    val flag: Boolean = false,
    /** Whether any other values are allowed. */
    val open: Boolean = false
  )
  """
    .trimIndent()

private fun TypeDef.Type.toFilename() = "/src/androidx/annotation/$annotationName.kt"

/** Represents usage of a TypeDef. Just for convenience and to avoid repetition. */
private data class TypeDefUsage(
  val type: TypeDef.Type,
  val annotationName: String,
  val values: List<Pair<String, String>>
) {
  constructor(
    type: TypeDef.Type,
    annotationName: String,
    vararg values: Pair<String, String>
  ) : this(type, annotationName, values.toList())

  val names: List<String> = values.map { it.first }

  fun toAnnotationUsage(
    usageLanguage: SourceLanguage,
    typeDefLanguage: SourceLanguage,
    usagePkg: String = TYPE_DEF_USAGE_PACKAGE,
    defPkg: String = FUNCTION_DEFINITION_PACKAGE
  ): String =
    when (usageLanguage) {
      JAVA -> toJavaAnnotationUsage(typeDefLanguage, usagePkg, defPkg)
      KOTLIN -> toKotlinAnnotationUsage(typeDefLanguage, usagePkg, defPkg)
    }

  /** Returns Java code of this usage. */
  fun toJavaAnnotationDefinition(pkg: String = TYPE_DEF_USAGE_PACKAGE): String {
    val nameList = values.joinToString(", ") { it.first }
    // language=java
    return """
      package $pkg;
      import androidx.annotation.${type.annotationName};
      public class ${annotationName}s {
        ${values.joinToString("\n        ") { it.toJavaDeclaration() }}
        @${type.kotlinTypeName}Def({$nameList})
        public @interface $annotationName {}
      }
      """
      .trimIndent()
  }

  fun toJavaAnnotationUsage(
    typeDefLanguage: SourceLanguage,
    usagePkg: String = TYPE_DEF_USAGE_PACKAGE,
    defPkg: String = FUNCTION_DEFINITION_PACKAGE
  ): String {
    // Java definition requires an outer class.
    val annotationImport =
      when (typeDefLanguage) {
        JAVA -> "$usagePkg.${annotationName}s.$annotationName"
        KOTLIN -> "$usagePkg.$annotationName"
      }
    val nonTypeDefParams = "String p1, int p2, long p3"
    // language=java
    return """
      package $defPkg;
      import $annotationImport;
      public class ${annotationName}Usage {
        public ${annotationName}Usage($nonTypeDefParams, @$annotationName ${type.javaTypeName} param) {}
        public static void use$annotationName($nonTypeDefParams, @$annotationName ${type.javaTypeName} param) {}
      }
      """
      .trimIndent()
  }

  fun toJavaMethodCompletionSrc(
    funcDefPkg: String = FUNCTION_DEFINITION_PACKAGE,
    prefix: String = nonTypeDefArgs,
    explicitCompanion: Boolean =
      false, // TODO(b/275626083) remove when companion methods resolve correctly
  ): String =
    // language=java
    """
    package $COMPLETION_PACKAGE;
    import $funcDefPkg.${annotationName}Usage;
    class ${annotationName}Completion {
      public static void myFunction() {
        ${annotationName}Usage.${if (explicitCompanion) "Companion." else ""}use$annotationName($prefix<caret>
      }
    }
    """
      .trimIndent()

  fun toJavaConstructorCompletionSrc(
    funcDefPkg: String = FUNCTION_DEFINITION_PACKAGE,
    prefix: String = nonTypeDefArgs,
  ): String =
    // language=java
    """
    package $COMPLETION_PACKAGE;
    import $funcDefPkg.${annotationName}Usage;
    class ${annotationName}Completion {
      public static void myFunction() {
        ${annotationName}Usage usage = new ${annotationName}Usage($prefix<caret>
      }
    }
    """
      .trimIndent()

  /**
   * Returns Kotlin code of this usage.
   *
   * The only restriction for typedef values is that they are const. So we construct it in all
   * possible ways.
   */
  fun toKotlinAnnotationDefinition(
    useNamedArgument: Boolean,
    pkg: String = TYPE_DEF_USAGE_PACKAGE
  ): String {
    require(values.size >= 3) { "Cannot generate Kotlin code without at least 3 values." }

    val holderObjectName =
      "${CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, values[1].first)}Holder"
    val names =
      listOf(values[0].first, "$holderObjectName.${values[1].first}") +
        values.drop(2).map { "$annotationName.${it.first}" }
    val nameList = names.joinToString(", ")
    val typeDefArgument = if (useNamedArgument) "value = [$nameList]" else nameList
    // language=kotlin
    return """
      package $pkg
      import androidx.annotation.${type.annotationName}
      ${values[0].toKotlinDeclaration()}
      object $holderObjectName {
        ${values[1].toKotlinDeclaration()}
      }
      @${type.annotationName}($typeDefArgument)
      annotation class $annotationName {
        companion object {
          ${values.drop(2).joinToString("\n") { it.toKotlinDeclaration() }}
        }
      }
      """
      .trimIndent()
  }

  fun toKotlinAnnotationUsage(
    typeDefLanguage: SourceLanguage,
    usagePkg: String = TYPE_DEF_USAGE_PACKAGE,
    defPkg: String = FUNCTION_DEFINITION_PACKAGE
  ): String {
    // Java definition requires an outer class.
    val annotationImport =
      when (typeDefLanguage) {
        JAVA -> "$usagePkg.${annotationName}s.$annotationName"
        KOTLIN -> "$usagePkg.$annotationName"
      }
    val nonTypeDefParams = "p1: String, p2: Int, p3: Long"
    // language=kotlin
    return """
      package $defPkg
      import $annotationImport
      class ${annotationName}Usage($nonTypeDefParams, @$annotationName param: ${type.kotlinTypeName}) {
        companion object {
          @JvmStatic // So it can be called from Java
          fun use$annotationName($nonTypeDefParams, @$annotationName param: ${type.kotlinTypeName}) {}
        }
      }
      """
      .trimIndent()
  }

  fun toKotlinFunctionCompletionSrc(
    funcDefPkg: String = FUNCTION_DEFINITION_PACKAGE,
    prefix: String = nonTypeDefArgs
  ): String =
    // language=kotlin
    """
      package $COMPLETION_PACKAGE
      import $funcDefPkg.${annotationName}Usage
      fun myFunction() {
        ${annotationName}Usage.use$annotationName($prefix<caret>
      }
      """
      .trimIndent()

  fun toKotlinConstructorCompletionSrc(
    funcDefPkg: String = FUNCTION_DEFINITION_PACKAGE,
    prefix: String = nonTypeDefArgs
  ): String =
    // language=kotlin
    """
      package $COMPLETION_PACKAGE
      import $funcDefPkg.${annotationName}Usage
      fun myFunction() {
        val usage = ${annotationName}Usage($prefix<caret>
      }
      """
      .trimIndent()

  fun toAnnotationUsageFilename(lang: SourceLanguage): String =
    "/src/${FUNCTION_DEFINITION_PACKAGE.replace('.', '/')}/${annotationName}Usage.${lang.extension}"

  fun toCompletionFilename(lang: SourceLanguage): String =
    "/src/${COMPLETION_PACKAGE.replace('.', '/')}/${annotationName}Completion.${lang.extension}"

  private fun Pair<String, String>.toJavaDeclaration() =
    "public static final ${type.javaTypeName} $first = $maybeQuotedSecond;"

  private fun Pair<String, String>.toKotlinDeclaration() = "const val $first = $maybeQuotedSecond"

  private val Pair<String, String>.maybeQuotedSecond
    get() = if (type == STRING) "\"${second}\"" else second

  private val nonTypeDefArgs = "/* p1= */ \"JustAString\", /* p2= */ 3, /* p3= */ 8675309L, "
}

enum class SourceLanguage(val extension: String) {
  JAVA("java"),
  KOTLIN("kt");

  override fun toString(): String = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name)
}

enum class CompletionType(val language: SourceLanguage) {
  JAVA_METHOD(JAVA),
  JAVA_CONSTRUCTOR(JAVA),
  KOTLIN_FUNCTION_POSITIONAL(KOTLIN),
  KOTLIN_FUNCTION_NAMED(KOTLIN),
  KOTLIN_CONSTRUCTOR_POSITIONAL(KOTLIN),
  KOTLIN_CONSTRUCTOR_NAMED(KOTLIN);

  fun isNamedCompletion() = this == KOTLIN_FUNCTION_NAMED || this == KOTLIN_CONSTRUCTOR_NAMED

  override fun toString(): String = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name)
}
