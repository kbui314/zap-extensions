/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2019 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.pscanrulesBeta;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;

class JsoScanRuleUnitTest extends PassiveScannerTest<JsoScanRule> {

    /* Testing JSO in response */
    @Test
    void shouldNotRaiseAlertGivenNoJsoHasBeenDetectedInResponse() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET / HTTP/1.1");
        msg.setResponseHeader(
                "HTTP/1.1 200 OK\r\n" + "X-Custom-Info: NOPE\r\n" + "Set-Cookie: NOPE=NOPE");

        // When
        scanHttpResponseReceive(msg);

        // Then
        assertThat(alertsRaised, empty());
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInHeaderOfResponse() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET / HTTP/1.1");
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setResponseHeader("HTTP/1.1 200 OK\r\n" + "X-Custom-Info: " + jso + "\r\n");

        // When
        scanHttpResponseReceive(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInCookieOfResponse() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET / HTTP/1.1");
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setResponseHeader("HTTP/1.1 200 OK\r\n" + "Set-Cookie: CRUNCHY=" + jso + "\r\n");

        // When
        scanHttpResponseReceive(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenRawJsoMagicBytesAreDetectedInRawBodyOfResponse() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET / HTTP/1.1");
        byte[] jso = createJso();
        msg.setResponseHeader(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Disposition: attachment; filename=\"jso.bin\"\r\n"
                        + "Content-Length: "
                        + jso.length
                        + "\r\n");
        msg.setResponseBody(jso);

        // When
        scanHttpResponseReceive(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInBodyOfResponse() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET / HTTP/1.1");
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setResponseHeader(
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Disposition: attachment; filename=\"jso.bin\"\r\n"
                        + "Content-Length: "
                        + jso.length()
                        + "\r\n");
        msg.setResponseBody(jso);

        // When
        scanHttpResponseReceive(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    /* Testing JSO in request */
    @Test
    void shouldNotRaiseAlertGivenNoJsoHasBeenDetectedInRequest() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader(
                "GET / HTTP/1.1\r\n" + "X-Custom-Info: NOPE\r\n" + "Cookie: NOPE=NOPE\r\n");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, empty());
    }

    @Test
    void shouldRaiseAlertGivenUriEncodedJsoMagicBytesAreDetectedInRequestParameterOfRequest()
            throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET /some_action?q=" + createUriEncodedJso() + "&p=&m HTTP/1.1");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInRequestParameterOfRequest()
            throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setRequestHeader("GET /some_action?q=" + jso + "&p=&m HTTP/1.1");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenUriEncodedJsoMagicBytesAreDetectedInHeaderOfRequest()
            throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET / HTTP/1.1\r\n" + "X-Custom-Info: " + createUriEncodedJso());

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInHeaderOfRequest() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setRequestHeader("GET / HTTP/1.1\r\n" + "X-Custom-Info: " + jso + "\r\n");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenUriEncodedJsoMagicBytesAreDetectedInCookieOfRequest()
            throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader(
                "GET / HTTP/1.1\r\n" + "Cookie: CRUNCHY=" + createUriEncodedJso() + "\r\n");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInCookieOfRequest() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setRequestHeader("GET / HTTP/1.1\r\n" + "Cookie: CRUNCHY=" + jso + "\r\n");

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenRawJsoMagicBytesAreDetectedInBodyOfRequest() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        byte[] jso = createJso();
        msg.setRequestHeader(
                "POST / HTTP/1.1\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Disposition: attachment; filename=\"jso.bin\"\r\n"
                        + "Content-Length: "
                        + jso.length
                        + "\r\n");
        msg.setRequestBody(jso);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldRaiseAlertGivenBase64JsoMagicBytesAreDetectedInBodyOfRequest() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        String jso = Base64.getEncoder().encodeToString(createJso());
        msg.setRequestHeader(
                "GET / HTTP/1.1\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Disposition: attachment; filename=\"jso.bin\"\r\n"
                        + "Content-Length: "
                        + jso.length()
                        + "\r\n");
        msg.setRequestBody(jso);

        // When
        scanHttpRequestSend(msg);

        // Then
        assertThat(alertsRaised, hasSize(1));
    }

    @Test
    void shouldReturnExpectedMappings() {
        // Given / When
        Map<String, String> tags = rule.getAlertTags();
        // Then
        assertThat(tags.size(), is(equalTo(3)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2021_A04_INSECURE_DESIGN.getTag()),
                is(equalTo(true)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2017_A08_INSECURE_DESERIAL.getTag()),
                is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.PENTEST.getTag()), is(equalTo(true)));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2021_A04_INSECURE_DESIGN.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2021_A04_INSECURE_DESIGN.getValue())));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2017_A08_INSECURE_DESERIAL.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2017_A08_INSECURE_DESERIAL.getValue())));
    }

    @Test
    void shouldHaveExpectedExampleAlerts() {
        // Given / WHen
        List<Alert> alerts = rule.getExampleAlerts();
        // Then
        assertThat(alerts.size(), is(equalTo(1)));
    }

    private static byte[] createJso() throws IOException {
        AnObject anObject = new AnObject();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
        objectOutputStream.writeObject(anObject);
        return out.toByteArray();
    }

    private static String createUriEncodedJso() throws IOException {
        return URLEncoder.encode(
                new String(createJso(), StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8.name());
    }

    @Override
    protected JsoScanRule createScanner() {
        return new JsoScanRule();
    }

    private static class AnObject implements Serializable {
        private static final long serialVersionUID = 1L;
        private static String value;

        public static String getValue() {
            return value;
        }

        public static void setValue(String value) {
            AnObject.value = value;
        }
    }
}
