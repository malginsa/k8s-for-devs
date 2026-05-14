package com.ms.intro.exceptions;

/**
 * runtime exception that should be thrown if not a mp3 file was uploaded.
 */
public class CannotUploadFileException extends RuntimeException{
    public CannotUploadFileException(){
        // empty
    }
    public CannotUploadFileException(Throwable cause) {
        super(cause);
    }
}
