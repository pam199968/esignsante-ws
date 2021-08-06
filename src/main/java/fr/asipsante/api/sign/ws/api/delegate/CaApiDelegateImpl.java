/**
 * (c) Copyright 1998-2020, ANS. All rights reserved.
 */

package fr.asipsante.api.sign.ws.api.delegate;

import fr.asipsante.api.sign.service.ICACRLService;
import fr.asipsante.api.sign.ws.api.CaApiDelegate;
import fr.asipsante.api.sign.ws.util.WsVars;
import io.swagger.annotations.Api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The Class CaApiDelegateImpl.
 */
@Service
@Api(value = "CA API Controller", produces = MediaType.APPLICATION_JSON_VALUE, tags = {"ca-api-controller"}) 
public class CaApiDelegateImpl extends ApiDelegate implements CaApiDelegate {

    /** The cacrl service. */
    @Autowired
    private ICACRLService cacrlService;

    /**
     * Gets the ca.
     *
     * @return the ca
     */
    @Override
    public ResponseEntity<List<String>> getCA() {
        final Optional<String> acceptHeader = getAcceptHeader();
        ResponseEntity<List<String>> re = new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        if (acceptHeader.isPresent() && acceptHeader.get().contains(WsVars.HEADER_TYPE.getVar())) {
            re = new ResponseEntity<>(cacrlService.getCa(), HttpStatus.OK);
        }
        return re;
    }
}
