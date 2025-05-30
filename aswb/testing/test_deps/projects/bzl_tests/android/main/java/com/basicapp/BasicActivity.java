// Copyright 2022 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.basicapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * The main activity of the Basic Sample App.
 */
public class BasicActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.basic_activity);

    final Button buttons[] = {
      findViewById(R.id.button_id_fizz), findViewById(R.id.button_id_buzz),
    };

    for (var b : buttons) {
      b.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              TextView tv = findViewById(R.id.text_hello);
              if (v.getId() == R.id.button_id_fizz) {
                tv.setText("fizz");
              } else if (v.getId() == R.id.button_id_buzz) {
                tv.setText("buzz");
              }
            }
          });
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu, menu);
    return true;
  }
}
