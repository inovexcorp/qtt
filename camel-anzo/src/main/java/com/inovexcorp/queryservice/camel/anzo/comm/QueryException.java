package com.inovexcorp.queryservice.camel.anzo.comm;

import java.io.IOException;

public class QueryException extends IOException {

    public QueryException(String msg) {
        super(msg);
    }

    public QueryException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
