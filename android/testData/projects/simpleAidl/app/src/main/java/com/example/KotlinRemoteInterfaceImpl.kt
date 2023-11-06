package com.example

class KotlinRemoteInterfaceImpl : IRemoteInterface.Stub() {
  override fun basicTypes(anInt: Int, aLong: Long, aBoolean: Boolean, aFloat: Float, aDouble: Double, aString: String) {}
}
