package br.com.ghartur.aws_project02.config.local;

import br.com.ghartur.aws_project02.repository.ProductEventLogRepository;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableDynamoDBRepositories(basePackageClasses = ProductEventLogRepository.class)
@Profile("local")
public class DynamoDBConfigLocal {

    @Value("${aws.region}")
    private String awsRegion;

    private final AmazonDynamoDB amazonDynamoDB;

    private static final Logger LOG = LoggerFactory.getLogger(DynamoDBConfigLocal.class);

    public DynamoDBConfigLocal(){
        this.amazonDynamoDB = AmazonDynamoDBClient.builder()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:4566", "us-east-1"))
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        attributeDefinitions.add(new AttributeDefinition("pk", ScalarAttributeType.S));
        attributeDefinitions.add(new AttributeDefinition("sk", ScalarAttributeType.S));

        List<KeySchemaElement> keySchema = new ArrayList<>();
        keySchema.add(new KeySchemaElement("pk", KeyType.HASH));
        keySchema.add(new KeySchemaElement("sk", KeyType.RANGE));

        CreateTableRequest createTableRequest = new CreateTableRequest()
                .withTableName("product-events")
                .withAttributeDefinitions(attributeDefinitions)
                .withKeySchema(keySchema)
                .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L));

        try {
            DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
            Table table = dynamoDB.createTable(createTableRequest);
            table.waitForActive();
            LOG.info("DynamoDB table 'product-events' criada no LocalStack");
        } catch (ResourceInUseException | InterruptedException e) {
            LOG.info("DynamoDB table 'product-events' ja existe no LocalStack");
        }
    }

    @Bean
    @Primary
    public DynamoDBMapperConfig dynamoDBMapperConfig() {
        return DynamoDBMapperConfig.DEFAULT;
    }

    @Bean
    @Primary
    public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB,
                                         DynamoDBMapperConfig dynamoDBMapperConfig) {
        return new DynamoDBMapper(amazonDynamoDB, dynamoDBMapperConfig);
    }

    @Bean
    @Primary
    public AmazonDynamoDB amazonDynamoDB() {
        return amazonDynamoDB;
    }
}
