/**
 * (c) Copyright 1998-2020, ANS. All rights reserved.
 */

package fr.asipsante.api.sign.ws.api.delegate;

import fr.asipsante.api.sign.bean.errors.ErreurSignature;
import fr.asipsante.api.sign.bean.metadata.MetaDatum;
import fr.asipsante.api.sign.bean.parameters.ProofParameters;
import fr.asipsante.api.sign.bean.parameters.SignatureParameters;
import fr.asipsante.api.sign.bean.parameters.SignatureValidationParameters;
import fr.asipsante.api.sign.bean.rapports.RapportSignature;
import fr.asipsante.api.sign.bean.rapports.RapportValidationSignature;
import fr.asipsante.api.sign.enums.MetaDataType;
import fr.asipsante.api.sign.service.ICACRLService;
import fr.asipsante.api.sign.service.IProofGenerationService;
import fr.asipsante.api.sign.service.ISignatureService;
import fr.asipsante.api.sign.service.ISignatureValidationService;
import fr.asipsante.api.sign.service.impl.utils.Version;
import fr.asipsante.api.sign.utils.AsipSignClientException;
import fr.asipsante.api.sign.utils.AsipSignException;
import fr.asipsante.api.sign.utils.AsipSignServerException;
import fr.asipsante.api.sign.ws.api.SignaturesApiDelegate;
import fr.asipsante.api.sign.ws.bean.config.IGlobalConf;
import fr.asipsante.api.sign.ws.bean.object.ProofConf;
import fr.asipsante.api.sign.ws.bean.object.SignVerifConf;
import fr.asipsante.api.sign.ws.bean.object.SignatureConf;
import fr.asipsante.api.sign.ws.model.ESignSanteSignatureReport;
import fr.asipsante.api.sign.ws.model.ESignSanteSignatureReportWithProof;
import fr.asipsante.api.sign.ws.model.Erreur;
import fr.asipsante.api.sign.ws.model.Metadata;
import fr.asipsante.api.sign.ws.util.SignWsUtils;
import fr.asipsante.api.sign.ws.util.WsVars;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * The Class SignaturesApiDelegateImpl.
 */
@Service
public class SignaturesApiDelegateImpl extends ApiDelegate implements SignaturesApiDelegate {

    /** Default ESignSante major version. */
    private static final int MAJOR = 2;

    /** Default ESignSante version. */
    private static final Version DEFAULT_VERSION = new Version(MAJOR, 0, 0, 0);

    /**
     * The log.
     */
    Logger log = LoggerFactory.getLogger(SignaturesApiDelegateImpl.class);

    /** The signature service. */
    @Autowired
    private ISignatureService signatureService;

    /** The signature validation service. */
    @Autowired
    private ISignatureValidationService signatureValidationService;

    /** The proof generation service. */
    @Autowired
    private IProofGenerationService proofGenerationService;

    /** The service ca crl. */
    @Autowired
    private ICACRLService serviceCaCrl;

    /** The global configurations. */
    @Autowired
    private IGlobalConf globalConf;

    /** ESignSante Build Properties. */
    @Autowired
    private BuildProperties buildProperties;

    /** Enable/disable secret. */
    @Value("${config.secret}")
    private String secretEnabled;

    /**
     * Digital signature with proof.
     *
     * @param secret          the secret
     * @param idSignConf      the id sign conf
     * @param doc             the doc
     * @param idVerifSignConf the id verif sign conf
     * @param proofParameters the proof parameters
     * @param isXades         the is xades
     * @return the response entity
     */
    private ResponseEntity<ESignSanteSignatureReportWithProof> digitalSignatureWithProof(
            final String secret, final Long idSignConf, final MultipartFile doc, final Long idVerifSignConf,
            final ProofParameters proofParameters, final boolean isXades) {
        final Optional<String> acceptHeader = getAcceptHeader();
        ResponseEntity<ESignSanteSignatureReportWithProof> re = new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        // get configurations
        final Optional<SignatureConf> signConf = globalConf.getSignatureById(idSignConf.toString());
        final Optional<SignVerifConf> verifConf = globalConf.getSignatureVerificationById(idVerifSignConf.toString());
        if (!signConf.isPresent() || !verifConf.isPresent()) {
            re = new ResponseEntity<>(HttpStatus.NOT_FOUND);
            log.error("Configuration {}" , HttpStatus.NOT_FOUND.getReasonPhrase());
        } else {
            final Optional<ProofConf> signProofConf = globalConf.getProofById(signConf.get().getIdProofConf());
            if (acceptHeader.isPresent() && acceptHeader.get().contains(WsVars.HEADER_TYPE.getVar())) {
                // this is redundant with current implementation, params are assured
                if (signParamsMissing(idSignConf, doc, idVerifSignConf) || proofParamsMissing(proofParameters)) {
                    re = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                } else if (!signProofConf.isPresent()) {
                    re = new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    log.error("Proof ID {}" , HttpStatus.NOT_FOUND.getReasonPhrase());
                } else if ("enable".equalsIgnoreCase(secretEnabled)
                        && signConf.get().noSecretMatch(secret)) {
                    re = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
                    log.error(HttpStatus.UNAUTHORIZED.getReasonPhrase());
                } else {
                    final SignatureParameters signParams = signConf.get().getSignParams();
                    final SignatureValidationParameters signVerifParams = verifConf.get().getSignVerifParams();
                    final SignatureParameters signProofParams = signProofConf.get().getSignProofParams();
                    re = signWithProof(doc, proofParameters, isXades, signParams, signVerifParams,
                            signProofParams);
                    log.info("Digital Signature With Proof Generated : {}", HttpStatus.OK.getReasonPhrase());
                }
            }
        }
        return re;
    }

    /**
     * Sign with proof.
     *
     * @param doc                      the doc
     * @param proofParameters          the proof parameters
     * @param isXades                  the is xades
     * @param signParams               the sign params
     * @param signValidationParameters the sign validation parameters
     * @param signProofParams          the sign proof params
     * @return the response entity
     */
    private ResponseEntity<ESignSanteSignatureReportWithProof> signWithProof(
            final MultipartFile doc, final ProofParameters proofParameters, final boolean isXades,
            final SignatureParameters signParams, final SignatureValidationParameters signValidationParameters,
            final SignatureParameters signProofParams) {
        ResponseEntity<ESignSanteSignatureReportWithProof> re;
        try (final InputStreamReader reader = new InputStreamReader(doc.getInputStream())) {
            final String docString = new String(doc.getBytes(), reader.getEncoding());
            // Contrôle du certificat de signature
            HttpStatus status = SignWsUtils.checkCertificate(signParams, serviceCaCrl.getCacrlWrapper());
            if (status != HttpStatus.CONTINUE) {
                re = new ResponseEntity<>(status);
            } else {
                final RapportSignature rapportSignature;
                final RapportValidationSignature rapportVerifSignature;
                // Signature du document
                if (isXades) {
                    rapportSignature = signatureService.signXADESBaselineB(docString, signParams);
                    // Validation de la signature
                    rapportVerifSignature = signatureValidationService.validateXADESBaseLineBSignature(
                            rapportSignature.getDocSigne(), signValidationParameters, serviceCaCrl.getCacrlWrapper());

                } else {
                    rapportSignature = signatureService.signXMLDsig(docString, signParams);
                    // Validation de la signature
                    rapportVerifSignature = signatureValidationService.validateXMLDsigSignature(
                            rapportSignature.getDocSigne(), signValidationParameters, serviceCaCrl.getCacrlWrapper());
                }
                // Géneration de la preuve
                final String proof = proofGenerationService.generateSignVerifProof(rapportVerifSignature,
                        proofParameters, serviceCaCrl.getCacrlWrapper());
                // Contrôle du certificat de signature de la preuve
                status = SignWsUtils.checkCertificate(signProofParams, serviceCaCrl.getCacrlWrapper());
                if (status != HttpStatus.CONTINUE) {
                    re = new ResponseEntity<>(status);
                } else {
                    // Signature de la preuve
                    final RapportSignature rapportSignaturePreuve = signatureService.signXADESBaselineB(proof,
                            signProofParams);

                    final ESignSanteSignatureReportWithProof rapport = populateResultSignWithProof(
                            rapportVerifSignature.getListeErreurSignature(), rapportVerifSignature.getMetaData(),
                            rapportVerifSignature.isValide(), rapportSignature.getDocSigne(),
                            rapportSignaturePreuve.getDocSigne());
                    re = new ResponseEntity<>(rapport, HttpStatus.OK);
                }
            }
        } catch (final AsipSignClientException e1) {
            log.error(ExceptionUtils.getStackTrace(e1));
            re = new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        } catch (final AsipSignServerException e1) {
            log.error(ExceptionUtils.getStackTrace(e1));
            re = new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (final IOException | AsipSignException e1) {
            log.error(ExceptionUtils.getStackTrace(e1));
            re = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return re;
    }

    /**
     * Checks if all signature params are present.
     *
     * @param idSignConf      the id sign conf
     * @param doc             the doc
     * @param idVerifSignConf the id verif sign conf
     * @return boolean
     */
    private boolean signParamsMissing(final Long idSignConf, final MultipartFile doc, final Long idVerifSignConf) {
        return idSignConf == null || doc == null || idVerifSignConf == null;
    }

    /**
     * Checks if proof params are present.
     *
     * @param proofParameters the proof parameters
     * @return boolean
     */
    private boolean proofParamsMissing(final ProofParameters proofParameters) {
        return proofParameters.getRequestid() == null || proofParameters.getProofTag() == null
                || proofParameters.getApplicantId() == null;
    }

    /**
     * Called operation.
     *
     * @param opName the op name
     * @return the string
     */
    private String calledOperation(final String opName) {
        final Optional<NativeWebRequest> request = getRequest();
        String operation = opName;
        if (request.isPresent()) {
            // get called operation for proof generation
            operation = request.get().getContextPath() + opName;
        }
        return operation;
    }

    /**
     * Signature XM ldsig with proof.
     *
     * @param idSignConf      the id sign conf
     * @param doc             the doc
     * @param idVerifSignConf the id verif sign conf
     * @param requestId       the request id
     * @param proofTag        the proof tag
     * @param applicantId     the applicant id
     * @param secret          the secret
     * @return the response entity
     */
    @Override
    public ResponseEntity<ESignSanteSignatureReportWithProof> signatureXMLdsigWithProof(
            final Long idSignConf, final MultipartFile doc, final Long idVerifSignConf, final String requestId,
            final String proofTag, final String applicantId, final String secret) {
        Version wsVersion = DEFAULT_VERSION;
        try {
            // get version object for proof generation
            wsVersion = new Version(buildProperties.getVersion());
        } catch (final ParseException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        final ProofParameters proofParameters = new ProofParameters("Sign", requestId, proofTag,
                applicantId, calledOperation("/signatures/xmldsigwithproof"), wsVersion);
        return digitalSignatureWithProof(secret, idSignConf, doc, idVerifSignConf, proofParameters, false);
    }

    /**
     * Signature xades with proof.
     *
     * @param idSignConf      the id sign conf
     * @param doc             the doc
     * @param idVerifSignConf the id verif sign conf
     * @param requestId       the request id
     * @param proofTag        the proof tag
     * @param applicantId     the applicant id
     * @param secret          the secret
     * @return the response entity
     */
    @Override
    public ResponseEntity<ESignSanteSignatureReportWithProof> signatureXadesWithProof(
            final Long idSignConf, final MultipartFile doc, final Long idVerifSignConf, final String requestId,
            final String proofTag, final String applicantId, final String secret) {
        Version wsVersion = DEFAULT_VERSION;
        try {
            // get version object for proof generation
            wsVersion = new Version(buildProperties.getVersion());
        } catch (final ParseException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        final ProofParameters proofParameters = new ProofParameters("Sign", requestId, proofTag,
                applicantId, calledOperation("/signatures/xadesbaselinebwithproof"), wsVersion);
        return digitalSignatureWithProof(secret, idSignConf, doc, idVerifSignConf, proofParameters, true);
    }

    /**
     * Digital signature.
     *
     * @param secret     the secret
     * @param idSignConf the id sign conf
     * @param doc        the doc
     * @param isXades    the is xades
     * @return the response entity
     */
    private ResponseEntity<ESignSanteSignatureReport> digitalSignature(final String secret, final Long idSignConf,
                                                                       final MultipartFile doc, final boolean isXades) {
        final Optional<String> acceptHeader = getAcceptHeader();
        ResponseEntity<ESignSanteSignatureReport> re = new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        if (idSignConf != null && doc != null) {
            final Optional<SignatureConf> signConf = globalConf.getSignatureById(idSignConf.toString());
            if (acceptHeader.isPresent() && acceptHeader.get().contains(WsVars.HEADER_TYPE.getVar())) {
                if (!signConf.isPresent()) {
                    re = new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    log.error("Configuration {}" , HttpStatus.NOT_FOUND.getReasonPhrase());
                } else if ("enable".equalsIgnoreCase(secretEnabled)
                        && signConf.get().noSecretMatch(secret)) {
                    re = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
                    log.error(HttpStatus.UNAUTHORIZED.getReasonPhrase());
                } else {
                    final SignatureParameters signParams = signConf.get().getSignParams();
                    re = sign(signParams, doc, isXades);
                    log.info("Digital Signature : {}", HttpStatus.OK.getReasonPhrase());
                }
            }
        } else {
            re = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return re;
    }

    /**
     * Sign.
     *
     * @param signParams the signature parameters
     * @param doc        the doc
     * @param isXades    the is xades
     * @return the response entity
     */
    private ResponseEntity<ESignSanteSignatureReport> sign(final SignatureParameters signParams,
                                                           final MultipartFile doc, final boolean isXades) {
        ResponseEntity<ESignSanteSignatureReport> re;
        try {
            final String docString = new String(doc.getBytes(), StandardCharsets.UTF_8);
            // Contrôle du certificat de signature
            final HttpStatus status = SignWsUtils.checkCertificate(signParams, serviceCaCrl.getCacrlWrapper());
            if (status != HttpStatus.CONTINUE) {
                re = new ResponseEntity<>(status);
            } else {
                // Signature
                final RapportSignature rapportSignature;
                if (isXades) {
                    rapportSignature = signatureService.signXADESBaselineB(docString, signParams);
                } else {
                    rapportSignature = signatureService.signXMLDsig(docString, signParams);
                }
                final ESignSanteSignatureReport rapport = populateResultSign(rapportSignature.getListeErreurSignature(),
                        rapportSignature.getDocSigne());
                re = new ResponseEntity<>(rapport, HttpStatus.OK);
            }
        } catch (final AsipSignClientException e2) {
            log.error(ExceptionUtils.getStackTrace(e2));
            re = new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        } catch (final AsipSignServerException e2) {
            log.error(ExceptionUtils.getStackTrace(e2));
            re = new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (final IOException | AsipSignException e2) {
            log.error(ExceptionUtils.getStackTrace(e2));
            re = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return re;
    }

    /**
     * Signature XM ldsig.
     *
     * @param idSignConf the id sign conf
     * @param doc        the doc
     * @param secret     the secret
     * @return the response entity
     */
    @Override
    public ResponseEntity<ESignSanteSignatureReport> signatureXMLdsig(final Long idSignConf, final MultipartFile doc,
                                                                final String secret) {
        return digitalSignature(secret, idSignConf, doc, false);
    }

    /**
     * Signature xades.
     *
     * @param idSignConf the id sign conf
     * @param doc        the doc
     * @param secret     the secret
     * @return the response entity
     */
    @Override
    public ResponseEntity<ESignSanteSignatureReport> signatureXades(final Long idSignConf, final MultipartFile doc,
                                                              final String secret) {
        return digitalSignature(secret, idSignConf, doc, true);
    }

    /**
     * Populate result sign.
     *
     * @param erreursSignature the erreurs signature
     * @param signedDocument   the signed document
     * @return the fr.asipsante.api.sign.ws.model. rapport signature
     */
    private ESignSanteSignatureReport populateResultSign(final List<ErreurSignature> erreursSignature,
                                                   final String signedDocument) {
        final ESignSanteSignatureReport rapport = new ESignSanteSignatureReport();
        rapport.setDocSigne(Base64.getEncoder().encodeToString(signedDocument.getBytes()));
        final List<Erreur> erreurs = new ArrayList<>();
        for (final ErreurSignature erreurANS : erreursSignature) {
            final Erreur erreur = new Erreur();
            erreur.setCodeErreur(erreurANS.getCode());
            erreur.setMessage(erreurANS.getMessage());
            erreurs.add(erreur);
        }
        rapport.setErreurs(erreurs);
        return rapport;
    }

    /**
     * Populate result sign with proof.
     *
     * @param erreursSignature the erreurs signature
     * @param metadata         the metadata
     * @param isValide         the is valide
     * @param signedDocument   the signed document
     * @param preuve           the preuve
     * @return the rapport signature with proof
     */
    private ESignSanteSignatureReportWithProof populateResultSignWithProof(
            final List<ErreurSignature> erreursSignature, final List<MetaDatum> metadata, final boolean isValide,
            final String signedDocument, final String preuve) {
        final ESignSanteSignatureReportWithProof rapport = new ESignSanteSignatureReportWithProof();
        rapport.setValide(isValide);
        rapport.setDocSigne(Base64.getEncoder().encodeToString(signedDocument.getBytes()));
        rapport.setPreuve(Base64.getEncoder().encodeToString(preuve.getBytes()));

        final List<Erreur> erreurs = new ArrayList<>();
        for (final ErreurSignature erreurANS : erreursSignature) {
            final Erreur erreur = new Erreur();
            erreur.setCodeErreur(erreurANS.getCode());
            erreur.setMessage(erreurANS.getMessage());
            erreurs.add(erreur);
        }
        rapport.setErreurs(erreurs);

        final List<Metadata> metas = new ArrayList<>();
        for (final MetaDatum metadatum : metadata) {
            final Metadata meta = new Metadata();
            meta.setTypeMetadata(metadatum.getType().getName());
            if (metadatum.getType().equals(MetaDataType.RAPPORT_DIAGNOSTIQUE)
                    || metadatum.getType().equals(MetaDataType.DOCUMENT_ORIGINAL_NON_SIGNE)
                    || metadatum.getType().equals(MetaDataType.RAPPORT_DSS)) {
                meta.setMessage(Base64.getEncoder().encodeToString(metadatum.getValue().getBytes()));
            } else {
                meta.setMessage(metadatum.getValue());
            }
            metas.add(meta);
        }
        rapport.setMetaData(metas);
        return rapport;
    }
}