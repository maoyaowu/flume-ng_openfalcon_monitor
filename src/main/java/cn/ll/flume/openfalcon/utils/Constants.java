package cn.ll.flume.openfalcon.utils;

public class Constants {

	public static enum CounterType { COUNTER, GAUGE }
	
	public static final String tagSeparator 			= ",";
	public static final String metricSeparator 			= ".";
	public static final int defaultStep 				= 60; // 单位秒
}
