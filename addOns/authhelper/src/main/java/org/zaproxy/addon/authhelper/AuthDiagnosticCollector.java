/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2023 The ZAP Development Team
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
package org.zaproxy.addon.authhelper;

import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang3.StringUtils;
import org.parosproxy.paros.network.HtmlParameter;
import org.parosproxy.paros.network.HttpBody;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.parosproxy.paros.network.HttpSender;
import org.zaproxy.zap.model.SessionStructure;
import org.zaproxy.zap.network.HttpSenderListener;

public class AuthDiagnosticCollector implements HttpSenderListener {

    private boolean enabled;
    private StringCollector collector;

    private Map<String, String> hostMap = new HashMap<>();
    private int hostId;

    private Map<String, String> tokenMap = new HashMap<>();
    private int tokenId;

    @Getter @Setter private String username;
    @Getter @Setter private String password;

    @Override
    public int getListenerOrder() {
        return 970;
    }

    @Override
    public void onHttpRequestSend(HttpMessage msg, int initiator, HttpSender sender) {
        // Ignore
    }

    @Override
    public void onHttpResponseReceive(HttpMessage msg, int initiator, HttpSender sender) {

        if (!enabled
                || collector == null
                || !AuthUtils.isRelevantToAuthDiags(msg)
                || initiator != HttpSender.PROXY_INITIATOR) {
            return;
        }

        try {
            // Build up in one go so requests are not interleaved
            StringBuilder sb = new StringBuilder(100);

            sb.append(">>>>>\n");
            // Sanitised first line
            String host = getSanitizedHost(msg);
            sb.append(msg.getRequestHeader().getMethod());
            sb.append(' ');
            sb.append(host);
            String name = msg.getRequestHeader().getURI().getName();
            if (name != null) {
                sb.append(name);
            } else {
                String hierPath = msg.getRequestHeader().getURI().getCurrentHierPath();
                if (hierPath != null) {
                    sb.append(hierPath);
                }
            }
            sb.append("\n");

            appendExactHeaders(msg.getRequestHeader(), HttpHeader.CONTENT_TYPE, sb);
            appendSanitisedHeaders(msg.getRequestHeader(), HttpHeader.AUTHORIZATION, sb);
            appendCookies(msg.getRequestHeader().getHttpCookies(), HttpHeader.COOKIE, sb);
            appendPostData(msg, true, sb);

            // The response
            sb.append("<<<\n");
            sb.append(msg.getResponseHeader().getVersion());
            sb.append(' ');
            sb.append(msg.getResponseHeader().getStatusCode());
            sb.append(' ');
            sb.append(msg.getResponseHeader().getReasonPhrase());
            sb.append('\n');

            appendExactHeaders(msg.getResponseHeader(), HttpHeader.CONTENT_TYPE, sb);
            appendSanitisedHeaders(msg.getResponseHeader(), HttpHeader.AUTHORIZATION, sb);
            appendCookies(msg.getResponseHeader().getHttpCookies(null), HttpHeader.SET_COOKIE, sb);
            appendPostData(msg, false, sb);
            logString(sb.toString());
        } catch (URIException e) {
            logString(e.getMessage());
        }
    }

    protected void logString(String str) {
        if (!enabled) {
            return;
        }
        this.collector.log(str);
    }

    protected void appendCookies(List<HttpCookie> cookies, String header, StringBuilder sb) {
        for (HttpCookie cookie : cookies) {
            cookie.setValue(getSanitizedToken(cookie.getValue()));
            String cookieStr = cookie.toString();
            String domain = cookie.getDomain();
            if (StringUtils.isNotBlank(domain)) {
                cookieStr =
                        cookieStr.replace(
                                "\"" + domain + "\"", "\"" + getSanitizedHost(domain) + "\"");
            }
            sb.append(header);
            sb.append(": ");
            sb.append(cookieStr);
            sb.append('\n');
        }
    }

    protected void appendPostData(HttpMessage msg, boolean isReq, StringBuilder sb) {
        HttpHeader header = isReq ? msg.getRequestHeader() : msg.getResponseHeader();
        HttpBody body = isReq ? msg.getRequestBody() : msg.getResponseBody();

        if (header.hasContentType("json")) {
            try {
                JSONObject jsonObj = JSONObject.fromObject(body.toString());
                sb.append('\n');
                sb.append(sanitiseJson(jsonObj));
                sb.append('\n');
            } catch (Exception e) {
                try {
                    JSONArray jsonArr = JSONArray.fromObject(body.toString());
                    sb.append('\n');
                    sb.append(sanitiseJson(jsonArr));
                    sb.append('\n');
                } catch (Exception e2) {
                    sb.append("\n<<Failed to parse JSON>>\n");
                }
            }
        } else if (isReq && HttpRequestHeader.POST.equals(msg.getRequestHeader().getMethod())) {
            TreeSet<HtmlParameter> params = msg.getFormParams();
            if (!params.isEmpty()) {
                sb.append('\n');
                params.forEach(
                        p ->
                                sb.append(p.getName())
                                        .append('=')
                                        .append(getSanitizedToken(p.getValue()))
                                        .append('&'));
                sb.append('\n');
            }
        }
    }

    protected void appendExactHeaders(HttpHeader header, String headerName, StringBuilder sb) {
        for (String value : header.getHeaderValues(headerName)) {
            sb.append(headerName);
            sb.append(": ");
            sb.append(value);
            sb.append('\n');
        }
    }

    protected void appendSanitisedHeaders(HttpHeader header, String headerName, StringBuilder sb) {
        for (String value : header.getHeaderValues(headerName)) {
            sb.append(headerName);
            sb.append(": ");
            // Special case
            if (HttpHeader.AUTHORIZATION.equalsIgnoreCase(headerName)
                    && value.toLowerCase(Locale.ROOT).startsWith("bearer")) {
                int offset = value.indexOf(' ');
                if (offset == -1) {
                    offset = value.indexOf(':');
                }
                if (offset == -1) {
                    sb.append(getSanitizedToken(value));
                } else {
                    sb.append(value.substring(0, offset));
                    sb.append(' ');
                    sb.append(getSanitizedToken(value.substring(offset + 1)));
                }
            } else {
                sb.append(getSanitizedToken(value));
            }
            sb.append('\n');
        }
    }

    protected synchronized String getSanitizedHost(HttpMessage msg) throws URIException {
        return getSanitizedHost(SessionStructure.getHostName(msg));
    }

    protected synchronized String getSanitizedHost(String host) {
        return hostMap.computeIfAbsent(host, s -> "https://example" + hostId++ + "/");
    }

    protected synchronized String getSanitizedToken(String token) {
        if (token.equals(username)) {
            return "FakeUserName@example.com";
        }
        if (token.equals(password)) {
            return "F4keP4ssw0rd";
        }
        return tokenMap.computeIfAbsent(token, s -> "sanitizedtoken" + tokenId++);
    }

    protected JSONObject sanitiseJson(JSONObject jsonObject) {
        JSONObject sanObj = new JSONObject();
        for (Object key : jsonObject.keySet()) {
            Object val = jsonObject.get(key);
            if (val instanceof String valStr) {
                sanObj.put(key, getSanitizedToken(valStr));
            } else {
                sanObj.put(key, val);
            }
        }
        return sanObj;
    }

    protected Object sanitiseJson(Object obj) {
        if (obj instanceof JSONObject jObj) {
            return sanitiseJson(jObj);
        } else if (obj instanceof JSONArray jArr) {
            JSONArray sanArr = new JSONArray();
            Object[] oa = jArr.toArray();
            for (int i = 0; i < oa.length; i++) {
                sanArr.add(sanitiseJson(oa[i]));
            }
            return sanArr;
        } else if (obj instanceof String objStr) {
            return getSanitizedToken(objStr);

        } else {
            return obj;
        }
    }

    public void setEnabled(boolean enable) {
        this.enabled = enable;
        if (!enabled) {
            this.username = null;
            this.password = null;
        }
    }

    public void reset() {
        this.hostMap.clear();
        this.tokenMap.clear();
        this.hostId = 0;
        this.tokenId = 0;
    }

    public void setCollector(StringCollector collector) {
        this.collector = collector;
    }

    public static interface StringCollector {
        void log(String str);
    }
}
