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

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.lang.ASTNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BuildDocumentationProvider}. */
@RunWith(JUnit4.class)
public class BuildDocumentationProviderTest {

  @Test
  public void testGenerateDoc_escapesHtmlEntitiesInFunctionNameAndParams() {
    FunctionStatement function = mock(FunctionStatement.class);
    when(function.getName()).thenReturn("my_func<String>");

    ParameterList paramList = mock(ParameterList.class);
    ASTNode paramListNode = mock(ASTNode.class);
    when(paramListNode.getChars()).thenReturn("(param1=\"<script>alert('test')</script>\")");
    when(paramList.getNode()).thenReturn(paramListNode);
    when(function.getParameterList()).thenReturn(paramList);

    BuildFile buildFile = mock(BuildFile.class);
    when(buildFile.getPresentableText()).thenReturn("BUILD");
    when(function.getContainingFile()).thenReturn(buildFile);

    StringLiteral docString = mock(StringLiteral.class);
    when(docString.getStringContents()).thenReturn("This is a docstring.");
    when(function.getDocString()).thenReturn(docString);

    BuildDocumentationProvider provider = new BuildDocumentationProvider();
    String doc = provider.generateDoc(function, null);

    assertThat(doc).contains("def <b>my_func&lt;String&gt;</b>(param1=&quot;&lt;script&gt;alert(&#39;test&#39;)&lt;/script&gt;&quot;)");
  }

  @Test
  public void testGenerateDoc_handlesNullFunctionName() {
    FunctionStatement function = mock(FunctionStatement.class);
    when(function.getName()).thenReturn(null);

    ParameterList paramList = mock(ParameterList.class);
    ASTNode paramListNode = mock(ASTNode.class);
    when(paramListNode.getChars()).thenReturn("(param1=\"value\")");
    when(paramList.getNode()).thenReturn(paramListNode);
    when(function.getParameterList()).thenReturn(paramList);

    BuildFile buildFile = mock(BuildFile.class);
    when(buildFile.getPresentableText()).thenReturn("BUILD");
    when(function.getContainingFile()).thenReturn(buildFile);

    StringLiteral docString = mock(StringLiteral.class);
    when(docString.getStringContents()).thenReturn("This is a docstring.");
    when(function.getDocString()).thenReturn(docString);

    BuildDocumentationProvider provider = new BuildDocumentationProvider();
    String doc = provider.generateDoc(function, null);

    assertThat(doc).contains("def <b>&lt;unnamed&gt;</b>(param1=&quot;value&quot;)");
  }
}
