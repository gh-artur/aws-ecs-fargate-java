package br.com.ghartur.aws_project02.service;

import br.com.ghartur.aws_project02.model.Envelope;
import br.com.ghartur.aws_project02.model.ProductEvent;
import br.com.ghartur.aws_project02.model.ProductEventLog;
import br.com.ghartur.aws_project02.model.SnsMessage;
import br.com.ghartur.aws_project02.repository.ProductEventLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Service
public class ProductEventConsumer {

    private ObjectMapper mapper;
    private final ProductEventLogRepository productEventLogRepository;

    private static final Logger logger = LoggerFactory.getLogger(ProductEventConsumer.class);

    public ProductEventConsumer(ObjectMapper mapper, ProductEventLogRepository productEventLogRepository) {
        this.mapper = mapper;
        this.productEventLogRepository = productEventLogRepository;
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

        ProductEventLog productEventLog = buildProductEventLog(productEvent, envelope);
        productEventLogRepository.save(productEventLog);
    }

    private ProductEventLog buildProductEventLog(ProductEvent productEvent, Envelope envelope) {
        long timestamp = Instant.now().toEpochMilli();

        ProductEventLog productEventLog = new  ProductEventLog();
        productEventLog.setPk(productEvent.getCode());
        productEventLog.setSk(envelope.getEventType() + "_" + timestamp);
        productEventLog.setEventType(envelope.getEventType());
        productEventLog.setProductId(productEvent.getProductId());
        productEventLog.setTimestamp(timestamp);
        productEventLog.setUsername(productEvent.getUsername());
        productEventLog.setTtl(Instant.now().plus(Duration.ofMinutes(10)).getEpochSecond());

        return productEventLog;
    }

}
