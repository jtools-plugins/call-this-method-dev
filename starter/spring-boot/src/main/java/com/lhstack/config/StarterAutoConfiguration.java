package com.lhstack.config;

import com.lhstack.invoke.InvokeServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class StarterAutoConfiguration {


    @Bean(initMethod = "start",destroyMethod = "close")
    public InvokeServer invokeServer(@Value("${idea.tools.plugin.run.server.port}") Integer port){
        return new InvokeServer(port);
    }
}
