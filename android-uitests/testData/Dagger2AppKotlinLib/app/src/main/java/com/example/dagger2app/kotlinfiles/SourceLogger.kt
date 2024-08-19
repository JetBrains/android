package com.example.dagger2app.kotlinfiles

import javax.inject.Inject

interface Logger{
    fun log(text:String)
}

class LoggerImpl @Inject constructor(): Logger {

    override fun log(text:String){
        println(text)
    }
}