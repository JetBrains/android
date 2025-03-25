<fold text='/.../' expand='false'>/*
 * Copyright (C) 2013 The Android Open Source Project
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
 */</fold>
package p1.p2;

import <fold text='...' expand='false'>android.app.Activity;
import p1.p2.R;
import static p1.p2.R.*;
import static p1.p2.R.string.*;
import static p1.p2.R.integer.*;
import android.os.Bundle;</fold>

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState)
  <fold text='{...}' expand='true'>{
    String label1 = <fold text='"Application Name"' expand='false'>getString(R.string.app_name)</fold>;
    String label2 = <fold text='"Application Name"' expand='false'>getString(string.app_name)</fold>;
    String label3 = <fold text='"Application Name"' expand='false'>getString(app_name)</fold>;

    String label4 = <fold text='"Vibration level is {10}."' expand='false'>getString(R.string.string_width_formatting, 10)</fold>;
    String label5 = <fold text='"Vibration level is {10}."' expand='false'>getString(string.string_width_formatting, 10)</fold>;
    String label6 = <fold text='"Vibration level is {10}."' expand='false'>getString(string_width_formatting, 10)</fold>;

    String label7 = <fold text='""' expand='false'>getString(R.string.empty)</fold>;
    String label8 = <fold text='""' expand='false'>getString(string.empty)</fold>;
    String label9 = <fold text='""' expand='false'>getString(empty)</fold>;

    String label10 = getString(R.string.unknown);
    String label11 = getString(string.unknown);
    String label12 = getString(unknown);

    String label13 = <fold text='shortint: 1' expand='false'>getResources().getInteger(R.integer.shortint)</fold>;
    String label14 = <fold text='shortint: 1' expand='false'>getResources().getInteger(integer.shortint)</fold>;
    String label15 = <fold text='shortint: 1' expand='false'>getResources().getInteger(shortint)</fold>;

    String label16 = <fold text='longerint: 1502' expand='false'>getResources().getInteger(R.integer.longerint)</fold>;
    String label17 = <fold text='longerint: 1502' expand='false'>getResources().getInteger(integer.longerint)</fold>;
    String label18 = <fold text='longerint: 1502' expand='false'>getResources().getInteger(longerint)</fold>;
  }</fold>
}
