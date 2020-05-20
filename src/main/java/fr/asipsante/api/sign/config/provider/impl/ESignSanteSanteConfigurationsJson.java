/**
 * (c) Copyright 1998-2020, ANS. All rights reserved.
 */

package fr.asipsante.api.sign.config.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.asipsante.api.sign.config.provider.IeSignSanteConfigurationsProvider;
import fr.asipsante.api.sign.ws.bean.config.IGlobalConf;
import fr.asipsante.api.sign.ws.bean.config.impl.GlobalConfJson;
import fr.asipsante.api.sign.ws.bean.object.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.InvalidParameterException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Class ESignSanteSanteConfigurationsJson.
 */
@Component
public class ESignSanteSanteConfigurationsJson extends Thread implements IeSignSanteConfigurationsProvider {

    /** The log. */
    private static final Logger log = LoggerFactory.getLogger(ESignSanteSanteConfigurationsJson.class);

    /**
     * TIMEOUT.
     */
    private static final long TIMEOUT = 25;

    /**
     * whether or not we're on app startup.
     */
    private boolean startup = true;

    /**
     * load global conf from file path.
     *
     * @return IGlobalConf
     */
    @Override
    public IGlobalConf load() {
        final File jsonFile = new File(System.getProperty("ws.conf"));
        return loadConf(jsonFile);
    }

    /**
     * load global conf from file.
     *
     * @param jsonFile json file
     * @return IGlobalConf
     */
    private IGlobalConf loadConf(final File jsonFile) {
        IGlobalConf conf = null;
        final ObjectMapper mapper = new ObjectMapper();
        boolean validConf = true;

        try {
            final String jsonConf = new String(Files.readAllBytes(Paths.get(jsonFile.toURI())));
            conf = mapper.readValue(jsonConf, GlobalConfJson.class);
            validConf = conf != null;
            if (validConf) {
                for (final SignatureConf signConf : conf.getSignature()){
                    validConf &= signConf.checkValid(); // Check if signConf is valid
                }
                for (final ProofConf proofConf : conf.getProof()){
                    validConf &= proofConf.checkValid(); // Check if proofConf is valid
                }
                for (final SignVerifConf signVerifConf : conf.getSignatureVerification()){
                    validConf &= signVerifConf.checkValid(); // Check if signVerifConf is valid
                }
                for (final CertVerifConf certVerifConf : conf.getCertificateVerification()){
                    validConf &= certVerifConf.checkValid(); // Check if certVerifConf is valid
                }
                for (final CaConf caConf : conf.getCa()){
                    validConf &= caConf.checkValid(); // Check if caConf is valid
                }
            }
        } catch (final IllegalAccessException | IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }

        if (validConf) {
            log.info("Configurations loaded.");
        } else {
            log.error("Could not load configurations.");
            if (startup) {
                throw new InvalidParameterException("Error in configuration file");
            } else {
                conf = null;
            }
        }
        return conf;
    }

    /**
     * Inner Class ReloadConfigurationsJson.
     */
    @Component
    public class ReloadConfigurationsJson extends Thread {

        /** The log. */
        private final Logger log = LoggerFactory.getLogger(ReloadConfigurationsJson.class);

        /**
         * globalConf.
         */
        @Autowired
        private IGlobalConf globalConf;

        /**
         * stop.
         */
        private AtomicBoolean stop = new AtomicBoolean(false);

        /**
         * Reload configuration file on change.
         */
        @PostConstruct
        public void reloadConfiguration() {
            start();
        }

        private void reloadConf(final File jsonFile) {
            startup = false;
            final IGlobalConf conf = loadConf(jsonFile);
            if (conf != null) {
                globalConf.setCa(conf.getCa());
                globalConf.setCertificateVerification(conf.getCertificateVerification());
                globalConf.setProof(conf.getProof());
                globalConf.setSignature(conf.getSignature());
                globalConf.setSignatureVerification(conf.getSignatureVerification());
                log.info("New configurations loaded.");
            } else {
                log.error("Could not load new configurations, will continue using current valid configurations.");
            }

        }

        /**
         * is thread stopped.
         *
         * @return boolean boolean
         */
        public boolean isStopped() {
            return stop.get();
        }

        /**
         * stop thread.
         */
        public void stopThread() {
            stop.set(true);
        }

        /**
         * reload global conf on change in file.
         *
         * @param file file
         */
        private void doOnChange(final File file) {
            reloadConf(file);
        }

        /**
         * run thread, once started it will run indefinitely in parallel.
         */
        @Override
        public void run() {
            final File file = new File(System.getProperty("ws.conf"));
            try (final WatchService watcher = FileSystems.getDefault().newWatchService()) {
                final Path path = file.getAbsoluteFile().toPath().getParent();
                path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                while (!isStopped()) { // infinite loop basically
                    final WatchKey key;
                    try {
                        key = watcher.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (final InterruptedException e) {
                        return;
                    }
                    if (key == null) {
                        Thread.yield();
                        continue;
                    }
                    // poll events since last timeout
                    watchEvents(file, key);
                    Thread.yield();
                }
            } catch (final IOException e) {
                log.error("une erreur est survenue durant la surveillance du fichier de configuration : ", e);
            }
        }

        /**
         * watch events.
         *
         * @param file file
         * @param key key
         */
        private void watchEvents(final File file, final WatchKey key) {
            for (final WatchEvent<?> event : key.pollEvents()) {
                final WatchEvent.Kind<?> kind = event.kind();

                @SuppressWarnings("unchecked") final WatchEvent<Path> ev = (WatchEvent<Path>) event;
                final Path filename = ev.context();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    Thread.yield();
                    continue;
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY
                        && filename.toString().equals(file.getName())) {
                    // do something only if it's a 'modification' event to the specific file
                    doOnChange(file);
                }
                final boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        }

    }

}
