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
package com.google.idea.blaze.cpp.includes;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.cpp.BlazeCppIntegrationTestCase;
import com.google.idea.blaze.cpp.includes.IwyuPragmas.ExportPragma;
import com.google.idea.blaze.cpp.includes.IwyuPragmas.KeepPragma;
import com.google.idea.blaze.cpp.includes.IwyuPragmas.PrivatePragma;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCIncludeDirective.Delimiters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for parsing {@link IwyuPragmas}. */
@RunWith(JUnit4.class)
public class IwyuPragmasTest extends BlazeCppIntegrationTestCase {

  @Test
  public void noPragmas() {
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"bar.h\"",
            "",
            "#include <memory>",
            "#include <vector>",
            "",
            "#include \"f/foo1.h\"",
            "#include \"f/foo2.h\"",
            "#include \"f/foo3.h\"  // blah",
            "",
            "void bar() {}");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isFalse();
    assertThat(pragmas.keeps).isEmpty();
    assertThat(pragmas.exports).isEmpty();
    assertThat(pragmas.associatedHeader.isPresent()).isFalse();
  }

  @Test
  public void incompletePath_parse() {
    OCFile file = createFile("bar.cc", "#include <memory>", "#include <vecto");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps).isEmpty();
  }

  @Test
  public void noPathYet_parse() {
    OCFile file = createFile("bar.cc", "#include <memory>", "#include ");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps).isEmpty();
  }

  @Test
  public void lineComment_parseKeep() {
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"bar.h\"",
            "",
            "#include <memory>",
            "#include <vector> // IWYU pragma: keep",
            "",
            "#include \"f/foo1.h\" // IWYU pragma: keep",
            "#include \"f/foo2.h\"",
            "#include \"f/foo3.h\"     // IWYU pragma: keep     ",
            "",
            "void bar() {}");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(
            KeepPragma.create(IncludePath.create("vector", Delimiters.ANGLE_BRACKETS)),
            KeepPragma.create(IncludePath.create("f/foo1.h", Delimiters.QUOTES)),
            KeepPragma.create(IncludePath.create("f/foo3.h", Delimiters.QUOTES)));
  }

  @Test
  public void blockComment_parseKeep() {
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"bar.h\"",
            "",
            "#include <memory>",
            "#include <vector> /* IWYU pragma: keep */",
            "",
            "#include \"f/foo1.h\" /* IWYU pragma: keep */",
            "#include \"f/foo2.h\"",
            "#include \"f/foo3.h\"     /* IWYU pragma: keep    */",
            "",
            "void bar() {}");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(
            KeepPragma.create(IncludePath.create("vector", Delimiters.ANGLE_BRACKETS)),
            KeepPragma.create(IncludePath.create("f/foo1.h", Delimiters.QUOTES)),
            KeepPragma.create(IncludePath.create("f/foo3.h", Delimiters.QUOTES)));
  }

  @Test
  public void lineCommentIncludeGuardPrefixes_parseKeep() {
    OCFile file =
        createFile(
            "bar.h",
            "#ifndef BAR_H_",
            "#define BAR_H_",
            "#include <memory>",
            "#include <vector> // IWYU pragma: keep",
            "",
            "#include \"f/foo1.h\" // IWYU pragma: keep",
            "#include \"f/foo2.h\"",
            "#include \"f/foo3.h\"     // IWYU pragma: keep     ",
            "",
            "void bar();",
            "#endif");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(
            KeepPragma.create(IncludePath.create("vector", Delimiters.ANGLE_BRACKETS)),
            KeepPragma.create(IncludePath.create("f/foo1.h", Delimiters.QUOTES)),
            KeepPragma.create(IncludePath.create("f/foo3.h", Delimiters.QUOTES)));
  }

  @Test
  public void incompletePragma_parseKeep() {
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"f/foo1.h\" // IWYU pragma: kee",
            "#include \"f/foo2.h\" // IWYU pragma: keep");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(KeepPragma.create(IncludePath.create("f/foo2.h", Delimiters.QUOTES)));
  }

  @Test
  public void hasSuffixSoNoMatch_parseKeep() {
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"f/foo1.h\" // IWYU pragma: keepaway",
            "#include \"f/foo2.h\" // IWYU pragma: keep");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(KeepPragma.create(IncludePath.create("f/foo2.h", Delimiters.QUOTES)));
  }

  @Test
  public void insideNamespace_parseKeep() {
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"f/foo1.h\" // IWYU pragma: keep",
            "#include \"f/foo2.h\"",
            "namespace change_namespace {",
            "#include \"f/foo3.h\" // IWYU pragma: keep",
            "}",
            "class C {",
            "#include \"f/foo4.h\" // IWYU pragma: keep",
            "};");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(
            KeepPragma.create(IncludePath.create("f/foo1.h", Delimiters.QUOTES)),
            KeepPragma.create(IncludePath.create("f/foo3.h", Delimiters.QUOTES)),
            KeepPragma.create(IncludePath.create("f/foo4.h", Delimiters.QUOTES)));
  }

  @Test
  public void duplicate_parseKeep() {
    // Probably need to disambiguate the PSI nodes somewhere (last one shouldn't be kept?)
    OCFile file =
        createFile(
            "bar.cc",
            "#include \"f/foo1.h\" // IWYU pragma: keep",
            "#include \"f/foo1.h\" // IWYU pragma: keep",
            "#include \"f/foo1.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(KeepPragma.create(IncludePath.create("f/foo1.h", Delimiters.QUOTES)));
  }

  @Test
  public void parseExport() {
    OCFile file =
        createFile(
            "public/foo.h",
            "/** Stuff */",
            "#include <private/memory> // IWYU pragma: export",
            "#include <vector>",
            "",
            "#include \"private/foo1.h\" // IWYU pragma: export",
            "#include \"private/foo2.h\"",
            "#include \"private/foo3.h\"     // IWYU pragma: export",
            "",
            "void doFoo(Foo* f);");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.exports)
        .containsExactly(
            ExportPragma.create(IncludePath.create("private/memory", Delimiters.ANGLE_BRACKETS)),
            ExportPragma.create(IncludePath.create("private/foo1.h", Delimiters.QUOTES)),
            ExportPragma.create(IncludePath.create("private/foo3.h", Delimiters.QUOTES)));
  }

  @Test
  public void singeRange_parseExportBeginEnd() {
    OCFile file =
        createFile(
            "public/foo.h",
            "/** Stuff */",
            "// IWYU pragma: begin_exports",
            "#include <private/memory>",
            "#include \"private/foo1.h\"",
            "#include \"private/foo2.h\"",
            "// IWYU pragma: end_exports",
            "#include \"private/foo3.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.exports)
        .containsExactly(
            ExportPragma.create(IncludePath.create("private/memory", Delimiters.ANGLE_BRACKETS)),
            ExportPragma.create(IncludePath.create("private/foo1.h", Delimiters.QUOTES)),
            ExportPragma.create(IncludePath.create("private/foo2.h", Delimiters.QUOTES)));
  }

  @Test
  public void multipleRanges_parseExportBeginEnd() {
    OCFile file =
        createFile(
            "public/foo.h",
            "/** Stuff */",
            "// IWYU pragma: begin_exports",
            "#include <private/memory>",
            "#include \"private/foo1.h\"",
            "#include \"private/foo2.h\"",
            "// IWYU pragma: end_exports",
            "#include \"private/foo3.h\"",
            "#include \"private/foo4.h\"",
            "   // IWYU pragma: begin_exports",
            "#include \"private/foo5.h\"",
            "   // IWYU pragma: end_exports",
            "#include \"private/foo6.h\"",
            "",
            "void doFoo(Foo& f);");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.exports)
        .containsExactly(
            ExportPragma.create(IncludePath.create("private/memory", Delimiters.ANGLE_BRACKETS)),
            ExportPragma.create(IncludePath.create("private/foo1.h", Delimiters.QUOTES)),
            ExportPragma.create(IncludePath.create("private/foo2.h", Delimiters.QUOTES)),
            ExportPragma.create(IncludePath.create("private/foo5.h", Delimiters.QUOTES)));
  }

  @Test
  public void exportRangePlusInlineKeep() {
    // Probably need to disambiguate the PSI nodes somewhere (last one shouldn't be kept?)
    OCFile file =
        createFile(
            "public/foo.h",
            "// IWYU pragma: begin_exports",
            "#include <memory>",
            "#include \"private/foo1.h\" // IWYU pragma: keep",
            "#include \"private/foo2.h\" // IWYU pragma: keep",
            "#include \"private/foo3.h\"",
            "// IWYU pragma: end_exports",
            "#include \"private/foo4.h\"",
            "",
            "void doFoo(Foo& f);");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.keeps)
        .containsExactly(
            KeepPragma.create(IncludePath.create("private/foo1.h", Delimiters.QUOTES)),
            KeepPragma.create(IncludePath.create("private/foo2.h", Delimiters.QUOTES)));
    assertThat(pragmas.exports)
        .containsExactly(
            ExportPragma.create(IncludePath.create("memory", Delimiters.ANGLE_BRACKETS)),
            ExportPragma.create(IncludePath.create("private/foo1.h", Delimiters.QUOTES)),
            ExportPragma.create(IncludePath.create("private/foo2.h", Delimiters.QUOTES)),
            ExportPragma.create(IncludePath.create("private/foo3.h", Delimiters.QUOTES)));
  }

  @Test
  public void parsePrivate() {
    OCFile file =
        createFile(
            "private.h", "// Stuff", "// IWYU pragma: private", "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isFalse();
  }

  @Test
  public void incomplete_parsePrivate() {
    OCFile file =
        createFile(
            "private.h", "// Stuff", "// IWYU pragma: privat", "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isFalse();
  }

  @Test
  public void parsePrivateIncludeOther() {
    OCFile file =
        createFile(
            "private.h",
            "// IWYU pragma: private, include \"f/public.h\"",
            "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isTrue();
    assertThat(privatePragma.includeOther.get())
        .isEqualTo(IncludePath.create("f/public.h", Delimiters.QUOTES));
  }

  @Test
  public void withWhiteSpace_parsePrivateIncludeOther() {
    OCFile file =
        createFile(
            "private.h",
            "//   IWYU pragma:private,include\"f/public.h\"   ",
            "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isTrue();
    assertThat(privatePragma.includeOther.get())
        .isEqualTo(IncludePath.create("f/public.h", Delimiters.QUOTES));
  }

  @Test
  public void parsePrivateIncludeOtherAngle() {
    OCFile file =
        createFile(
            "private.h",
            "// IWYU pragma: private, include <f/public.h>",
            "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isTrue();
    assertThat(privatePragma.includeOther.get())
        .isEqualTo(IncludePath.create("f/public.h", Delimiters.ANGLE_BRACKETS));
  }

  @Test
  public void parsePrivateIncludeOtherNoQuotes() {
    OCFile file =
        createFile(
            "private.h",
            "// IWYU pragma: private, include f/public.h",
            "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isTrue();
    assertThat(privatePragma.includeOther.get())
        .isEqualTo(IncludePath.create("f/public.h", Delimiters.NONE));
  }

  @Test
  public void hasMultiple_parsePrivate() {
    // We shouldn't have multiple private pragmas, but we just take the last one.
    // An alternative might be to take the most descriptive one (when there is a unique
    // most descriptive one).
    OCFile file =
        createFile(
            "private.h",
            "// Stuff",
            "// IWYU pragma: private",
            "// IWYU pragma: private, include \"public.h\"",
            "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isTrue();
    assertThat(privatePragma.includeOther.get())
        .isEqualTo(IncludePath.create("public.h", Delimiters.QUOTES));
  }

  @Test
  public void hasMultipleOtherOrder_parsePrivate() {
    // Like hasMultiple_parsePrivate, but with order swapped
    OCFile file =
        createFile(
            "private.h",
            "// Stuff",
            "// IWYU pragma: private, include \"public.h\"",
            "// IWYU pragma: private",
            "#include \"private_details.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isFalse();
  }

  @Test
  public void sortOfCommentViaIfdef_parsePrivate() {
    OCFile file =
        createFile("bar.cc", "#include <memory>", "#if 0", "IWYU pragma: private", "#endif");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    // We don't really support this, but #ifdef'ed out things may be parsed as PsiComment,
    // so at least make sure we don't assert. Answer could go either way.
    assertThat(pragmas.privatePragma.isPresent()).isTrue();
    PrivatePragma privatePragma = pragmas.privatePragma.get();
    assertThat(privatePragma.includeOther.isPresent()).isFalse();
  }

  @Test
  public void parseAssociated() {
    OCFile file =
        createFile(
            "implementation.cc",
            "#include \"some/interface.h\" // IWYU pragma: associated",
            "",
            "#include \"other_stuff.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.associatedHeader.isPresent()).isTrue();
    assertThat(pragmas.associatedHeader.get())
        .isEqualTo(IncludePath.create("some/interface.h", Delimiters.QUOTES));
  }

  @Test
  public void parseAssociatedAngle() {
    OCFile file =
        createFile(
            "implementation.cc",
            "#include <some/interface.h> // IWYU pragma: associated",
            "",
            "#include \"other_stuff.h\"");
    IwyuPragmas pragmas = IwyuPragmas.parse(file);
    assertThat(pragmas.associatedHeader.isPresent()).isTrue();
    assertThat(pragmas.associatedHeader.get())
        .isEqualTo(IncludePath.create("some/interface.h", Delimiters.ANGLE_BRACKETS));
  }
}
