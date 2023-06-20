/*
 * Copyright (C) 2016 The Android Open Source Project
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
package google.testartifacts;

import android.app.Application;
import android.test.ApplicationTestCase;
import java.time.LocalDate;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ExampleTest extends ApplicationTestCase<Application> {
    public ExampleTest() {
        super(Application.class);
    }

    public String testLocalDate() {
      LocalDate date = LocalDate.now();
      String month = date.getMonth().name();
      return month;
    }

    public static String getText() {
      java.util.Collection<String> collection
        = java.util.Arrays.asList("first", "second", "third");
      java.util.stream.Stream<String> streamOfCollection = collection.stream();
      return streamOfCollection.findFirst().get();
    }
}
