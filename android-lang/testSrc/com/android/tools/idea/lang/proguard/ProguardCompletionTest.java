/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.lang.proguard;

import com.android.tools.idea.lang.LangTestDataKt;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ProguardCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String TEST_FOLDER = FileUtil.join(LangTestDataKt.getTestDataPath(), "lang/proguard");

  @Override
  protected String getTestDataPath() {
    return TEST_FOLDER;
  }

  public void testSingleFlagNameCompletionSingle() throws Throwable {
    performTestCompletion("flagname1.pro", "flagname1_after.pro");
  }

  public void testFlagNameCompletionMultiple() throws Throwable {
    performTestCompletion("flagname2.pro", "flagname2_after.pro");
  }

  public void testFlagNameCompletionVariants() throws Throwable {
    performTestCompletionVariants("flagname2.pro",
                                  "keepattributes",
                                  "keepnames",
                                  "keeppackagenames",
                                  "keepclasseswithmembernames");
  }

  public void testNewlineAtEOFNotRequired() throws Throwable {
    copyFileToProject("validfile_with_eol.pro");
    copyFileToProject("validfile_without_eol.pro");

    // Expect no errors or warnings for these files.
    myFixture.testHighlighting(true, true, true, "validfile_with_eol.pro");
    myFixture.testHighlighting(true, true, true, "validfile_without_eol.pro");
  }

  public void testInvalidProguardFile() throws Throwable {
    copyFileToProject("invalidfile.pro");
    List<HighlightInfo> highlights = myFixture.doHighlighting();
    assertFalse("Expected at least one highlight", highlights.isEmpty());

    HighlightInfo expectedError = highlights.get(0);
    assertEquals("Expected a highlight of type ERROR", HighlightInfoType.ERROR, expectedError.type);
  }

  public void testCommercialAtIncludeSynonym() throws Throwable {
    // commercialat.pro is syntactically correct but semantically dubious (it includes itself).  If proguard
    // support extends to detecting mistakes like that, think harder about this test.
    copyFileToProject("commercialat.pro");
    List<HighlightInfo> highlights = myFixture.doHighlighting();
    assertTrue("Expected no highlights", highlights.isEmpty());
  }

  private void performTestFilenameCompletion(String completionFile, String otherFile) throws Throwable {
    assert !completionFile.startsWith("z");
    myFixture.addFileToProject("zzz.pro", "");
    myFixture.addFileToProject(otherFile, "");
    performTestCompletionVariants(completionFile, completionFile, otherFile);
  }

  public void testIncludeCompletion() throws Throwable {
    performTestFilenameCompletion("includecompletion.pro", "ijklk.pro");
  }

  public void testApplyMappingCompletion() throws Throwable {
    performTestFilenameCompletion("applymappingcompletion.pro", "abc.pro");
  }

  public void testObfuscationDictionaryCompletion() throws Throwable {
    performTestFilenameCompletion("obfuscationdictionarycompletion.pro", "obvs.pro");
  }

  public void testClassObfuscationDictionaryCompletion() throws Throwable {
    performTestFilenameCompletion("classobfuscationdictionarycompletion.pro", "cat.pro");
  }

  public void testPackageObfuscationDictionaryCompletion() throws Throwable {
    performTestFilenameCompletion("packageobfuscationdictionarycompletion.pro", "pi.pro");
  }

  public void testPrintSeedsCompletion() throws Throwable {
    performTestFilenameCompletion("printseedscompletion.pro", "pi.pro");
  }

  public void testPrintUsageCompletion() throws Throwable {
    performTestFilenameCompletion("printusagecompletion.pro", "pi.pro");
  }

  public void testPrintMappingCompletion() throws Throwable {
    performTestFilenameCompletion("printmappingcompletion.pro", "pi.pro");
  }

  public void testPrintConfigurationCompletion() throws Throwable {
    performTestFilenameCompletion("printconfigurationcompletion.pro", "pi.pro");
  }

  public void testDumpCompletion() throws Throwable {
    performTestFilenameCompletion("dumpcompletion.pro", "dog.pro");
  }

  // TODO(xof): rework so that we don't have to manually generate all (flags)x(quotes) test cases
  //  but get full coverage
  public void testFilenameCompletionWithSingleQuotes() throws Throwable {
    performTestFilenameCompletion("dumpwithsinglequotescompletion.pro", "dog.pro");
  }

  public void testFilenameCompletionWithDoubleQuotes() throws Throwable {
    performTestFilenameCompletion("dumpwithdoublequotescompletion.pro", "dog.pro");
  }
  /**
   * Tests basic completion on the input file {@code fileBefore} at caret position, comparing it
   * against the contents of the file {@code fileAfter}.
   *
   * @param fileBefore the source file on which completion is performed
   * @param fileAfter  the expected contents of the file after completion is performed
   * @throws IOException
   */
  protected void performTestCompletion(String fileBefore, String fileAfter) throws IOException {
    copyFileToProject(fileBefore);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(fileAfter);
  }

  /**
   * Tests completion variants on the input file {@code fileBefore} at caret position, verifying
   * that all the entries in {@code expectedVariants} are present in the completion variants.
   */
  protected void performTestCompletionVariants(String fileBefore, String... expectedVariants)
    throws IOException {
    copyFileToProject(fileBefore);

    // TODO: figure out why this doesn't work getCompletionVariants() seems to return an empty list
    // myFixture.testCompletionVariants(fileBefore, expectedVariants);

    LookupElement[] completions = myFixture.complete(CompletionType.BASIC);
    assertNotNull("Expected at least two completions in " + fileBefore, completions);

    // Transform array of LookupElement into a List of Strings.
    List<String> completionVariants =
      Lists.transform(Arrays.asList(completions), new Function<LookupElement, String>() {
        @Override
        public String apply(LookupElement input) {
          return input.getLookupString();
        }
      });

    for (String variant : expectedVariants) {
       assertContainsElements(completionVariants, variant);
    }
  }

  private void copyFileToProject(String path) throws IOException {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(TEST_FOLDER + '/' + path, path));
  }
}
