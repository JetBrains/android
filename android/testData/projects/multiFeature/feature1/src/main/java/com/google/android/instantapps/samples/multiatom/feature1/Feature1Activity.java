package com.google.android.instantapps.samples.multiatom.feature1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * A simple activity that allows the user to switch to an equally simple activity.
 */
public class Feature1Activity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_feature1);

    Button goToFeature2Button = (Button) findViewById(R.id.feature2_button);
    goToFeature2Button.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent feature2Intent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("https://multiatom.samples.androidinstantapps.com/feature2"));
        startActivity(feature2Intent);
      }
    });
  }
}
