package br.com.ghartur.aws_project01.model;

import br.com.ghartur.aws_project01.enums.EventType;
import lombok.Data;

@Data
public class Envelope {
    private EventType eventType;
    private String data;
}
