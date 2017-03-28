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
package com.android.tools.swing.util;

import javax.swing.*;
import java.math.BigInteger;

public class BigSpinnerNumberModel extends SpinnerNumberModel {

  public BigSpinnerNumberModel(BigInteger value, Comparable minimum, Comparable maximum, Number stepSize) {
    super(value, minimum, maximum, stepSize);
  }

  private Number incrValue(int dir) {
    BigInteger value = (BigInteger)getNumber();
    //noinspection unchecked
    Comparable<BigInteger> maximum = getMaximum();
    //noinspection unchecked
    Comparable<BigInteger> minimum = getMinimum();

    BigInteger newValue = value.add(BigInteger.valueOf(getStepSize().longValue() * (long)dir));

    if ((maximum != null) && (maximum.compareTo(newValue) < 0)) {
      return null;
    }
    if ((minimum != null) && (minimum.compareTo(newValue) > 0)) {
      return null;
    }
    else {
      return newValue;
    }
  }

  @Override
  public Object getNextValue() {
    return incrValue(+1);
  }

  @Override
  public Object getPreviousValue() {
    return incrValue(-1);
  }

}
