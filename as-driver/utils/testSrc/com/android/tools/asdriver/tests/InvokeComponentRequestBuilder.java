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
import java.util.List;

/**
 * Provides a nicer API than the underlying proto's builders. The proto's generated classes
 * aren't accessible to test classes anyway.
 */
public class InvokeComponentRequestBuilder {
  ASDriver.InvokeComponentRequest.Builder internalBuilder = ASDriver.InvokeComponentRequest.newBuilder();

  public InvokeComponentRequestBuilder() { }

  public InvokeComponentRequestBuilder addComponentTextMatch(String text) {
    ASDriver.ComponentTextMatch textBuilder = ASDriver.ComponentTextMatch.newBuilder().setText(text).build();
    ASDriver.ComponentMatcher.Builder componentMatcher = ASDriver.ComponentMatcher.newBuilder().setComponentTextMatch(textBuilder);

    internalBuilder.addMatchers(componentMatcher);
    return this;
  }

  public InvokeComponentRequestBuilder addSvgIconMatch(List<String> icons) {
    ASDriver.SvgIconMatch svgBuilder = ASDriver.SvgIconMatch.newBuilder().addAllIcon(icons).build();
    ASDriver.ComponentMatcher.Builder componentMatcher = ASDriver.ComponentMatcher.newBuilder().setSvgIconMatch(svgBuilder);

    internalBuilder.addMatchers(componentMatcher);
    return this;
  }

  public InvokeComponentRequestBuilder addSwingClassRegexMatch(String regex) {
    ASDriver.SwingClassRegexMatch regexBuilder = ASDriver.SwingClassRegexMatch.newBuilder().setRegex(regex).build();
    ASDriver.ComponentMatcher.Builder componentMatcher = ASDriver.ComponentMatcher.newBuilder().setSwingClassRegexMatch(regexBuilder);

    internalBuilder.addMatchers(componentMatcher);
    return this;
  }

  public ASDriver.InvokeComponentRequest build() {
    return internalBuilder.build();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (ASDriver.ComponentMatcher.Builder builder : internalBuilder.getMatchersBuilderList()) {
      if (sb.length() > 0) {
        sb.append(" -> ");
      }
      if (builder.hasComponentTextMatch()) {
        sb.append(String.format("text==\"%s\"", builder.getComponentTextMatch().getText()));
      } else if (builder.hasSvgIconMatch()) {
        sb.append(String.format("icons==\"%s\"", builder.getSvgIconMatch().getIconList()));
      } else if (builder.hasSwingClassRegexMatch()) {
        sb.append(String.format("Swing-class regex==\"%s\"", builder.getSwingClassRegexMatch().getRegex()));
      } else {
        sb.append("UNRECOGNIZED MATCHER: ").append(builder.getDescriptorForType());
      }
    }
    return sb.toString();
  }
}

