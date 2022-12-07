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
package com.android.tools.idea.nav.safeargs.codegen.gradle

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.error.ErrorType
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.uast.UClass
import org.jetbrains.uast.kotlin.KotlinUClass
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
class SafeArgsGeneratedKotlinCodeMatchTest {
  private val moduleName = "kotlinapp"
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
      File(projectRoot, "common/src/main/java/FooClass.kt").apply {
        parentFile.mkdirs()
        createNewFile()
        writeText(
          // language=kotlin
          """
            class FooClass
          """.trimIndent())
      }
    }

    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  @Ignore("b/246884723")
  @Test
  @RunsInEdt
  fun compile() {
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

    // now find all that code via other means (in memory codegen) and assert it is the same.

    val moduleDescriptor = projectRule.project.findAppModule().getMainModule().toDescriptor()!!
    moduleDescriptor.resolveClassByFqName(FqName("com.example.safeargtest.Foo"), NoLookupLocation.WHEN_FIND_BY_FQNAME)

    allGeneratedCode.forEach { generated ->

      val classDescriptor = if (generated.isCompanionObject) {
        moduleDescriptor.resolveClassByFqName(FqName(generated.qualifiedName).parent(), NoLookupLocation.WHEN_FIND_BY_FQNAME)
          ?.companionObjectDescriptor?.toDescription()
      }
      else {
        moduleDescriptor.resolveClassByFqName(FqName(generated.qualifiedName), NoLookupLocation.WHEN_FIND_BY_FQNAME)
          ?.toDescription()
      }

      expect.withMessage(generated.qualifiedName).that(classDescriptor).isNotNull()
      classDescriptor!!.let {
        expect.withMessage(generated.qualifiedName).that(classDescriptor.qualifiedName).isEqualTo(generated.qualifiedName)
        expect.withMessage(generated.qualifiedName).that(classDescriptor.constructor).isEqualTo(generated.constructor)
        expect.withMessage(generated.qualifiedName).that(classDescriptor.methods).containsExactlyElementsIn(generated.methods)
        expect.withMessage(generated.qualifiedName).that(classDescriptor.fields).containsExactlyElementsIn(generated.fields)
      }
    }
  }

  private fun loadClasses(classesOut: File): List<ClassDescription> {
    return classesOut.walkTopDown().filter {
      it.name.endsWith("kt")
    }.toList().flatMap { generatedSourceFile ->
      generatedSourceFile.loadClassesDescriptions()
    }
  }

  private fun File.loadClassesDescriptions(): List<ClassDescription> {
    val descriptions = mutableListOf<ClassDescription>()

    val virtual = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: throw IllegalArgumentException("cannot find $this")
    val psi = PsiManager.getInstance(projectRule.project).findFile(virtual)
    val uast = psi.toUElement()!!
    uast.accept(object : AbstractUastVisitor() {
      override fun visitClass(node: UClass): Boolean {
        val descriptor = (node as KotlinUClass).sourcePsi?.descriptor as? ClassDescriptor
        descriptor?.takeIf { it.visibility == DescriptorVisibilities.PUBLIC }?.toDescription()?.let {
          descriptions.add(it)
        }
        return super.visitClass(node)
      }
    })
    return descriptions
  }

  private fun ClassDescriptor.toDescription() = ClassDescription(
    isCompanionObject = this.isCompanionObject,
    qualifiedName = this.fqNameSafe.asString(),
    constructor = (this.unsubstitutedPrimaryConstructor as? FunctionDescriptor)
      ?.takeIf { it.visibility == DescriptorVisibilities.PUBLIC }
      ?.toDescription(),
    methods = this.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.FUNCTIONS)
      .asSequence()
      .filterIsInstance<FunctionDescriptor>()
      .filter { it.visibility == DescriptorVisibilities.PUBLIC }
      .map { it.toDescription() }
      .filter { !IGNORED_METHODS.contains(it.name) }
      .sortedBy { it.name }
      .toSet(),
    fields = this.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES)
      .asSequence()
      .filterIsInstance<PropertyDescriptor>()
      .map { it.toDescription() }
      .sortedBy { it.name }
      .toSet()
  )

  private fun FunctionDescriptor.toDescription() = MethodDescription(
    name = this.name.asString(),
    type = this.returnType?.toDescription(),
    params = this.valueParameters.map { it.toDescription() }.toSet(),
    modifiers = setOf(this.visibility.toString(), this.modality.toString())
  )

  private fun PropertyDescriptor.toDescription() = FieldDescription(
    name = this.name.asString(),
    type = this.type.toDescription(),
    modifiers = setOf(this.visibility.toString(), this.modality.toString())
  )

  private fun ValueParameterDescriptor.toDescription() = ParamDescription(
    name = this.name.asString(),
    type = this.type.toDescription(),
    modifiers = setOf(this.visibility.toString())
  )

  private fun KotlinType.toDescription(): String {
    val type = if (this.isMarkedNullable) this.makeNullable() else this
    return when (type) {
      // Note: References to dependencies are not working when generating sources, e.g. NavDirections, but they are
      // not critical to verifying safe args behavior, so we're OK simply peeling the class name out of the error type
      // for now.
      is ErrorType -> type.debugMessage.removePrefix("Unresolved type for ").substringAfterLast('.').substringAfterLast('$')
      else -> type.fqName!!.shortName().asString().substringAfterLast('$')
    }
  }

  private data class ClassDescription(
    val isCompanionObject: Boolean,
    val qualifiedName: String,
    val constructor: MethodDescription?,
    val methods: Set<MethodDescription>,
    val fields: Set<FieldDescription>
  )

  private data class MethodDescription(
    val name: String,
    val type: String?,
    val modifiers: Set<String>,
    val params: Set<ParamDescription>)

  private data class FieldDescription(
    val name: String,
    val type: String,
    val modifiers: Set<String>)

  private data class ParamDescription(
    val name: String,
    val type: String,
    val modifiers: Set<String>)

  companion object {
    const val PLUGIN_OUT_DIR = "build/generated/source/navigation-args/debug"
    const val GENERATE_TASK = "generateSafeArgsDebug"
  }
}