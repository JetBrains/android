/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.wizard.model.demo.npw.android;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

/**
 * A mock representation of {@link com.android.tools.idea.npw.FormFactorUtils.FormFactor} slimmed
 * down for demo purposes.
 */
public final class FormFactor {

  public static final FormFactor MOBILE =
    new FormFactor("Mobile", ActivityTemplate.MOBILE_BLANK, ActivityTemplate.MOBLIE_EMPTY, ActivityTemplate.MOBILE_ADMOB);
  public static final FormFactor WEAR =
    new FormFactor("Wear", ActivityTemplate.WEAR_BLANK, ActivityTemplate.WEAR_ALWAYS_ON, ActivityTemplate.WEAR_FACE);
  public static final FormFactor TV = new FormFactor("TV", ActivityTemplate.TV);

  public static final List<FormFactor> FORM_FACTORS = Lists.newArrayList(MOBILE, WEAR, TV);

  private static final int DEFAULT_API = 15;

  private final String myName;
  private final List<ActivityTemplate> myTemplates;
  private int myApi;

  public FormFactor(String name, ActivityTemplate... templates) {
    myName = name;
    myTemplates = Arrays.asList(templates);
    myApi = DEFAULT_API;
  }

  public String getName() {
    return myName;
  }

  public int getApi() {
    return myApi;
  }

  public void setApi(int api) {
    myApi = api;
  }

  public List<ActivityTemplate> getTemplates() {
    return myTemplates;
  }
}
