/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import java.util.Arrays;
import org.intellij.lang.annotations.MagicConstant;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test the WordScanner indexes keywords in the way we expect.<br>
 * This is vital for navigation, refactoring, highlighting etc.
 */
@RunWith(JUnit4.class)
public class GlobalWordIndexTest extends BuildFileIntegrationTestCase {

  @Test
  public void testWordsInComments() {
    VirtualFile file =
        workspace.createFile(new WorkspacePath("java/com/google/BUILD"), "# words in comments");
    assertContainsWords(file, UsageSearchContext.IN_COMMENTS, "words", "in", "comments");
  }

  @Test
  public void testWordsInStrings() {
    VirtualFile file =
        workspace.createFile(
            new WorkspacePath("java/com/google/BUILD"),
            "name = \"long name with spaces\",",
            "src = [\"name_without_spaces\"]");
    assertContainsWords(
        file,
        UsageSearchContext.IN_STRINGS,
        "long",
        "name",
        "with",
        "spaces",
        "name_without_spaces");
  }

  @Test
  public void testWordsInCode() {
    VirtualFile file =
        workspace.createFile(
            new WorkspacePath("java/com/google/BUILD"),
            "java_library(",
            "name = \"long name with spaces\",",
            "src = [\"name_without_spaces\"]",
            ")");
    assertContainsWords(file, UsageSearchContext.IN_CODE, "java_library", "name", "src");
  }

  private void assertContainsWords(
      VirtualFile file,
      @MagicConstant(flagsFromClass = UsageSearchContext.class) short occurenceMask,
      String... words) {

    for (String word : words) {
      VirtualFile[] files =
          CacheManager.SERVICE
              .getInstance(getProject())
              .getVirtualFilesWithWord(
                  word, occurenceMask, GlobalSearchScope.fileScope(getProject(), file), true);
      if (!Arrays.asList(files).contains(file)) {
        Assert.fail(String.format("Word '%s' not found in file '%s'", word, file));
      }
    }
  }
}
