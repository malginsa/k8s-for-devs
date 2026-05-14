package com.ms.intro.exceptions;

/**
 * runtime exception that should be thrown if not a mp3 file was uploaded.
 */
public class NotMp3Exception extends RuntimeException{
    public NotMp3Exception(){
        // empty
    }
    public NotMp3Exception(Throwable cause) {
        super(cause);
    }
}
