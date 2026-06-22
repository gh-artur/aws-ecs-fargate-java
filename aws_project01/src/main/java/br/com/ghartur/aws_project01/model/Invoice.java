package br.com.ghartur.aws_project01.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"invoiceNumber"})
})
@Entity
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 32, nullable = false)
    private String invoiceNumber;

    @Column(length = 32, nullable = false)
    private String customerName;

    private BigDecimal totalValue;

    private long productId;

    private int quantity;

}
