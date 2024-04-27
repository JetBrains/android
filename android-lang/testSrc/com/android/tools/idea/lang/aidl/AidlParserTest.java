/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl;

import static com.android.SdkConstants.DOT_TXT;

import com.android.tools.idea.lang.LangTestDataKt;
import com.google.common.base.Charsets;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.ParsingTestCase;
import java.io.File;
import kotlin.io.FilesKt;

/**
 * Tests for Aidl.bnf.
 */
public class AidlParserTest extends ParsingTestCase {
  // If set to true, on test failures the test will rewrite the expected
  // text files in place; this makes it more convenient to update the
  // ~20 golden files after making a structural change to the grammar
  // or generated PSI elements.
  private static final boolean UPDATE_EXPECTED_FILES_IN_PLACE = false;

  public AidlParserTest() {
    super("lang/aidl/parser",
          AidlFileType.DEFAULT_ASSOCIATED_EXTENSION, new AidlParserDefinition());
  }

  protected void doTest(boolean checkResult, boolean ensureNoErrorElements) {
    try {
      super.doTest(checkResult, ensureNoErrorElements);
    } catch (FileComparisonFailure e) {
      if (UPDATE_EXPECTED_FILES_IN_PLACE) {
        File file = new File(myFullDataPath, getTestName() + DOT_TXT);
        assertTrue(file.getPath(), file.isFile());
        String expected = toParseTreeText(myFile, skipSpaces(), includeRanges()).trim();
        FilesKt.writeText(file, expected, Charsets.UTF_8);
      }
      throw e;
    }
  }

  @Override
  protected String getTestDataPath() {
    return LangTestDataKt.getTestDataPath();
  }

  @Override
  protected boolean skipSpaces() {
    return true;
  }

  public void testIAidlInterface() {
    checkNoErrors();
  }

  public void testIWorkManagerImplCallback() {
    checkNoErrors();
  }

  public void testParcelable() {
    checkNoErrors();
  }

  public void testEmptyMethodParameters() {
    checkNoErrors();
  }

  public void testImportRecover() {
    // test recover when import statement is incomplete
    doTest(true);
  }

  /* TODO: We're not recovering in the same way; decide whether this is something
     we can support (and whether it's important)
  public void testDeclarationRecover() {
    // test recover when declaration is incomplete
    doTest(true);
  }

  public void testMethodRecover() {
    // test recover when method definition is incomplete
    doTest(true);
  }
   */

  public void testParameterRecover() {
    // test recover when method parameter is incomplete
    doTest(true);
  }

  // The following tests are AIDL files from the AIDL distribution:
  // https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/tests/android/aidl/tests/

  public void testITestService() {
    // This is a pretty comprehensive test, testing lots of language features and corner cases --
    // annotations, comments, constants, types, nesting. It doesn't have everything, such as enums and unions.
    // CURRENTLY FAILS: has PsiError
    // From AIDL system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
      checkNoErrors();
  }

  public void testStructuredParcelable() {
    // Another comprehensive test of AIDL language features: various initializer forms
    // (including char literals and array literals, arithmetic on initializer expressions
    // and string concatenations.)
    // From AIDL system/tools/aidl/tests/android/aidl/tests/StructuredParcelable.aidl
    checkNoErrors();
  }

  public void testUnion() {
    // Basic test of unions. Also has a corner case which required adjustments
    // to the grammar: "int[] ns = {};".
    // From AIDL system/tools/aidl/tests/android/aidl/tests/Union.aidl
    checkNoErrors();
  }

  public void testPair() {
    // Simple parcelable declaration, but has type parameters in the declaration name which we want to make sure is handled correctly.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/generic/Pair.aidl
    checkNoErrors();
  }

  public void testArrayOfInterfaces() {
    // Tests various language features, including nullability annotations on
    // member declarations, nesting, in/out/inout members and array types.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/ArrayOfInterfaces()
    checkNoErrors();
  }

  public void testByteEnum() {
    // Simple enum test, along with trailing comma, enum initializers and an annotation.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/ByteEnum.aidl
    checkNoErrors();
  }

  public void testConstantExpressionEnum() {
    // Various corner cases of constant expressions
    // From AIDL system/tools/aidl/tests/android/aidl/tests/ConstantExpressionEnum.aidl
    checkNoErrors();
  }

  public void testINestedService() {
    // Nested interfaces and parcelables.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
    checkNoErrors();
  }

  public void testDeeplyNested() {
    // Deep parcelable nesting test, along with qualified reference names.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/nested/DeeplyNested.aidl
    checkNoErrors();
  }

  public void testIProtected() {
    // More complex annotation expressions.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/permission/IProtected.aidl
    // Currently fails!
    checkNoErrors();
  }

  public void testSimpleParcelable() {
    // A test of the cpp_header form of a parcelable.
    // From AIDL system/tools/aidl/tests/android/aidl/tests/SimpleParcelable.aidl
    checkNoErrors();
  }

  public void testVoiceInteractor() {
    // Tests parcelable with dotted declaration name.
    // From platform:
    // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/VoiceInteractor.aidl
    checkNoErrors();
  }

  public void testEnumNoTrailingComma() {
    // Test an enum where we don't have a trailing comma in the enum declaration
    checkNoErrors();
  }

  private void checkNoErrors() {
    doTest(true, true);
  }
}
