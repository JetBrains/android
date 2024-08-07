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
package com.google.idea.blaze.base.lang.buildfile.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.ArgumentList;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that keyword references are correctly resolved. */
@RunWith(JUnit4.class)
public class KeywordReferenceTest extends BuildFileIntegrationTestCase {

  @Test
  public void testPlainKeywordReference() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"),
            "def function(name, deps)",
            "function(name = \"name\", deps = [])");

    ParameterList params = file.firstChildOfClass(FunctionStatement.class).getParameterList();
    assertThat(params.getElements()).hasLength(2);

    ArgumentList args = file.firstChildOfClass(FuncallExpression.class).getArgList();
    assertThat(args.getKeywordArgument("name").getReferencedElement())
        .isEqualTo(params.findParameterByName("name"));

    assertThat(args.getKeywordArgument("deps").getReferencedElement())
        .isEqualTo(params.findParameterByName("deps"));
  }

  @Test
  public void testKwargsReference() {
    BuildFile file =
        createBuildFile(
            new WorkspacePath("java/com/google/build_defs.bzl"),
            "def function(name, **kwargs)",
            "function(name = \"name\", deps = [])");

    ArgumentList args = file.firstChildOfClass(FuncallExpression.class).getArgList();
    assertThat(args.getKeywordArgument("deps").getReferencedElement())
        .isInstanceOf(Parameter.StarStar.class);
  }
}
