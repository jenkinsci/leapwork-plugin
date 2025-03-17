package com.Leapwork.Leapwork_plugin.model;
import java.util.List;
public final class ZephyrScaleResult {
	public int version;
    public List<LeapworkExecution> executions;
    
    public ZephyrScaleResult() {}
    public ZephyrScaleResult(int version, List<LeapworkExecution> executions) {
        this.version = version;
        this.executions = executions;
    }
    public int getVersion() {
        return version;
    }
    public void setVersion(int version) {
        this.version = version;
    }
    public List<LeapworkExecution> getExecutions() {
        return executions;
    }
    public void setExecutions(List<LeapworkExecution> executions) {
        this.executions = executions;
    }
}