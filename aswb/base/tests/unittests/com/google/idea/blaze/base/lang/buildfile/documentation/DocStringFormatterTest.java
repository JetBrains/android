/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.documentation;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.base.lang.buildfile.psi.DocStringOwner;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DocStringFormatter}. */
@RunWith(JUnit4.class)
public class DocStringFormatterTest {

  @Test
  public void testFormatDocString_escapesHtmlEntities() {
    StringLiteral docstring = mock(StringLiteral.class);
    when(docstring.getStringContents()).thenReturn("This is a <script>alert('test')</script> docstring.");
    DocStringOwner owner = mock(DocStringOwner.class);

    String formatted = DocStringFormatter.formatDocString(docstring, owner);

    assertThat(formatted).isEqualTo("<pre>This is a &lt;script&gt;alert(&#39;test&#39;)&lt;/script&gt; docstring.<br></pre>");
  }

  @Test
  public void testFormatDocString_handlesIndentation() {
    StringLiteral docstring = mock(StringLiteral.class);
    when(docstring.getStringContents()).thenReturn("  Line 1\n  Line 2\n    Line 3");
    DocStringOwner owner = mock(DocStringOwner.class);

    String formatted = DocStringFormatter.formatDocString(docstring, owner);

    assertThat(formatted).isEqualTo("<pre>Line 1<br>Line 2<br>  Line 3<br></pre>");
  }
}
