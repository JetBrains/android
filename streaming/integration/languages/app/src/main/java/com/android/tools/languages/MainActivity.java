/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.languages;

import android.app.Activity;
import android.app.LocaleManager;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import java.util.Locale;

public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  }

  public void switchToEnglish(View view) {
    switchLanguage("");
  }

  public void switchToDanish(View view) {
    switchLanguage("da");
  }

  public void switchToItalian(View view) {
    switchLanguage("it");
  }

  public void switchToSpanish(View view) {
    switchLanguage("es");
  }

  public void switchToPseudoEnglish(View view) {
    switchLanguage("en-XA");
  }

  public void switchToPseudoArabic(View view) {
    switchLanguage("ar-XB");
  }

  private void switchLanguage(String languageTag) {
    LocaleManager manager = getSystemService(LocaleManager.class);
    manager.setApplicationLocales(new LocaleList(Locale.forLanguageTag(languageTag)));
  }
}
