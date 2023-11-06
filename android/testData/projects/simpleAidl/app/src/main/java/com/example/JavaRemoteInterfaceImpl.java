package com.example;

class JavaRemoteInterfaceImpl extends IRemoteInterface.Stub {
  @Override
  public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) {}
}
