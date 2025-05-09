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
package org.zaproxy.zap.extension.pscanrules;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.PolicyTag;

/**
 * Unit test for ModernAppDetectionScanRule
 *
 * @see ModernAppDetectionScanRule
 */
class ModernAppDetectionScanRuleUnitTest extends PassiveScannerTest<ModernAppDetectionScanRule> {

    @Override
    protected ModernAppDetectionScanRule createScanner() {
        return new ModernAppDetectionScanRule();
    }

    @Test
    void shouldNotRaiseAlertWithNonHtmlContentType() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: application/foo\r\n");
        msg.setResponseBody("content that would normally raise <a> an alert");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(0));
    }

    @Test
    void shouldNotRaiseAlertWithBasicHtml() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseBody(
                "<html><head></head><body><H1>Nothing to see here...</H1></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(0));
    }

    @Test
    void shouldRaiseAlertWithHashHref() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody("<html><head></head><body><a href=\"#\">Link</a></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(1));
        assertThat(alertsRaised.get(0).getEvidence(), is("<a href=\"#\">Link</a>"));
    }

    @Test
    void shouldNotRaiseAlertWithFragmentHref() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody("<html><head></head><body><a href=\"#blah\">Link</a></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(0));
    }

    @Test
    void shouldRaiseAlertWithSelfTarget() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody(
                "<html><head></head><body><a href=\"link\" target=\"_self\">Link</a></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(1));
        assertThat(
                alertsRaised.get(0).getEvidence(),
                is("<a href=\"link\" target=\"_self\">Link</a>"));
    }

    @Test
    void shouldRaiseAlertWithEmptyHref() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody("<html><head></head><body><a href=\"\">Link</a></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(1));
        assertThat(alertsRaised.get(0).getEvidence(), is("<a href=\"\">Link</a>"));
    }

    @Test
    void shouldRaiseAlertWithScriptsInBodyButNoLinks() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody(
                "<html><head></head><body><script src=\"/script.js\"></script></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(1));
        assertThat(alertsRaised.get(0).getEvidence(), is("<script src=\"/script.js\"></script>"));
    }

    @Test
    void shouldRaiseAlertWithScriptsInHeadButNoLinks() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody(
                "<html><head><script src=\"/script.js\"></script></head><body></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(1));
        assertThat(alertsRaised.get(0).getEvidence(), is("<script src=\"/script.js\"></script>"));
    }

    @Test
    void shouldRaiseAlertWithNoScript() throws Exception {
        // Given
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseHeader("HTTP/1.1 200\r\n" + "Content-Type: text/html; charset=UTF-8\r\n");
        msg.setResponseBody(
                "<html><head><script src=\"/script.js\"></script></head><body><a href=\"link\">link</a><noscript>You need to enable JavaScript to run this app.</noscript></body></html>");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), is(1));
        assertThat(
                alertsRaised.get(0).getEvidence(),
                is("<noscript>You need to enable JavaScript to run this app.</noscript>"));
    }

    @Test
    void shouldReturnExpectedMappings() {
        // Given / When
        Map<String, String> tags = rule.getAlertTags();
        // Then
        assertThat(tags.size(), is(equalTo(3)));
        assertThat(tags.containsKey(PolicyTag.PENTEST.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.DEV_STD.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.QA_STD.getTag()), is(equalTo(true)));
    }

    @Test
    void shouldHaveExpectedExampleAlerts() {
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
}
