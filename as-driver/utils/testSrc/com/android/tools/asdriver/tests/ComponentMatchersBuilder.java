/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.tools.asdriver.proto.ASDriver;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides a nicer API than the underlying proto's builders. The proto's generated classes aren't
 * accessible to test classes anyway.
 */
public class ComponentMatchersBuilder {
  List<ASDriver.ComponentMatcher.Builder> builders = new ArrayList<>();

  public ComponentMatchersBuilder() { }

  public ComponentMatchersBuilder addComponentTextMatch(String text) {
    ASDriver.ComponentTextMatch textBuilder = ASDriver.ComponentTextMatch.newBuilder().setText(text).build();
    ASDriver.ComponentMatcher.Builder componentMatcher = ASDriver.ComponentMatcher.newBuilder().setComponentTextMatch(textBuilder);

    builders.add(componentMatcher);
    return this;
  }

  public ComponentMatchersBuilder addSvgIconMatch(List<String> icons) {
    ASDriver.SvgIconMatch svgBuilder = ASDriver.SvgIconMatch.newBuilder().addAllIcon(icons).build();
    ASDriver.ComponentMatcher.Builder componentMatcher = ASDriver.ComponentMatcher.newBuilder().setSvgIconMatch(svgBuilder);

    builders.add(componentMatcher);
    return this;
  }

  public ComponentMatchersBuilder addSwingClassRegexMatch(String regex) {
    ASDriver.SwingClassRegexMatch regexBuilder = ASDriver.SwingClassRegexMatch.newBuilder().setRegex(regex).build();
    ASDriver.ComponentMatcher.Builder componentMatcher = ASDriver.ComponentMatcher.newBuilder().setSwingClassRegexMatch(regexBuilder);

    builders.add(componentMatcher);
    return this;
  }

  public List<ASDriver.ComponentMatcher> build() {
    return builders.stream().map(ASDriver.ComponentMatcher.Builder::build).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return builders.toString();
  }
}
