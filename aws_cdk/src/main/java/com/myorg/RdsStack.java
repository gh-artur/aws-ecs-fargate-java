package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.Collections;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class RdsStack extends Stack {
    public RdsStack(final Construct scope, final String id, Vpc vpc) {
        this(scope, id, null, vpc);
    }

    public RdsStack(final Construct scope, final String id, final StackProps props, Vpc vpc) {
        super(scope, id, props);

        // parametro será passado no momendo de deploy pelo cdk
        CfnParameter databasePassword = CfnParameter.Builder.create(this, "databasePassword")
                .type("String")
                .description("RDS instance password")
                .noEcho(true)
                .minLength(8)
                .build();

        // abre porta 3306 para requisições
        ISecurityGroup iSecurityGroup = SecurityGroup.fromSecurityGroupId(this, id, vpc.getVpcDefaultSecurityGroup());
        iSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(3306));

        // definição da instancia do banco de dados
        DatabaseInstance databaseInstance = DatabaseInstance.Builder
                .create(this, "Rds01")
                .instanceIdentifier("aws-project01-db")
                // MySQL 8.0 em vez de 5.7: o 5.7 saiu do suporte padrao e a AWS
                // cobra Extended Support por vCPU-hora automaticamente (principal custo p/ estudo)
                .engine(DatabaseInstanceEngine.mysql(MySqlInstanceEngineProps.builder()
                        .version(MysqlEngineVersion.VER_8_0)
                        .build()))
                .vpc(vpc)
                .credentials(Credentials.fromUsername("admin",
                        CredentialsFromUsernameOptions.builder()
                                .password(SecretValue.cfnParameter(databasePassword))
                                .build()))
                // Graviton (t4g.micro): mesma performance, mais barato que o t3.micro
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE4_GRAVITON, InstanceSize.MICRO))
                .multiAz(false)
                .allocatedStorage(10)
                // gp3 e mais barato/flexivel que o gp2 padrao
                .storageType(StorageType.GP3)
                // ambiente de estudo: sem backups automaticos cobrados e teardown limpo
                .backupRetention(Duration.days(0))
                .deleteAutomatedBackups(true)
                .deletionProtection(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .securityGroups(Collections.singletonList(iSecurityGroup))
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(vpc.getIsolatedSubnets())
                        .build())
                .build();

        // define como parametro de saída o endpoint gerado pra instancia do db
        CfnOutput.Builder.create(this, "rds-endpoint")
                .exportName("rds-endpoint")
                .value(databaseInstance.getDbInstanceEndpointAddress())
                .build();

        // define como parametro de saída a senha passada no deploy
        CfnOutput.Builder.create(this, "rds-password")
                .exportName("rds-password")
                .value(databasePassword.getValueAsString())
                .build();
        
    }
}
