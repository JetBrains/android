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
package com.google.idea.blaze.base.run.testlogs;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Collection;

/** Results from a 'blaze test' invocation. */
public class BlazeTestResults {

  public static final BlazeTestResults NO_RESULTS = new BlazeTestResults(ImmutableMultimap.of());

  public static BlazeTestResults fromFlatList(Collection<BlazeTestResult> results) {
    if (results.isEmpty()) {
      return NO_RESULTS;
    }
    ImmutableMultimap.Builder<Label, BlazeTestResult> map = ImmutableMultimap.builder();
    results.forEach(result -> map.put(result.getLabel(), result));
    return new BlazeTestResults(map.build());
  }

  public final ImmutableMultimap<Label, BlazeTestResult> perTargetResults;

  private BlazeTestResults(ImmutableMultimap<Label, BlazeTestResult> perTargetResults) {
    this.perTargetResults = perTargetResults;
  }
}
