package com.Leapwork.Leapwork_plugin.model;
public final class LeapworkExecution {
	private String source;
    private String result;
    
    public LeapworkExecution() {}
    public LeapworkExecution(String source, String result) {
        this.source = source;
        this.result = result;
    }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
}