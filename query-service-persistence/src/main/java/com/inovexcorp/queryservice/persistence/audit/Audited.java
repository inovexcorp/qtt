package com.inovexcorp.queryservice.persistence.audit;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose changes to routes and datasources are a user's configuration change, recorded in their
 * {@code modifiedBy}/{@code modifiedAt} (and {@code createdBy}/{@code createdAt} when new). Changes made outside such
 * a method (health checks, background jobs) leave those fields alone. The application's interceptor supplies the
 * signed-in user.
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Audited {
}
