/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.tests;

import static com.android.tools.tests.IdeaTestSuiteBase.linkIntoOfflineMavenRepo;
import static com.android.tools.tests.IdeaTestSuiteBase.unzipIntoOfflineMavenRepo;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.rules.ExternalResource;

/**
 * A test rule that will set up maven repo dependencies.
 *
 * <p>This rule should only be used as a {@link org.junit.ClassRule} within a test
 * {@link org.junit.runners.Suite}, so maven repositories are only set up once.
 */
public class MavenRepoRule extends ExternalResource {
  private final List<String> mavenRepos;
  private boolean nonce;

  /** Creates a MavenRepoRule using repo paths in the test.suite.repos system property. */
  public static MavenRepoRule fromTestSuiteSystemProperty() {
    String repoString = System.getProperty("test.suite.repos");
    if (repoString == null) {
      throw new RuntimeException("test.suite.repos system property note set!");
    }
    return new MavenRepoRule(ImmutableList.copyOf(repoString.split(",")));
  }

  MavenRepoRule(List<String> mavenRepos) {
    this.mavenRepos = mavenRepos;
  }

  @Override
  protected void before() throws Throwable {
    if (nonce) {
      throw new IllegalStateException("MavenRepoRule being called twice, it should only be called once!" +
                                      " Please use this rule as a @ClassRule within a Suite, to ensure" +
                                      " it is only run once.");
    }
    long start = System.currentTimeMillis();
    for (String repo : mavenRepos) {
      if (repo.endsWith(".manifest")) {
        linkIntoOfflineMavenRepo(repo);
      } else if (repo.endsWith(".zip")) {
        unzipIntoOfflineMavenRepo(repo);
      } else {
        throw new IllegalArgumentException("Unexpected maven repo: " + repo);
      }
    }
    System.out.printf("Maven Repos configured in %dms%n", (System.currentTimeMillis() - start));
    nonce = true;
  }
}
