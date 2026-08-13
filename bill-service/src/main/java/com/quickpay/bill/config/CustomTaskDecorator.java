package com.quickpay.bill.config;

import org.springframework.core.task.TaskDecorator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CustomTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable){
        Map<String,String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            if (contextMap != null){
                MDC.setContextMap(contextMap);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
