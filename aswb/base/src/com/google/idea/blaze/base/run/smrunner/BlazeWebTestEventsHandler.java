/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.smrunner;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Encodes everything directly into the URL, will be decoded and re-encoded for the appropriate
 * underlying {@link BlazeTestEventsHandler}.
 */
public class BlazeWebTestEventsHandler implements BlazeTestEventsHandler {
  static final String WEB_TEST_PROTOCOL = "blaze:web:test";

  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind == RuleTypes.WEB_TEST.getKind();
  }

  @Override
  @Nullable
  public SMTestLocator getTestLocator() {
    return BlazeWebTestLocator.INSTANCE;
  }

  /** Uses the {@link BlazeTestEventsHandler} that handles the language of the test location. */
  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    return testLocations.stream()
        .map(Location::getVirtualFile)
        .filter(Objects::nonNull)
        .map(VirtualFile::getExtension)
        .filter(Objects::nonNull)
        .map(LanguageClass::fromExtension)
        .filter(Objects::nonNull)
        .map(Kind::getKindsForLanguage)
        .flatMap(Collection::stream)
        .filter(kind -> kind.getRuleType() == RuleType.TEST)
        .map(BlazeTestEventsHandler::getHandlerForTargetKind)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(handler -> !(handler instanceof BlazeWebTestEventsHandler))
        .map(handler -> handler.getTestFilter(project, testLocations))
        .filter(Objects::nonNull)
        .filter(filter -> !filter.isEmpty())
        .findFirst()
        .orElse(null);
  }

  @Override
  public String testLocationUrl(
      Label label,
      @Nullable Kind kind,
      String parentSuite,
      String name,
      @Nullable String className) {
    return WEB_TEST_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + label
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + parentSuite
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + Strings.nullToEmpty(className);
  }

  @Override
  public String suiteLocationUrl(Label label, @Nullable Kind kind, String name) {
    return WEB_TEST_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + label
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name;
  }

  @Override
  public boolean ignoreSuite(Label label, @Nullable Kind kind, TestSuite suite) {
    return BlazeTestEventsHandler.super.ignoreSuite(label, kind, suite)
        && suite.testCases.stream().allMatch(BlazeXmlToTestEventsConverter::isIgnored);
  }
}
