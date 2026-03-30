package com.maple.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MapleSftpProperties.class)
public class MapleSftpConfiguration {}
