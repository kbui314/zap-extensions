/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2016 The ZAP Development Team
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
package org.zaproxy.zap.extension.ascanrules;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import java.util.List;
import java.util.Map;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Plugin.AlertThreshold;
import org.parosproxy.paros.core.scanner.Plugin.AttackStrength;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;
import org.zaproxy.addon.commonlib.http.HttpFieldsNames;
import org.zaproxy.zap.model.Tech;
import org.zaproxy.zap.model.TechSet;
import org.zaproxy.zap.testutils.NanoServerHandler;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

/** Unit test for {@link SourceCodeDisclosureCve20121823ScanRule}. */
class SourceCodeDisclosureCve20121823ScanRuleUnitTest
        extends ActiveScannerTest<SourceCodeDisclosureCve20121823ScanRule> {

    private static final String RESPONSE_HEADER_404_NOT_FOUND =
            "HTTP/1.1 404 Not Found\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n";
    private static final String PHP_SOURCE_TAGS = "<?php $x=0; echo '<h1>Welcome!</h1>'; ?>";
    private static final String PHP_SOURCE_ECHO_TAG = "<?= '<h1>Welcome!</h1>' ?>";

    @Override
    protected SourceCodeDisclosureCve20121823ScanRule createScanner() {
        SourceCodeDisclosureCve20121823ScanRule rule =
                new SourceCodeDisclosureCve20121823ScanRule();
        rule.setConfig(new ZapXmlConfiguration());
        return rule;
    }

    @Test
    void shouldTargetPhpTech() throws Exception {
        // Given
        TechSet techSet = techSet(Tech.PHP);
        // When
        boolean targets = rule.targets(techSet);
        // Then
        assertThat(targets, is(equalTo(true)));
    }

    @Test
    void shouldNotTargetNonPhpTechs() throws Exception {
        // Given
        TechSet techSet = techSetWithout(Tech.PHP);
        // When
        boolean targets = rule.targets(techSet);
        // Then
        assertThat(targets, is(equalTo(false)));
    }

    @Test
    void shouldIgnoreNonTextResponses() throws Exception {
        // Given
        HttpMessage message = getHttpMessage("/");
        message.getResponseHeader().setHeader(HttpFieldsNames.CONTENT_TYPE, "image/jpeg");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
    }

    @Test
    void shouldIgnore404NotFoundResponsesOnMediumAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.init(message, parent);
        rule.setAttackStrength(AttackStrength.MEDIUM);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
    }

    @Test
    void shouldIgnore404NotFoundResponsesOnLowAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.init(message, parent);
        rule.setAttackStrength(AttackStrength.LOW);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
    }

    @Test
    void shouldScan404NotFoundResponsesOnHighAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.setAttackStrength(AttackStrength.HIGH);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    void shouldScan404NotFoundResponsesOnInsaneAttackStrength() throws Exception {
        // Given
        HttpMessage message = httpMessage404NotFound();
        rule.init(message, parent);
        rule.setAttackStrength(AttackStrength.INSANE);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    void shouldScanUrlsWithoutPath() throws Exception {
        // Given
        nano.addHandler(
                new NanoServerHandler("shouldScanUrlsWithoutPath") {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        return newFixedLengthResponse("No Source Code here!");
                    }
                });
        HttpMessage message = getHttpMessage("");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    void shouldScanUrlsWithEncodedCharsInPath() throws Exception {
        // Given
        String test = "/shouldScanUrlsWithEncodedCharsInPath/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        return newFixedLengthResponse("No Source Code here!");
                    }
                });
        HttpMessage message = getHttpMessage(test + "%7B+%25%24");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
    }

    @Test
    void shouldNotAlertIfThereIsNoSourceCodeDisclosure() throws Exception {
        // Given
        String test = "/shouldNotAlertIfThereIsNoSourceCodeDisclosure/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        return newFixedLengthResponse("No Source Code here!");
                    }
                });
        HttpMessage message = getHttpMessage(test);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    void shouldAlertIfPhpSourceTagsWereDisclosedInResponseBody() throws Exception {
        // Given
        String test = "/shouldAlertIfPhpSourceTagsWereDisclosedInResponseBody/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_TAGS);
                        return newFixedLengthResponse(
                                "<html><body>" + encodedPhpCode + "</body></html>");
                    }
                });
        HttpMessage message = getHttpMessage(test);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(1));
        assertThat(alertsRaised.get(0).getEvidence(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getParam(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getAttack(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alertsRaised.get(0).getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        assertThat(alertsRaised.get(0).getOtherInfo(), is(equalTo(PHP_SOURCE_TAGS)));
    }

    @Test
    void shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpSourceTagsWereDisclosedInResponseBody()
            throws Exception {
        // Given
        String test =
                "/shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpSourceTagsWereDisclosedInResponseBody/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_TAGS);
                        return newFixedLengthResponse(
                                Response.Status.INTERNAL_ERROR,
                                "text/html",
                                "<html><body>" + encodedPhpCode + "</body></html>");
                    }
                });
        HttpMessage message = getHttpMessage(test);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
        assertThat(alertsRaised, hasSize(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {PHP_SOURCE_TAGS, PHP_SOURCE_ECHO_TAG})
    void shouldNotScanIfPhpSourceWasAlreadyPresentInResponse(String source) throws Exception {
        // Given
        var response =
                "<html><body>PHP Tutorial: <code>"
                        + StringEscapeUtils.escapeHtml4(source)
                        + "</code></body></html>";
        HttpMessage message = getHttpMessage("GET", "/", response);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    void shouldNotScanBinaryResponse() throws Exception {
        // Given
        var response = "�PNG\n\n";
        HttpMessage message = getHttpMessage("GET", "/", response);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(0));
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    void shouldAlertIfPhpEchoTagsWereDisclosedInResponseBody() throws Exception {
        // Given
        String test = "/shouldAlertIfPhpEchoTagsWereDisclosedInResponseBody/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_ECHO_TAG);
                        return newFixedLengthResponse(
                                "<html><body>" + encodedPhpCode + "</body></html>");
                    }
                });
        HttpMessage message = getHttpMessage(test);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(1));
        assertThat(alertsRaised.get(0).getEvidence(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getParam(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getAttack(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alertsRaised.get(0).getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        assertThat(alertsRaised.get(0).getOtherInfo(), is(equalTo(PHP_SOURCE_ECHO_TAG)));
    }

    @Test
    void shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpEchoTagsWereDisclosedInResponseBody()
            throws Exception {
        // Given
        String test =
                "/shouldNotAlertIfResponseIsNotSuccessfulEvenIfPhpEchoTagsWereDisclosedInResponseBody/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_ECHO_TAG);
                        return newFixedLengthResponse(
                                Response.Status.INTERNAL_ERROR,
                                "text/html",
                                "<html><body>" + encodedPhpCode + "</body></html>");
                    }
                });
        HttpMessage message = getHttpMessage(test);
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(httpMessagesSent, hasSize(1));
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    void shouldNotAlertIfJavaScriptFilesAtDefaultThreshold() throws Exception {
        // Given
        String test = "/shouldNotAlertIfJavaScriptFilesAtDefaultThreshold/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_ECHO_TAG);
                        Response response =
                                newFixedLengthResponse(
                                        Response.Status.OK,
                                        "text/javascript",
                                        "/* javascript comment blah blah " + encodedPhpCode + "*/");
                        response.addHeader("Content-Type", "text/javascript");
                        return response;
                    }
                });
        HttpMessage message = getHttpMessage(test, "text/javascript");
        rule.init(message, parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    void shouldAlertIfJavaScriptFilesAtLowThreshold() throws Exception {
        // Given
        String test = "/shouldAlertIfJavaScriptFilesAtLowThreshold/";
        nano.addHandler(
                new NanoServerHandler(test) {

                    @Override
                    protected Response serve(IHTTPSession session) {
                        String encodedPhpCode = StringEscapeUtils.escapeHtml4(PHP_SOURCE_ECHO_TAG);
                        return newFixedLengthResponse(
                                Response.Status.OK,
                                "text/javascript",
                                "/* javascript comment blah blah " + encodedPhpCode + "*/");
                    }
                });
        HttpMessage message = getHttpMessage(test, "text/javascript");
        rule.init(message, parent);
        rule.setAlertThreshold(AlertThreshold.LOW);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(1));
        assertThat(alertsRaised.get(0).getEvidence(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getParam(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getAttack(), is(equalTo("")));
        assertThat(alertsRaised.get(0).getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alertsRaised.get(0).getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        assertThat(alertsRaised.get(0).getOtherInfo(), is(equalTo(PHP_SOURCE_ECHO_TAG)));
    }

    @Test
    void shouldReturnExpectedMappings() {
        // Given / When
        int cwe = rule.getCweId();
        int wasc = rule.getWascId();
        Map<String, String> tags = rule.getAlertTags();
        // Then
        assertThat(cwe, is(equalTo(20)));
        assertThat(wasc, is(equalTo(20)));
        assertThat(tags.size(), is(equalTo(5)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2021_A06_VULN_COMP.getTag()),
                is(equalTo(true)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2017_A09_VULN_COMP.getTag()),
                is(equalTo(true)));
        assertThat(tags.containsKey("CVE-2012-1823"), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.QA_FULL.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.PENTEST.getTag()), is(equalTo(true)));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2021_A06_VULN_COMP.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2021_A06_VULN_COMP.getValue())));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2017_A09_VULN_COMP.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2017_A09_VULN_COMP.getValue())));
    }

    @Test
    void shouldHaveExpectedExampleAlert() {
        // Given / When
        List<Alert> alerts = rule.getExampleAlerts();
        // Then
        assertThat(alerts.size(), is(equalTo(1)));
    }

    @Test
    @Override
    public void shouldHaveValidReferences() {
        super.shouldHaveValidReferences();
    }

    private HttpMessage httpMessage404NotFound() throws Exception {
        HttpMessage message = getHttpMessage("/");
        message.setResponseHeader(RESPONSE_HEADER_404_NOT_FOUND);
        return message;
    }
}
