package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.AnnotationDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Basic tests for all elements of [IrMethod]/[MethodDiff] except:
 *  - localVariables -> see [LocalVariableTest]
 *  - parameters -> see ParameterTest
 */
class MethodTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun testAccess() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        open fun method() = 0
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        private fun method() = 0
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertMethods(diff, buildMap {
      put("method()I", object : MethodVisitor {
        override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
          assertEquals(setOf(IrAccessFlag.FINAL, IrAccessFlag.PRIVATE), added)
          assertEquals(setOf(IrAccessFlag.PUBLIC), removed)
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertMethods(inv, buildMap {
      put("method()I", object : MethodVisitor {
        override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
          assertEquals(setOf(IrAccessFlag.PUBLIC), added)
          assertEquals(setOf(IrAccessFlag.FINAL, IrAccessFlag.PRIVATE), removed)
        }
      })
    })
  }

  @Test
  fun testSignature() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun method(value: List<String>) = 0
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        fun method(value: List<Int>) = 0
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertMethods(diff, buildMap {
      put("method(Ljava/util/List;)I", object : MethodVisitor {
        override fun visitSignature(old: String?, new: String?) {
          assertEquals("(Ljava/util/List<Ljava/lang/String;>;)I", old)
          assertEquals("(Ljava/util/List<Ljava/lang/Integer;>;)I", new)
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertMethods(inv, buildMap {
      put("method(Ljava/util/List;)I", object : MethodVisitor {
        override fun visitSignature(old: String?, new: String?) {
          assertEquals("(Ljava/util/List<Ljava/lang/Integer;>;)I", old)
          assertEquals("(Ljava/util/List<Ljava/lang/String;>;)I", new)
        }
      })
    })
  }

  @Test
  fun testAddRemoveMethod() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        fun methodA() = 0
        fun methodB() = 0
        fun methodC() = 0
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        fun methodB() = "default"
        fun methodD() = 0
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    diff.accept(object : ClassVisitor {
      override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
        assertEquals(setOf("methodD", "methodB"), added.map(IrMethod::name).toSet())
        assertEquals(setOf("methodA", "methodC", "methodB"), removed.map(IrMethod::name).toSet())
        assertTrue(modified.isEmpty())
      }
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    inv.accept(object : ClassVisitor {
      override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
        assertEquals(setOf("methodA", "methodC", "methodB"), added.map(IrMethod::name).toSet())
        assertEquals(setOf("methodD", "methodB"), removed.map(IrMethod::name).toSet())
        assertTrue(modified.isEmpty())
      }
    })
  }

  @Test
  fun testAddRemoveMethodAnnotation() {
    val file = projectRule.createKtFile("A.kt", """
      annotation class Q
      annotation class R
      annotation class S
      class A {
        @Q
        @R
        fun method() = 0
  
        fun other() = 0
      }""")
    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      annotation class Q
      annotation class R
      annotation class S
      class A {
        @R
        @S
        fun method() = 0
  
        @R
        @S
        fun other() = 0
      }""")
    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertMethods(diff, buildMap {
      put("method()I", object : MethodVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertEquals(listOf("LS;"), added.map(IrAnnotation::desc))
          assertEquals(listOf("LQ;"), removed.map(IrAnnotation::desc))
          assertTrue(modified.isEmpty())
        }
      })
      put("other()I", object : MethodVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertEquals(listOf("LR;", "LS;"), added.map(IrAnnotation::desc))
          assertTrue(removed.isEmpty())
          assertTrue(modified.isEmpty())
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertMethods(inv, buildMap {
      put("method()I", object : MethodVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertEquals(listOf("LQ;"), added.map(IrAnnotation::desc))
          assertEquals(listOf("LS;"), removed.map(IrAnnotation::desc))
          assertTrue(modified.isEmpty())
        }
      })
      put("other()I", object : MethodVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertTrue(added.isEmpty())
          assertEquals(listOf("LR;", "LS;"), removed.map(IrAnnotation::desc))
          assertTrue(modified.isEmpty())
        }
      })
    })
  }

  // There was a bug where we thought SAM methods would cause the class diff to erroneously always show changes.
  // That turned out to not be the case, but we leave this test here just in case.
  @Test
  fun testSAM() {
    val file = projectRule.createKtFile("A.kt", """
      fun interface Sam {
        fun getNewString() : String
      }""")
    val original = projectRule.directApiCompileIr(file)
    for (clazz in original.values) {
      assertNoChanges(clazz, clazz)
    }
  }
}