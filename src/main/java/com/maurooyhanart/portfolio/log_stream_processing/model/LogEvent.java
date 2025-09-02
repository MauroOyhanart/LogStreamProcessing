package com.maurooyhanart.portfolio.log_stream_processing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEvent {
    public Instant timestamp;
    public String level;
    public String service;
    public String message;
    public Map<String, Object> context;
}


