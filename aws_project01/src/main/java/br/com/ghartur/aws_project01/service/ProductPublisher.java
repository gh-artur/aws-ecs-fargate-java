package br.com.ghartur.aws_project01.service;

import br.com.ghartur.aws_project01.enums.EventType;
import br.com.ghartur.aws_project01.model.Envelope;
import br.com.ghartur.aws_project01.model.Product;
import br.com.ghartur.aws_project01.model.ProductEvent;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.Topic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ProductPublisher {

    private final AmazonSNS snsClient;
    private final Topic productEventsTopic;
    private final ObjectMapper objectMapper;

    private static final Logger LOG = LoggerFactory.getLogger(ProductPublisher.class);

    public ProductPublisher(AmazonSNS snsClient,
                            @Qualifier("productEventsTopic")Topic productEventsTopic,
                            ObjectMapper objectMapper) {
        this.snsClient = snsClient;
        this.productEventsTopic = productEventsTopic;
        this.objectMapper = objectMapper;
    }

    public void publishProductEvent(Product product, EventType eventType, String username){
        ProductEvent productEvent = new ProductEvent();
        productEvent.setProductId(product.getId());
        productEvent.setCode(product.getCode());
        productEvent.setUsername(username);

        Envelope productEnvelope = new Envelope();
        productEnvelope.setEventType(eventType);

        try {
            productEnvelope.setData(objectMapper.writeValueAsString(productEvent));

            PublishResult publishResult = snsClient.publish(productEventsTopic.getTopicArn(), objectMapper.writeValueAsString(productEnvelope));

            LOG.info("Product event sent - event {} - productId {} - messageid {}",
                    productEnvelope.getEventType(),
                    productEvent.getProductId(),
                    publishResult.getMessageId());

        } catch (JsonProcessingException e) {
            LOG.error("Erro ao criar mensagem de evento: "+e.getMessage());
        }

    }
}
