package br.com.ghartur.aws_project02.service;

import br.com.ghartur.aws_project02.model.Envelope;
import br.com.ghartur.aws_project02.model.ProductEvent;
import br.com.ghartur.aws_project02.model.SnsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.io.IOException;

@Service
public class ProductEventConsumer {

    private ObjectMapper mapper;

    private static final Logger logger = LoggerFactory.getLogger(ProductEventConsumer.class);

    public ProductEventConsumer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @JmsListener(destination = "${aws.sqs.queue.product.events.name}")
    public void receiveProductEvent(TextMessage textMessage) throws JMSException, IOException {
        SnsMessage snsMessage = mapper.readValue(textMessage.getText(), SnsMessage.class);

        Envelope envelope = mapper.readValue(snsMessage.getMessage(), Envelope.class);

        ProductEvent productEvent = mapper.readValue(envelope.getData(), ProductEvent.class);

        logger.info("Product event received - event {} - productId {} - messageid {}",
                envelope.getEventType(),
                productEvent.getProductId(),
                snsMessage.getMessageId());
    }
}
