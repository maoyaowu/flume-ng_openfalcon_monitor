# flume openfalcon monitor 简介
flume openfalcon monitor是一个基于flume-ng 监控的自定义监控插件，通过这个插件，结合open-falcon agent，可以采集flume-ng进程的服务状态，并将采集信息自动上报给open-falcon服务端

## 主要功能

通过flume-ng内置的jmx监控，采集flume-ng进程的统计信息。

对应用程序代码无侵入，几乎不占用系统资源。

## 环境需求

Linux，Windows

JDK>=1.7

Open-Falcon>=0.0.5

Flume-ng>=1.7(该监控组件仅在flume-ng1.7 测试过)

目标flume-ng的java启动环境变量配置文件flume-env.sh(linux enviroment),flume-env.ps1(windows platform)中配置$JAVA_OPTS变量

## flume openfalcon monitor部署

1. 在flume-ng根目录下建立plugins.d/flume-openfalcon/lib,plugins.d/flume-openfalcon/libext目录，将flume-openfalcon-monitor-0.0.1.jar拷贝到plugins.d/flume-openfalcon/lib下，将依赖的jar文件拷贝到plugins.d/flume-openfalcon/libext下
2. 编辑flume-ng的环境变量配置文件$flume-ng_home/conf/flume-env.sh,在$JAVA_OPTS变量中加入flume openfalcon monitor环境变量配置。
3. 正常启动flume-ng。	

## 配置说明
仅需要配置在flume-env.sh的$JAVA_OPTS变量即可，内容说明如下：

    # 监控组件名称：指定监控组件的FQDN名，不要修改。
    #-Dflume.monitoring.type=cn.ll.flume.openfalcon.OpenFalconServer
     
    # 上报数据到open-falcon的agent接口地址url：如果使用open-falcon的默认配置，则这里不需要改变，除了本地agent上报数据接口的url地址做为主用外，还可以另加一个备用的地址。
    #-Dflume.monitoring.urls=http://localhost:1988/v1/push,http://10.254.253.210:1988/v1/push
    
    # 可选项：主机名称：上报给open-falcon的endpoint，默认值为本机hostname。根据需求修改
    #-Dflume.monitoring.hostname=myflume
    
    # 可选项：tag，用于区分不同的flume-ng组件。根据需求修改
    #-Dflume.monitoring.tags=app=flowfilter
    
    # 可选项：上报时间间隔， 上报给open-falcon的上报间隔，默认值60，单位秒。不建议修改   
    # -Dflume.monitoring.pollFrequency=60
    
    



## 采集指标

|  Counters |  Type |  Notes| 
| -----| ------| ------| 
| SOURCE.r1.AppendAcceptedCount |  GAUGE  |  Event收到数量  | 
| SOURCE.r1.AppendReceivedCount |  GAUGE  |  Event接收数量  | 
| SOURCE.r1.AppendBatchAcceptedCount |  GAUGE  |  Event批量收到数量  | 
| SOURCE.r1.AppendBatchReceivedCount  |  GAUGE  |  Event批量接收数量  | 
| -----| ------| ------|
| CHANNEL.c1.ChannelSize  |  GAUGE  | 通道大小  | 
| CHANNEL.c1.ChannelCapacity |  GAUGE  | 通道容量  | 
| CHANNEL.c1.ChannelFillPercentage |  GAUGE  | 通道占用百分比  | 
| CHANNEL.c1.EventPutSuccessCount |  GAUGE  |  Event放入成功数量  | 
| CHANNEL.c1.EventPutAttemptCount |  GAUGE  |  Event尝试放入数量  | 
| CHANNEL.c1.EventTakeSuccessCount  |  GAUGE  |  Event尝试取出成功数量  | 
| CHANNEL.c1.EventTakeAttemptCount   |  GAUGE  |  Event尝试取出数量  | 
| CHANNEL.c1.StartTime  |  GAUGE  |  通道启动时间  | 
| CHANNEL.c1.StopTime   |  GAUGE  |  通道停止时间  | 
| -----| ------| ------|
| SINK.k1.BatchCompleteCount |  GAUGE  |  Event批处理完成数量  | 
| SINK.k1.BatchEmptyCount |  GAUGE  |  Event批处理为空数量  | 
| SINK.k1.BatchUnderflowCount |  GAUGE  |  Event批处理下溢数量  | 
| SINK.k1.ConnectionClosedCount |  GAUGE  |  Sink连接关闭数量  | 
| SINK.k1.ConnectionCreatedCount  |  GAUGE  |  Sink连接创建数量  | 
| SINK.k1.ConnectionFailedCount  |  GAUGE  |  Sink连接失败数量  | 
| SINK.k1.EventdrainAttemptCount  |  GAUGE  |  Event写入尝试数量  | 
| SINK.k1.EventDrainSuccessCount |  GAUGE  |  Event写入成功数量  | 
| SINK.k1.StartTime  |  GAUGE  |  Sink启动时间  | 
| SINK.k1.StopTime  |  GAUGE  |  Sink停止时间  | 

## 建议设置监控告警项

不同应用根据其特点，可以灵活调整触发条件及触发阈值

|  告警项 |  触发条件 |  备注|
| -----| ------| ------|
|  CHANNEL.c1.ChannelFillPercentage  |  all(#3)>80  |  通道占用百分比低于80%，可能会导致通道溢出，丢数据或者影响性能，需要增加通道容量或提升sink效率  | 
|  SINK.k1.ConnectionFailedCount  |  all(#1)>0  |  sink连接失败，需要找原因  |
|  SINK.k1.BatchUnderflowCount |  all(#1)>0  |  批量写入溢出，影响性能  |
