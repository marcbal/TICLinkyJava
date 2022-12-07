package fr.mbaloup.home.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.rapidoid.log.Log;

public class MQTTSender {


	private static final String SERVER_URI = "tcp://" + System.getenv("TIC_MQTT_SERVER_IP");
	private static final String HASS_DISCOVERY_PREFIX = System.getenv("TIC_MQTT_HASS_DISCOVERY_PREFIX");
	private static final String USER = System.getenv("TIC_MQTT_USERNAME");
	private static final String PASSWORD = System.getenv("TIC_MQTT_PASSWORD");

	public static final Topic<Integer> INDEX_TOTAL = new Topic<>("tic/index/_total_", 1, true, "tic_index_total", "TIC Index Total", "Wh", "energy", "total_increasing");
	public static final Topic<Integer> APPARENT_POWER = new Topic<>("tic/power/app", 0, true, "tic_power_app", "TIC Apparent power", "VA", "energy", "measurement");
	public static final Topic<Integer> CUT_POWER = new Topic<>("tic/power/cut", 0, true, "tic_power_cut", "TIC Cut power", "VA", "energy", null);
	
	
	
	
	
	private static final String clientId = UUID.randomUUID().toString();
	
	private static IMqttClient publisher = null;
	
	private static void connect() {
		try {
			publisher = new MqttClient(SERVER_URI, clientId);
			
			MqttConnectOptions options = new MqttConnectOptions();
			options.setAutomaticReconnect(true);
			options.setCleanSession(true);
			options.setConnectionTimeout(60);
			options.setUserName(USER);
			options.setPassword(PASSWORD.toCharArray());
			publisher.connect(options);
		} catch (MqttException e) {
			Log.error("Cannot connect to MQTT broker.", e);
		}
	}
	
	private static synchronized void publish(String topic, String value, int qos, boolean retained) {
		if (publisher == null)
			connect();
		try {
			publisher.publish(topic, value.getBytes(StandardCharsets.UTF_8), qos, retained);
		} catch (MqttException e) {
			Log.error("Cannot publish MQTT message.", e);
		}
	}
	
	
	
	
	
	
	
	public static class Topic<T> {
		private final String topic;
		private final int qos;
		private final boolean retained;
		
		private final String hassSensorId;
		private final String hassName;
		private final String hassUnit;
		private final String hassDeviceClass;
		private final String hassStateClass;
		
		private boolean hassConfigured = false;
		
		protected Topic(String t, int q, boolean r, String id, String name, String unit, String dClass, String sClass) {
			topic = t;
			qos = q;
			retained = r;
			hassSensorId = id;
			hassName = name;
			hassUnit = unit;
			hassDeviceClass = dClass;
			hassStateClass = sClass;
		}
		
		public synchronized void publish(T value) {
			if (!hassConfigured)
				configure();
			MQTTSender.publish(topic, valueToString(value), qos, retained);
		}
		
		private void configure() {
			
			String hassJson = "{"
					+ "\"name\":\"" + hassName + "\","
					+ "\"unique_id\":\"" + hassSensorId + "\","
					+ "\"state_topic\":\"" + topic + "\"";
			if (hassUnit != null)
				hassJson += ",\"unit_of_measurement\":\"" + hassUnit + "\"";
			if (hassDeviceClass != null)
				hassJson += ",\"device_class\":\"" + hassDeviceClass + "\"";
			if (hassStateClass != null)
				hassJson += ",\"state_class\":\"" + hassStateClass + "\"";
			hassJson += "}";
			
			MQTTSender.publish(HASS_DISCOVERY_PREFIX + "/sensor/" + hassSensorId + "/config", hassJson, 1, true);
			hassConfigured = true;
		}
		
		protected String valueToString(T value) {
			return Objects.toString(value);
		}
	}

}
