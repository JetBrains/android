package com.example.kmpjvmonly

class KmpJvmOnlyLibClass {

    fun callCommonLibClass(): String {
        return KmpCommonJvmOnlyLibClass().get()
    }
}
