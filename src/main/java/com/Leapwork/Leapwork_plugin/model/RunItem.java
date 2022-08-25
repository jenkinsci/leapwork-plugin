package com.Leapwork.Leapwork_plugin.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "testcase")
public final class RunItem {

	private String caseName;

	private String caseStatus;

	private double elapsedTime;

	private String classname;
	@XmlElement
	public Failure failure;

	public RunItem() {
	}
	@SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
	public RunItem(String caseTitle, String caseStatus, double elapsed, String schedule) {
		caseName = caseTitle;
		this.caseStatus = caseStatus;
		elapsedTime = elapsed;
		classname = schedule;
		failure = null;

	}
	@SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
	public RunItem(String caseTitle, String caseStatus, double elapsed, String stacktrace, String schedule) {
		caseName = caseTitle;
		this.caseStatus = caseStatus;
		elapsedTime = elapsed;
		failure = new Failure(stacktrace);
		classname = schedule;
	}

	@XmlAttribute(name = "name")
	public String getCaseName() {
		return caseName;
	}

	public void setCaseName(String name) {
		caseName = name;
	}

	@XmlAttribute(name = "status")
	public String getCaseStatus() {
		return caseStatus;
	}

	public void setCaseStatus(String status) {
		caseStatus = status;
	}

	@XmlAttribute(name = "time")
	public double getElapsedTime() {
		return elapsedTime;
	}

	public void setElapsedTime(double time) {
		elapsedTime = time;
	}

	@XmlAttribute(name = "classname")
	public String getClassname() {
		return classname;
	}

	public void setClassname(String classname) {
		this.classname = classname;
	}
}
