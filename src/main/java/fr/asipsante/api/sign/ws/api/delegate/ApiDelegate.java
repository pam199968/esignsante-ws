/**
 * (c) Copyright 1998-2020, ANS. All rights reserved.
 */

package fr.asipsante.api.sign.ws.api.delegate;

import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Optional;

/**
 * The Class ApiDelegate.
 */
public class ApiDelegate {

    /**
     * Gets the request.
     *
     * @return the request
     */
    public Optional<NativeWebRequest> getRequest() {
        Optional<NativeWebRequest> request = Optional.empty();
        final ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes();

        if (attrs != null) {
            request = Optional.of(new ServletWebRequest(attrs.getRequest()));
        }
        return request;
    }

    /**
     * Gets the accept header.
     *
     * @return the accept header
     */
    public Optional<String> getAcceptHeader() {
        return getRequest().map(r -> r.getHeader("Accept"));
    }
}
