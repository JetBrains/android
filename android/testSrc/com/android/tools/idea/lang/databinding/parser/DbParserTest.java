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
package com.android.tools.idea.lang.databinding.parser;

import com.android.tools.idea.lang.databinding.DbFileType;
import com.android.tools.idea.lang.databinding.DbParserDefinition;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.android.AndroidTestBase;

public class DbParserTest extends ParsingTestCase {
  public DbParserTest() {
    super("databinding_parsing", DbFileType.EXT, true, new DbParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return AndroidTestBase.getTestDataPath();
  }

  @Override
  protected boolean includeRanges() {
    return true;
  }

  public void testBinaryOr() {
    doTest(true);
  }

  public void testBitShift() {
    doTest(true);
  }

  public void testBooleanLiteral() {
    doTest(true);
  }

  public void testCast() {
    doTest(true);
  }

  public void testCharLiteral() {
    doTest(true);
  }

  public void testClassExtraction() {
    doTest(true);
  }

  public void testComplex1() {
    doTest(true);
  }

  public void testComplex2() {
    doTest(true);
  }

  public void testFloatLiteral() {
    doTest(true);
  }

  public void testFloatLiteral2() {
    doTest(true);
  }

  public void testIdentifier() {
    doTest(true);
  }

  public void testInequality() {
    doTest(true);
  }

  public void testInstanceOf() {
    doTest(true);
  }

  public void testIntLiteral() {
    doTest(true);
  }

  public void testLongLiteral() {
    doTest(true);
  }

  public void testMathExpr() {
    doTest(true);
  }

  public void testMethod() {
    doTest(true);
  }

  public void testNegation() {
    doTest(true);
  }

  public void testNullLiteral() {
    doTest(true);
  }

  public void testResourceReference() {
    doTest(true);
  }

  public void testSignChange() {
    doTest(true);
  }

  public void testStringLiteral() {
    doTest(true);
  }

  public void testTernary() {
    doTest(true);
  }
}