package com.lhstack.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("test")
public class TestController {

    @GetMapping("1")
    public String hello(String msg, Integer port) {
        return "hello world";
    }

    @GetMapping("2")
    public void hello1(String msg, Integer port) {

    }

    @GetMapping("3")
    public Void hello2(String msg, Integer port) {
        return null;
    }

    /**
     * 味精
     *
     * @mock msg 味精
     * @return {@link Map }<{@link String },{@link Object }>
     */
    public static Map<String,Object> msg(long[] msg){
        System.out.println("hello world");
        return new HashMap<>();
    }

}
