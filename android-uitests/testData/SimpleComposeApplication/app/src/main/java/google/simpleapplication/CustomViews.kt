package google.simpleapplication

import android.content.Context
import android.util.AttributeSet
import android.widget.Button

class CustomButton : Button {
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    text = "Custom Button"
  }
}

class AnotherCustomButton : Button {
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    text = "Another Custom Button"
  }
}
