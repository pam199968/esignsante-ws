/**
 * (c) Copyright 1998-2020, ANS. All rights reserved.
 */

package fr.asipsante.api.sign.ws.api.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import fr.asipsante.api.sign.config.provider.impl.ESignSanteSanteConfigurationsJson;
import fr.asipsante.api.sign.config.CACRLConfig;
import fr.asipsante.api.sign.config.ScheduledConfig;
import fr.asipsante.api.sign.config.WebConfig;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * The Class ValidationApiIntegrationTest.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { ESignSanteSanteConfigurationsJson.class, CACRLConfig.class, ScheduledConfig.class,
        WebConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@ComponentScan("fr.asipsante.api.sign.ws.api")
public class ValidationApiIntegrationTest {

    /** The mock mvc. */
    @Autowired
    private MockMvc mockMvc;

    /** The doc. */
    private MockMultipartFile doc;

    static {
        final String confPath;
        try {
            confPath = String.valueOf(Paths.get(Paths.get(Objects.requireNonNull(Thread.currentThread().
                    getContextClassLoader().getResource("esignsante-conf.json")).toURI()).toString()));
            System.setProperty("ws.conf", confPath);
        } catch (final URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inits the.
     *
     * @throws Exception the exception
     */
    @Before
    public void init() throws Exception {
        doc = new MockMultipartFile("file",
                Thread.currentThread().getContextClassLoader().getResourceAsStream("TOM_FICHIER.xml"));
    }

    /**
     * Cas passant validation XMLDsig.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXMLdsigTest() throws Exception {

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xmldsigwithproof").file(doc)
                .param("idVerifSignConf", "1").param("requestId", "Request-1").param("proofTag", "MonTAG")
                .param("applicantId", "RPPS").param("idProofConf", "1").accept("application/json"))
                .andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 4, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERDOCN01\"}"));
    }

    /**
     * Cas passant validation XADES.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXadesBaselineBTest() throws Exception {

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xadesbaselinebwithproof").file(doc)
                .param("idVerifSignConf", "1").param("requestId", "Request-1").param("proofTag", "MonTAG")
                .param("applicantId", "RPPS").param("idProofConf", "1").accept("application/json"))
                .andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 4, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERDOCN01\"}"));
    }

    /**
     * Cas non passant validation XADES certificat expiré.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXadesExpireTest() throws Exception {
        final MockMultipartFile document = new MockMultipartFile("file",
                Thread.currentThread().getContextClassLoader().getResourceAsStream("XADESCertExpire.xml"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xadesbaselineb").file(document)
                .param("idVerifSignConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERSIGN05\"}"));
    }

    /**
     * Cas passant - validation certificat expiré.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifCertificatExpireTest() throws Exception {
        final MockMultipartFile cert = new MockMultipartFile("file", Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("rpps.tra.henix.asipsante.fr-sign-expire.pem"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/certificats").file(cert)
                .param("idVerifCertConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERCERT01\"}"));
    }

    /**
     * Cas non passant de validation de signature XMLDSIG avec preuve avec un
     * document non conforme (XML corrompu / cassé).
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXMLdsigTestBadFile() throws Exception {
        doc = new MockMultipartFile("file",
                Thread.currentThread().getContextClassLoader().getResourceAsStream("TOM_FICHIER_bad.xml"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xmldsigwithproof").file(doc)
                .param("idSignConf", "1").param("idVerifSignConf", "1").param("requestId", "Request-1")
                .param("proofTag", "MonTAG").param("applicantId", "RPPS").param("idProofConf", "1")
                .accept("application/json")).andExpect(status().isNotImplemented()).andDo(print());
    }

    /**
     * Cas non passant validation XMLDSIG document altéré.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXmldsigEnveloppeeDocAltereTest() throws Exception {
        final MockMultipartFile document = new MockMultipartFile("file", Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("Signature_Dsig_enveloppee_document_modifie.xml"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xmldsig").file(document)
                .param("idVerifSignConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 3 erreurs", 3, body.getJSONArray("erreurs").length());
    }

    /**
     * Cas non passant validation xades enveloppante document altéré.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXadesEnveloppanteDocAltereTest() throws Exception {
        final MockMultipartFile document = new MockMultipartFile("file", Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("sign_xades_enveloping_modifiedDoc.xml"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xadesbaselineb").file(document)
                .param("idVerifSignConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir une erreur", 1, body.getJSONArray("erreurs").length());
    }

    /**
     * Cas non passant validation XADES mauvaise autorité.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifSignXadesBadACTest() throws Exception {
        final MockMultipartFile document = new MockMultipartFile("file",
                Thread.currentThread().getContextClassLoader().getResourceAsStream("SignatureCA-PERSONNE.xml"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/signatures/xadesbaselineb").file(document)
                .param("idVerifSignConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERDOCN01\"}"));
    }

    /**
     * Cas passant validation certificat PEM.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifCertificatTest() throws Exception {
        final MockMultipartFile cert = new MockMultipartFile("file", Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("asip-p12-EL-TEST-ORG-SIGN-20181116-141712.pem"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/certificats").file(cert)
                .param("idVerifCertConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait être vide", 0, body.getJSONArray("erreurs").length());
    }

    /**
     * Cas passant validation certificat PEM avec preuve.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifCertificatWithProofTest() throws Exception {
        final MockMultipartFile cert = new MockMultipartFile("file", Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("asip-p12-EL-TEST-ORG-SIGN-20181116-141712.pem"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/certificatswithproof").file(cert)
                .param("idSignConf", "1").param("idVerifCertConf", "1").param("requestId", "Request-1")
                .param("proofTag", "MonTAG").param("applicantId", "RPPS").param("idProofConf", "1")
                .accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 4, body.names().length());
        assertEquals("La Liste des erreurs devrait être vide", 0, body.getJSONArray("erreurs").length());
    }

    /**
     * Cas passant validation certificat PEM avec mauvaise autorité.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifCertificatBadACTest() throws Exception {
        final MockMultipartFile cert = new MockMultipartFile("file",
                Thread.currentThread().getContextClassLoader().getResourceAsStream("classe4.pem"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/certificats").file(cert)
                .param("idVerifCertConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERCERT04\"}"));
    }

    /**
     * Cas passant validation certificat DER.
     *
     * @throws Exception the exception
     */
    @Test
    public void verifCertificatDERTest() throws Exception {
        final MockMultipartFile cert = new MockMultipartFile("file", Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("asip-p12-EL-TEST-ORG-SIGN-20181116-141712.der"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/certificats").file(cert)
                .param("idVerifCertConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait être vide", 0, body.getJSONArray("erreurs").length());
    }

    /**
     * Cas non passant validation certificat DER (mauvaise autorité).
     *
     * @throws Exception the exception
     */
    @Test
    public void verifCertificatBadCADERTest() throws Exception {
        final MockMultipartFile cert = new MockMultipartFile("file",
                Thread.currentThread().getContextClassLoader().getResourceAsStream("2600301752-Auth.crt"));
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/validation/certificats").file(cert)
                .param("idVerifCertConf", "1").accept("application/json")).andExpect(status().isOk()).andDo(print()).andReturn();

        final JSONObject body = new JSONObject(result.getResponse().getContentAsString());
        assertEquals("Toutes les données attendus en réponse ne sont pas retrouvées", 3, body.names().length());
        assertEquals("La Liste des erreurs devrait contenir 1 erreur", 1, body.getJSONArray("erreurs").length());
        assertTrue("Le code erreur attendu n'est pas le bon", body.getJSONArray("erreurs").get(0).toString().endsWith("\"codeErreur\":\"ERCERT04\"}"));
    }
}
