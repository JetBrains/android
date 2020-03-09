package com.example.test;

import android.app.Activity;
import android.os.Bundle;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class TestActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Predicate<Integer> isEven = e -> e % 2 == 0;
    List<Integer> intList = IntStream.range(200, 400)
      .filter(isEven::test)
      .boxed()
      .collect(Collectors.toList());
  }
}