package br.com.ghartur.aws_project01.model;

import lombok.Data;

@Data
public class ProductEvent {
    private Long productId;
    private String code;
    private String username;
}
