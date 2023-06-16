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
package com.android.tools.idea.debug

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndWait
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString

private const val COMPANION_PREFIX = "\$-CC"

/**
 * Tests for [com.android.tools.idea.debug.DesugarUtils]
 */
class DesugarUtilsTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val rule = RuleChain(projectRule)

  private val mockDebugProcess = mock<DebugProcessImpl>()
  private val positionManager = object : PositionManagerImpl(mockDebugProcess) {}
  private val mockVirtualMachineProxy = mock<VirtualMachineProxyImpl>()
  private val mockClassPrepareRequestor = mock<ClassPrepareRequestor>()
  private val mockRequestManager = mock<RequestManagerImpl>()
  private val desugarUtils = DesugarUtils(positionManager, mockDebugProcess)
  private val allClasses = mutableListOf<ReferenceType>()

  @Before
  fun setUp() {
    whenever(mockDebugProcess.requestsManager).thenReturn(mockRequestManager)
    whenever(mockRequestManager.createClassPrepareRequest(any(), anyString())).thenAnswer { invocation ->
      FakeClassPrepareRequest(invocation.arguments[1].toString())
    }
    whenever(mockDebugProcess.virtualMachineProxy).thenReturn(mockVirtualMachineProxy)
    whenever(mockDebugProcess.project).thenReturn(projectRule.project)
    whenever(mockDebugProcess.searchScope).thenReturn(GlobalSearchScope.allScope(projectRule.project))

    // todo: In reality, `allClasses` throws so, add a mode where we test when it throws.
    whenever(mockVirtualMachineProxy.allClasses()).thenReturn(allClasses)
  }

  @Test
  fun getExtraPrepareRequests_InterfaceWithStaticMethod_hasResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = desugarUtils.getExtraPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly("p1.p2.Foo\$*")
  }

  @Test
  fun getExtraPrepareRequests_InterfaceWithDefaultMethod_hasResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        default void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = desugarUtils.getExtraPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly("p1.p2.Foo\$*")
  }

  @Test
  fun getExtraPrepareRequests_SimpleClass_noResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      class Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = desugarUtils.getExtraPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests).isEmpty()
  }

  @Test
  fun getExtraPrepareRequests_InterfaceWithStaticInitializer_noResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        String STR = "foo"
          .concat("bar"); // break here
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = desugarUtils.getExtraPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests).isEmpty()
  }

  @Test
  fun getCompanionClasses_InterfaceWithStaticMethod_hasResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()
    val classesFromPositionManager = positionManager.getAllClasses(position)

    val types = desugarUtils.getCompanionClasses(position, classesFromPositionManager)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo$-CC",
    )
  }

  @Test
  fun getCompanionClasses_InterfaceWithDefaultMethod_hasResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        default void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()
    val classesFromPositionManager = positionManager.getAllClasses(position)

    val types = desugarUtils.getCompanionClasses(position, classesFromPositionManager)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo$-CC",
    )
  }

  @Test
  fun getCompanionClasses_SimpleClass_noResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      class Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()
    val classesFromPositionManager = positionManager.getAllClasses(position)

    val types = desugarUtils.getCompanionClasses(position, classesFromPositionManager)

    assertThat(types).isEmpty()
  }

  @Test
  fun getCompanionClasses_InterfaceWithStaticInitializer_noResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        String STR = "foo"
          .concat("bar"); // break here
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()
    val classesFromPositionManager = positionManager.getAllClasses(position)

    val types = desugarUtils.getCompanionClasses(position, classesFromPositionManager)

    assertThat(types).isEmpty()
  }

  @Test
  fun getCompanionClasses_IgnoresUnrelatedInnerClass() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        static void bar() {
          int test = 2; // break here
        }
        
        class Unrelated {
          static void unrelated() {
            int test = true;
          } 
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()
    val classesFromPositionManager = positionManager.getAllClasses(position)

    val types = desugarUtils.getCompanionClasses(position, classesFromPositionManager)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo$-CC",
    )
  }

  private fun setupFromFile(content: String): PsiFile {
    val path = content.lineSequence().first().substringAfter("package ").substringBefore(";").trim().replace('.', '/')
    val psiFile = projectRule.fixture.addFileToProject("src/$path/Test.java", content)
    runInEdtAndWait {
      PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).forEach { psiClass ->
        val jvmName = psiClass.getJvmName()
        val referenceType = FakeReferenceType(jvmName)
        allClasses.add(referenceType)
        if (psiClass.isInterfaceWithStaticMethod()) {
          allClasses.add(FakeReferenceType("$jvmName$COMPANION_PREFIX", hasLocations = true))
        }
        whenever(mockVirtualMachineProxy.classesByName(jvmName)).thenReturn(listOf(referenceType))
      }
    }
    return psiFile
  }

  private fun PsiFile.getBreakpointPosition(): SourcePosition {
    val line = text.lineSequence().indexOfFirst { it.contains("break here") }
    return SourcePosition.createFromLine(this, line)
  }

  class FakeClassPrepareRequest(private val filter: String, delegate: ClassPrepareRequest = mock()) : ClassPrepareRequest by delegate {
    override fun toString() = filter
  }

  /**
   * IDEA doesn't like it when interfaces from `com.sun.jdi` are mocked.
   */

  private class FakeReferenceType(
    val name: String,
    val hasLocations: Boolean = false,
    delegate: ReferenceType = mock(),
  ) : ReferenceType by delegate {
    override fun name(): String = name

    override fun locationsOfLine(stratum: String?, sourceName: String?, lineNumber: Int) =
      if (hasLocations) listOf(mock<Location>()) else emptyList()
  }
}