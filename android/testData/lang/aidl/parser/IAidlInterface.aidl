// IMyAidlInterface.aidl
package com.google;

import com.google.A;

interface IAidlInterface {
  // Todo
  A   basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString, in com.google.A a, in A a2) = 0;

  /*
  * TODO
  */
  A   basicTypes2(in int[] arrays) = 1;
}