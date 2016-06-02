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
package com.android.tools.idea.ui.validation.validators;

import com.android.repository.io.FileOp;
import com.android.repository.testframework.MockFileOp;
import com.android.tools.idea.ui.validation.Validator;
import com.google.common.base.Strings;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class PathValidatorTest {

  private MockFileOp myFileOp;

  private static void assertRuleFails(FileOp fileOp, PathValidator.Rule rule, File file) {
    assertRuleFails(fileOp, rule, file, file); // inputFile is itself the cause of failure
  }

  private static void assertRuleFails(FileOp fileOp, PathValidator.Rule rule, File inputFile, File failureCause) {
    PathValidator validator = new PathValidator.Builder().withRule(rule, Validator.Severity.ERROR).build("test path", fileOp);
    Validator.Result result = validator.validate(inputFile);

    assertThat(result.getSeverity()).isEqualTo(Validator.Severity.ERROR);
    assertThat(result.getMessage()).isEqualTo(rule.getMessage(failureCause, "test path"));
  }

  private static void assertRulePasses(FileOp fileOp, PathValidator.Rule rule, File file) {
    PathValidator validator = new PathValidator.Builder().withRule(rule, Validator.Severity.ERROR).build("test path", fileOp);
    PathValidator.Result result = validator.validate(file);

    assertThat(result.getSeverity()).isEqualTo(Validator.Severity.OK);
  }

  @Before
  public void setUp() throws Exception {
    myFileOp = new MockFileOp();
  }

  @Test
  public void testIsEmptyRuleMatches() throws Exception {
    assertRuleFails(myFileOp, PathValidator.IS_EMPTY, new File(""));
  }

  @Test
  public void testIsEmptyRuleOk() throws Exception {
    assertRulePasses(myFileOp, PathValidator.IS_EMPTY, new File("/not/empty.txt"));
  }

  @Test
  public void testInvalidSlashesRuleMatches() throws Exception {
    // java.io.File normalizes all the forward slashes to backslashes during construction on Windows, so it becomes virtually impossible
    // to pass a File with an "invalid" slash. Note that this behavior isn't symmetric to Linux/MacOSX,
    // where backslashes remain "as is", despite being not supported as path separators
    Assume.assumeFalse(SystemInfo.isWindows);

    File file = new File("at\\least/one\\of/these\\slashes/are\\wrong");
    assertRuleFails(myFileOp, PathValidator.INVALID_SLASHES, file);
  }

  @Test
  public void testInvalidSlashesRuleOk() throws Exception {
    String slashedPath = String.format("%1$ca%1$cb%1$cc", File.separatorChar); // /a/b/c or \a\b\c
    assertRulePasses(myFileOp, PathValidator.INVALID_SLASHES, new File(slashedPath));
  }

  @Test
  public void illegalCharacterMatches() throws Exception {
    File file = new File("\"bad file name\"!!!.txt");
    assertRuleFails(myFileOp, PathValidator.ILLEGAL_CHARACTER, file);
  }

  @Test
  public void illegalCharacterOk() throws Exception {
    assertRulePasses(myFileOp, PathValidator.ILLEGAL_CHARACTER, new File("/no/illegal/chars"));
  }

  @Test
  public void illegalFilenameMatches() throws Exception {
    File file = new File("/aux/is/reserved/");
    // "aux" is responsible for the reserved keyword failure
    myFileOp.setIsWindows(true);
    assertRuleFails(myFileOp, PathValidator.ILLEGAL_FILENAME, file, new File("aux"));
    myFileOp.setIsWindows(false);
    assertRulePasses(myFileOp, PathValidator.ILLEGAL_FILENAME, file);
  }

  @Test
  public void illegalFilenameOk() throws Exception {
    File file = new File("/no/reserved/keywords/");
    assertRulePasses(myFileOp, PathValidator.ILLEGAL_FILENAME, file);
  }

  @Test
  public void whitespaceMatches() throws Exception {
    File file = new File("/no whitespace/is allowed/");
    assertRuleFails(myFileOp, PathValidator.WHITESPACE, file);
  }

  @Test
  public void whitespaceOk() throws Exception {
    File file = new File("/no/whitespace/is/allowed/");
    assertRulePasses(myFileOp, PathValidator.WHITESPACE, file);
  }

  @Test
  public void nonAsciiCharsMatches() throws Exception {
    File file = new File("/users/\uD83D\uDCA9/");
    assertRuleFails(myFileOp, PathValidator.NON_ASCII_CHARS, file);
  }

  @Test
  public void nonAsciiCharsOk() throws Exception {
    File file = new File("/users/janedoe/");
    assertRulePasses(myFileOp, PathValidator.NON_ASCII_CHARS, file);
  }

  @Test
  public void parentDirectoryNotWritableMatches() throws Exception {
    File parent = new File("/a/b/");
    myFileOp.mkdirs(parent);
    myFileOp.setReadOnly(parent);

    File file = new File("/a/b/c/d/e.txt");

    // Because /a/b/ is readonly, it's /a/b/c that finally triggers the failure.
    // This causes the error message to complain about its parent, "a/b/"
    File failureCause = new File("/a/b/c");

    assertRuleFails(myFileOp, PathValidator.PARENT_DIRECTORY_NOT_WRITABLE, file, failureCause);
  }

  @Test
  public void parentDirectoryNotWritableOk() throws Exception {
    File file = new File("/a/b/c/d/e.txt");
    assertRulePasses(myFileOp, PathValidator.PARENT_DIRECTORY_NOT_WRITABLE, file);
  }

  @Test
  public void pathTooLongMatches() throws Exception {
    File file = new File(Strings.repeat("/abcdefghij", 11));
    assertRuleFails(myFileOp, PathValidator.PATH_TOO_LONG, file);
  }

  @Test
  public void pathTooLongOk() throws Exception {
    File file = new File("/qrstuvwxyz");
    assertRulePasses(myFileOp, PathValidator.PATH_TOO_LONG, file);
  }

  @Test
  public void locationIsAFileMatches() throws Exception {
    File file = new File("/a/b/c/d/e.txt");
    myFileOp.createNewFile(file);
    assertRuleFails(myFileOp, PathValidator.LOCATION_IS_A_FILE, file);
  }

  @Test
  public void locationIsAFileOk() throws Exception {
    myFileOp.createNewFile(new File("/a/b/c/d/e.txt"));
    File file = new File("/a/b/c/d/e2.txt");
    assertRulePasses(myFileOp, PathValidator.LOCATION_IS_A_FILE, file);
  }

  @Test
  public void locationIsRootMatches() throws Exception {
    assertRuleFails(myFileOp, PathValidator.LOCATION_IS_ROOT, new File("/"));
  }

  @Test
  public void locationIsRootOk() throws Exception {
    assertRulePasses(myFileOp, PathValidator.LOCATION_IS_ROOT, new File("/not/root"));
  }

  @Test
  public void parentIsNotADirectoryMatches() throws Exception {
    myFileOp.createNewFile(new File("/a/b/c/d/e.txt"));
    File file = new File("/a/b/c/d/e.txt/f.txt");
    assertRuleFails(myFileOp, PathValidator.PARENT_IS_NOT_A_DIRECTORY, file);
  }

  @Test
  public void parentIsNotADirectoryOk() throws Exception {
    myFileOp.recordExistingFolder(new File("/a/b/c/d/e/"));
    File file = new File("/a/b/c/d/e/f.txt");
    assertRulePasses(myFileOp, PathValidator.PARENT_IS_NOT_A_DIRECTORY, file);
  }

  @Test
  public void pathNotWritableMatches() throws Exception {
    File file = new File("/a/b/c/d/e/");
    myFileOp.recordExistingFolder(file);
    myFileOp.setReadOnly(file);

    assertRuleFails(myFileOp, PathValidator.PATH_NOT_WRITABLE, file);
  }

  @Test
  public void pathNotWritableOk() throws Exception {
    File file = new File("/a/b/c/d/e/");
    assertRulePasses(myFileOp, PathValidator.PATH_NOT_WRITABLE, file);
  }

  @Test
  public void nonEmptyDirectoryMatches() throws Exception {
    myFileOp.createNewFile(new File("/a/b/c/d/e.txt"));
    File file = new File("/a/b/c/d/");

    assertRuleFails(myFileOp, PathValidator.NON_EMPTY_DIRECTORY, file);
  }

  @Test
  public void nonEmptyDirectoryOk() throws Exception {
    File file = new File("/a/b/c/d/");
    myFileOp.recordExistingFolder(file);

    assertRulePasses(myFileOp, PathValidator.NON_EMPTY_DIRECTORY, file);
  }

  @Test
  public void errorsShownBeforeWarnings() throws Exception {
    PathValidator validator = new PathValidator.Builder().
      withRule(PathValidator.WHITESPACE, Validator.Severity.WARNING).
      withRule(PathValidator.ILLEGAL_CHARACTER, Validator.Severity.ERROR).build("test path");

    // This path validator has its warning registered before its error, but we should still show the error first
    PathValidator.Result result = validator.validate(new File("whitespace and illegal characters??!"));
    assertThat(result.getSeverity()).isEqualTo(Validator.Severity.ERROR);
  }

  @Test
  public void ruleMustHaveValidSeverity() throws Exception {
    try {
      new PathValidator.Builder().withRule(PathValidator.ILLEGAL_CHARACTER, Validator.Severity.OK);
      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }
}