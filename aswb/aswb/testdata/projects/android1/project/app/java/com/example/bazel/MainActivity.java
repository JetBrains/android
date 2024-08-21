/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.example.bazel;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import com.example.external.lib.ExternalGreeter;
import com.example.lib.Greeter;

/** Main class for the Bazel Android "Hello, World" app. */
public class MainActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.v("Bazel", "Hello, Android");

    setContentView(R.layout.activity_main);

    Button clickMeButton = findViewById(R.id.clickMeButton);
    Button clickMeButtonExternal = findViewById(R.id.clickMeButtonExternal);
    TextView helloBazelTextView = findViewById(R.id.helloBazelTextView);

    Greeter greeter = new Greeter();
    ExternalGreeter externalGreeter = new ExternalGreeter();

    // Bazel supports Java 8 language features like lambdas!
    clickMeButton.setOnClickListener(v -> helloBazelTextView.setText(greeter.sayHello()));
  }
}
