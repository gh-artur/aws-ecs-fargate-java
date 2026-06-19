package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class Service02Stack extends Stack {
    public Service02Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic, Table productEventsTable) {
        this(scope, id, null, cluster, productEventsTopic, productEventsTable);
    }

    public Service02Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic, Table productEventsTable) {
        super(scope, id, props);

        Queue productEventsDlq = Queue.Builder.create(this, "ProductEventsDlq")
                .queueName("product-events-dlq")
                .enforceSsl(false)
                .encryption(QueueEncryption.UNENCRYPTED)
                .build();

        DeadLetterQueue deadLetterQueue = DeadLetterQueue.builder()
                .queue(productEventsDlq)
                .maxReceiveCount(3)
                .build();

        Queue productEventsQueue = Queue.Builder.create(this, "ProductEvents")
                .queueName("product-events")
                .enforceSsl(false)
                .encryption(QueueEncryption.UNENCRYPTED)
                .deadLetterQueue(deadLetterQueue)
                .build();

        SqsSubscription sqsSubscription = SqsSubscription.Builder.create(productEventsQueue).build();

        productEventsTopic.getTopic().addSubscription(sqsSubscription);

        Map<String, String> envVariables = new HashMap<>();
        envVariables.put("AWS_REGION", "us-east-1");
        envVariables.put("AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME", productEventsQueue.getQueueName());

        // Definição do serviço
        ApplicationLoadBalancedFargateService service02 = ApplicationLoadBalancedFargateService.Builder.create(this, "ALB02")
                .serviceName("service02")
                .cluster(cluster)

                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2)
                .listenerPort(9090)
                .assignPublicIp(true)

                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("aws_project02")
                                .image(ContainerImage.fromRegistry("afsantos22/siecola_aws_project02:1.0.4"))
                                .containerPort(9090)
                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                        .logGroup(LogGroup.Builder.create(this, "Service02LogGroup")
                                                .logGroupName("Service02")
                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                .build())
                                        .streamPrefix("Service02")
                                        .build()))
                                .environment(envVariables)
                                .build())
                .publicLoadBalancer(true)
                .build();

        // Configuração do endpoint de health-check - vai ficar batendo nessa uri pra verificar a saúde da instancia
        service02.getTargetGroup().configureHealthCheck(new HealthCheck.Builder()
                        .path("/actuator/health")
                        .port("9090")
                        .healthyHttpCodes("200")
                .build());

        service02.getTargetGroup().setAttribute("deregistration_delay.timeout_seconds", "30");

        // Config de escalabilidade, quantas instacias da aplicação rodam
        ScalableTaskCount scalableTaskCount = service02.getService().autoScaleTaskCount(EnableScalingProps.builder()
                        .minCapacity(2)
                        .maxCapacity(4)
                .build());

        // Config de escalabilidade, condições para subir ou descer instancias
        scalableTaskCount.scaleOnCpuUtilization("Service02AutoScaling", CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(50)
                        .scaleInCooldown(Duration.seconds(60))
                        .scaleOutCooldown(Duration.seconds(60))
                .build());

        productEventsQueue.grantConsumeMessages(service02.getTaskDefinition().getTaskRole());

        productEventsTable.grantReadWriteData(service02.getTaskDefinition().getTaskRole());



    }
}
