package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.AnnotationDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAnnotation
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Basic tests for all elements of [IrField]/[FieldDiff] except:
 *  - value -> unclear how to produce Kotlin code that modifies this
 */
class FieldTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

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
  fun testDesc() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        val field: Int = 0
      }""")

    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        val field: String = "value"
      }""")

    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertFields(diff, buildMap {
      put("field", object : FieldVisitor {
        override fun visitDesc(old: String?, new: String?) {
          assertEquals("I", old)
          assertEquals("Ljava/lang/String;", new)
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertFields(inv, buildMap {
      put("field", object : FieldVisitor {
        override fun visitDesc(old: String?, new: String?) {
          assertEquals("Ljava/lang/String;", old)
          assertEquals("I", new)
        }
      })
    })
  }

  @Test
  fun testSignature() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        val field: List<Int>? = null
      }""")

    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        val field: List<String>? = null
      }""")

    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertFields(diff, buildMap {
      put("field", object : FieldVisitor {
        override fun visitSignature(old: String?, new: String?) {
          assertEquals("Ljava/util/List<Ljava/lang/Integer;>;", old)
          assertEquals("Ljava/util/List<Ljava/lang/String;>;", new)
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertFields(inv, buildMap {
      put("field", object : FieldVisitor {
        override fun visitSignature(old: String?, new: String?) {
          assertEquals("Ljava/util/List<Ljava/lang/String;>;", old)
          assertEquals("Ljava/util/List<Ljava/lang/Integer;>;", new)
        }
      })
    })
  }

  @Test
  fun testAccess() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        var field = 0
      }""")

    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        val field = 0
      }""")

    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertFields(diff, buildMap {
      put("field", object : FieldVisitor {
        override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
          assertEquals(setOf(IrAccessFlag.FINAL), added)
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertFields(inv, buildMap {
      put("field", object : FieldVisitor {
        override fun visitAccess(added: Set<IrAccessFlag>, removed: Set<IrAccessFlag>) {
          assertEquals(setOf(IrAccessFlag.FINAL), removed)
        }
      })
    })
  }

  @Test
  fun testAddRemoveField() {
    val file = projectRule.createKtFile("A.kt", """
      class A {
        val field = 0
        val other = 1
        val old = "hello"
      }""")

    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      class A {
        val field = 0
        val other = 1
        val new = 2.0
      }""")

    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    diff.accept(object : ClassVisitor {
      override fun visitFields(added: List<IrField>, removed: List<IrField>, modified: List<FieldDiff>) {
        assertEquals(listOf("new"), added.map(IrField::name))
        assertEquals(listOf("old"), removed.map(IrField::name))
        assertTrue(modified.isEmpty())
      }
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    inv.accept(object : ClassVisitor {
      override fun visitFields(added: List<IrField>, removed: List<IrField>, modified: List<FieldDiff>) {
        assertEquals(added.map(IrField::name), listOf("old"))
        assertEquals(removed.map(IrField::name), listOf("new"))
        assertTrue(modified.isEmpty())
      }
    })
  }

  @Test
  fun testAddRemoveFieldAnnotation() {
    val file = projectRule.createKtFile("A.kt", """
      annotation class Q
      annotation class R
      annotation class S
      class A {
        @field:Q
        @field:R
        val field = 0

        val other = 1
      }""")

    val original = projectRule.directApiCompileIr(file)["A"]!!

    projectRule.modifyKtFile(file, """
      annotation class Q
      annotation class R
      annotation class S
      class A {
        @field:R
        @field:S
        val field = 0

        @field:R
        @field:S
        val other = 1
      }""")

    val new = projectRule.directApiCompileIr(file)["A"]!!

    assertNull(diff(original, original))
    assertNull(diff(new, new))

    val diff = diff(original, new)
    assertNotNull(diff)

    assertFields(diff, buildMap {
      put("field", object : FieldVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertEquals(listOf("LS;"), added.map(IrAnnotation::desc))
          assertEquals(listOf("LQ;"), removed.map(IrAnnotation::desc))
          assertTrue(modified.isEmpty())
        }
      })
      put("other", object : FieldVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertEquals(listOf("LR;", "LS;"), added.map(IrAnnotation::desc))
          assertTrue(removed.isEmpty())
          assertTrue(modified.isEmpty())
        }
      })
    })

    val inv = diff(new, original)
    assertNotNull(inv)

    assertFields(inv, buildMap {
      put("field", object : FieldVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertEquals(listOf("LQ;"), added.map(IrAnnotation::desc))
          assertEquals(listOf("LS;"), removed.map(IrAnnotation::desc))
          assertTrue(modified.isEmpty())
        }
      })
      put("other", object : FieldVisitor {
        override fun visitAnnotations(added: List<IrAnnotation>, removed: List<IrAnnotation>, modified: List<AnnotationDiff>) {
          assertTrue(added.isEmpty())
          assertEquals(listOf("LR;", "LS;"), removed.map(IrAnnotation::desc))
          assertTrue(modified.isEmpty())
        }
      })
    })
  }
}