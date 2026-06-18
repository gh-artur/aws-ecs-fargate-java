package br.com.ghartur.aws_project02.config.local;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.destination.DynamicDestinationResolver;

import javax.jms.Session;

@Configuration
@EnableJms
@Profile("local")
public class JmsConfigLocal {

    @Value("${aws.region}")
    private String awsRegion;

    private SQSConnectionFactory connectionFactory;

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
        connectionFactory = new SQSConnectionFactory(
                new ProviderConfiguration(),
                AmazonSQSClient.builder()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:4566", "us-east-1"))
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build()
        );

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDestinationResolver(new DynamicDestinationResolver());
        factory.setConcurrency("2");
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);

        return factory;
    }
}
