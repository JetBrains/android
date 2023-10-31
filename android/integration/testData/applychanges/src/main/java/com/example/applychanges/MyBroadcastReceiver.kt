package com.example.applychanges

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MyBroadcastReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context?, intent: Intent?) {
    // EASILY SEARCHABLE ONRECEIVE LINE (this comment exists for the test to be able to find/replace from here until the next comment)
    printBefore()
    // END ONRECEIVE SEARCH
  }

  fun printBefore() {
    Log.i("MyBroadcastReceiver", "onReceive Before")
  }

  fun printAfter() {
    Log.i("MyBroadcastReceiver", "onReceive After")
  }

}