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
package com.android.tools.idea.databinding.integration.gradle

import com.android.SdkConstants.ANDROIDX_DATA_BINDING_LIB_ARTIFACT
import com.android.SdkConstants.DATA_BINDING_LIB_ARTIFACT
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_SUPPORT
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.findClass
import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.apache.commons.io.FileUtils
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.util.TreeSet
import java.util.jar.JarFile

private fun String.pkgToPath(): String {
  return this.replace(".", "/")
}
private fun String.pathToPkg(): String {
  return this.replace("/", ".")
}

/**
 * This test uses both ASM (to walk through actual, compiled byte code) and PSI (to walk through
 * IntelliJ's view of a project). The following utility class helps convert both into a collection
 * of human readable descriptions that uniquely summarize a class, and allow comparing both of them
 * together.
 */
private object ClassDescriber {
  private val IMPORTANT_MODIFIERS: Map<String, Int> = mapOf(
    PsiModifier.PUBLIC to Opcodes.ACC_PUBLIC,
    PsiModifier.STATIC to Opcodes.ACC_STATIC)

  private val BASIC_ASM_TYPES: Map<PsiType, String> = mapOf(
    PsiTypes.voidType() to "V",
    PsiTypes.booleanType() to "Z",
    PsiTypes.charType() to "C",
    PsiTypes.byteType() to "B",
    PsiTypes.shortType() to "S",
    PsiTypes.intType() to "I",
    PsiTypes.floatType() to "F",
    PsiTypes.longType() to "J",
    PsiTypes.doubleType() to "d")

  private fun PsiType.toAsm(): String {
    return BASIC_ASM_TYPES[this] ?: if (this is PsiArrayType) {
      "[" + this.componentType.toAsm()
    }
    else {
      "L" + this.canonicalText.pkgToPath() + ";"
    }
  }

  /**
   * Given an ASM class reader and a list of excluded methods and fields, return a ordered set of
   * descriptions for that class.
   */
  fun collectDescriptionSet(classReader: ClassReader, exclude: Set<String> = setOf()): Set<String> {
    val descriptionSet = TreeSet<String>()

    classReader.accept(object : ClassVisitor(Opcodes.ASM5) {
      override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
        val interfaceList = interfaces!!.toMutableList().apply { sort() }
        descriptionSet.add("$name : $superName -> ${interfaceList.joinToString(", ")}")
      }

      override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        if (access and Opcodes.ACC_PUBLIC != 0 && !name.startsWith("<")) {
          descriptionSet.add("${modifierDesc(access)} $name : $desc")
        }
        return super.visitMethod(access, name, desc, signature, exceptions)
      }

      override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
        if (access and Opcodes.ACC_PUBLIC != 0) {
          descriptionSet.add("${modifierDesc(access)} $name : $desc")
        }
        return super.visitField(access, name, desc, signature, value)
      }
    }, 0)

    descriptionSet.removeAll(exclude)
    return descriptionSet
  }

  /**
   * Given PSI class, return a list of descriptions for that class.
   */
  fun collectDescriptionSet(psiClass: PsiClass): TreeSet<String> {
    val descriptionSet = TreeSet<String>()
    val superTypes = psiClass.superTypes
    val superType = if (superTypes.isEmpty()) "java/lang/Object" else superTypes[0].canonicalText.pkgToPath()
    val sortedInterfaces = psiClass.interfaces
      .map { psiInterface -> psiInterface.qualifiedName!!.pkgToPath() }
      .sorted()

    descriptionSet.add("${psiClass.qualifiedName!!.pkgToPath()} : $superType -> ${sortedInterfaces.joinToString(", ")}")
    for (method in psiClass.methods) {
      if (method.modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
        descriptionSet.add("${method.modifierDesc()} ${method.name} : ${method.methodDesc()}")
      }
    }
    for (field in psiClass.fields) {
      if (field.modifierList != null && field.modifierList!!.hasModifierProperty(PsiModifier.PUBLIC)) {
        descriptionSet.add("${field.modifierDesc()} ${field.name} : ${field.fieldDesc()}")
      }
    }

    return descriptionSet
  }

  private fun PsiField.fieldDesc() = type.toAsm()

  private fun PsiMethod.methodDesc(): String {
    val res = StringBuilder()
    val returnType = returnType!!
    res.append("(")
    for (param in parameterList.parameters) {
      res.append(param.type.toAsm())
    }
    res.append(")").append(returnType.toAsm())
    return res.toString()
  }

  private fun PsiModifierListOwner.modifierDesc(): String {
    return IMPORTANT_MODIFIERS.keys.filter { modifier -> this.modifierList!!.hasModifierProperty(modifier) }.joinToString(" ")
  }

  private fun modifierDesc(access: Int): String {
    return IMPORTANT_MODIFIERS.entries.filter { it.value and access != 0 }.joinToString(" ") { it.key }
  }
}

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 */
@RunWith(Parameterized::class)
class GeneratedCodeMatchTest(private val parameters: TestParameters) {
  companion object {
    @get:Parameters(name = "{0}")
    @get:JvmStatic
    val parameters: List<TestParameters>
      get() = Lists.newArrayList(TestParameters(DataBindingMode.SUPPORT), TestParameters(DataBindingMode.ANDROIDX))
  }

  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  class TestParameters(val mode: DataBindingMode) {
    val projectName: String =
      if (mode == DataBindingMode.ANDROIDX) PROJECT_WITH_DATA_BINDING_ANDROID_X else PROJECT_WITH_DATA_BINDING_SUPPORT
    val dataBindingLibArtifact: String =
      if (mode == DataBindingMode.ANDROIDX) ANDROIDX_DATA_BINDING_LIB_ARTIFACT else DATA_BINDING_LIB_ARTIFACT
    val dataBindingBaseBindingClass: String = mode.viewDataBinding.pkgToPath() + ".class"

    // `toString` output is used by JUnit for parameterized test names, so keep it simple
    override fun toString(): String {
      return mode.toString()
    }
  }

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(parameters.projectName)
  }

  private fun findViewDataBindingClass(): ClassReader {
    val model = GradleAndroidModel.get(projectRule.androidFacet(":app"))!!
    val classJar = model.mainArtifact.compileClasspath.androidLibraries.first { lib ->
      lib.target.artifactAddress.startsWith(parameters.dataBindingLibArtifact)
    }.target.runtimeJarFiles.find {
      it.name == "classes.jar"
    }

    assertThat(classJar?.exists()).isTrue()
    JarFile(classJar, true).use {
      val entry = it.getEntry(parameters.dataBindingBaseBindingClass)!!
      return ClassReader(it.getInputStream(entry))
    }
  }

  @Test
  @RunsInEdt
  fun testGeneratedCodeMatchesExpected() {
    // temporary fix until test model can detect dependencies properly
    val assembleDebug = projectRule.invokeTasks("assembleDebug")
    assertThat(assembleDebug.isBuildSuccessful).isTrue()

    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()
    assertThat(parameters.mode).isEqualTo(LayoutBindingModuleCache.getInstance(projectRule.androidFacet(":app")).dataBindingMode)

    // trigger initialization
    ResourceRepositoryManager.getModuleResources(projectRule.androidFacet(":app"))

    val classesOut = File(projectRule.project.basePath, "/app/build/intermediates/javac//debug/classes")

    val classes = FileUtils.listFiles(classesOut, arrayOf("class"), true)
    assertWithMessage("No compiled classes found. Something is wrong with this test.")
      .that(classes).isNotEmpty()

    val viewDataBindingClass = findViewDataBindingClass()

    // Grab a description set for the ViewDataBinding base class, as we'll strip it out of the
    // description set for the Binding subclasses, since otherwise it's just noise to us
    val baseClassInfo = ClassDescriber.collectDescriptionSet(viewDataBindingClass)

    val classMap = classes.mapNotNull<File, ClassReader> { file ->
      try {
        ClassReader(FileUtils.readFileToByteArray(file))
      }
      catch (e: IOException) {
        e.printStackTrace()
        fail(e.message)
        null
      }
    }.map { classReader -> classReader.className to classReader }.toMap()

    val context = fixture.findClass("com.android.example.appwithdatabinding.MainActivity")

    // The data binding compiler generates a bunch of stuff we don't care about in Studio. The
    // following set is what we want to make sure we generate PSI for.
    val interestingClasses = setOf(
      "${parameters.mode.packageName}DataBindingComponent",
      "com.android.example.appwithdatabinding.BR",
      "com.android.example.appwithdatabinding.databinding.ActivityMainBinding",
      "com.android.example.appwithdatabinding.databinding.MultiConfigLayoutBinding",
      "com.android.example.appwithdatabinding.databinding.MultiConfigLayoutBindingImpl",
      "com.android.example.appwithdatabinding.databinding.MultiConfigLayoutBindingLandImpl",
      "com.android.example.appwithdatabinding.databinding.NoVariableLayoutBinding"
    )
    val generatedClasses = mutableSetOf<String>()
    val missingClasses = mutableSetOf<String>()
    for (classReader in classMap.values) {
      val className = classReader.className.pathToPkg()
      if (!interestingClasses.contains(className)) {
        continue
      }
      generatedClasses.add(className)
      val psiClass = fixture.findClass(className, context)
      if (psiClass == null) {
        missingClasses.add(className)
        continue
      }

      // Convert Asm and PSI classes into description sets and verify they're the same
      val asmInfo = ClassDescriber.collectDescriptionSet(classReader, baseClassInfo)
      val psiInfo = ClassDescriber.collectDescriptionSet(psiClass)

      assertWithMessage(className).that(psiInfo).isEqualTo(asmInfo)
    }
    assertWithMessage("Failed to find expected generated data binding classes; did the compiler change?")
      .that(generatedClasses).containsExactlyElementsIn(interestingClasses)

    assertWithMessage("PSI could not be found for some generated code: ${missingClasses.joinToString(", ")}")
      .that(missingClasses).isEmpty()
  }
}
