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
package com.google.idea.blaze.android.projectview;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ProjectViewDefaultValueProvider;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Allows manual override of the android sdk. */
public class AndroidSdkPlatformSection {
  public static final SectionKey<String, ScalarSection<String>> KEY =
      SectionKey.of("android_sdk_platform");
  public static final SectionParser PARSER = new AndroidSdkPlatformParser();

  private static class AndroidSdkPlatformParser extends ScalarSectionParser<String> {
    public AndroidSdkPlatformParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected String parseItem(ProjectViewParser parser, ParseContext parseContext, String rest) {
      return StringUtil.unquoteString(rest);
    }

    @Override
    protected void printItem(StringBuilder sb, String value) {
      sb.append(value);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }
  }

  static class AndroidSdkPlatformProjectViewDefaultValueProvider
      implements ProjectViewDefaultValueProvider {
    @Override
    public ProjectView addProjectViewDefaultValue(
        BuildSystemName buildSystemName,
        ProjectViewSet projectViewSet,
        ProjectView topLevelProjectView) {
      if (!topLevelProjectView.getSectionsOfType(KEY).isEmpty()) {
        return topLevelProjectView;
      }
      List<Sdk> sdks = BlazeSdkProvider.getInstance().getAllAndroidSdks();
      ProjectView.Builder builder =
          ProjectView.builder(topLevelProjectView).add(TextBlockSection.of(TextBlock.newLine()));

      if (sdks.isEmpty()) {
        builder
            .add(TextBlockSection.of(TextBlock.of("# Please set to an android SDK platform")))
            .add(
                TextBlockSection.of(
                    TextBlock.of(
                        "# You currently have no SDKs. Please use the SDK manager first.")))
            .add(ScalarSection.builder(KEY).set("(android sdk goes here)"));
      } else if (sdks.size() == 1) {
        builder.add(
            ScalarSection.builder(KEY)
                .set(BlazeSdkProvider.getInstance().getSdkTargetHash(sdks.get(0))));
      } else {
        builder.add(
            TextBlockSection.of(
                TextBlock.of("# Please uncomment an android-SDK platform. Available SDKs are:")));
        List<String> sdkOptions =
            AndroidSdkFromProjectView.getAvailableSdkTargetHashes(sdks)
                .stream()
                .map(androidSdk -> "# android_sdk_platform: " + androidSdk)
                .collect(toList());
        builder.add(TextBlockSection.of(new TextBlock(ImmutableList.copyOf(sdkOptions))));
      }
      return builder.build();
    }

    @Override
    public SectionKey<?, ?> getSectionKey() {
      return KEY;
    }
  }
}
