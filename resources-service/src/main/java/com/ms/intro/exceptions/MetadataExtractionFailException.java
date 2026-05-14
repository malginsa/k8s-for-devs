package com.ms.intro.exceptions;

public class MetadataExtractionFailException extends RuntimeException {
    public MetadataExtractionFailException(Exception e) {
        super(e);
    }

    public MetadataExtractionFailException() {
    }
}
