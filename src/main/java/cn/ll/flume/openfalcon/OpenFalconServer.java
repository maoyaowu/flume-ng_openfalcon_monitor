package cn.ll.flume.openfalcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.flume.Context;
import org.apache.flume.FlumeException;
import org.apache.flume.conf.ConfigurationException;
import org.apache.flume.instrumentation.MonitorService;
import org.apache.flume.instrumentation.util.JMXPollUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.ll.flume.openfalcon.utils.Constants;
import cn.ll.flume.openfalcon.utils.Constants.CounterType;
import cn.ll.flume.openfalcon.utils.HttpClientUtils;
import cn.ll.flume.openfalcon.utils.HttpClientUtils.HttpResult;
import cn.ll.flume.openfalcon.utils.JacksonUtil;
import cn.ll.flume.openfalcon.utils.Utils;

public class OpenFalconServer implements MonitorService {
	

	/**
	 * A Open-Falcon server that polls JMX based at a configured frequency
	 * (defaults to once every 60 seconds). This implementation can send data to
	 * open-falcon agent
	 * <p>
	 * 
	 * <b>Mandatory Parameters:</b>
	 * <p>
	 * <tt>postUrls: </tt> List of comma separated hostname:ports of
	 * open-falcon's /v1/push interface such as
	 * :http://localhost:1988/v1/push,http://192.168.0.6:1988/v1/push servers to
	 * report metrics to. The data first report to the first url,if
	 * successed,the second url will not be reported.
	 * <p>
	 * <b>Optional Parameters: </b>
	 * <p>
	 * <tt>pollFrequency:</tt>Interval in seconds between consecutive reports to
	 * Open-Falcon servers. Default = 60 seconds.
	 * 
	 * <tt>hostname:</tt>Endpoint use to report the data. Default value is the
	 * hostname of the Linux host which flume run at
	 * 
	 * <tt>tags:</tt>tags use to report the data. Default value is
	 * "app/flowfilter" which is the first project i use this monitor component.
	 * 
	 */

	private static final Logger logger = LoggerFactory.getLogger(OpenFalconServer.class);

	private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

	protected final OpenFalconCollector collectorRunnable;

	private int pollFrequency = 60;
	private String tags;
	private String hostname;
	public List<String> postUrls;

	public final String CONF_POLL_FREQUENCY = "pollFrequency";
	public final int DEFAULT_POLL_FREQUENCY = 60;
	public final String CONF_HOSTNAME = "hostname";
	public final String CONF_URLS = "urls";
	public final String CONF_TAGS = "tags";
	public final String DEFAULT_TAGS = "app=flowfilter";

	public OpenFalconServer() throws FlumeException {

		collectorRunnable = new OpenFalconCollector();

	}

	public synchronized void sendToOpenFalconNodes(List<FalconItem> items) {

		try {

			String content = JacksonUtil.writeBeanToString(items, false);
			HttpResult postResult = null;
			boolean isTransfered = false;

			for (String url : this.postUrls) {

				postResult = HttpClientUtils.getInstance().post(url, content);

				if (postResult.getStatusCode() == HttpClientUtils.okStatusCode || postResult.getT() == null) {
					isTransfered = true;
					break;
				} else {
					logger.error("######post status=" + postResult.getStatusCode() + ", post url:" + url + " is down!!! please check it!");
				}
			}
			if (!isTransfered) {
				logger.error("#######All Open-Falcon agent configured were failed!");
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}

	}

	/**
	 * Start this server, causing it to poll JMX at the configured frequency.
	 */
	@Override
	public void start() {

		collectorRunnable.server = this;
		collectorRunnable.hostname = this.hostname;
		collectorRunnable.pollFrequency = this.pollFrequency;
		collectorRunnable.tags = this.tags;

		if (service.isShutdown() || service.isTerminated()) {
			service = Executors.newSingleThreadScheduledExecutor();
		}
		service.scheduleWithFixedDelay(collectorRunnable, 0, pollFrequency, TimeUnit.SECONDS);
	}

	
	/**
	 * Stop this server.
	 */
	@Override
	public void stop() {
		service.shutdown();

		while (!service.isTerminated()) {
			try {
				logger.warn("Waiting for open-falcon monitor service to stop");
				service.awaitTermination(500, TimeUnit.MILLISECONDS);
			} catch (InterruptedException ex) {
				logger.warn("Interrupted while waiting" + " for open-falcon monitor to shutdown", ex);
				service.shutdownNow();
			}
		}
	}

	@Override
	public void configure(Context context) {

		String hostname = Utils.getHostNameForLinux();
		this.pollFrequency = context.getInteger(this.CONF_POLL_FREQUENCY, 60);
		this.hostname = context.getString(this.CONF_HOSTNAME, hostname);
		this.tags = context.getString(this.CONF_TAGS, this.DEFAULT_TAGS);
		// context.getSubProperties();
		logger.debug("#########@@@@@@@@@@@@@@@@#############$$$$$$$$$$$$$$$$$$4" + context.getParameters().toString());

		String urls = context.getString(this.CONF_URLS, "http://127.0.0.1:1988/v1/push");

		if (urls == null || urls.isEmpty()) {
			throw new ConfigurationException("OpenFalcon agent's v1/push interface list cannot be empty.");
		}
		this.postUrls = getPostUrlsFromString(urls);
	}

	private List<String> getPostUrlsFromString(String postUrls) {

		List<String> urls = new ArrayList<String>();
		String[] tmp_urls = postUrls.split(",");
		for (String url : tmp_urls) {
			urls.add(url.trim());

		}

		if (urls.isEmpty()) {
			throw new FlumeException("No valid open-falcon hosts defined!");
		}
		return urls;
	}

	/**
	 * Worker which polls JMX for all mbeans with
	 * {@link javax.management.ObjectName} within the flume namespace:
	 * org.apache.flume. All attributes of such beans are sent to the hosts
	 * specified by the server that owns it's instance. if
	 * 
	 */

	protected class OpenFalconCollector implements Runnable {

		private OpenFalconServer server;
		private String hostname;
		private int pollFrequency;
		private String tags;

		@Override
		public void run() {
			List<FalconItem> items = null;
			FalconItem item = null;
			try {
				items = new ArrayList<FalconItem>();
				Map<String, Map<String, String>> metricsMap = JMXPollUtil.getAllMBeans();
				for (String component : metricsMap.keySet()) {
					Map<String, String> attributeMap = metricsMap.get(component);
					for (String attribute : attributeMap.keySet()) {

						if (!attribute.equalsIgnoreCase("Type")) {
							item = new FalconItem();
							item.setCounterType(CounterType.GAUGE.toString());
							item.setEndpoint(hostname);
							//item.setMetric(StringUtils.lowerCase(component + Constants.metricSeparator + attribute));
							item.setMetric(component + Constants.metricSeparator + attribute);
							item.setStep(pollFrequency);
							item.setTags(tags);
							item.setTimestamp(System.currentTimeMillis() / 1000);
							logger.debug("#######################" + component + "." + attribute + "=" + attributeMap.get(attribute));
							item.setValue(new Double(attributeMap.get(attribute)).doubleValue());
							items.add(item);
						}
					}
				}
				server.sendToOpenFalconNodes(items);
			} catch (Throwable t) {
				logger.error("Unexpected error", t);
			}
		}

	}
}
