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
package com.android.tools.idea.gradle.dsl.parser.android.testOptions;

import static com.android.tools.idea.gradle.dsl.model.android.testOptions.EmulatorSnapshotsModelImpl.COMPRESS_SNAPSHOTS;
import static com.android.tools.idea.gradle.dsl.model.android.testOptions.EmulatorSnapshotsModelImpl.ENABLE_FOR_TEST_FAILURES;
import static com.android.tools.idea.gradle.dsl.model.android.testOptions.EmulatorSnapshotsModelImpl.MAX_SNAPSHOTS_FOR_TEST_FAILURES;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class EmulatorSnapshotsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<EmulatorSnapshotsDslElement> EMULATOR_SNAPSHOTS =
    new PropertiesElementDescription<>("emulatorSnapshots",
                                       EmulatorSnapshotsDslElement.class,
                                       EmulatorSnapshotsDslElement::new,
                                       EmulatorSnapshotsElementSchema::new);

  public static final ExternalToModelMap ktsToModelMap = Stream.of(new Object[][]{
    {"compressSnapshots", property, COMPRESS_SNAPSHOTS, VAR},
    {"enableForTestFailures", property, ENABLE_FOR_TEST_FAILURES, VAR},
    {"maxSnapshotsForTestFailures", property, MAX_SNAPSHOTS_FOR_TEST_FAILURES, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelMap = Stream.of(new Object[][]{
    {"compressSnapshots", property, COMPRESS_SNAPSHOTS, VAR},
    {"compressSnapshots", exactly(1), COMPRESS_SNAPSHOTS, SET},
    {"enableForTestFailures", property, ENABLE_FOR_TEST_FAILURES, VAR},
    {"enableForTestFailures", exactly(1), ENABLE_FOR_TEST_FAILURES, SET},
    {"maxSnapshotsForTestFailures", property, MAX_SNAPSHOTS_FOR_TEST_FAILURES, VAR},
    {"maxSnapshotsForTestFailures", exactly(1), MAX_SNAPSHOTS_FOR_TEST_FAILURES, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelMap = Stream.of(new Object[][]{
    {"compressSnapshots", property, COMPRESS_SNAPSHOTS, VAR},
    {"enableForTestFailures", property, ENABLE_FOR_TEST_FAILURES, VAR},
    {"maxSnapshotsForTestFailures", property, MAX_SNAPSHOTS_FOR_TEST_FAILURES, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
  }

  public EmulatorSnapshotsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class EmulatorSnapshotsElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
    }

    @NotNull
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.EmulatorSnapshots";
    }
  }
}
