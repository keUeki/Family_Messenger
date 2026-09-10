package com.shanyangcode.common.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Request/response logging AOP
 **/
@Aspect
@Component
@Slf4j
public class LogInterceptor {

    /**
     * Runs the interception
     */
    @Around("execution(* com.shanyangcode..controller.*.*(..))")
    public Object doInterceptor(ProceedingJoinPoint point) throws Throwable {
        // Start the timer
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        // Resolve the request path
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();
        // Generate a unique request id
        String requestId = UUID.randomUUID().toString();
        String url = httpServletRequest.getRequestURI();
        // Collect the request parameters
        Object[] args = point.getArgs();
        String reqParam = "[" + StringUtils.join(args, ", ") + "]";
        // Log the incoming request
        log.info("request start, id: {}, path: {}, ip: {}, params: {}", requestId, url, httpServletRequest.getRemoteHost(), reqParam);
        // Invoke the original method
        Object result = point.proceed();
        // Log the response
        stopWatch.stop();
        long totalTimeMillis = stopWatch.getTotalTimeMillis();
        log.info("request end, id: {}, cost: {}ms", requestId, totalTimeMillis);
        return result;
    }
}