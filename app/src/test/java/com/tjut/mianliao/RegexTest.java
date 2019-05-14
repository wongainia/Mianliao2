package com.tjut.mianliao;

import org.junit.Test;

import java.util.regex.Pattern;

/**
 * Created by dengming on 2017/12/9.
 */

public class RegexTest {
    //private static final Pattern CUSTOM_MATCHER = Pattern.compile("\\[\\S{0,15}?\\]");
    private static final Pattern CUSTOM_MATCHER = Pattern.compile("\\[\\S{0,15}?\\]");

    @Test
    public  void test(){
        //System.out.println(CUSTOM_MATCHER.matcher("111111111111111r33333377777777"));
        System.out.println(CUSTOM_MATCHER.matcher("r").matches());
    }
}
