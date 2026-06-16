package br.com.ghartur.aws_project01.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Table(
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"code"})
        }
)
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(length = 32, nullable = false)
    private String name;

    @NotBlank
    @Column(length = 24, nullable = false)
    private String model;

    @NotBlank
    @Column(length = 8, nullable = false)
    private String code;

    @NotNull
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;
}
