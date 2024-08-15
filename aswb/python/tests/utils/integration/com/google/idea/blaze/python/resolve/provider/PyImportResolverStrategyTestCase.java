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
package com.google.idea.blaze.python.resolve.provider;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider;
import com.jetbrains.python.psi.PyElement;
import org.junit.Before;

/** Sets up mocks required for integration tests of {@link PyImportResolverStrategy}'s. */
public abstract class PyImportResolverStrategyTestCase extends BlazeIntegrationTestCase {

  @Before
  public final void doSetup() {
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setOutputBase(fileSystem.getRootDir())
                .build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
  }

  static AutoImportQuickFix getImportQuickFix(PyElement unresolvedSymbol) {
    PsiReference ref = unresolvedSymbol.getReference();
    assertThat(ref).isNotNull();

    String text = ref.getElement().getText();
    AutoImportQuickFix fix = new AutoImportQuickFix(unresolvedSymbol, ref.getClass(), text, false);
    for (PyImportCandidateProvider provider :
        Extensions.getExtensions(PyImportCandidateProvider.EP_NAME)) {
      provider.addImportCandidates(ref, text, fix);
    }
    fix.sortCandidates();
    return fix;
  }
}
