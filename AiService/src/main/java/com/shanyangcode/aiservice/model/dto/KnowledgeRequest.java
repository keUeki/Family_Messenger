package com.shanyangcode.aiservice.model.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class KnowledgeRequest implements Serializable {


    /**
     * The question, for example: What is this software called?
     */
    private String question;

    /**
     * The answer, for example: This software is called Qianyan...
     */
    private String answer;

    /**
     * (Optional) source name, used to stand in for file_name
     */
    private String sourceName;

}

