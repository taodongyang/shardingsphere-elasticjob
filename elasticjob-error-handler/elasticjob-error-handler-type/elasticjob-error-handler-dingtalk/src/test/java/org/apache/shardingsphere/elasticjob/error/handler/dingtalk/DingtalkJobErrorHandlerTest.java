/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.elasticjob.error.handler.dingtalk;

import lombok.SneakyThrows;
import org.apache.shardingsphere.elasticjob.error.handler.JobErrorHandlerFactory;
import org.apache.shardingsphere.elasticjob.error.handler.dingtalk.fixture.DingtalkInternalController;
import org.apache.shardingsphere.elasticjob.infra.exception.JobConfigurationException;
import org.apache.shardingsphere.elasticjob.restful.NettyRestfulService;
import org.apache.shardingsphere.elasticjob.restful.NettyRestfulServiceConfiguration;
import org.apache.shardingsphere.elasticjob.restful.RestfulService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public final class DingtalkJobErrorHandlerTest {
    
    private static final int PORT = 9875;
    
    private static final String HOST = "localhost";
    
    private static RestfulService restfulService;
    
    @Mock
    private Logger log;
    
    @BeforeClass
    public static void init() {
        NettyRestfulServiceConfiguration config = new NettyRestfulServiceConfiguration(PORT);
        config.setHost(HOST);
        config.addControllerInstance(new DingtalkInternalController());
        restfulService = new NettyRestfulService(config);
        restfulService.startup();
    }
    
    @AfterClass
    public static void close() {
        if (null != restfulService) {
            restfulService.shutdown();
        }
    }
    
    @Test
    public void assertHandleExceptionWithNotifySuccessful() {
        DingtalkJobErrorHandler actual = getDingtalkJobErrorHandler();
        setStaticFieldValue(actual);
        Throwable cause = new RuntimeException("test");
        actual.handleException("test_job", createDingtalkConfiguration("http://localhost:9875/send?access_token=mocked_token"), cause);
        verify(log).info("An exception has occurred in Job '{}', Notification to Dingtalk was successful.", "test_job", cause);
    }
    
    @Test
    public void assertHandleExceptionWithWrongToken() {
        DingtalkJobErrorHandler actual = getDingtalkJobErrorHandler();
        setStaticFieldValue(actual);
        Throwable cause = new RuntimeException("test");
        actual.handleException("test_job", createDingtalkConfiguration("http://localhost:9875/send?access_token=wrong_token"), cause);
        verify(log).info("An exception has occurred in Job '{}', But failed to send alert by Dingtalk because of: {}", "test_job", "token is not exist", cause);
    }
    
    @Test
    public void assertHandleExceptionWithUrlIsNotFound() {
        DingtalkJobErrorHandler actual = getDingtalkJobErrorHandler();
        setStaticFieldValue(actual);
        Throwable cause = new RuntimeException("test");
        actual.handleException("test_job", createDingtalkConfiguration("http://localhost:9875/404"), cause);
        verify(log).error("An exception has occurred in Job '{}', But failed to send alert by Dingtalk because of: Unexpected response status: {}", "test_job", 404, cause);
    }
    
    @Test
    public void assertHandleExceptionWithWrongUrl() {
        DingtalkJobErrorHandler actual = getDingtalkJobErrorHandler();
        setStaticFieldValue(actual);
        Throwable cause = new RuntimeException("test");
        actual.handleException("test_job", createNoSignJobDingtalkConfiguration("http://wrongUrl"), cause);
        verify(log).error("An exception has occurred in Job '{}', But failed to send alert by Dingtalk because of", "test_job", cause);
    }
    
    @Test
    public void assertHandleExceptionWithNoSign() {
        DingtalkJobErrorHandler actual = getDingtalkJobErrorHandler();
        setStaticFieldValue(actual);
        Throwable cause = new RuntimeException("test");
        actual.handleException("test_job", createNoSignJobDingtalkConfiguration("http://localhost:9875/send?access_token=mocked_token"), cause);
        verify(log).info("An exception has occurred in Job '{}', Notification to Dingtalk was successful.", "test_job", cause);
    }
    
    private DingtalkJobErrorHandler getDingtalkJobErrorHandler() {
        return (DingtalkJobErrorHandler) JobErrorHandlerFactory.createHandler("DINGTALK").orElseThrow(() -> new JobConfigurationException("DINGTALK error handler not found."));
    }
    
    @SneakyThrows
    private void setStaticFieldValue(final DingtalkJobErrorHandler dingtalkJobErrorHandler) {
        Field field = dingtalkJobErrorHandler.getClass().getDeclaredField("log");
        field.setAccessible(true);
        Field modifiers = field.getClass().getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(dingtalkJobErrorHandler, log);
    }
    
    private DingtalkConfiguration createDingtalkConfiguration(final String webhook) {
        return DingtalkConfiguration.newBuilder(webhook).keyword("mocked_keyword").secret("mocked_secret").connectTimeoutMillisecond(4000).readTimeoutMillisecond(6000).build();
    }
    
    private DingtalkConfiguration createNoSignJobDingtalkConfiguration(final String webhook) {
        return DingtalkConfiguration.newBuilder(webhook).keyword("mocked_keyword").connectTimeoutMillisecond(4000).readTimeoutMillisecond(6000).build();
    }
}
