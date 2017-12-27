/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.error;

import com.android.tools.idea.common.model.NlComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NlIssueTest {

  @Mock
  NlComponent component1;

  @Mock
  NlComponent component2;

  private static class TestIssue extends NlIssue {

    private final String summary;
    private final String description;
    private final NlComponent source;
    private final String category;
    private final HighlightSeverity mySeverity;

    public TestIssue(@NotNull String summary,
                     @NotNull String description,
                     @Nullable NlComponent source,
                     @NotNull String category,
                     @NotNull HighlightSeverity severity) {
      this.summary = summary;
      this.description = description;
      this.source = source;
      this.category = category;
      mySeverity = severity;
    }

    @NotNull
    @Override
    public String getSummary() {
      return summary;
    }

    @NotNull
    @Override
    public String getDescription() {
      return description;
    }

    @NotNull
    @Override
    public HighlightSeverity getSeverity() {
      return mySeverity;
    }

    @Nullable
    @Override
    public NlComponent getSource() {
      return source;
    }

    @Override
    public String getCategory() {
      return category;
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testHashCode() {
    int hash1 = new TestIssue("a", "b", component1, "d", HighlightSeverity.ERROR).hashCode();
    int hash2 = new TestIssue("a", "b", component1, "d", HighlightSeverity.ERROR).hashCode();
    int hash3 = new TestIssue("a", "e", component2, "d", HighlightSeverity.ERROR).hashCode();
    int hash4 = new TestIssue("a", "e", null, "d", HighlightSeverity.ERROR).hashCode();
    int hash5 = new TestIssue("a", "e", null, "d", HighlightSeverity.ERROR).hashCode();
    Assert.assertEquals(hash1, hash2);
    Assert.assertEquals(hash4, hash5);
    Assert.assertNotEquals(hash1, hash3);
    Assert.assertNotEquals(hash1, hash3);
    Assert.assertNotEquals(hash1, hash4);
  }

  @Test
  public void testEqual() {
    TestIssue er1 = new TestIssue("a", "b", component1, "d", HighlightSeverity.ERROR);
    TestIssue er2 = new TestIssue("a", "b", component1, "d", HighlightSeverity.ERROR);
    Assert.assertEquals(er1, er2);
    Assert.assertEquals(er1.hashCode(), er2.hashCode());

    TestIssue er3 = new TestIssue("a", "b", component1, "d", HighlightSeverity.ERROR);
    TestIssue er4 = new TestIssue("a", "e", component2, "d", HighlightSeverity.ERROR);
    TestIssue er5 = new TestIssue("a", "e", null, "d", HighlightSeverity.ERROR);
    TestIssue er6 = new TestIssue("a", "e", null, "d", HighlightSeverity.ERROR);
    Assert.assertNotEquals(er3, er4);
    Assert.assertNotEquals(er4, er5);
    Assert.assertEquals(er5, er6);
    Assert.assertNotEquals(er3.hashCode(), er4.hashCode());
  }
}