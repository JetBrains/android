/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:Suppress("UElementAsPsi")

package com.android.tools.idea.nav.safeargs.codegen.gradle

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import java.io.File

@RunsInEdt
class SafeArgsGeneratedJavaCodeMatchTest {
  private val moduleName = "javaapp"
  private val projectRule = AndroidGradleProjectRule()
  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture
  //TODO (b/162520387): Do not ignore these methods when testing.
  private val IGNORED_METHODS = setOf("equals", "hashCode", "toString", "getActionId", "getArguments")

  @get:Rule
  val expect: Expect = Expect.create()

  @get:Rule
  val enableSafeArgsCodeGen = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun initProject() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)
    // to be able to change the project before import, we copy it into a temp folder
    val testSrc = resolveWorkspacePath("tools/adt/idea/nav/safeargs/testData/projects/SafeArgsTestApp")
    val container = temporaryFolder.newFile("TestApp")
    testSrc.toFile().copyRecursively(container, overwrite = true)

    val settingsFile = container.resolve("settings.gradle").also {
      assertWithMessage("settings file should exist").that(it.exists()).isTrue()
    }
    // update settings to only include the desired module
    settingsFile.writeText("""
      include ':$moduleName'
    """.trimIndent())

    projectRule.fixture.testDataPath = temporaryFolder.root.absolutePath
    projectRule.load("TestApp") { projectRoot ->
      // Create a sample class we can use as a context PsiElement later
      File(projectRoot, "common/src/main/java/FooClass.java").apply {
        parentFile.mkdirs()
        createNewFile()
        writeText(
          // language=java
          """
            class FooClass
          """.trimIndent())
      }
    }

    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  private fun compile(): Collection<ClassDescription> {
    val assembleDebug = projectRule.invokeTasks(GENERATE_TASK)
    assertThat(assembleDebug.isBuildSuccessful).isTrue()

    LocalFileSystem.getInstance().refresh(false)
    val codeOutDir = File(projectRule.project.basePath, "$moduleName/$PLUGIN_OUT_DIR").also {
      assertWithMessage("should be able to find generated navigation code").that(it.exists()).isTrue()
    }
    // parse generated code
    val allGeneratedCode = listOf(codeOutDir).flatMap(::loadClasses).toSet()
    // delete generated code
    assertThat(codeOutDir.deleteRecursively()).isTrue()
    LocalFileSystem.getInstance().refresh(false)

    return allGeneratedCode
  }

  @Ignore("b/246884723")
  @Test
  @RunsInEdt
  fun compiledJavaCodeMatchesInMemoryPsi() {
    val allGeneratedCode = compile()

    // now find all that code via other means (in memory codegen) and assert it is the same.
    val psiFacade = JavaPsiFacade.getInstance(projectRule.project)
    val scope = fixture.findClass("FooClass").resolveScope

    allGeneratedCode.forEach { generated ->
      val psiClass = psiFacade.findClass(generated.qualifiedName, scope)!!
      val psiDescription = psiClass.toDescription()

      expect.withMessage(generated.qualifiedName).that(psiDescription.qualifiedName).isEqualTo(generated.qualifiedName)
      expect.withMessage(generated.qualifiedName).that(psiDescription.methods).containsExactlyElementsIn(generated.methods)
      expect.withMessage(generated.qualifiedName).that(psiDescription.fields).containsExactlyElementsIn(generated.fields)
    }
  }

  private fun loadClasses(classesOut: File): List<ClassDescription> {
    return classesOut.walkTopDown()
      .filter { it.name.endsWith(".java") }
      .toList()
      .flatMap { generatedSourceFile -> generatedSourceFile.loadClassesDescriptions() }
  }

  private fun File.loadClassesDescriptions(): List<ClassDescription> {
    val descriptions = mutableListOf<ClassDescription>()

    val virtual = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: throw IllegalArgumentException("cannot find $this")
    val psi = PsiManager.getInstance(projectRule.project).findFile(virtual)
    val uast = psi.toUElement()!!
    uast.accept(object : AbstractUastVisitor() {
      override fun visitClass(node: UClass): Boolean {
        node.javaPsi.takeIf { it.modifierSet().contains(JvmModifier.PUBLIC) }
          ?.toDescription()
          ?.let { descriptions.add(it) }
        return super.visitClass(node)
      }
    })
    return descriptions
  }

  private fun PsiClass.toDescription() = ClassDescription(
    qualifiedName = this.qualifiedName!!,
    methods = (methods + constructors)
      .filter { it.modifierSet().contains(JvmModifier.PUBLIC) }
      .map { it.toDescription() }
      .filter { !IGNORED_METHODS.contains(it.name) }
      .sortedBy { it.name }
      .toSet(),
    fields = fields
      .filter { it.modifierSet().contains(JvmModifier.PUBLIC) }
      .map { it.toDescription() }
      .sortedBy { it.name }
      .toSet()
  )

  private fun PsiMethod.toDescription() = MethodDescription(
    name = this.name,
    type = this.returnType?.toDescription(),
    params = this.parameters
      .filterIsInstance<PsiParameter>()
      .map { it.toDescription() }
      .toSet(),
    modifiers = this.modifierSet()
  )

  private fun PsiParameter.toDescription() = ParamDescription(
    name = this.name,
    type = this.type.toDescription(),
    modifiers = this.modifierSet().filter { it != JvmModifier.PACKAGE_LOCAL }.toSet()
  )

  private fun PsiField.toDescription() = FieldDescription(
    name = this.name,
    type = this.type.toDescription(),
    modifiers = this.modifierSet()
  )

  private fun PsiType.toDescription() = this.canonicalText.substringAfterLast('.').substringAfterLast('$')

  private data class ClassDescription(
    val qualifiedName: String,
    val methods: Set<MethodDescription>,
    val fields: Set<FieldDescription>
  )

  private data class MethodDescription(
    val name: String,
    val type: String?,
    val modifiers: Set<JvmModifier>,
    val params: Set<ParamDescription>)

  private data class FieldDescription(
    val name: String,
    val type: String,
    val modifiers: Set<JvmModifier>)

  private data class ParamDescription(
    val name: String,
    val type: String,
    val modifiers: Set<JvmModifier>)

  private fun PsiModifierListOwner.modifierSet() = JvmModifier.values().filter { hasModifier(it) }.toSet()

  companion object {
    const val PLUGIN_OUT_DIR = "build/generated/source/navigation-args/debug"
    const val GENERATE_TASK = "generateSafeArgsDebug"
  }
}