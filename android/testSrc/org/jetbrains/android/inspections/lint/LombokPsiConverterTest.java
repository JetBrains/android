/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.inspections.lint;

import com.android.annotations.Nullable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import lombok.ast.CompilationUnit;
import lombok.ast.Node;
import lombok.ast.ecj.EcjTreeConverter;
import lombok.ast.printer.SourcePrinter;
import lombok.ast.printer.StructureFormatter;
import lombok.ast.printer.TextFormatter;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import java.util.List;
import java.util.Random;

public class LombokPsiConverterTest extends AndroidTestCase {
  /**
   * Check that AST positions are okay? This works by comparing the
   * offsets of each AST node with the positions in the corresponding
   * offsets in an AST generated with the Parboiled Java parser. There are
   * a lot of individual differences in these two files; it's not clear
   * whether a method should include its javadoc range etc -- so these
   * tests aren't expected to pass, but the diff is useful to inspect
   * the position ranges of AST nodes, and fill out the missing ones etc.
   * (There are quite a few missing ones right now; the focus has been
   * on adding the ones that Lint will actually look up and use.)
   */
  private static final boolean CHECK_POSITIONS = false;

  // TODO:
  // Test interface body, test enum body, test annotation body!

  /** Include varargs in the test (currently fails, but isn't used by lint) */
  private static final boolean INCLUDE_VARARGS = false;

  public void testPsiToLombokConversion1() {
    VirtualFile file = myFixture.copyFileToProject("intentions/R.java", "src/p1/p2/R.java");
    check(file);
  }

  public void testPsiToLombokConversion2() {
    VirtualFile file = myFixture.copyFileToProject("intentions/R.java", "src/p1/p2/R.java");
    check(file);
  }

  public void testPsiToLombokConversion3() {
    // This code is formatted a bit strangely; this is done such that it matches how the
    // code is printed *back* by Lombok's AST pretty printer, so we can diff the two.
    //noinspection FieldMayBeStatic,UnusedAssignment,ConstantConditions,OctalInteger,MismatchedReadAndWriteOfArray,SpellCheckingInspection,MethodMayBeStatic,SynchronizeOnThis,UnnecessaryContinue
    @Language("JAVA")
    String testClass =
      "package p1.p2;\n" +
      "\n" +
      // Imports
      "import java.util.List;\n" +
      "import java.util.regexp.*;\n" +
      "import static java.util.Arrays.asList;\n" +
      "\n" +
      "public final class R2<K,V> {\n" +
      "    int myField1;\n" +
      "    \n" +
      "    final int myField2 = 42;\n" +
      "    \n" +
      "    // Comments and extra whitespace gets stripped\n" +
      "    \n" +
      "    private static final int CONSTANT = 42;\n" +
      "    private int[] foo1 = new int[5];\n" +
      "    private int[] foo2 = new int[] {1};\n" +
      "    private static int myRed = android.R.color.red;\n" +
      "    \n" +
      // Constructors
      "    public R2(int x,List list) {\n" +
      // Method invocations
      "        System.out.println(list.size());\n" +
      "        System.out.println(R2.drawable.s2);\n" +
      "    }\n" +
      "    \n" +
      // Methods
      "    @Override\n" +
      "    @SuppressWarnings({\"foo1\", \"foo2\"})\n" +
      //"    @android.annotation.SuppressLint({\"foo1\",\"foo2\"})\n" +
      //"    @android.annotation.TargetApi(value=5})\n" +
      "    public void myMethod1(List list) {\n" +
      "    }\n" +
      "    \n" +
      "    public int myMethod2() {\n" +
      "        return 42;\n" +
      "    }\n" +
      "    \n" +
      // Misc
      "    private void myvarargs(int" + (INCLUDE_VARARGS ? "..." : "[]") + " x) {\n" +
      "        Collections.<Map<String,String>>emptyMap();\n" +
      "    }\n" +
      "    \n" +
      "    private void myvarargs2(java.lang.String" + (INCLUDE_VARARGS ? "..." : "[]") + " x) {\n" +
      "    }\n" +
      "    \n" +
      "    private void myarraytest(String[][] args, int index) {\n" +
      "        int y = args[5][index + 1];\n" +
      "    }\n" +
      "    \n" +
      "    private void controlStructs(\n" +
      "    @SuppressWarnings(\"all\")\n" +
      "    int myAnnotatedArg) {\n" +
      "        boolean x = false;\n" +
      "        int y = 4, z = 5, w;\n" +
      "        if (x) {\n" +
      "            System.out.println(\"Ok\");\n" +
      "        }\n" +
      "        if (x) {\n" +
      "            System.out.println(\"Ok\");\n" +
      "        } else {\n" +
      "            System.out.println(\"Not OK\");\n" +
      "        }\n" +
      "        String[] args = new String[] {\"test1\", \"test2\"};\n" +
      "        for (String arg : args) {\n" +
      "            System.out.println(arg);\n" +
      "        }\n" +
      "        for (int i = 0, n = args.length; i < n; i++, i--, i++) {\n" +
      "            y++;\n" +
      "            --z;\n" +
      "            w = y;\n" +
      "            x = !x;\n" +
      "            if (w == 2) {\n" +
      "                continue;\n" +
      "            }\n" +
      "        }\n" +
      "        switch (y) {\n" +
      "        case 1:\n" +
      "            {\n" +
      "                x = false;\n" +
      "                break;\n" +
      "            }\n" +
      "        case 2:\n" +
      "            {\n" +
      "            }\n" +
      "        }\n" +
      "        synchronized (this) {\n" +
      "            x = false;\n" +
      "        }\n" +
      "        w = y + z;\n" +
      "        w = y - z;\n" +
      "        w = y * z;\n" +
      "        w = y / z;\n" +
      "        w = y % z;\n" +
      "        w = y ^ z;\n" +
      "        w = y & z;\n" +
      "        w = y | z;\n" +
      "        w = y << z;\n" +
      "        w = y >> z;\n" +
      "        w = y >>> z;\n" +
      "        f = y < z;\n" +
      "        f = y <= z;\n" +
      "        f = y > z;\n" +
      "        f = y >= z;\n" +
      "        y++;\n" +
      "        y--;\n" +
      "        ++y;\n" +
      "        --y;\n" +
      "        y += 1;\n" +
      "        y -= 1;\n" +
      "        y *= 1;\n" +
      "        y /= 1;\n" +
      "        y %= 1;\n" +
      "        y <<= 1;\n" +
      "        y >>= 1;\n" +
      "        y >>>= 1;\n" +

      "        f = f && x;\n" +
      "        f = f || x;\n" +
      "        f = !f;\n" +
      "        f = y != z;\n" +
      "        y = -y;\n" +
      "        y = +y;\n" +
      // We're stripping parentheses from the Lombok AST:
      //"        y = (y + z) * w;\n" +
      "        y = x ? y : w;\n" +
      "\n" +
      // Anonymous inner class
      "        Runnable r = new Runnable() {\n" +
      "          @Override\n" +
      "          public void run() {\n" +
      "            System.out.println(\"Test\");\n" +
      "          }\n" +
      "        };\n" +
      "\n" +
      "    }\n" +
      "    \n" +
      // Innerclass
      "    public static final class drawable {\n" +
      // Check literals
      "        public static String s = \"This is a test\";\n" +
      "        \n" +
      "        public static int s2 = 42;\n" +
      "        \n" +
      "        public static int s2octal = 042;\n" +
      "        \n" +
      "        public static int s2hex = 0x42;\n" +
      "        \n" +
      "        public static long s3 = 42L;\n" +
      "        \n" +
      "        public static double s4 = 3.3;\n" +
      "        \n" +
      "        public static float s5 = 3.2e5f;\n" +
      "        \n" +
      "        public static char s6 = 'x';\n" +
      "        \n" +
      "        public static int icon = 0x7f020000;\n" +
      "        \n" +
      "        public static double s7 = -3.3;\n" +
      "        \n" +
      "        public static int s8 = -1;\n" +
      "        \n" +
      "        public static char s9 = 'a';\n" +
      "    }\n" +
      "}";
    check(testClass, "src/p1/p2/R2.java");
  }

  public void testPsiToLombokConversion4() {
    //noinspection MethodMayBeStatic
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import android.annotation.SuppressLint;\n" +
      "import android.annotation.TargetApi;\n" +
      "import android.os.Build;\n" +
      "import org.w3c.dom.DOMError;\n" +
      "import org.w3c.dom.DOMErrorHandler;\n" +
      "import org.w3c.dom.DOMLocator;\n" +
      "\n" +
      "import android.view.ViewGroup.LayoutParams;\n" +
      "import android.app.Activity;\n" +
      "import android.app.ApplicationErrorReport;\n" +
      "import android.app.ApplicationErrorReport.BatteryInfo;\n" +
      "import android.graphics.PorterDuff;\n" +
      "import android.graphics.PorterDuff.Mode;\n" +
      "import android.widget.Chronometer;\n" +
      "import android.widget.GridLayout;\n" +
      "import dalvik.bytecode.OpcodeInfo;\n" +
      "\n" +
      "public class ApiCallTest extends Activity {\n" +
      "    @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)\n" +
      "    public void method(Chronometer chronometer, DOMLocator locator) {\n" +
      "        String s = \"/sdcard/fyfaen\";\n" +
      "        // Virtual call\n" +
      "        getActionBar(); // API 11\n" +
      "\n" +
      "        // Class references (no call or field access)\n" +
      "        DOMError error = null; // API 8\n" +
      "        Class<?> clz = DOMErrorHandler.class; // API 8\n" +
      "\n" +
      "        // Method call\n" +
      "        chronometer.getOnChronometerTickListener(); // API 3\n" +
      "\n" +
      "        // Inherited method call (from TextView\n" +
      "        chronometer.setTextIsSelectable(true); // API 11\n" +
      "\n" +
      "        // Field access\n" +
      "        int field = OpcodeInfo.MAXIMUM_VALUE; // API 11\n" +
      "        int fillParent = LayoutParams.FILL_PARENT; // API 1\n" +
      "        // This is a final int, which means it gets inlined\n" +
      "        int matchParent = LayoutParams.MATCH_PARENT; // API 8\n" +
      "        // Field access: non final\n" +
      "        BatteryInfo batteryInfo = getReport().batteryInfo;\n" +
      "\n" +
      "        // Enum access\n" +
      "        Mode mode = PorterDuff.Mode.OVERLAY; // API 11\n" +
      "    }\n" +
      "\n" +
      "    // Return type\n" +
      "    GridLayout getGridLayout() { // API 14\n" +
      "        return null;\n" +
      "    }\n" +
      "\n" +
      "    private ApplicationErrorReport getReport() {\n" +
      "        return null;\n" +
      "    }\n" +
      "}\n";

    // Parse the above file as a PSI datastructure
    PsiFile file = myFixture.addFileToProject("src/test/pkg/ApiCallTest.java", testClass);
    check(file, testClass);
  }

  public void testPsiToLombokConversion5() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "/* This stub is for using by IDE only. It is NOT the Manifest class actually packed into APK */\n" +
      "public final class Manifest {\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/Manifest.java", testClass);
    check(file, testClass);
  }

  public void testPsiToLombokConversion6() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "import java.util.HashMap;\n" +
      "\n" +
      "/* This stub is for using by IDE only. It is NOT the Manifest class actually packed into APK */\n" +
      "public final class Wildcards {\n" +
      "  HashMap<Integer, Integer> s4 = new HashMap<Integer, Integer>();\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/Wildcards.java", testClass);
    check(file, testClass);
  }

  public void testPsiToLombokConversion7() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "import java.util.HashMap;\n" +
      "\n" +
      "/* This stub is for using by IDE only. It is NOT the Manifest class actually packed into APK */\n" +
      "public final class R3<K, V> {\n" +
      "  HashMap<Integer, Map<String, Integer>> s1 = new HashMap<Integer, Map<String, Integer>>();\n" +
      "  Map<Map<String[], List<Integer[]>>,List<String[]>>[] s2;\n" +
      "  Map<Integer, Map<String, List<Integer>>> s3;\n" +
      "  int[][] s4;\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R3.java", testClass);
    check(file, testClass);
  }

  // This test currently fails; need to tweak handling of whitespace around type parameters
  //public void testPsiToLombokConversion8() {
  //  String testClass =
  //    "package test.pkg;\n" +
  //    "import java.util.HashMap;\n" +
  //    "\n" +
  //    "/* This stub is for using by IDE only. It is NOT the Manifest class actually packed into APK */\n" +
  //    "public final class R4 {\n" +
  //    "    Object o = Collections.<Map<String,String>>emptyMap();\n" +
  //    "    Object o2 = Collections.< Map < String , String>>>emptyMap();\n" +
  //    "}";
  //  PsiFile file = myFixture.addFileToProject("src/test/pkg/R4.java", testClass);
  //  check(file, testClass);
  //}

  public void testPsiToLombokConversion9() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "public final class R5 {\n" +
      "    public void foo() {\n" +
      "        setTitleColor(android.R.color.black);\n" +
      "        setTitleColor(R.color.black);\n" +
      "    }\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R5.java", testClass);
    check(file, testClass);
  }

  public void testPsiToLombokConversion10() {
    // Checks that annotations on variable declarations are preserved
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import android.annotation.SuppressLint;\n" +
      "import android.annotation.TargetApi;\n" +
      "import android.os.Build;\n" +
      "\n" +
      "public class SuppressTest {\n" +
      "    @SuppressLint(\"ResourceAsColor\")\n" +
      "    @TargetApi(Build.VERSION_CODES.HONEYCOMB)\n" +
      "    private void test() {\n" +
      "        @SuppressLint(\"SdCardPath\") String s = \"/sdcard/fyfaen\";\n" +
      "        setTitleColor(android.R.color.black);\n" +
      "    }\n" +
      "\n" +
      "    private void setTitleColor(int color) {\n" +
      "        //To change body of created methods use File | Settings | File Templates.\n" +
      "    }\n" +
      "}\n";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/SuppressTest.java", testClass);
    check(file, testClass);
  }

  public void test() {
    //noinspection MethodMayBeStatic,ForLoopReplaceableByWhile,ConstantConditions,InfiniteLoopStatement
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import android.annotation.SuppressLint;\n" +
      "import android.annotation.TargetApi;\n" +
      "import android.os.Build;\n" +
      "\n" +
      "public class EmptyStatementTest {\n" +
      "    public final View test(View start, Predicate<View> predicate) {\n" +
      "        View childToSkip = null;\n" +
      "        for (;;) {\n" +
      "            View view = start.findViewByPredicateTraversal(predicate, childToSkip);\n" +
      "        }\n" +
      "    }\n" +
      "}\n";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/SuppressTest.java", testClass);
    check(file, testClass);
  }

  public void testPsiToLombokConversion11() {
    // Test Polyadic Expression
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "public final class R5 {\n" +
      "    public static final int test = 5;\n" +
      "    public String getString(int id) { return \"\"; }\n" +
      "    public void foo() {\n" +
      "        String trackName = \"test\";\n" +
      "        String x = trackName + \" - \" + getString(R5.test);\n" +
      "    }\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R5.java", testClass);
    check(file, testClass);
  }

  public void testPsiNullValue() {
    // From the IO scheduling app:
    //  String SESSIONS_SNIPPET = "snippet(" + Tables.SESSIONS_SEARCH + ",'{','}','\u2026')";
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "public final class R6 {\n" +
      "    public void foo() {\n" +
      "        String s = \",'{','}','\\u2026')\";\n" +
      "    }\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R6.java", testClass);
    check(file, testClass);
  }

  public void testEmptyR() {
    @Language("JAVA")
    String testClass =
      "/*___Generated_by_IDEA___*/\n" +
      "\n" +
      "package com.g.android.u;\n" +
      "\n" +
      "/* This stub is only used by the IDE. It is NOT the R class actually packed into the APK */\n" +
      "public final class R {\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/com/g/android/u/R.java", testClass);
    check(file, testClass);
  }

  public void test57783() {
    //noinspection UnusedAssignment
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "public final class R7 {\n" +
      "    public void foo() {\n" +
      "        int i = 0;\n" +
      "        int j = 0;\n" +
      "        for (i = 0; i < 10; i++)\n" +
      "            i++;" +
      "        for (i = 0, j = 0; i < 10; i++)\n" +
      "            i++;" +
      "    }\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R7.java", testClass);
    check(file, testClass);
  }

  public void testSuper() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "public final class R8 {\n" +
      "    public String toString() {\n" +
      "        return super.toString();\n" +
      "    }\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R8.java", testClass);
    check(file, testClass);
  }

  public void testPrivateEnum2() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import com.android.demo.myapplication.R;\n" +
      "\n" +
      "public class UnusedReference2 {\n" +
      "    public enum DocumentBulkAction {\n" +
      "        UPDATE_ALL() {\n" +
      "            @Override\n" +
      "            public int getLabelId() {\n" +
      "                return R.string.update_all;\n" +
      "            }\n" +
      "        };\n" +
      "\n" +
      "        public abstract int getLabelId();\n" +
      "    }\n" +
      "}\n";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/UnusedReference2.java", testClass);
    check(file, testClass);
  }

  public void testPrivateEnum() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import android.os.Bundle;\n" +
      "import android.app.Activity;\n" +
      "\n" +
      "public class R9 extends Activity {\n" +
      "\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        setContentView(R.layout.main);\n" +
      "    }\n" +
      "\n" +
      "    private enum IconGridSize {\n" +
      "        NORMAL(R.layout.other);\n" +
      "\n" +
      "        IconGridSize(int foo) {\n" +
      "        }\n" +
      "    }\n" +
      "}\n";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R9.java", testClass);
    check(file, testClass);
  }

  public void testInterface() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "public interface R11 {\n" +
      "    int call();\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R10.java", testClass);
    check(file, testClass);
  }

  public void testAnnotation() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "\n" +
      "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
      "import static java.lang.annotation.ElementType.FIELD;\n" +
      "import static java.lang.annotation.ElementType.METHOD;\n" +
      "import static java.lang.annotation.ElementType.PARAMETER;\n" +
      "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
      "import static java.lang.annotation.RetentionPolicy.SOURCE;\n" +
      "@Retention(CLASS)\n" +
      "@Target({ANNOTATION_TYPE})\n" +
      "public @interface R11 {\n" +
      "    long[] value();\n" +
      "    boolean flag();\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R11.java", testClass);
    check(file, testClass);
  }

  public void testClassLiterals() {
    //noinspection IfStatementWithIdenticalBranches,StatementWithEmptyBody
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import java.lang.reflect.Field;\n" +
      "\n" +
      "public class R13 {\n" +
      "    private static void dumpFlags(Field field) {\n" +
      "        if (field.getType().equals(int.class)) {\n" +
      "        } else if (field.getType().equals(int[].class)) {\n" +
      "        } else if (field.getType().equals(int[][].class)) {\n" +
      "        }\n" +
      "    }\n" +
      "}";
    PsiFile file = myFixture.addFileToProject("src/test/pkg/R12.java", testClass);
    check(file, testClass);
  }

  public void testClassDeclarationInBlockStatement() {
    // Regression test for issue 161534
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "public class R15 {\n" +
      "    private static void sendMessage(String message) {\n" +
      "        int x, y = 5;\n" +
      "        class MessageThread extends Thread {\n" +
      "            @Override public void run() {\n" +
      "            }\n" +
      "        }\n" +
      "        new MessageThread().start();\n" +
      "    }\n" +
      "}\n";

    check(testClass, "src/test/pkg/R15.java");
  }

  public void testJava7() {
    @Language("JAVA")
    String testClass =
      "package test.pkg;\n" +
      "\n" +
      "import java.io.BufferedReader;\n" +
      "import java.io.FileReader;\n" +
      "import java.io.IOException;\n" +
      "import java.lang.reflect.InvocationTargetException;\n" +
      "import java.util.List;\n" +
      "import java.util.Map;\n" +
      "import java.util.TreeMap;\n" +
      "\n" +
      "public class Java7LanguageFeatureTest {\n" +
      "    public void testDiamondOperator() {\n" +
      "        Map<String, List<Integer>> map = new TreeMap<>();\n" +
      "    }\n" +
      "\n" +
      "    public int testStringSwitches(String value) {\n" +
      "        final String first = \"first\";\n" +
      "        final String second = \"second\";\n" +
      "\n" +
      "        switch (value) {\n" +
      "            case first:\n" +
      "                return 41;\n" +
      "            case second:\n" +
      "                return 42;\n" +
      "            default:\n" +
      "                return 0;\n" +
      "        }\n" +
      "    }\n" +
      "\n" +
      "    public String testTryWithResources(String path) throws IOException {\n" +
      "        try (BufferedReader br = new BufferedReader(new FileReader(path))) {\n" +
      "            return br.readLine();\n" +
      "        }\n" +
      "    }\n" +
      "\n" +
      "    public void testNumericLiterals() {\n" +
      "        int thousand = 1_000;\n" +
      "        int million = 1_000_000;\n" +
      "        int binary = 0B01010101;\n" +
      "    }\n" +
      "\n" +
      "    public void testMultiCatch() {\n" +
      "\n" +
      "        try {\n" +
      "            Class.forName(\"java.lang.Integer\").getMethod(\"toString\").invoke(null);\n" +
      "        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {\n" +
      "            e.printStackTrace();\n" +
      "        } catch (ClassNotFoundException e) {\n" +
      "            // TODO: Logging here\n" +
      "        }\n" +
      "    }\n" +
      "}\n";
    PsiFile psiFile = myFixture.addFileToProject("src/test/pkg/R9.java", testClass);

    // Can't call check(psiFile, testClass) here; the source code won't parse
    // with Lombok's parser since it doesn't support Java 7, so we just manually
    // check that the LombokPsiConverter doesn't abort, and format it's view of
    // the Lombok AST and check that it looks like what we expect; an AST containing
    // fragments usable by lint, but not providing support for syntactic constructs
    // such as try with resources or containing type variables where the diamond operator
    // should have been etc.
    assertTrue(psiFile.getClass().getName(), psiFile instanceof PsiJavaFile);
    PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
    CompilationUnit node = LombokPsiConverter.convert(psiJavaFile);
    assertNotNull(node);
    TextFormatter formatter = new TextFormatter();
    node.accept(new SourcePrinter(formatter));
    String actual = formatter.finish();

    assertEquals("package test.pkg;\n" +
                 "\n" +
                 "import java.io.BufferedReader;\n" +
                 "import java.io.FileReader;\n" +
                 "import java.io.IOException;\n" +
                 "import java.lang.reflect.InvocationTargetException;\n" +
                 "import java.util.List;\n" +
                 "import java.util.Map;\n" +
                 "import java.util.TreeMap;\n" +
                 "\n" +
                 "public class Java7LanguageFeatureTest {\n" +
                 "    public void testDiamondOperator() {\n" +
                 "        Map<String, List<Integer>> map = new TreeMap();\n" +
                 "    }\n" +
                 "    \n" +
                 "    public int testStringSwitches(String value) {\n" +
                 "        final String first = \"first\";\n" +
                 "        final String second = \"second\";\n" +
                 "        switch (value) {\n" +
                 "        case first:\n" +
                 "            return 41;\n" +
                 "        case second:\n" +
                 "            return 42;\n" +
                 "        case :\n" +
                 "            return 0;\n" +
                 "        }\n" +
                 "    }\n" +
                 "    \n" +
                 "    public String testTryWithResources(String path) throws IOException {\n" +
                 "        try {\n" +
                 "            return br.readLine();\n" +
                 "        }\n" +
                 "    }\n" +
                 "    \n" +
                 "    public void testNumericLiterals() {\n" +
                 "        int thousand = 1_000;\n" +
                 "        int million = 1_000_000;\n" +
                 "        int binary = 0B01010101;\n" +
                 "    }\n" +
                 "    \n" +
                 "    public void testMultiCatch() {\n" +
                 "        try {\n" +
                 "            Class.forName(\"java.lang.Integer\").getMethod(\"toString\").invoke(null);\n" +
                 "        } catch (?!?INVALID_IDENTIFIER: IllegalAccessException | InvocationTargetException | NoSuchMethodException?!? e) {\n" +
                 "            e.printStackTrace();\n" +
                 "        } catch (ClassNotFoundException e) {\n" +
                 "        }\n" +
                 "    }\n" +
                 "}",
                 actual);
  }

  private void check(VirtualFile file) {
    assertNotNull(file);
    assertTrue(file.exists());
    Project project = getProject();
    assertNotNull(project);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    assertNotNull(psiFile);
    check(psiFile, psiFile.getText());
  }

  private void check(@Language("JAVA") String source, String relativePath) {
    PsiFile file = myFixture.addFileToProject(relativePath, source);
    check(file, source);
  }

  private static void check(PsiFile psiFile, @Language("JAVA") String source) {
    assertTrue(psiFile.getClass().getName(), psiFile instanceof PsiJavaFile);
    PsiJavaFile psiJavaFile = (PsiJavaFile)psiFile;
    CompilationUnit node = LombokPsiConverter.convert(psiJavaFile);
    assertNotNull(node);

    String actualStructure;
    if (CHECK_POSITIONS) {
      StructureFormatter structureFormatter = StructureFormatter.formatterWithPositions();
      node.accept(new SourcePrinter(structureFormatter));
      actualStructure = structureFormatter.finish();
    }

    TextFormatter formatter = new TextFormatter();
    node.accept(new SourcePrinter(formatter));
    String actual = formatter.finish();

    Node expectedNode = parse(source);
    assertNotNull(expectedNode);

    if (CHECK_POSITIONS) {
      StructureFormatter structureFormatter = StructureFormatter.formatterWithPositions();
      expectedNode.accept(new SourcePrinter(structureFormatter));
      String masterStructure = structureFormatter.finish();
      assertEquals(masterStructure, actualStructure);
    }

    formatter = new TextFormatter();
    expectedNode.accept(new SourcePrinter(formatter));
    String master = formatter.finish();
    assertEquals(master, actual);

    // Check for resilience to error nodes being present in the AST
    Project project = psiFile.getProject();
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document document = manager.getDocument(psiFile);
    assertNotNull(document);
    final Random random = new Random(0L); // fixed seed for test reproducibility
    for (int i = 0; i < 500; i++) {
      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          int pos = random.nextInt(document.getTextLength() - 1);
          char ch = (char)(random.nextInt(64) + 32);
          double operation = random.nextDouble();
          if (operation < 0.33) {
            document.insertString(pos, Character.toString(ch));
          } else if (operation < 0.67) {
            document.replaceString(pos, pos + 1, Character.toString(ch));
          } else {
            document.deleteString(pos, pos + 1);
          }
          manager.commitDocument(document);
        }
      });

      node = LombokPsiConverter.convert(psiJavaFile);
      assertNotNull(psiJavaFile.getText(), node);
    }
  }

  @Nullable
  private static Node parse(String code) {
    CompilerOptions options = new CompilerOptions();
    options.complianceLevel = options.sourceLevel = options.targetJDK = ClassFileConstants.JDK1_7;
    options.parseLiteralExpressionsAsConstants = true;
    ProblemReporter problemReporter = new ProblemReporter(
      DefaultErrorHandlingPolicies.exitOnFirstError(), options, new DefaultProblemFactory());
    Parser parser = new Parser(problemReporter, options.parseLiteralExpressionsAsConstants);
    parser.javadocParser.checkDocComment = false;
    EcjTreeConverter converter = new EcjTreeConverter();
    org.eclipse.jdt.internal.compiler.batch.CompilationUnit sourceUnit =
      new org.eclipse.jdt.internal.compiler.batch.CompilationUnit(code.toCharArray(), "unitTest", "UTF-8");
    CompilationResult compilationResult = new CompilationResult(sourceUnit, 0, 0, 0);
    CompilationUnitDeclaration unit = parser.parse(sourceUnit, compilationResult);
    if (unit == null) {
      return null;
    }
    converter.visit(code, unit);
    List<? extends Node> nodes = converter.getAll();
    for (lombok.ast.Node node : nodes) {
      if (node instanceof lombok.ast.CompilationUnit) {
        return node;
      }
    }
    return null;
  }
}
