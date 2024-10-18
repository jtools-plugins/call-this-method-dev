package com.lhstack.invoke;

import com.alibaba.fastjson2.JSONObject;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class InvokeServer {

    public static class LogEntity {
        private String type;

        private String value;

        public String getType() {
            return type;
        }

        public LogEntity setType(String type) {
            this.type = type;
            return this;
        }

        public String getValue() {
            return value;
        }

        public LogEntity setValue(String value) {
            this.value = value;
            return this;
        }
    }

    public static interface Log {

        void debug(Object msg);

        void info(Object msg);

        void warn(Object msg);

        void error(Object msg);
    }


    private static final Logger LOGGER = LoggerFactory.getLogger(InvokeServer.class);

    private final int port;

    private HttpServer httpServer;

    @Autowired
    private ConversionService conversionService;

    @Autowired
    private ApplicationContext context;

    public InvokeServer(int port) {
        this.port = port;
    }


    public void start() {
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setBlockedThreadCheckInterval(30)
                .setBlockedThreadCheckIntervalUnit(TimeUnit.DAYS)
                .setWarningExceptionTime(30)
                .setWarningExceptionTimeUnit(TimeUnit.DAYS));
        this.httpServer = vertx.createHttpServer(new HttpServerOptions()
                .setIdleTimeoutUnit(TimeUnit.MINUTES)
                .setIdleTimeout(10)
                .setReadIdleTimeout(10)
                .setWriteIdleTimeout(10));
        Router router = Router.router(vertx);
        router.route()
                .handler(BodyHandler.create(false));
        this.methodInvoke(vertx, router);
        httpServer.requestHandler(router);
        httpServer.listen(this.port);
        LOGGER.info("invoke server listener port: {}", port);
    }

    private void methodInvoke(Vertx vertx, Router router) {
        router.post("/mockNormalParameter").handler(ctx -> {
            try {
                String normalParameter = ctx.body().asString();
                JSONObject parameter = JSONObject.parseObject(normalParameter);
                String mock = parameter.getString("mockTag");
                String parameterType = parameter.getString("parameterType");
                if (StringUtils.startsWithIgnoreCase(mock, "groovy:")) {
                    String script = mock.substring("groovy:".length());
                    Binding binding = new Binding();
                    binding.setVariable("context", context);
                    GroovyShell groovyShell = new GroovyShell(binding);
                    Object result = groovyShell.evaluate(script);
                    Object convert = conversionService.convert(result, this.getParameterType(parameterType));
                    ctx.end(String.valueOf(convert));
                } else if (StringUtils.startsWithIgnoreCase(mock, "spel:")) {
                    SpelExpression spelExpression = new SpelExpressionParser().parseRaw(mock.substring("spel:".length()));
                    StandardEvaluationContext context = new StandardEvaluationContext();
                    context.setBeanResolver(new BeanFactoryResolver(this.context));
                    Object result = spelExpression.getValue(context);
                    Object convert = conversionService.convert(result, this.getParameterType(parameterType));
                    ctx.end(String.valueOf(convert));
                } else {
                    ctx.end(mock);
                }
            } catch (Throwable e) {
                ctx.response().putHeader("Content-Type", "text/plain;charset=utf-8");
                ctx.end(Buffer.buffer(extra(e), "UTF-8"));
            }
        });
        router.post("/script").handler(ctx -> {
            List<LogEntity> logEntities = new ArrayList<>();
            JSONObject jsonObject = new JSONObject();
            try {
                String script = ctx.body().asString();
                Binding binding = new Binding();
                binding.setVariable("context", context);
                binding.setVariable("log", new Log() {
                    @Override
                    public void debug(Object msg) {
                        logEntities.add(new LogEntity().setType("debug").setValue(String.valueOf(msg)));
                    }

                    @Override
                    public void info(Object msg) {
                        logEntities.add(new LogEntity().setType("info").setValue(String.valueOf(msg)));
                    }

                    @Override
                    public void warn(Object msg) {
                        logEntities.add(new LogEntity().setType("warn").setValue(String.valueOf(msg)));
                    }

                    @Override
                    public void error(Object msg) {
                        logEntities.add(new LogEntity().setType("error").setValue(String.valueOf(msg)));
                    }
                });
                GroovyShell groovyShell = new GroovyShell(binding);
                Object result = groovyShell.evaluate(script);
                if (result != null) {
                    ctx.response().putHeader("Content-Type", "application/json;charset=utf-8");
                    jsonObject.put("result", JSONObject.toJSONString(result));
                }
                jsonObject.put("logEntities", logEntities);
            } catch (Throwable e) {
                jsonObject.put("logEntities", logEntities);
                jsonObject.put("result", extra(e));
                ctx.response().putHeader("Content-Type", "application/json;charset=utf-8");
            } finally {
                ctx.end(Buffer.buffer(jsonObject.toJSONString(), "UTF-8"));
            }
        });
        router.post("/invoke").handler(ctx -> {
            InvokeGroovyContext groovyContext = new InvokeGroovyContext();
            groovyContext.setContext(context)
                    .setEnv(context.getEnvironment())
                    .setConversionService(conversionService);
            MethodInvokeEntity entity = null;
            try {
                String jsonStr = ctx.body().asString();
                LOGGER.debug("invoke static request json: {}", jsonStr);
                entity = JSONObject.parseObject(jsonStr, MethodInvokeEntity.class);
                groovyContext.setMethodName(entity.getMethod())
                        .setClassname(entity.getClassname());


                List<MethodInvokeEntity.Parameter> psiParameters = entity.getParameters();
                Object[] parameters = new Object[psiParameters.size()];


                Map<String, Class<?>> parameterTypes = groovyContext.getParameterTypes();
                //替换参数
                Map<String, Object> parameterMap = groovyContext.getParameterMap();

                for (int i = 0; i < entity.getParameters().size(); i++) {
                    MethodInvokeEntity.Parameter psiParameter = psiParameters.get(i);
                    Class<?> type = getParameterType(psiParameter.getClassname());
                    parameterTypes.put(psiParameter.getName(), type);
                    try {
                        if (type.getCanonicalName().equals("java.lang.Class")) {
                            parameters[i] = Class.forName(psiParameter.getValue());
                        } else {
                            parameters[i] = JSONObject.parseObject(psiParameter.getValue(), type);
                        }
                    } catch (Throwable e) {
                        parameters[i] = conversionService.convert(psiParameter.getValue(), type);
                    }
                    parameterMap.put(psiParameter.getName(), parameters[i]);
                }

                List<String> preScripts = entity.getPreScripts();
                if (!CollectionUtils.isEmpty(preScripts)) {
                    Binding binding = new Binding();
                    binding.setVariable("ctx", groovyContext);
                    binding.setVariable("context", context);
                    GroovyShell shell = new GroovyShell(binding);
                    for (String preScript : preScripts) {
                        shell.evaluate(preScript);
                    }
                }

                Class<?> clazz = Class.forName(groovyContext.getClassname());
                Class[] types = new Class[psiParameters.size()];
                for (int i = 0; i < types.length; i++) {
                    MethodInvokeEntity.Parameter parameter = psiParameters.get(i);
                    types[i] = groovyContext.getParameterTypes().get(parameter.getName());
                }
                Method method = findMethod(clazz, groovyContext.getMethodName(), types);
                if (method != null) {
                    method.setAccessible(true);
                    Object obj = null;
                    if (entity.getType() != null && !entity.getType().equals("static")) {
                        obj = context.getBean(clazz);
                        if (!entity.getInvokeProxy()) {
                            Object singletonTarget = AopProxyUtils.getSingletonTarget(obj);
                            if (singletonTarget != null) {
                                obj = singletonTarget;
                            }
                        }
                    }
                    Type[] genericParameterTypes = method.getGenericParameterTypes();
                    for (int i = 0; i < psiParameters.size(); i++) {
                        MethodInvokeEntity.Parameter psiParameter = psiParameters.get(i);
                        Object o = parameterMap.get(psiParameter.getName());
                        if (o != null) {
                            Type type = genericParameterTypes[i];
                            if (type instanceof ParameterizedType) {
                                parameters[i] = JSONObject.parseObject(JSONObject.toJSONString(o), genericParameterTypes[i]);
                            } else {
                                parameters[i] = o;
                            }
                        }
                    }
                    Object result = method.invoke(obj, parameters);
                    groovyContext.setResult(result);
                } else {
                    groovyContext.setError(new RuntimeException("No Such Method For Class: " + clazz.getName()));
                }
            } catch (Throwable e) {
                LOGGER.error("invoke method failure", e);
                groovyContext.setError(e);
            } finally {
                if (entity != null) {
                    List<String> postScripts = entity.getPostScripts();
                    if (!CollectionUtils.isEmpty(postScripts)) {
                        Binding binding = new Binding();
                        binding.setVariable("ctx", groovyContext);
                        binding.setVariable("context", groovyContext);
                        GroovyShell shell = new GroovyShell(binding);
                        for (String postScript : postScripts) {
                            shell.evaluate(postScript);
                        }
                    }
                    Object result = groovyContext.getResult();
                    if (groovyContext.getResult() != null) {
                        if (result == null || result.getClass().isAssignableFrom(void.class) || result.getClass().isAssignableFrom(Void.class)) {
                            ctx.end();
                        } else {
                            ctx.response().putHeader("Content-Type", "application/json;charset=utf-8");
                            ctx.end(Buffer.buffer(JSONObject.toJSONString(result), "UTF-8"));
                        }
                    } else {
                        Throwable error = groovyContext.getError();
                        if (error != null) {
                            ctx.response().putHeader("Content-Type", "text/plain;charset=utf-8");
                            ctx.end(Buffer.buffer(extra(error), "UTF-8"));
                        } else {
                            ctx.end();
                        }
                    }
                } else {
                    Throwable error = groovyContext.getError();
                    if (error != null) {
                        ctx.response().putHeader("Content-Type", "text/plain;charset=utf-8");
                        ctx.end(Buffer.buffer(extra(error), "UTF-8"));
                    } else {
                        ctx.end();
                    }
                }
            }
        });
    }

    private Method findMethod(Class<?> clazz, String methodName, Class[] types) {
        try {
            return clazz.getDeclaredMethod(methodName, types);
        } catch (NoSuchMethodException e) {
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(clazz)) {
                if (method.getName().equals(methodName)) {
                    Type[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == types.length) {
                        boolean flag = true;
                        for (int i = 0; i < parameterTypes.length; i++) {
                            if (parameterTypes[i] != types[i] && parameterTypes[i] != Object.class) {
                                flag = false;
                            }
                        }
                        if (flag) {
                            return method;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Class<?> getParameterType(String classname) throws ClassNotFoundException {
        switch (classname) {
            case "long":
                return long.class;
            case "int":
                return int.class;
            case "byte":
                return byte.class;
            case "void":
                return void.class;
            case "char":
                return char.class;
            case "boolean":
                return boolean.class;
            case "short":
                return short.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "java.lang.Class":
                return Class.class;
        }
        return Class.forName(classname);
    }

    public String extra(Throwable e) {
        return e + Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString)
                .collect(Collectors.joining("\r\n"));
    }


    public void close() {
        try {
            httpServer.close();
        } catch (Throwable ignore) {

        }
    }
}
