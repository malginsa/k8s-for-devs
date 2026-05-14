package com.ms.intro.exceptions;

public class NotFoundSongDataException extends RuntimeException {
    public NotFoundSongDataException(){
        super();
    }
    public NotFoundSongDataException(String errMsg){
        super(errMsg);
    }
}
