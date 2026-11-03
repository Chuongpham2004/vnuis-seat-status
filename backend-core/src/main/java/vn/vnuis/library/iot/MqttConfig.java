package vn.vnuis.library.iot;

import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import vn.vnuis.library.config.AppProperties;

/**
 * Spring Integration MQTT inbound adapter subscribed to seat telemetry and status (LWT) topics.
 * Messages are handled in order on the Paho callback thread (DirectChannel), so presence
 * changes of one seat are never reordered.
 */
@Configuration
public class MqttConfig {

    public static final String INBOUND_CHANNEL = "mqttInboundChannel";

    @Bean
    public MqttPahoClientFactory mqttClientFactory(AppProperties.Mqtt props) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {props.url()});
        if (props.username() != null && !props.username().isBlank()) {
            options.setUserName(props.username());
            options.setPassword(props.password() == null ? new char[0] : props.password().toCharArray());
        }
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(10);

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        factory.setPersistence(new MemoryPersistence()); // no Paho state files in the working directory
        return factory;
    }

    @Bean(name = INBOUND_CHANNEL)
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    /** Not auto-started: {@code SeatStateBootstrapper} starts it once Redis is hydrated. */
    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(AppProperties.Mqtt props,
                                                                  MqttPahoClientFactory factory) {
        String clientId = props.clientIdPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId, factory, props.telemetryTopic(), props.statusTopic());
        adapter.setQos(1, 1);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setOutputChannel(mqttInboundChannel());
        adapter.setCompletionTimeout(10_000);
        adapter.setAutoStartup(false);
        return adapter;
    }
}
