/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations

import com.android.test.testutils.TestUtils
import com.android.tools.idea.actions.annotations.InferAnnotations.Companion.HEADER
import com.android.tools.idea.actions.annotations.InferAnnotations.Companion.generateReport
import com.android.tools.idea.flags.StudioFlags.INFER_ANNOTATIONS_REFACTORING_ENABLED
import com.android.tools.lint.client.api.LintClient
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.psi.PsiFile
import com.intellij.usageView.UsageInfo
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Ignore
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

private const val INFER_PATH = "/infer/"

// TODO: Test varargs
// TODO: Inferred Permission requirement with multiple (anyOf/allOf)
// TODO: Resource types with array initializer
// TODO: Restore disabled/ignored tests
//  "Inference:  Method{androidx.compose.ui.res.vectorResource(Companion,Theme,Resources,int)}Parameter{int resId}:@AnyRes because it's passed getResourceId(
//            AndroidVectorResources.STYLEABLE_ANIMATED_VECTOR_DRAWABLE_DRAWABLE,
//            0
//        ) (a resource of any type) in a call"
//   --> This looks like it should be a drawable or a styleable; double check this

// Require resources with spaces (HTML File template)
// https://github.com/bazelbuild/bazel/issues/374
@Ignore
class InferAnnotationsTest : JavaCodeInsightTestCase() {
  override fun setUp() {
    INFER_ANNOTATIONS_REFACTORING_ENABLED.override(true)
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
    super.setUp()
  }

  override fun getTestDataPath(): String {
    return AndroidTestBase.getTestDataPath()
  }

  fun testProperties() {
    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate", "ConstPropertyName")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.ColorRes

        class Test(
                arg1: Int,
                val arg2: Int,
                var arg3: Int,
                internal val arg4: Int,
                private var arg5: Int
        ) {
          init {
            test(arg1); test(arg2); test(arg3); test(arg4); test(arg5)
          }

          val prop1: Int = 0
          var prop2: Int = 0
          internal val prop3: Int = 0
          private var prop4: Int = 0
          val prop5: Int by lazy { 0 }
          const val prop6: Int = 0
          fun test2() {
            test(prop1); test(prop2); test(prop3); test(prop4); test(prop5); test(prop6)
          }
        }

        fun test(@ColorRes c: Int) {
        }
        """,
      expectedDiffs = """
        @@ -6 +6
          class Test(
        -         arg1: Int,
        -         val arg2: Int,
        -         var arg3: Int,
        -         internal val arg4: Int,
        -         private var arg5: Int
        +         @ColorRes arg1: Int,
        +         @get:ColorRes @param:ColorRes val arg2: Int,
        +         @get:ColorRes @set:ColorRes @param:ColorRes var arg3: Int,
        +         @get:ColorRes @param:ColorRes internal val arg4: Int,
        +         @ColorRes private var arg5: Int
          ) {
        @@ -16 +16
        +   @get:ColorRes
        +   @field:ColorRes
            val prop1: Int = 0
        @@ -17 +19
            val prop1: Int = 0
        +   @get:ColorRes
        +   @set:ColorRes
        +   @field:ColorRes
            var prop2: Int = 0
        @@ -18 +23
            var prop2: Int = 0
        +   @get:ColorRes
        +   @field:ColorRes
            internal val prop3: Int = 0
        @@ -19 +26
            internal val prop3: Int = 0
        +   @ColorRes
            private var prop4: Int = 0
        @@ -20 +28
            private var prop4: Int = 0
        +   @get:ColorRes
            val prop5: Int by lazy { 0 }
        @@ -21 +30
            val prop5: Int by lazy { 0 }
        +   @ColorRes
            const val prop6: Int = 0
        """
    )
  }

  fun testPropertyGetterSetter() {
    // Makes sure that if we have a @get:Annotation annotation on the
    // property itself, we don't also place the annotation on the
    // individual getter or setter bodies!
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.IdRes
        class Test {
            private val destinationId by lazy {
                0
            }

            @IdRes private var startDestId = 0
            @get:IdRes
            var startDestinationId: Int
                get() = startDestId
                private set(startDestId) {
                    this.startDestId = startDestId
                }

            private fun test() {
                call(startDestId)
                call(startDestinationId)
                call(destinationId)
            }

            private fun call(@IdRes id: Int) { }
        }
        """,
      expectedDiffs = """
        @@ -4 +4
          class Test {
        +     @get:IdRes
              private val destinationId by lazy {
        """
    )
  }

  fun testInlineClassConstructor() {
    @Suppress("CanBeParameter")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.ColorRes

        @Suppress("INLINE_CLASS_DEPRECATED", "EXPERIMENTAL_FEATURE_WARNING")
        inline class Color(val value: ULong) {
        }

        fun test(@ColorRes c: ULong): Color {
            return Color(c)
        }

        class Parent(@IdRes private var id: Int)
        class Child(var id: Int) : Parent(id)
        """,
      expectedReport = "Nothing found."
    )
  }

  fun testAnyRes() {
    // If we return something marked @AnyRes, the return type should also be @AnyRes.
    // Also makes sure we don't inline all the individual resource types.
    checkJava(
      before = """
        import androidx.annotation.AnyRes;
        public class Test {
            private int test(@AnyRes int id) {
                return id;
            }
        }
        """,
      expectedReport = """
        Class Test:
          Method test(int):
            @AnyRes because it returns id annotated with @AnyRes
        """,
      expectedDiffs = """
        @@ -3 +3
          public class Test {
        +     @AnyRes
              private int test(@AnyRes int id) {
        """
    )
  }

  fun testAnyResDeleteExistingJava() {
    // If we add @AnyRes, remove any other existing resource annotations first
    checkJava(
      before = """
        import androidx.annotation.*;
        public class Test {
            @StringRes @DrawableRes
            @CheckResult
            @ColorRes
            private int test(@AnyRes int id) {
                return id;
            }
        }
        """,
      expectedReport = """
        Class Test:
          Method test(int):
            @AnyRes because it returns id annotated with @AnyRes
        """,
      expectedDiffs = """
        @@ -3 +3
          public class Test {
        -     @StringRes @DrawableRes
        +     @AnyRes
              @CheckResult
        @@ -5 +5
              @CheckResult
        -     @ColorRes
              private int test(@AnyRes int id) {
        """
    )
  }

  fun testAnyResDeleteExistingKotlin() {
    // If we add @AnyRes, remove any other existing resource annotations first.
    checkKotlin(
      before = """
        import androidx.annotation.*
        class Test {
            @StringRes @DrawableRes
            @CheckResult
            @ColorRes
            fun test(@AnyRes id: Int) {
                return id
            }
        }
        """,
      expectedReport = """
        Class Test:
          Method test(int):
            @AnyRes because it returns id annotated with @AnyRes
        """,
      expectedDiffs = """
        @@ -3 +3
          class Test {
        -     @StringRes @DrawableRes
        +     @AnyRes
              @CheckResult
        @@ -5 +5
              @CheckResult
        -     @ColorRes
              fun test(@AnyRes id: Int) {
        """
    )
  }

  fun testAnyResAllowed() {
    // Makes sure that if we *call* something annotated with @AnyRes, we don't then conclude
    // that the passed in thing is also allowed to be any res
    checkJava(
      before = """
        import androidx.annotation.AnyRes;
        import androidx.annotation.DimenRes;
        public class Test {
            public void test(int sample, @DimenRes int id) {
                test(id);
            }
            private void test(@AnyRes int id) {
            }
        }
        """,
      expectedReport = "Nothing found."
    )
  }

  fun testHalfFloat() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            fun test1(@HalfFloat d: Int) {
                test1(arg3 = d)
            }

            fun test1(arg1: Int = 0, arg2: Int = 0) { }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method test1(int,int):
            Parameter int arg1:
              @HalfFloat because it's passed d (a half-precision float) in a call from InferTypes#test1
        """,
      expectedDiffs = """
        @@ -9 +9
        -     fun test1(arg1: Int = 0, arg2: Int = 0) { }
        +     fun test1(@HalfFloat arg1: Int = 0, arg2: Int = 0) { }
          }
        """
    )
  }

  fun testCheckResult() {
    @Suppress("RedundantOverride")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*

        open class Parent {
            @CheckResult
            open fun test(): String = ""

            fun test2(): String {
                return test()
            }
        }

        class Child1 : Parent() {
            override fun test(): String = super.test()
        }
        """,
      expectedReport = """
        Class test.pkg.Child1:
          Method test():
            @CheckResult because it extends or is overridden by an annotated method

        Class test.pkg.Parent:
          Method test2():
            @CheckResult because it returns Parent#test annotated with @CheckResult
        """
    )
  }

  fun testSkipInheritance() {
    // Some annotations shouldn't be inherited (for example, because they're defined to apply to the hierarchy anyway so lint
    // will look up the chain). This test makes sure we don't inherit these.
    @Suppress("RedundantOverride")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*

        open class Parent {
            @IdRes // already present on child; checking that we don't repeat it
            @UiThread @WorkerThread @CallSuper @CheckResult
            open fun test() = 0
        }

        class Child1 : Parent() {
            @IdRes override fun test(): Int = super.test()
        }
        """,
      expectedReport = """
        Class test.pkg.Child1:
          Method test():
            @CheckResult because it extends or is overridden by an annotated method
        """
    )
  }

  fun testReverseConstants() {
    // When we pick up annotations with numbers, if the numbers represent logical constants like Integer.MAX_VALUE
    // or Float.MAX_VALUE, we shouldn't inline the exact value, we should in the annotation source use the
    // constants instead. This test looks for that.
    @Suppress("RedundantOverride")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        import androidx.annotation.IntRange

        abstract class Parent {
            @IntRange(from = ${Integer.MIN_VALUE}, to = ${Long.MAX_VALUE})
            abstract fun test1(): Int
            @FloatRange(from = ${Double.MIN_VALUE}, to = ${Float.MAX_VALUE})
            abstract fun test2(): Int
        }

        class Child : Parent() {
            override fun test1(): Int = super.test1()
            override fun test2(): Int = super.test2()
        }
        """,
      expectedDiffs = """
        @@ -13 +13
          class Child : Parent() {
        +     @IntRange(from = -java.lang.Integer.MIN_VALUE.toLong(), to = java.lang.Long.MAX_VALUE)
              override fun test1(): Int = super.test1()
        @@ -14 +15
              override fun test1(): Int = super.test1()
        +     @FloatRange(from = Double.MIN_VALUE, to = Float.MAX_VALUE.toDouble())
              override fun test2(): Int = super.test2()
        """
    )
  }

  fun testIgnore() {
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // We should ignore elements that have been annotated with @Suppress
            @Suppress("InferAnnotations")
            var test2 = android.R.string.ok

            @Suppress("InferAnnotations")
            private fun test(): Int {
              return android.R.string.ok
            }
        }
        """,
      expectedReport = "Nothing found."
    )
  }

  fun testLocalVariableInference() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkJava(
      before = """
        package test.pkg;

        import androidx.annotation.*;

        public class JavaTest {
            void test1(@DrawableRes int d) {
                @ColorRes int e = 0;
                test1(d, e);
            }

            void test1(int arg1, int arg2) { }
        }
        """,
      expectedReport = """
        Class test.pkg.JavaTest:
          Method test1(int,int):
            Parameter int arg1:
              @DrawableRes because it's passed d (a drawable) in a call from JavaTest#test1
          Method test1(int,int):
            Parameter int arg2:
              @ColorRes because it's passed e (a color) in a call from JavaTest#test1
        """
    )
  }

  fun testPropertyAccessor() {
    @Suppress("RedundantGetter", "RedundantSetter", "MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*

        class Test {
          var drawable: Int = 0
            get() {
              return field
            }

            set(value) {
              field = value
            }
        }
        """,
      expectedReport = """
        Class test.pkg.Test:
          Property drawable (getter):
            @DrawableRes because it's passed to the d parameter in JavaTest#paint, a drawable
          Property drawable (setter):
            @ColorRes because it's passed d (a color) in a call from JavaTest#test
        """,
      expectedDiffs = """
        @@ -7 +7
            var drawable: Int = 0
        +     @DrawableRes
              get() {
        @@ -11 +12
        -     set(value) {
        +     set(@ColorRes value) {
                field = value
        """,
      extraFiles = arrayOf(
        createFile(
          "JavaAccess.java",
          // language=JAVA
          """
          package test.pkg;
          import androidx.annotation.*;
          public class JavaTest {
              void test(@ColorRes int d) {
                  Test test = new Test();
                  paint(test.getDrawable());
                  test.setDrawable(d);
              }
              void paint(@DrawableRes int d) {
              }
          }
          """
        )
      )
    )
  }

  fun testAnonymousClassJava() {
    @Suppress("ConstantConditions")
    checkJava(
      before = """
        package test.pkg;
        import android.view.View;
        import androidx.annotation.*;
        public class JavaTest {
            void test() {
                new Runnable() {
                    public void run() {
                        new View(null) {
                            @Override
                            public void setPadding(int left, int top, int right, int bottom) {
                                super.setPadding(left, top, right, bottom);
                            }
                            void test(@Px int size) {
                                setPadding(0, 0, 0, size);
                            }
                        };
                    }
                };
            }
        }
        """,
      expectedReport = """
        Class test.pkg.JavaTest.<Anonymous Runnable>.<Anonymous View>:
          Method setPadding(int,int,int,int):
            Parameter int bottom:
              @Px because it's passed size (a pixel dimension) in a call from anonymous class extending View#test
        """,
      expectedDiffs = """
        @@ -10 +10
                              @Override
        -                     public void setPadding(int left, int top, int right, int bottom) {
        +                     public void setPadding(int left, int top, int right, @Px int bottom) {
                                  super.setPadding(left, top, right, bottom);
        """
    )
  }

  fun testAnonymousClassKotlin() {
    // Also tests companion objects and top level functions
    @Suppress("ConstantConditions", "RedundantOverride")
    checkKotlin(
      before = """
        package test.pkg
        import android.view.View
        import androidx.annotation.*
        class KotlinTest {
            companion object {
                fun test(width: Int, height: Int) { }
                fun test() {
                    object : View(null) {
                        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
                            super.setPadding(left, top, right, bottom)
                        }

                        fun test(@Px size: Int) {
                            setPadding(size, 0, 0, 0)
                            test(size, 0)
                            test2(size)
                        }
                    }
                }
            }
        }
        fun test2(w: Int) { }
        """,
      expectedReport = """
        Function test.pkg.test2(int):
          Parameter int w:
            @Px because it's passed size (a pixel dimension) in a call from KotlinTest#test

        Class test.pkg.KotlinTest.Companion:
          Method test(int,int):
            Parameter int width:
              @Px because it's passed size (a pixel dimension) in a call from KotlinTest#test

        Class test.pkg.KotlinTest.Companion.<Anonymous View>:
          Method setPadding(int,int,int,int):
            Parameter int left:
              @Px because it's passed size (a pixel dimension) in a call from KotlinTest#test
        """,
      expectedDiffs = """
        @@ -6 +6
              companion object {
        -         fun test(width: Int, height: Int) { }
        +         fun test(@Px width: Int, height: Int) { }
                  fun test() {
        @@ -9 +9
                      object : View(null) {
        -                 override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        +                 override fun setPadding(@Px left: Int, top: Int, right: Int, bottom: Int) {
                              super.setPadding(left, top, right, bottom)
        @@ -22 +22
          }
        - fun test2(w: Int) { }
        @@ -23 +22
        + fun test2(@Px w: Int) { }
        """,
      includeAndroidJar = true
    )
  }

  fun testWeirdSample() {
    @Suppress("unused")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.CheckResult

        class PagingData<T : Any> internal constructor(
            internal val flow: Flow<PageEvent<T>>,
            internal val receiver: UiReceiver
        )

        interface UiReceiver
        internal sealed class PageEvent<T : Any>
        interface Flow<out T>

        @CheckResult
        fun <T : R, R : Any> PagingData<T>.insertSeparatorsAsync(
        ): PagingData<R> = error("not implemented")

        inline fun <T, R> Flow<T>.map(crossinline transform: suspend (value: T) -> R): Flow<R> = error("not implemented")

        private lateinit var pagingDataStream: Flow<PagingData<String>>

        fun insertSeparatorsFutureSample() {
            pagingDataStream.map { pagingData ->
                pagingData.insertSeparatorsAsync()
            }
        }
        """,
      expectedReport = "Nothing found."
    )
  }

  fun testIgnoreMathUtilities() {
    // TODO: What about androidx' core/core/src/main/java/androidx/core/math/MathUtils.java?
    @Suppress("ResultOfMethodCallIgnored")
    checkJava(
      before = """
        package test.pkg;
        import androidx.annotation.*;
        public class JavaTest {
            public void usage1(@Px float size1, @Px int size2) {
                int rnd = Math.round(size1);
                int max = java.lang.Math.max(rnd, size2);
            }

            public float usage2(float cost, int c1, int c2) {
                Math.max(c1, c2);
                return Math.round(cost);
            }
        }
        """,
      expectedReport = "Nothing found.",
      includeAndroidJar = true
    )
  }

  fun testRequiresPermissionExceptionsHandled() {
    // Makes sure that when we call a method which requires permissions but we end up
    // cathing the exception, either directly (SecurityException) or some super class of
    // it, we don't then transfer the permission requirement too.
    @Suppress("CatchMayIgnoreException", "TryWithIdenticalCatches", "UnusedAssignment")
    checkJava(
      before = """
        package test.pkg;
        import android.Manifest;
        import android.hardware.camera2.CameraAccessException;
        import androidx.annotation.*;

        public class Security {
            public void safeOpenCamera() {
                try {
                    openCamera();
                } catch (CameraAccessException e) {
                } catch (SecurityException e) {
                }
            }

            @RequiresPermission(android.Manifest.permission.CAMERA)
            public void openCamera() throws CameraAccessException { }

            public static class CameraDeviceHolder {
                @RequiresPermission(Manifest.permission.CAMERA)
                CameraDeviceHolder() {}
            }

            public static boolean tryOpenCamera(String cameraId) {
                CameraDeviceHolder deviceHolder = null;
                boolean ret = true;
                try {
                    deviceHolder = new CameraDeviceHolder();
                } catch (Exception e) {
                    ret = false;
                }
                return ret;
            }
        }
        """,
      expectedReport = "Nothing found.",
      includeAndroidJar = true
    )
  }

  fun testRequiresPermissionConstantMapping() {
    // Makes sure that we map back from strings into constants when we can for the built-in permissions
    @Suppress("CatchMayIgnoreException", "TryWithIdenticalCatches", "UnusedAssignment")
    checkJava(
      before = """
        package test.pkg;
        import androidx.annotation.*;

        public class PermissionTest {
            public void requiresPermission1() {
                openCamera1();
            }
            public void requiresPermission2() {
                openCamera2();
            }

            @RequiresPermission(android.Manifest.permission.CAMERA)
            public void openCamera1() { }
            @RequiresPermission("android.permission.CAMERA")
            public void openCamera2() { }
        }
        """,
      expectedReport = """
        Class test.pkg.PermissionTest:
          Method requiresPermission1():
            @RequiresPermission(Manifest.permission.CAMERA) because it calls PermissionTest#openCamera1
          Method requiresPermission2():
            @RequiresPermission(Manifest.permission.CAMERA) because it calls PermissionTest#openCamera2
        """,
      expectedDiffs = """
        @@ -2 +2
          package test.pkg;
        + import android.Manifest;
        +
          import androidx.annotation.*;
        @@ -5 +7
          public class PermissionTest {
        +     @RequiresPermission(Manifest.permission.CAMERA)
              public void requiresPermission1() {
        @@ -8 +11
              }
        +     @RequiresPermission(Manifest.permission.CAMERA)
              public void requiresPermission2() {
        """,
      includeAndroidJar = true
    )
  }

  fun testRanges() {
    @Suppress("RedundantOverride")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        import androidx.annotation.IntRange
        import android.graphics.fonts.FontFamily

        class Test {
            @FloatRange(from=0.0,to=1.00)
            fun getAlpha(): Float = 0f

            @IntRange(from = 0L, to = 255L)
            fun getViewAlpha(): Int = 0

            fun getSecondaryAlpha() = getAlpha()
            fun getSecondaryViewAlpha(): Int { return getViewAlpha() }
        }

        fun testRange(index: Int, family: FontFamily) {
            family.getFont(index) // has >= 0 constraint in android.jar
        }
        """,
      expectedReport = """
        Class test.pkg.Test:
          Method getSecondaryAlpha():
            @FloatRange(from=0.0, to=1.00) because it returns Test#getAlpha annotated with @FloatRange(from=0.0, to=1.00)
          Method getSecondaryViewAlpha():
            @IntRange(from = 0L, to = 255L) because it returns Test#getViewAlpha annotated with @IntRange(from = 0L, to = 255L)
        """,
      expectedDiffs = """
        @@ -13 +13
        +     @FloatRange(from = 0.0, to = 1.0)
              fun getSecondaryAlpha() = getAlpha()
        @@ -14 +15
              fun getSecondaryAlpha() = getAlpha()
        +     @IntRange(from = 0L, to = 255L)
              fun getSecondaryViewAlpha(): Int { return getViewAlpha() }
        """
    )
  }

  fun testAnnotationSourceJava() {
    // Tests passing annotation constraints and making sure that we stringify these properly
    // to Java (todo: consider placing the usage in a separate Kotlin file!)
    checkJava(
      before = """
        package test.pkg;

        import static test.pkg.JavaTest.call2;
        import androidx.annotation.*;

        public class Test {
            public static int test() {
                return JavaTest.call();
            }

            public static int test2() {
                return call2();
            }
        }
        """,
      expectedReport = """
        Class test.pkg.Test:
          Method test():
            @IntRange(from = MY_VALUE, to = Long.MAX_VALUE) because it returns JavaTest#call annotated with @IntRange(from = MY_VALUE, to = Long.MAX_VALUE)
          Method test2():
            @CheckResult(suggest = "My \"suggestion\"\nNext line.") because it returns JavaTest#call2 annotated with @CheckResult(suggest = "My \"suggestion\"\nNext line.")
            @IntRange(from = MY_CONSTANT) because it returns JavaTest#call2 annotated with @IntRange(from = MY_CONSTANT)
        """,
      // In the below, we should be getting a fully qualified name reference to MY_CONSTANT, but it doesn't
      // resolve from unit tests.
      expectedDiffs = """
        @@ -7 +7
          public class Test {
        +     @IntRange(from = JavaTest.MY_VALUE, to = Long.MAX_VALUE)
              public static int test() {
        @@ -11 +12
        +     @CheckResult(suggest = "My \"suggestion\"\nNext line.")
        +     @IntRange(from = MY_CONSTANT)
              public static int test2() {
        """,
      includeAndroidJar = true, // to resolve Long.MAX_VALUE
      extraFiles = arrayOf(
        createFile(
          "JavaTest.java",
          // language=Java
          """
          package test.pkg;

          import androidx.annotation.CheckResult;
          import androidx.annotation.IntRange;
          import test.pkg.Constants.Companion.MY_CONSTANT;

          public class JavaTest {
              public static final int MY_VALUE = -1;

              @IntRange(from = MY_VALUE, to = Long.MAX_VALUE)
              public static int call() {
                  return 0;
              }

              @CheckResult(suggest = "My \"suggestion\"\nNext line.")
              @IntRange(from = MY_CONSTANT)
              public static int call2() {
                  return 0;
              }
          }
          """
        ),
        createFile(
          "Constants.kt",
          // language=kotlin
          """
          package test.pkg
          class Constants {
              companion object {
                  const val MY_CONSTANT = 10L
              }
          }
          fun test(): Int = JavaTest.call()
          """
        ),
      )
    )
  }

  fun testTurnOffInferenceViaSettings() {
    // We're configuring specific settings here which turn off both inheritance and resource inferences
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        class InferTypes {
            fun test1(@DrawableRes d: Int) {
                test1(arg3 = d)
            }
            fun test1(arg1: Int = 0, arg2: Int = 0, arg3: Int = 0, arg4: Int = 0) { }
        }

        open class Parent {
            @CheckResult
            open fun test(): String = ""
        }
        class Child1 : Parent() {
            override fun test(): String = ""
        }
        """,
      expectedReport = "Nothing found.",
      settings = InferAnnotationsSettings().apply { resources = false; inherit = false }
    )
  }

  fun testPublicOnlyJava() {
    // Makes sure that we ignore non-public attributes in Java if that setting is turned on
    checkJava(
      before = """
        package test.pkg;
        import androidx.annotation.*;
        class InferTypes {
            void caller(@DrawableRes int d) {
                callee(d);
            }
            void callee(int arg) { }
            int foo = android.R.string.ok;
        }
        """,
      expectedReport = "Nothing found.",
      settings = InferAnnotationsSettings().apply { publicOnly = true }
    )
  }

  fun testPublicOnlyKotlin() {
    // Makes sure that we ignore non-public attributes in Java if that setting is turned on
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        class InferTypes {
            fun caller(@DrawableRes d: Int) {
                callee(arg3 = d)
            }
            internal fun callee(arg1: Int = 0) { }
            private var foo = android.R.string.ok
        }
        """,
      expectedReport = "Nothing found.",
      settings = InferAnnotationsSettings().apply { publicOnly = true }
    )
  }

  fun testKotlinInternal() {
    // Makes sure that when we generate signatures for internal Kotlin elements, we don't include
    // the mangling signatures (a$b$c...)
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        internal class InferTypes {
            internal fun caller(@DrawableRes d: Int) {
                callee(arg3 = d)
            }
            internal fun callee(arg1: Int = 0) { }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method callee(int):
            Parameter int arg1:
              @DrawableRes because it's passed d (a drawable) in a call from InferTypes#caller
        """
    )
  }

  fun testResourceTypes1() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 1: here we use default and named parameters to make sure we correctly
            // match up parameters with arguments; we can conclude that arg3 should be a
            // drawable
            fun test1(@DrawableRes d: Int) {
                test1target(arg3 = d)
            }

            fun test1target(arg1: Int = 0, arg2: Int = 0, arg3: Int = 0, arg4: Int = 0) { }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method test1target(int,int,int,int):
            Parameter int arg3:
              @DrawableRes because it's passed d (a drawable) in a call from InferTypes#test1
        """
    )
  }

  fun testResourceTypes2() {
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 2: here we should conclude that test2 is a property of type @StringRes
            var test2 = android.R.string.ok
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Property test2:
            @StringRes because it's assigned android.R.string.ok
        """
    )
  }

  fun testResourceTypes2Java() {
    // Like testResourceTypes2 but for Java
    checkJava(
      before = """
        package test.pkg;

        import androidx.annotation.*;
        class InferTypes {
            // Test 2: here we should conclude that test2 is a field of type @StringRes
            int test2 = android.R.string.ok;
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Field test2:
            @StringRes because it's assigned android.R.string.ok
        """
    )
  }

  fun testResourceTypes3() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Here we're assigning a return value that has resource types. Here we're testing
            // multiple things: (1) that for a property initializer we transfer resource types
            // associated with the initializer, and (2) that we'll include transitively inferred
            // constraints
            var test3: Int = test3a(0)

            private fun test3a(i: Int): Int {
              return android.R.string.ok
            }

            // Make sure that when we inferred on a property, we will transitively use that
            // in further analysis, e.g. suggest returning it here
            fun test3b(): Int {
                return test3
            }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method test3a(int):
            @StringRes because it returns android.R.string.ok
          Method test3b():
            @StringRes because it returns InferTypes#test3 annotated with @StringRes
          Property test3:
            @StringRes because it's assigned test3a(0), a string
        """
    )
  }

  fun testResourceTypes4() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 4: if we return something of a known resource type, we should add
            // it to the return value. Here we should be adding both @StringRes
            // and @DrawableRes. We've added two @DrawableRes' to make sure we don't
            // add it twice, and an existing return value to make sure we merge rather
            // than replace. We're also testing that we can handle lifted-out return
            // statements. We're also making sure we handle resource constants directly.
            @ColorRes
            fun test4(@StringRes s: Int, @DrawableRes d1: Int, @DrawableRes d2: Int, condition: Int): Int {
                return if (condition < 0) {
                    d1
                } else if (condition > 10) {
                    d2
                } else if (condition == 5) {
                    android.R.dimen.app_icon_size
                } else {
                    s
                }
            }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method test4(int,int,int,int):
            @DimenRes because it returns android.R.dimen.app_icon_size
            @DrawableRes because it returns d2, a drawable
            @StringRes because it returns s, a string
        """
    )
  }

  fun testResourceTypes5() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 5: Here we're testing that if we *return* an @AnyRes, the method
            // is annotated with @AnyRes. And any *other* resource types, such as a @DrawableRes,
            // gets absorbed into the @AnyRes.
            fun test5(@AnyRes a: Int, @DrawableRes d: Int, condition: Boolean): Int {
                if (condition) {
                    return a
                }
                return d
            }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method test5(int,int,boolean):
            @AnyRes because it returns a annotated with @AnyRes
        """
    )
  }

  fun testResourceTypes6() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 6: If we *pass* an @AnyRes to another method, that parameter has to also
            // allow any resource. And again, we absorb all inferred types into @AnyRes.
            fun test6(@AnyRes a: Int, @DrawableRes d: Int, condition: Boolean) {
                if (condition) {
                    test6(a)
                }
                test6(d)
            }
            fun test6(unknown: Int) {
            }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes:
          Method test6(int):
            Parameter int unknown:
              @AnyRes because it's passed a (a resource of any type) in a call from InferTypes#test6
        """,
      expectedDiffs = """
        @@ -13 +13
              }
        -     fun test6(unknown: Int) {
        +     fun test6(@AnyRes unknown: Int) {
              }
        """
    )
  }

  fun testResourceTypes7() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 7: If we call a method where the parameter indicates that it can take
            // any resource, that does *not* mean we should assume that this specific
            // argument is allowed to be any resource type.
            fun test7(b: Boolean, drawable: Int, @DrawableRes drawable2: Int) {
                test7(drawable)
                test7(drawable2)
            }
            fun test7(@AnyRes a: Int) {
            }
        }
        """,
      expectedReport = "Nothing found."
    )
  }

  fun testResourceTypes8() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 8: Similarly, if a method parameter allows multiple resource types,
            // that does not mean we can assume they all will.
            fun test8(b: Boolean, s: Int) {
                test8(s)
            }
            fun test8(@StringRes @ColorRes a: Int) {
            }
        }
        """,
      expectedReport = "Nothing found."
    )
  }

  fun testResourceTypes9() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 9: Make sure we transfer resource annotations down the hierarchy
            open class Parent1 { @StringRes open fun test(@DrawableRes d: Int): Int = 0 }
            class Child1 : Parent1() { override fun test(d: Int): Int = 0 }
        }
        """,
      expectedReport = """
        Class test.pkg.InferTypes.Child1:
          Method test(int):
            @StringRes because it extends or is overridden by an annotated method
          Method test(int):
            Parameter int d:
              @DrawableRes because it extends a method with that parameter annotated or inferred
        """,
      expectedDiffs = """
        @@ -7 +7
              open class Parent1 { @StringRes open fun test(@DrawableRes d: Int): Int = 0 }
        -     class Child1 : Parent1() { override fun test(d: Int): Int = 0 }
        +     class Child1 : Parent1() { @StringRes
        +     override fun test(@DrawableRes d: Int): Int = 0 }
          }
        """
    )
  }

  fun testResourceTypes10() {
    @Suppress("MemberVisibilityCanBePrivate")
    checkKotlin(
      before = """
        package test.pkg

        import androidx.annotation.*
        class InferTypes {
            // Test 10: Transfer resource annotations up the hierarchy?
            open class Parent2 { open fun test(d: Int): Int = 0 }
            class Child2 : Parent2() { @StringRes override fun test(@DrawableRes d: Int): Int = 0 }

        }
        """,
      // For now, we don't push requirements *up* in the hierarchy. Maybe we should... maybe we shouldn't....
      expectedReport = "Nothing found."
    )
  }

  fun testResourceTypes11() {
    // Make sure we handle the special marker resource types @ColorInt and @Px/@Dimension correctly
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        fun test11(p1: Int, p2: Int, p3: Int) {
            paint(p1, p2)
            style(p3)
        }
        fun paint(@ColorInt c: Int, @Px p: Int) { }
        fun style(@StyleableRes s: Int) { }
        """,
      expectedReport = """
        Function test.pkg.test11(int,int,int):
          Parameter int p1:
            @ColorInt because it's passed to the c parameter in paint, a color int
          Parameter int p2:
            @Px because it's passed to the p parameter in paint, a pixel dimension
          Parameter int p3:
            @StyleableRes because it's passed to the s parameter in style, a styleable
        """,
      expectedDiffs =
      """
        @@ -3 +3
          import androidx.annotation.*
        - fun test11(p1: Int, p2: Int, p3: Int) {
        + fun test11(@ColorInt p1: Int, @Px p2: Int, @StyleableRes p3: Int) {
              paint(p1, p2)
        """
    )
  }

  fun testDimensionUnits() {
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        import androidx.annotation.Dimension.DP
        fun test11(p1: Int, p2: Int, p3: Int) {
            paint(p1, p2, p3)
        }
        // @Dimension without unit defaults to PX, equivalent to @Px
        fun paint(@Dimension d: Int, @Dimension(unit = Dimension.SP) s: Int, @Dimension(unit = DP) d2: Int) { }
      """,
      expectedReport = """
        Function test.pkg.test11(int,int,int):
          Parameter int p1:
            @Px because it's passed to the d parameter in paint, a pixel dimension
          Parameter int p2:
            @Dimension(SP) because it's passed to the s parameter in paint, a scale-independent (sp) pixel dimension
          Parameter int p3:
            @Dimension(DP) because it's passed to the d2 parameter in paint, a density-independent (dp) pixel dimension
        """,
      expectedDiffs = """
        @@ -4 +4
          import androidx.annotation.Dimension.DP
        - fun test11(p1: Int, p2: Int, p3: Int) {
        + fun test11(@Px p1: Int, @Dimension(Dimension.SP) p2: Int, @Dimension(DP) p3: Int) {
              paint(p1, p2, p3)
        """
    )
  }

  fun testLambdaVariables() {
    @Suppress("Convert2MethodRef", "CodeBlock2Expr")
    checkKotlin(
      before = """
        package test.pkg
        import androidx.annotation.*
        fun testLambda(list: List<Int>) {
            list.forEach { item-> paint(item) }
            list.forEach { paint(it) }
            for (icon in list) {
                paint(icon)
            }
        }
        fun paint(@DrawableRes drawable: Int) {}
        """,
      expectedReport = "Nothing found.",
      settings = InferAnnotationsSettings().apply { annotateLocalVariables = true },
      extraFiles = arrayOf(
        createFile(
          "JavaTest.java",
          // language=Java
          """
          package test.pkg;
          import androidx.annotation.*;
          import java.util.List;

          public class JavaTest {
              public void test(List<Integer> list) {
                  list.forEach(integer -> {
                      paint(integer);
                  });
                  list.forEach(this::paint);
                  for (Integer icon : list) {
                      paint(icon);
                  }
              }

              public void paint(@DrawableRes Integer drawable) {
              }
          }
          """
        )
      ),
    )
  }

  fun testInferParameterFromUsage() {
    doTest(
      """
      Class InferParameterFromUsage:
        Method inferParameterFromMethodCall(int,int):
          Parameter int id:
            @DimenRes because it's passed to the id parameter in InferParameterFromUsage#getDimensionPixelSize, a dimension
      """
    )
  }

  fun testInferResourceFromArgument() {
    doTest(
      """
      Class InferResourceFromArgument:
        Method inferredParameterFromOutsideCall(boolean,int):
          Parameter int inferredDimension:
            @DimenRes because it's passed R.dimen.some_dimension in a call from InferResourceFromArgument#callWhichImpliesParameterType
      """
    )
  }

  fun testInferMethodAnnotationFromReturnValue() {
    doTest(
      """
      Class InferMethodAnnotationFromReturnValue:
        Method test(int,boolean):
          @DrawableRes because it returns R.mipmap.some_image, a drawable
          @IdRes because it returns id annotated with @IdRes
      """
    )
  }

  fun testReturnValue() {
    doTest(
      KotlinFileType.INSTANCE,
      """
      package test.pkg
      import androidx.annotation.DimenRes

      fun unknownReturnType(): Int = getKnownReturnType();

      @DimenRes fun getKnownReturnType(): Int { return 0 }
      """,
      """
      Function test.pkg.unknownReturnType():
        @DimenRes because it returns getKnownReturnType annotated with @DimenRes
      """
    )
  }

  fun testInferFromInheritance() {
    doTest(
      """
      Class InferFromInheritance.Child:
        Method foo(int):
          @DrawableRes because it extends or is overridden by an annotated method
        Method something(int):
          Parameter int foo:
            @DrawableRes because it extends a method with that parameter annotated or inferred
      """
    )
  }

  fun testEnforcePermission1() {
    @Suppress("SwitchStatementWithTooFewBranches")
    checkJava(
      before = """
        import android.content.Context;
        import androidx.annotation.RequiresPermission;
        public class EnforcePermission {
            public static final String MY_PERMISSION = "mypermission";
            private Context mContext;
            boolean impliedPermission() {
                // From this we should infer @RequiresPermission(MY_PERMISSION)
                mContext.enforceCallingOrSelfPermission(MY_PERMISSION, "");
                return true;
            }
            boolean impliedPermissionByStringLiteral() {
                // From this we should infer @RequiresPermission(MY_PERMISSION)
                mContext.enforceCallingOrSelfPermission("mypermission", "");
                mContext.enforceCallingOrSelfPermission("mypermission", "");
                return true;
            }
            boolean conditional(int x) { // don't report
                if (x > 10) {
                  mContext.enforceCallingOrSelfPermission("MY_PERMISSION", "");
                }
                switch(x) {
                  case 10: {
                    mContext.enforceCallingOrSelfPermission("MY_PERMISSION", "");
                  }
                }
                return true;
            }
        }
        """,
      expectedReport = """
        Class EnforcePermission:
          Method impliedPermission():
            @RequiresPermission(EnforcePermission.MY_PERMISSION) because it calls enforceCallingOrSelfPermission
          Method impliedPermissionByStringLiteral():
            @RequiresPermission("mypermission") because it calls enforceCallingOrSelfPermission
        """,
      expectedDiffs = """
        @@ -6 +6
              private Context mContext;
        +     @RequiresPermission(EnforcePermission.MY_PERMISSION)
              boolean impliedPermission() {
        @@ -11 +12
              }
        +     @RequiresPermission("mypermission")
              boolean impliedPermissionByStringLiteral() {
        """
    )
  }

  fun testPermissionMany() {
    checkKotlin(
      before = """
        package test.pkg

        import android.Manifest.permission.ACCESS_COARSE_LOCATION
        import android.Manifest.permission.ACCESS_FINE_LOCATION
        import androidx.annotation.RequiresPermission

        @RequiresPermission(allOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
        fun allOf() {
        }

        @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
        fun anyOf() {
        }

        @RequiresPermission(ACCESS_COARSE_LOCATION)
        fun single1() {
        }

        @RequiresPermission(value = ACCESS_COARSE_LOCATION)
        fun single2() {
        }

        @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION])
        fun single3() {
        }

        fun callAllOf() {
          allOf()
        }

        fun callAnyOf() {
          anyOf()
        }

        fun callSingle1() {
          single1()
        }

        fun callSingle2() {
          single2()
        }

        fun callSingle3() {
          single3()
        }
        """,
      expectedReport = """
        Function test.pkg.callAllOf():
          @RequiresPermission(allOf=[Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION]) because it calls allOf
        Function test.pkg.callAnyOf():
          @RequiresPermission(anyOf=[Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION]) because it calls anyOf
        Function test.pkg.callSingle1():
          @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION) because it calls single1
        Function test.pkg.callSingle2():
          @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION) because it calls single2
        Function test.pkg.callSingle3():
          @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION) because it calls single3
        """,
      expectedDiffs = """
        @@ -27 +27
        + @RequiresPermission(allOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
          fun callAllOf() {
        @@ -31 +32
        + @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
          fun callAnyOf() {
        @@ -35 +37
        + @RequiresPermission(ACCESS_COARSE_LOCATION)
          fun callSingle1() {
        @@ -39 +42
        + @RequiresPermission(ACCESS_COARSE_LOCATION)
          fun callSingle2() {
        @@ -43 +47
        + @RequiresPermission(ACCESS_COARSE_LOCATION)
          fun callSingle3() {
        """,
      includeAndroidJar = true
    )
  }

  fun testLocalVariables() {
    checkJava(
      before = """
        import androidx.annotation.*;
        public class LocalVars {
            public void call(@DrawableRes int drawable) {
            }
            public void test() {
                int drawable = 0;
                call(drawable);
            }
        }
        """,
      settings = InferAnnotationsSettings().apply { annotateLocalVariables = true },
      expectedReport = """
        @DrawableRes because it's passed to the drawable parameter in LocalVars#call, a drawable
        """,
      expectedDiffs = """
        @@ -6 +6
              public void test() {
        -         int drawable = 0;
        +         @DrawableRes int drawable = 0;
                  call(drawable);
        """
    )
  }

  fun testEnforcePermission2() {
    checkJava(
      before = """
        import androidx.annotation.RequiresPermission;

        @SuppressWarnings({"unused", "WeakerAccess"})
        public class EnforcePermission {
            public static final String MY_PERMISSION = "mypermission";

            public void unconditionalPermission() {
                // This should transfer the permission requirement from impliedPermission to this method
                impliedPermission();
            }

            @RequiresPermission(EnforcePermission.MY_PERMISSION)
            boolean impliedPermission() {
                return true;
            }
        }
        """,
      expectedReport = """
        Class EnforcePermission:
          Method unconditionalPermission():
            @RequiresPermission(EnforcePermission.MY_PERMISSION) because it calls EnforcePermission#impliedPermission
        """,
      expectedDiffs = """
        @@ -7 +7
        +     @RequiresPermission(EnforcePermission.MY_PERMISSION)
              public void unconditionalPermission() {
        """
    )
  }

  fun ignored_testConditionalPermission() {
    if (!INFER_ANNOTATIONS_REFACTORING_ENABLED.get()) {
      return
    }

    // None of the permission requirements should transfer; all calls are conditional
    try {
      doTest("Nothing found.")
      fail("Should infer nothing")
    } catch (e: RuntimeException) {
      if (!Comparing.strEqual(e.message, "Nothing found to infer")) {
        fail()
      }
    }
  }

  fun ignored_testIndirectPermission() {
    // Not yet implemented: Expected fail!
    try {
      doTest(null)
      fail("Expected this test to fail")
    } catch (e: java.lang.RuntimeException) {
      assertEquals("Nothing found to infer", e.message)
    }
  }

  fun testReflectionJava() {
    @Suppress("RedundantThrows")
    checkJava(
      before = """
        package test.pkg;
        import java.lang.reflect.InvocationTargetException;
        import java.lang.reflect.Method;

        @SuppressWarnings({"unused", "WeakerAccess"})
        public class Reflection {
            public void usedFromReflection1(int value) {
            }

            public static void usedFromReflection2(int value) {
            }

            public static void usedFromReflection2(int value, int value2) {
            }

            public static void usedFromReflection2(String value) {
            }

            public void reflect1(Object o) throws ClassNotFoundException, NoSuchMethodException,
                    InvocationTargetException, IllegalAccessException {
                Class<?> cls = Class.forName("test.pkg.Reflection");
                Method method = cls.getDeclaredMethod("usedFromReflection1", int.class);
                method.invoke(o, 42);
            }

            public void reflect2() throws ClassNotFoundException, NoSuchMethodException,
                    InvocationTargetException, IllegalAccessException {
                Class<Reflection> cls = Reflection.class;
                Method method = cls.getDeclaredMethod("usedFromReflection2", int.class);
                method.invoke(null, 42);
            }

            public void reflect3() throws ClassNotFoundException, NoSuchMethodException,
                    InvocationTargetException, IllegalAccessException {
                Reflection.class.getDeclaredMethod("usedFromReflection2", int.class, int.class).invoke(null, 42, 42);
            }

            public void reflect4() throws ClassNotFoundException, NoSuchMethodException,
                    InvocationTargetException, IllegalAccessException {
                Class.forName("test.pkg.Reflection").getMethod("usedFromReflection2", String.class).invoke(null, "Hello World");
            }
        }
      """,
      expectedReport = """
        Class test.pkg.Reflection:
          Method usedFromReflection1(int):
            @Keep because it is called reflectively from Reflection#reflect1
          Method usedFromReflection2(String):
            @Keep because it is called reflectively from Reflection#reflect4
          Method usedFromReflection2(int):
            @Keep because it is called reflectively from Reflection#reflect2
          Method usedFromReflection2(int,int):
            @Keep because it is called reflectively from Reflection#reflect3
        """,
      expectedDiffs = """
        @@ -2 +2
          package test.pkg;
        + import androidx.annotation.Keep;
        +
          import java.lang.reflect.InvocationTargetException;
        @@ -7 +9
          public class Reflection {
        +     @Keep
              public void usedFromReflection1(int value) {
        @@ -10 +13
        +     @Keep
              public static void usedFromReflection2(int value) {
        @@ -13 +17
        +     @Keep
              public static void usedFromReflection2(int value, int value2) {
        @@ -16 +21
        +     @Keep
              public static void usedFromReflection2(String value) {
        """
    )
  }

  fun testReflectionKotlin() {
    // This tests regular Kotlin access of Java reflection, not the specialized Kotlin reflection APIs
    @Suppress("RemoveRedundantQualifierName")
    checkKotlin(
      before = """
        @file:Suppress("unused", "UNUSED_PARAMETER")
        package test.pkg
        import androidx.annotation.*
        class Reflection(value: Int) {
            constructor(value1: Int, value2: Int) : this(value1)
            fun usedFromReflection1(value: Int) {}
            fun usedFromReflection2(value: Int) {}
            fun usedFromReflection2(value: Int, value2: Int) {}
            fun usedFromReflection2(value: String?) {}
            fun usedFromReflection2(list: List<String>) {}

            fun reflect1(o: Any?) {
                val cls = Class.forName("test.pkg.Reflection")
                val method = cls.getDeclaredMethod("usedFromReflection1", Int::class.javaPrimitiveType)
                method.invoke(o, 42)
            }

            fun reflect2() {
                val cls = Reflection::class.java
                val method = cls.getDeclaredMethod("usedFromReflection2", Int::class.javaPrimitiveType)
                method.invoke(null, 42)
            }

            fun reflect3() {
                Reflection::class.java.getDeclaredMethod(
                    ("usedFromReflection2"),
                    (Int::class.java),
                    Int::class.javaPrimitiveType
                ).invoke(null, 42, 42)
            }

            fun reflect4() {
                Class.forName("test.pkg.Reflection").getMethod("usedFromReflection2", String::class.java)
                    .invoke(null, "Hello World")
            }

            fun reflect5() {
                val cls = test.pkg.Reflection::class.java
                val method = cls.getDeclaredMethod("usedFromReflection2", List::class.java)
                method.invoke(null, 42)
            }

            fun reflect6() {
                // Constructor
                Reflection::class.java.getDeclaredConstructor(Int::class.java).newInstance(0)
            }

            fun reflect7() {
                // Constructor
                val constructor = Reflection::class.java.getConstructor(Int::class.java, Int::class.java)
                constructor.isAccessible = true
                constructor.newInstance(0, 0)
            }
        }
      """,
      // TODO: usedFromReflection2(String>) below is suspicious; figure out why
      expectedReport = """
        Class test.pkg.Reflection:
          Method Reflection(int):
            @Keep because it is called reflectively from Reflection#reflect6
          Method Reflection(int,int):
            @Keep because it is called reflectively from Reflection#reflect7
          Method usedFromReflection1(int):
            @Keep because it is called reflectively from Reflection#reflect1
          Method usedFromReflection2(String):
            @Keep because it is called reflectively from Reflection#reflect4
          Method usedFromReflection2(String>):
            @Keep because it is called reflectively from Reflection#reflect5
          Method usedFromReflection2(int):
            @Keep because it is called reflectively from Reflection#reflect2
          Method usedFromReflection2(int,int):
            @Keep because it is called reflectively from Reflection#reflect3
              """,
      expectedDiffs = """
        @@ -4 +4
          import androidx.annotation.*
        - class Reflection(value: Int) {
        + class Reflection @Keep constructor(value: Int) {
        +     @Keep
              constructor(value1: Int, value2: Int) : this(value1)
        @@ -6 +7
              constructor(value1: Int, value2: Int) : this(value1)
        +     @Keep
              fun usedFromReflection1(value: Int) {}
        @@ -7 +9
              fun usedFromReflection1(value: Int) {}
        +     @Keep
              fun usedFromReflection2(value: Int) {}
        @@ -8 +11
              fun usedFromReflection2(value: Int) {}
        +     @Keep
              fun usedFromReflection2(value: Int, value2: Int) {}
        @@ -9 +13
              fun usedFromReflection2(value: Int, value2: Int) {}
        +     @Keep
              fun usedFromReflection2(value: String?) {}
        @@ -10 +15
              fun usedFromReflection2(value: String?) {}
        +     @Keep
              fun usedFromReflection2(list: List<String>) {}
        """
    )
  }

  fun testFieldReflection() {
    // From androidx
    checkKotlin(
      before = """
        @file:JvmName("InspectableValueKt")
        package androidx.compose.ui.platform
        import android.util.Log
        var isDebugInspectorInfoEnabled = false
        interface InspectableValue
        private fun enableDebugInspectorInfo() {
            // Set isDebugInspectorInfoEnabled to true via reflection such that R8 cannot see the
            // assignment. This allows the InspectorInfo lambdas to be stripped from release builds.
            try {
                val packageClass = Class.forName("androidx.compose.ui.platform.InspectableValueKt")
                val field = packageClass.getDeclaredField("isDebugInspectorInfoEnabled")
                field.isAccessible = true
                field.setBoolean(null, true)
            } catch (ignored: Exception) {
                Log.w("tag", "Could not access isDebugInspectorInfoEnabled. Please set explicitly.")
            }
        }
        """,
      expectedReport = """
        Property isDebugInspectorInfoEnabled:
          @Keep because it is called reflectively from enableDebugInspectorInfo
        """
    )
  }

  fun ignored_testThreadFlow() {
    // Not yet implemented: Expected fail!
    try {
      doTest(null)
      fail("Expected this test to fail")
    } catch (e: java.lang.RuntimeException) {
      assertEquals("Nothing found to infer", e.message)
    }
  }

  fun testMultiplePasses() {
    doTest(
      """
      Class A:
        Method fromA(int):
          Parameter int id:
            @DimenRes because it's passed to the id parameter in A#something, a dimension

      Class D:
        Method d(int):
          Parameter int id:
            @DrawableRes because it's passed to the id parameter in D#something, a drawable
      """,
      findVirtualFile(INFER_PATH + "A.java"),
      findVirtualFile(INFER_PATH + "B.java"),
      findVirtualFile(INFER_PATH + "C.java"),
      findVirtualFile(INFER_PATH + "D.java")
    )
  }

  fun testPutValue() {
    if (!INFER_ANNOTATIONS_REFACTORING_ENABLED.get()) {
      return
    }

    // Ensure that if we see somebody putting a resource into
    // an intent map, we don't then conclude that ALL values put
    // into the map must be of that type
    try {
      doTest("Nothing found.")
      fail("Should infer nothing")
    } catch (e: RuntimeException) {
      if (!Comparing.strEqual(e.message, "Nothing found to infer")) {
        fail()
      }
    }
  }

  fun testHiddenJava() {
    @Suppress("JavaDoc")
    checkJava(
      before = """
      import androidx.annotation.DimenRes;
      /** @hide */
      public class InferParameterFromUsage {
          public void inferParameterFromMethodCall(int sample, int id) {
              // Here we can infer that parameter id must be @DimenRes from the below method call
              getDimensionPixelSize(id);
          }
          private void getDimensionPixelSize(@DimenRes int id) {
          }
      }
      """,
      expectedReport = """
      Class InferParameterFromUsage (Hidden):
        Method inferParameterFromMethodCall(int,int) (Hidden):
          Parameter int id:
            @DimenRes because it's passed to the id parameter in InferParameterFromUsage#getDimensionPixelSize, a dimension
      """
    )
  }

  fun testHiddenKotlin() {
    checkKotlin(
      before = """
      package test.pkg
      import androidx.annotation.DimenRes
      /**
       * @hide
       */
      class InferParameterFromUsage {
          fun inferParameterFromMethodCall(sample: Int, id: Int) {
              // Here we can infer that parameter id must be @DimenRes from the below method call
              getDimensionPixelSize(id)
          }
          private fun getDimensionPixelSize(@DimenRes id: Int) {}
      }
      """,
      expectedReport = """
      Class test.pkg.InferParameterFromUsage (Hidden):
        Method inferParameterFromMethodCall(int,int) (Hidden):
          Parameter int id:
            @DimenRes because it's passed to the id parameter in InferParameterFromUsage#getDimensionPixelSize, a dimension
      """
    )
  }

  // ---------------- Test infrastructure below ----------------

  private var myResultFiles = mutableMapOf<String, VirtualFile>()

  // Used by checkResultByFile to locate test data; there is no checkResult method that
  // lets us pass in a string, so we use this to inject contents we want for the file
  // based checking method.
  override fun findVirtualFile(filePath: String): VirtualFile {
    myResultFiles[filePath]?.let { return it }
    return super.findVirtualFile(filePath)
  }

  /**
   * Given [before], a Java string containing a source file, this method runs
   * inference, and then optionally checks that the generated report matches
   * [expectedReport] and that when we apply the suggestions the resulting
   * file is updated to match [after].
   */
  private fun checkJava(
    @Language("java") before: String = "",
    expectedReport: String? = null,
    @Language("java") after: String? = null,
    expectedDiffs: String? = null,
    settings: InferAnnotationsSettings = InferAnnotationsSettings(),
    includeAndroidJar: Boolean = false,
    vararg extraFiles: PsiFile
  ) {
    doTest(JavaFileType.INSTANCE, before, expectedReport, after, expectedDiffs, settings, includeAndroidJar, *extraFiles)
  }

  /**
   * Given [before], a Kotlin string containing a source file, this method
   * runs inference, and then optionally checks that the generated report
   * matches [expectedReport] and that when we apply the suggestions the
   * resulting file is updated to match [after].
   */
  private fun checkKotlin(
    @Language("kotlin") before: String = "",
    expectedReport: String? = null,
    @Language("kotlin") after: String? = null,
    expectedDiffs: String? = null,
    settings: InferAnnotationsSettings = InferAnnotationsSettings(),
    includeAndroidJar: Boolean = false,
    vararg extraFiles: PsiFile
  ) {
    doTest(KotlinFileType.INSTANCE, before, expectedReport, after, expectedDiffs, settings, includeAndroidJar, *extraFiles)
  }

  /**
   * Given a [before] string containing a source file of type [fileType],
   * runs inference, and then optionally checks that the generated report
   * matches [expectedReport] and that when we apply the suggestions the
   * resulting file is updated to match [after].
   */
  private fun doTest(
    fileType: FileType,
    before: String,
    expectedReport: String? = null,
    after: String? = null,
    expectedDiffs: String? = null,
    settings: InferAnnotationsSettings = InferAnnotationsSettings(),
    includeAndroidJar: Boolean = false,
    vararg extraFiles: PsiFile
  ) {
    assertTrue(expectedReport != null || after != null || expectedDiffs != null) // must test *something*
    doTest(
      setup = { inference ->
        val file = configureByText(fileType, before.trimIndent())
        inference.collect(file)
        if (extraFiles.isEmpty()) {
          AnalysisScope(file)
        } else {
          for (extraFile in extraFiles) {
            inference.collect(extraFile)
          }
          AnalysisScope(project, extraFiles.map { it.virtualFile } + file.virtualFile)
        }
      },
      verify = { inference, report ->
        expectedReport?.let { assertEquals(it.trimIndent(), report) }

        if (after != null || expectedDiffs != null) {
          inference.apply(inference.settings, project)
        }

        if (expectedDiffs != null) {
          val actualAfter = file.text
          val actualDiffs = getDiff(before.trimIndent(), actualAfter)
          assertEquals(expectedDiffs.trimIndent().trim(), actualDiffs)
        }

        if (after != null) {
          val outputBytes = after.trimIndent().toByteArray()
          val fileName = "after.${fileType.defaultExtension}"
          try {
            val file = object : FakeVirtualFile(myFile.virtualFile.parent, fileName) {
              override fun getCharset(): Charset = Charsets.UTF_8
              override fun getInputStream(): InputStream = ByteArrayInputStream(outputBytes)
              override fun getLength(): Long = outputBytes.size.toLong()
            }
            myResultFiles[fileName] = file

            checkResultByFile(fileName)
          } finally {
            myResultFiles.remove(fileName)
          }
        }
      },
      settings = settings,
      includeAndroidJar = includeAndroidJar
    )
  }

  private fun getDiff(left: String, right: String) =
    TestUtils.getDiff(left, right, 1).trim().lines().map { it.trimEnd() }.filter { it.isNotBlank() }.joinToString("\n")

  /**
   * Loads the test file named after the calling unit test, runs inference on
   * it, generates an inference report and compares it to [expectedReport].
   */
  private fun doTest(expectedReport: String?) {
    val name = getTestName(false) + ".java"
    doTest(
      setup = { inference ->
        configureByFile(INFER_PATH + "before" + name)
        inference.collect(file)
        AnalysisScope(file)
      },
      verify = { inference, report ->
        expectedReport?.let { assertEquals(it.trimIndent(), report) }
        inference.apply(inference.settings, project)
        checkResultByFile(INFER_PATH + "after" + name)
      }
    )
  }

  /**
   * Loads the test file named after the calling unit test, adds in all the
   * extra [files], and runs inference across all those files, generates an
   * inference report and compares it to [expectedReport].
   */
  private fun doTest(@Suppress("SameParameterValue") expectedReport: String?, vararg files: VirtualFile) {
    assert(files.isNotEmpty()) // should have chosen other override
    doTest(
      setup = { inference ->
        configureByFiles(null, *files)
        for (i in 0 until InferAnnotationsAction.MAX_PASSES) {
          for (virtualFile in files) {
            val psiFile = psiManager.findFile(virtualFile)
            assertNotNull(psiFile)
            inference.collect(psiFile!!)
          }
        }
        AnalysisScope(project, files.toList())
      },
      verify = { _, report ->
        expectedReport?.let { assertEquals(it.trimIndent(), report) }
      }
    )
  }

  /**
   * Generic test which performs environment setup and invokes various
   * callbacks to do additional configuration and verification.
   */
  private fun doTest(
    create: (InferAnnotationsSettings) -> InferAnnotations = { InferAnnotations(it, project) },
    setup: (InferAnnotations) -> AnalysisScope,
    verify: (InferAnnotations, String) -> Unit,
    settings: InferAnnotationsSettings = InferAnnotationsSettings(),
    includeAndroidJar: Boolean = false
  ) {
    if (!INFER_ANNOTATIONS_REFACTORING_ENABLED.get()) {
      return
    }
    val annotationsJarPath = "$testDataPath/infer/data.jar"
    val annotationsJar = LocalFileSystem.getInstance().findFileByPath(annotationsJarPath)
    if (annotationsJar != null) {
      val file = JarFileSystem.getInstance().getJarRootForLocalFile(annotationsJar)
      if (file != null) {
        ModuleRootModificationUtil.addModuleLibrary(myModule, file.url)
      }
    }

    if (includeAndroidJar) {
      val androidJar = TestUtils.resolvePlatformPath("android.jar")
      assertNotNull(androidJar)
      val jarFile = LocalFileSystem.getInstance().findFileByPath(androidJar.toString())!!
      val file = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile)
      if (file != null) {
        ModuleRootModificationUtil.addModuleLibrary(myModule, file.url)
      }
    }

    val inference = create(settings)
    val scope: AnalysisScope = setup(inference)

    val inferred = mutableListOf<UsageInfo>()
    inference.collect(inferred, scope, settings.includeBinaries)

    val actual = generateReport(inferred.toTypedArray()).removePrefix(HEADER).trim()
    verify(inference, actual)
  }
}