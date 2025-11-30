package com.example.iot.model;

public class ConfigBody {
    public String device_id;
    public String action;
    public String target;
    public boolean enabled;
    public Object value;


    public ConfigBody(String device_id, String action, String target, boolean enabled) {
        this.device_id = device_id;
        this.action = action;
        this.target = target;
        this.enabled = enabled;
        this.value = enabled;
    }
}