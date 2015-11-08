package com.example;

import org.junit.Test;
//
import static org.junit.Assert.assertEquals;
import com.google.common.collect.Collections2; // unresolved (in androidTest's library dependency)

public class UnitTest {
    TestUtil util;

    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    Common common;
    ApplicationTest test; // unresolved (in androidTest's source dependency)

}