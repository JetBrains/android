/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCReferenceElement;
import com.jetbrains.cidr.lang.quickfixes.OCImportSymbolFix;
import com.jetbrains.cidr.lang.quickfixes.OCImportSymbolFix.AutoImportItem;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that {@link BlazeCppAutoImportHelper} is able to get the correct form of #include for the
 * {@link OCImportSymbolFix} quickfix, given typical workspace layouts / location of system headers.
 */
@RunWith(JUnit4.class)
public class BlazeCppAutoImportHelperTest extends BlazeCppResolvingTestCase {

  @Test
  @Ignore("b/156117635")
  public void stlPathsUnderWorkspaceRoot_importStlHeader() {
    // Normally this is <vector> without .h, but we need to trick the file type detector into
    // realizing that this is an OCFile.
    OCFile header =
        createFile(
            "third_party/stl/vector.h",
            "namespace std {",
            "template<typename T> class vector {};",
            "}");
    OCFile file = createFile("foo/bar/bar.cc", "std::vector<int> my_vector;");

    buildSymbols(file, header);

    AutoImportItem importItem = getAutoImportItem(file, "std::vector<int>");
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'std::vector'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("<vector.h>");
  }

  @Test
  public void sameDirectory_importUserHeader() {
    OCFile header = createFile("foo/bar/test.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    buildSymbols(file, header);

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");

    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"foo/bar/test.h\"");
  }

  @Test
  public void differentDirectory_importUserHeader() {
    OCFile header = createFile("baz/test.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    buildSymbols(file, header);

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"baz/test.h\"");
  }

  @Test
  public void importGenfile_relativeToOutputBase() {
    OCFile header =
        createNonWorkspaceFile("output/genfiles/foo/bar/test.proto.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    buildSymbols(file, header);

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"foo/bar/test.proto.h\"");
  }

  @Test
  public void underReadonly_importUserHeader() {
    // This is not much more test coverage than "importGenfile_relativeToOutputBase"
    // but just a reminder that the workspace can also have a READONLY root.
    OCFile header = createNonWorkspaceFile("READONLY/workspace/baz/test.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    buildSymbols(file, header);

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"baz/test.h\"");
  }

  private AutoImportItem getAutoImportItem(OCFile file, String referenceText) {
    testFixture.openFileInEditor(file.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText(referenceText, OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    return Iterables.getOnlyElement(fix.getAutoImportItems());
  }
}
