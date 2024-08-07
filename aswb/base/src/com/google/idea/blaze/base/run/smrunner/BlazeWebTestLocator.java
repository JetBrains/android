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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.io.URLUtil;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Finds the corresponding wrapped test for a web_test and recreates the test URL with the correct
 * {@link BlazeTestEventsHandler}, then locates the test elements using the corresponding {@link
 * SMTestLocator}.
 */
class BlazeWebTestLocator implements SMTestLocator {
  static final BlazeWebTestLocator INSTANCE = new BlazeWebTestLocator();

  @Override
  @SuppressWarnings("rawtypes")
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    if (!Objects.equals(protocol, BlazeWebTestEventsHandler.WEB_TEST_PROTOCOL)) {
      return ImmutableList.of();
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }
    List<String> components = Splitter.on(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER).splitToList(path);
    if (components.isEmpty()) {
      return ImmutableList.of();
    }
    Label wrapperLabel = Label.createIfValid(components.get(0));
    if (wrapperLabel == null) {
      return ImmutableList.of();
    }
    TargetIdeInfo wrapperTarget =
        projectData.getTargetMap().get(TargetKey.forPlainTarget(wrapperLabel));
    if (wrapperTarget == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Location> builder = ImmutableList.builder();
    for (Dependency dependency : wrapperTarget.getDependencies()) {
      TargetKey targetKey = dependency.getTargetKey();
      TargetIdeInfo target = projectData.getTargetMap().get(targetKey);
      if (target == null) {
        continue;
      }
      Kind kind = target.getKind();
      Label label = targetKey.getLabel();
      if (Stream.of("_wrapped_test", "_debug").noneMatch(label.targetName().toString()::endsWith)) {
        continue;
      }
      BlazeTestEventsHandler handler =
          BlazeTestEventsHandler.getHandlerForTargetKind(kind).orElse(null);
      if (handler == null || handler.getTestLocator() == null) {
        continue;
      }
      String url = recreateUrl(handler, label, kind, components);
      if (url == null) {
        continue;
      }
      builder.addAll(locate(handler.getTestLocator(), url, project, scope));
    }
    return builder.build();
  }

  @Nullable
  private static String recreateUrl(
      BlazeTestEventsHandler handler, Label label, Kind kind, List<String> components) {
    switch (components.size()) {
      case 2: // test suite
        return handler.suiteLocationUrl(label, kind, /* name = */ components.get(1));
      case 4: // test case
        return handler.testLocationUrl(
            label,
            kind,
            /* parentSuite = */ components.get(1),
            /* name = */ components.get(2),
            /* className = */ Strings.emptyToNull(components.get(3)));
      default:
        return null;
    }
  }

  @SuppressWarnings("rawtypes")
  private static List<Location> locate(
      SMTestLocator locator, String url, Project project, GlobalSearchScope scope) {
    List<String> components = Splitter.on(URLUtil.SCHEME_SEPARATOR).limit(2).splitToList(url);
    if (components.size() != 2) {
      return ImmutableList.of();
    }
    return locator.getLocation(
        /* protocol = */ components.get(0), /* path = */ components.get(1), project, scope);
  }
}
