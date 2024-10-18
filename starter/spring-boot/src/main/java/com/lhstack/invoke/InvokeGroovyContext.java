package com.lhstack.invoke;

import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

public class InvokeGroovyContext {

    /**
     * 属性
     */
    private Map<String,Object> attributes = new HashMap<>();

    /**
     * 参数处理
     */
    private Map<String,Object> parameterMap = new HashMap<>();

    /**
     * 参数类型
     */
    private Map<String,Class<?>> parameterTypes = new HashMap<>();

    private ConversionService conversionService;

    private ApplicationContext context;

    private Environment env;

    private String classname;

    private String methodName;

    /**
     * 后置脚本获取,如果有异常,result就是null
     */
    private Throwable error;

    /**
     * 后置脚本获取,如果没有异常,result就是结果
     */
    private Object result;


    public ConversionService getConversionService() {
        return conversionService;
    }

    public InvokeGroovyContext setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
        return this;
    }

    public ApplicationContext getContext() {
        return context;
    }

    public InvokeGroovyContext setContext(ApplicationContext context) {
        this.context = context;
        return this;
    }

    public Environment getEnv() {
        return env;
    }

    public InvokeGroovyContext setEnv(Environment env) {
        this.env = env;
        return this;
    }

    public Map<String, Class<?>> getParameterTypes() {
        return parameterTypes;
    }

    public InvokeGroovyContext setParameterTypes(Map<String, Class<?>> parameterTypes) {
        this.parameterTypes = parameterTypes;
        return this;
    }

    public String getClassname() {
        return classname;
    }

    public InvokeGroovyContext setClassname(String classname) {
        this.classname = classname;
        return this;
    }

    public String getMethodName() {
        return methodName;
    }

    public InvokeGroovyContext setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }


    public Map<String, Object> getParameterMap() {
        return parameterMap;
    }

    public InvokeGroovyContext setParameterMap(Map<String, Object> parameterMap) {
        this.parameterMap = parameterMap;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public InvokeGroovyContext setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }


    public Throwable getError() {
        return error;
    }

    public InvokeGroovyContext setError(Throwable error) {
        this.error = error;
        return this;
    }

    public Object getResult() {
        return result;
    }

    public InvokeGroovyContext setResult(Object result) {
        this.result = result;
        return this;
    }

    @Override
    public String toString() {
        return "InvokeGroovyContext{" +
                "attributes=" + attributes +
                ", parameterMap=" + parameterMap +
                ", parameterTypes=" + parameterTypes +
                ", classname='" + classname + '\'' +
                ", methodName='" + methodName + '\'' +
                ", error=" + error +
                ", result=" + result +
                '}';
    }
}
