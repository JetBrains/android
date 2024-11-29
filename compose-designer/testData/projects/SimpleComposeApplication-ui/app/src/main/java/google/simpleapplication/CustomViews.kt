package google.simpleapplication

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

class CustomButton : AppCompatButton {
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    text = "Custom Button"
  }
}

class AnotherCustomButton : AppCompatButton {
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    text = "Another Custom Button"
  }
}
