package com.lhstack.invoke;

import java.util.Collections;
import java.util.List;

public class MethodInvokeEntity {

    public static class Parameter {
        /**
         * 参数名称
         */
        private String name;

        /**
         * 参数类型
         */
        private String classname;

        /**
         * 参数值
         */
        private String value;

        public String getName() {
            return name;
        }

        public Parameter setName(String name) {
            this.name = name;
            return this;
        }

        public String getClassname() {
            return classname;
        }

        public Parameter setClassname(String classname) {
            this.classname = classname;
            return this;
        }

        public String getValue() {
            return value;
        }

        public Parameter setValue(String value) {
            this.value = value;
            return this;
        }
    }

    private String type;

    /**
     * 是否调用原始类
     */
    private Boolean invokeProxy = true;

    private List<String> preScripts = Collections.emptyList();

    private List<String> postScripts = Collections.emptyList();

    private List<Parameter> parameters = Collections.emptyList();

    private String classname;

    private String method;

    public Boolean getInvokeProxy() {
        return invokeProxy;
    }

    public MethodInvokeEntity setInvokeProxy(Boolean invokeProxy) {
        this.invokeProxy = invokeProxy;
        return this;
    }

    public String getType() {
        return type;
    }

    public List<String> getPostScripts() {
        return postScripts;
    }

    public MethodInvokeEntity setPostScripts(List<String> postScripts) {
        this.postScripts = postScripts;
        return this;
    }

    public MethodInvokeEntity setType(String type) {
        this.type = type;
        return this;
    }

    public List<String> getPreScripts() {
        return preScripts;
    }

    public MethodInvokeEntity setPreScripts(List<String> preScripts) {
        this.preScripts = preScripts;
        return this;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public MethodInvokeEntity setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
        return this;
    }

    public String getClassname() {
        return classname;
    }

    public MethodInvokeEntity setClassname(String classname) {
        this.classname = classname;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public MethodInvokeEntity setMethod(String method) {
        this.method = method;
        return this;
    }
}
