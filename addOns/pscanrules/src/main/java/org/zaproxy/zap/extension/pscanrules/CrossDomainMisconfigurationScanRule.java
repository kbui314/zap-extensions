/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2014 The ZAP Development Team
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.htmlparser.jericho.Source;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;
import org.zaproxy.zap.extension.pscan.PluginPassiveScanner;

/**
 * A class to passively scan responses for Cross Domain MisConfigurations, which relax the Same
 * Origin Policy in the web browser, for instance. The current implementation looks at excessively
 * permissive CORS headers.
 *
 * @author 70pointer@gmail.com
 */
public class CrossDomainMisconfigurationScanRule extends PluginPassiveScanner
        implements CommonPassiveScanRuleInfo {

    /** the logger. it logs stuff. */
    private static final Logger LOGGER =
            LogManager.getLogger(CrossDomainMisconfigurationScanRule.class);

    /** Prefix for internationalized messages used by this rule */
    private static final String MESSAGE_PREFIX = "pscanrules.crossdomain.";

    private static final Map<String, String> ALERT_TAGS;

    static {
        Map<String, String> alertTags =
                new HashMap<>(
                        CommonAlertTag.toMap(
                                CommonAlertTag.OWASP_2021_A01_BROKEN_AC,
                                CommonAlertTag.OWASP_2017_A05_BROKEN_AC));
        alertTags.put(PolicyTag.PENTEST.getTag(), "");
        alertTags.put(PolicyTag.QA_STD.getTag(), "");
        ALERT_TAGS = Collections.unmodifiableMap(alertTags);
    }

    /**
     * gets the name of the scanner
     *
     * @return
     */
    @Override
    public String getName() {
        return Constant.messages.getString(MESSAGE_PREFIX + "name");
    }

    /**
     * scans the HTTP response for cross-domain mis-configurations
     *
     * @param msg
     * @param id
     * @param source unused
     */
    @Override
    public void scanHttpResponseReceive(HttpMessage msg, int id, Source source) {

        try {
            LOGGER.debug(
                    "Checking message {} for Cross-Domain misconfigurations",
                    msg.getRequestHeader().getURI());

            String corsAllowOriginValue =
                    msg.getResponseHeader().getHeader(HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN);
            // String corsAllowHeadersValue =
            // msg.getResponseHeader().getHeader(HttpHeader.ACCESS_CONTROL_ALLOW_HEADERS);
            // String corsAllowMethodsValue =
            // msg.getResponseHeader().getHeader(HttpHeader.ACCESS_CONTROL_ALLOW_METHODS);
            // String corsExposeHeadersValue =
            // msg.getResponseHeader().getHeader(HttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS);

            if (corsAllowOriginValue != null && corsAllowOriginValue.equals("*")) {
                LOGGER.debug(
                        "Raising a Medium risk Cross Domain alert on {}: {}",
                        HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN,
                        corsAllowOriginValue);
                // Its a Medium, rather than a High (as originally thought), for the following
                // reasons:
                // Assumption: if an API is accessible in an unauthenticated manner, it doesn't need
                // to be protected
                //  (if it should be protected, its a Missing Function Level Access Control issue,
                // not a Cross Domain Misconfiguration)
                //
                // Case 1) Request sent using XHR
                // - cookies will not be sent with the request at all unless withCredentials = true
                // on the XHR request;
                // - If a cookie was sent with the request, the browser will not give access to the
                // response body via JavaScript unless the response headers say
                // "Access-Control-Allow-Credentials: true"
                // - If "Access-Control-Allow-Credentials: true" and "Access-Control-Allow-Origin:
                // *" in the response, the browser will not give access to the response body.
                //	  (this is an edge case, but is actually really important, because it blocks all
                // the useful attacks, and is well supported by modern browsers)
                // Case 2) Request sent using HTML Form POST with an iframe, for instance, and
                // attempting to access the iframe body (ie, the Cross Domain response) using
                // JavaScript
                // - the cookie will be sent by the web browser (possibly leading to CSRF, but with
                // no impact from the point of view of the Same Origin Policy / Cross Domain
                // Misconfiguration
                // - the HTML response is not accessible in JavaScript, regardless of the CORS
                // headers sent in the response (in all my trials, at least)
                //   (this is even more restrictive than the equivalent request sent by XHR)

                // The CORS misconfig could still allow an attacker to access the data returned from
                // an unauthenticated API, which is protected by some other form of security, such
                // as IP address white-listing, for instance.

                buildAlert(
                                extractEvidence(
                                        msg.getResponseHeader().toString(),
                                        HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN))
                        .raise();
            }

        } catch (Exception e) {
            LOGGER.error(
                    "An error occurred trying to passively scan a message for Cross Domain Misconfigurations");
        }
    }

    private AlertBuilder buildAlert(String evidence) {
        return newAlert()
                .setRisk(Alert.RISK_MEDIUM)
                .setConfidence(Alert.CONFIDENCE_MEDIUM)
                .setDescription(Constant.messages.getString(MESSAGE_PREFIX + "desc"))
                .setOtherInfo(Constant.messages.getString(MESSAGE_PREFIX + "extrainfo"))
                .setSolution(Constant.messages.getString(MESSAGE_PREFIX + "soln"))
                .setReference(Constant.messages.getString(MESSAGE_PREFIX + "refs"))
                .setEvidence(evidence)
                .setCweId(264) // CWE 264: Permissions, Privileges, and Access Controls
                .setWascId(14); // WASC-14: Server Misconfiguration
    }

    private static String extractEvidence(String header, String headerName) {
        int start = header.toLowerCase(Locale.ROOT).indexOf(headerName);
        return header.substring(start, header.indexOf("\r", start));
    }

    @Override
    public Map<String, String> getAlertTags() {
        return ALERT_TAGS;
    }

    /**
     * get the id of the scanner
     *
     * @return
     */
    @Override
    public int getPluginId() {
        return 10098;
    }

    @Override
    public List<Alert> getExampleAlerts() {
        return List.of(buildAlert("access-control-allow-origin: *").build());
    }
}
