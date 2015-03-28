/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.util.Pair;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * This class holds methods for adding URLs management.
 * @see #openUrl(String, boolean, ITaskMonitor, Header[])
 */
public class UrlOpener {

    private static final boolean DEBUG =
        System.getenv("ANDROID_DEBUG_URL_OPENER") != null; //$NON-NLS-1$

    public static class CanceledByUserException extends Exception {
        private static final long serialVersionUID = -7669346110926032403L;

        public CanceledByUserException(String message) {
            super(message);
        }
    }

    private static Map<String, UserCredentials> sRealmCache =
            new HashMap<String, UserCredentials>();

    static {
        if (DEBUG) {
            Properties props = System.getProperties();
            for (String key : new String[] {
                    "http.proxyHost",           //$NON-NLS-1$
                    "http.proxyPort",           //$NON-NLS-1$
                    "https.proxyHost",          //$NON-NLS-1$
                    "https.proxyPort" }) {      //$NON-NLS-1$
                String prop = props.getProperty(key);
                if (prop != null) {
                    System.out.printf(
                            "SdkLib.UrlOpener Java.Prop %s='%s'\n",   //$NON-NLS-1$
                            key, prop);
                }
            }
        }
    }

    /**
     * Opens a URL. It can be a simple URL or one which requires basic
     * authentication.
     * <p/>
     * Tries to access the given URL. If http response is either
     * {@code HttpStatus.SC_UNAUTHORIZED} or
     * {@code HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED}, asks for
     * login/password and tries to authenticate into proxy server and/or URL.
     * <p/>
     * This implementation relies on the Apache Http Client due to its
     * capabilities of proxy/http authentication. <br/>
     * Proxy configuration is determined by {@link ProxySelectorRoutePlanner} using the JVM proxy
     * settings by default.
     * <p/>
     * For more information see: <br/>
     * - {@code http://hc.apache.org/httpcomponents-client-ga/} <br/>
     * - {@code http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/conn/ProxySelectorRoutePlanner.html}
     * <p/>
     * There's a very simple realm cache implementation.
     * Login/Password for each realm are stored in a static {@link Map}.
     * Before asking the user the method verifies if the information is already
     * available in the memory cache.
     *
     * @param url the URL string to be opened.
     * @param needsMarkResetSupport Indicates the caller <em>must</em> have an input stream that
     *      supports the mark/reset operations (as indicated by {@link InputStream#markSupported()}.
     *      Implementation detail: If the original stream does not, it will be fetched and wrapped
     *      into a {@link ByteArrayInputStream}. This can only work sanely if the resource is a
     *      small file that can fit in memory. It also means the caller has no chance of showing
     *      a meaningful download progress. If unsure, callers should set this to false.
     * @param monitor {@link ITaskMonitor} to output status.
     * @param headers An optional array of HTTP headers to use in the GET request.
     * @return Returns a {@link Pair} with {@code first} holding an {@link InputStream}
     *      and {@code second} holding an {@link HttpResponse}.
     *      The input stream can be null. The response is never null and contains
     *      at least a code; for http requests that provide them the response
     *      also contains locale, headers and an status line.
     *      The returned pair is never null.
     *      The caller must only accept the stream if the response code is 200 or similar.
     * @throws IOException Exception thrown when there are problems retrieving
     *             the URL or its content.
     * @throws CanceledByUserException Exception thrown if the user cancels the
     *              authentication dialog.
     */
    static @NonNull Pair<InputStream, HttpResponse> openUrl(
            @NonNull String url,
            boolean needsMarkResetSupport,
            @NonNull ITaskMonitor monitor,
            @Nullable Header[] headers)
        throws IOException, CanceledByUserException {

        Pair<InputStream, HttpResponse> result = null;

        try {
            result = openWithHttpClient(url, monitor, headers);

        } catch (Exception e) {
            // If the protocol is not supported by HttpClient (e.g. file:///),
            // revert to the standard java.net.Url.open.

            try {
                result = openWithUrl(url, headers);
            } catch (Exception e2) {
            }
        }

        // If the caller requires an InputStream that supports mark/reset, let's
        // make sure we have such a stream.
        if (result != null && needsMarkResetSupport) {
            InputStream is = result.getFirst();
            if (is != null) {
                if (!is.markSupported()) {
                    try {
                        // Consume the whole input stream and offer a byte array stream instead.
                        // This can only work sanely if the resource is a small file that can
                        // fit in memory. It also means the caller has no chance of showing
                        // a meaningful download progress.
                        InputStream is2 = toByteArrayInputStream(is);
                        if (is2 != null) {
                            result = Pair.of(is2, result.getSecond());
                            try {
                                is.close();
                            } catch (Exception ignore) {}
                        }
                    } catch (Exception e3) {
                        // Ignore. If this can't work, caller will fail later.
                    }
                }
            }
        }

        if (result == null) {
            HttpResponse outResponse = new BasicHttpResponse(
                    new ProtocolVersion("HTTP", 1, 0),  //$NON-NLS-1$
                    424, "");                           //$NON-NLS-1$;  // 424=Method Failure
            result = Pair.of(null, outResponse);
        }

        return result;
    }

    // ByteArrayInputStream is the duct tape of input streams.
    private static InputStream toByteArrayInputStream(InputStream is) throws IOException {
        int inc = 4096;
        int curr = 0;
        byte[] result = new byte[inc];

        int n;
        while ((n = is.read(result, curr, result.length - curr)) != -1) {
            curr += n;
            if (curr == result.length) {
                byte[] temp = new byte[curr + inc];
                System.arraycopy(result, 0, temp, 0, curr);
                result = temp;
            }
        }

        return new ByteArrayInputStream(result, 0, curr);
    }

    private static Pair<InputStream, HttpResponse> openWithUrl(
            String url,
            Header[] inHeaders) throws IOException {
        URL u = new URL(url);

        URLConnection c = u.openConnection();

        if (inHeaders != null) {
            for (Header header : inHeaders) {
                c.setRequestProperty(header.getName(), header.getValue());
            }
        }

        // Trigger the access to the resource
        // (at which point setRequestProperty can't be used anymore.)
        int code = 200;

        if (c instanceof HttpURLConnection) {
            code = ((HttpURLConnection) c).getResponseCode();
        }

        // Get the input stream. That can fail for a file:// that doesn't exist
        // in which case we set the response code to 404.
        // Also we need a buffered input stream since the caller need to use is.reset().
        InputStream is = null;
        try {
            is = new BufferedInputStream(c.getInputStream());
        } catch (Exception ignore) {
            if (is == null && code == 200) {
                code = 404;
            }
        }

        HttpResponse outResponse = new BasicHttpResponse(
                new ProtocolVersion(u.getProtocol(),  1, 0), // make up the protocol version
                code, "");  //$NON-NLS-1$;

        Map<String, List<String>> outHeaderMap = c.getHeaderFields();

        for (Entry<String, List<String>> entry : outHeaderMap.entrySet()) {
            String name = entry.getKey();
            if (name != null) {
                List<String> values = entry.getValue();
                if (!values.isEmpty()) {
                    outResponse.setHeader(name, values.get(0));
                }
            }
        }

        return Pair.of(is, outResponse);
    }

    private static @NonNull Pair<InputStream, HttpResponse> openWithHttpClient(
            @NonNull String url,
            @NonNull ITaskMonitor monitor,
            Header[] inHeaders)
            throws IOException, ClientProtocolException, CanceledByUserException {
        UserCredentials result = null;
        String realm = null;

        // use the simple one
        final DefaultHttpClient httpClient = new DefaultHttpClient();

        // create local execution context
        HttpContext localContext = new BasicHttpContext();
        final HttpGet httpGet = new HttpGet(url);
        if (inHeaders != null) {
            for (Header header : inHeaders) {
                httpGet.addHeader(header);
            }
        }

        // retrieve local java configured network in case there is the need to
        // authenticate a proxy
        ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(
                    httpClient.getConnectionManager().getSchemeRegistry(),
                    ProxySelector.getDefault());
        httpClient.setRoutePlanner(routePlanner);

        // Set preference order for authentication options.
        // In particular, we don't add AuthPolicy.SPNEGO, which is given preference over NTLM in
        // servers that support both, as it is more secure. However, we don't seem to handle it
        // very well, so we leave it off the list.
        // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html for
        // more info.
        List<String> authpref = new ArrayList<String>();
        authpref.add(AuthPolicy.BASIC);
        authpref.add(AuthPolicy.DIGEST);
        authpref.add(AuthPolicy.NTLM);
        httpClient.getParams().setParameter(AuthPNames.PROXY_AUTH_PREF, authpref);
        httpClient.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF, authpref);

        if (DEBUG) {
            try {
                URI uri = new URI(url);
                ProxySelector sel = routePlanner.getProxySelector();
                if (sel != null && uri.getScheme().startsWith("httP")) {               //$NON-NLS-1$
                    List<Proxy> list = sel.select(uri);
                    System.out.printf(
                            "SdkLib.UrlOpener:\n  Connect to: %s\n  Proxy List: %s\n", //$NON-NLS-1$
                            url,
                            list == null ? "(null)" : Arrays.toString(list.toArray()));//$NON-NLS-1$
                }
            } catch (Exception e) {
                System.out.printf(
                        "SdkLib.UrlOpener: Failed to get proxy info for %s: %s\n",     //$NON-NLS-1$
                        url, e.toString());
            }
        }

        boolean trying = true;
        // loop while the response is being fetched
        while (trying) {
            // connect and get status code
            HttpResponse response = httpClient.execute(httpGet, localContext);
            int statusCode = response.getStatusLine().getStatusCode();

            if (DEBUG) {
                System.out.printf("  Status: %d\n", statusCode);                       //$NON-NLS-1$
            }

            // check whether any authentication is required
            AuthState authenticationState = null;
            if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                // Target host authentication required
                authenticationState = (AuthState) localContext
                        .getAttribute(ClientContext.TARGET_AUTH_STATE);
            }
            if (statusCode == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                // Proxy authentication required
                authenticationState = (AuthState) localContext
                        .getAttribute(ClientContext.PROXY_AUTH_STATE);
            }
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NOT_MODIFIED) {
                // in case the status is OK and there is a realm and result,
                // cache it
                if (realm != null && result != null) {
                    sRealmCache.put(realm, result);
                }
            }

            // there is the need for authentication
            if (authenticationState != null) {

                // get scope and realm
                AuthScope authScope = authenticationState.getAuthScope();

                // If the current realm is different from the last one it means
                // a pass was performed successfully to the last URL, therefore
                // cache the last realm
                if (realm != null && !realm.equals(authScope.getRealm())) {
                    sRealmCache.put(realm, result);
                }

                realm = authScope.getRealm();

                // in case there is cache for this Realm, use it to authenticate
                if (sRealmCache.containsKey(realm)) {
                    result = sRealmCache.get(realm);
                } else {
                    // since there is no cache, request for login and password
                    result = monitor.displayLoginCredentialsPrompt("Site Authentication",
                            "Please login to the following domain: " + realm +
                            "\n\nServer requiring authentication:\n" + authScope.getHost());
                    if (result == null) {
                        throw new CanceledByUserException("User canceled login dialog.");
                    }
                }

                // retrieve authentication data
                String user = result.getUserName();
                String password = result.getPassword();
                String workstation = result.getWorkstation();
                String domain = result.getDomain();

                // proceed in case there is indeed a user
                if (user != null && user.length() > 0) {
                    Credentials credentials = new NTCredentials(user, password,
                            workstation, domain);
                    httpClient.getCredentialsProvider().setCredentials(authScope, credentials);
                    trying = true;
                } else {
                    trying = false;
                }
            } else {
                trying = false;
            }

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                if (trying) {
                    // in case another pass to the Http Client will be performed, close the entity.
                    entity.getContent().close();
                } else {
                    // since no pass to the Http Client is needed, retrieve the
                    // entity's content.

                    // Note: don't use something like a BufferedHttpEntity since it would consume
                    // all content and store it in memory, resulting in an OutOfMemory exception
                    // on a large download.
                    InputStream is = new FilterInputStream(entity.getContent()) {
                        @Override
                        public void close() throws IOException {
                            // Since Http Client is no longer needed, close it.

                            // Bug #21167: we need to tell http client to shutdown
                            // first, otherwise the super.close() would continue
                            // downloading and not return till complete.

                            httpClient.getConnectionManager().shutdown();
                            super.close();
                        }
                    };

                    HttpResponse outResponse = new BasicHttpResponse(response.getStatusLine());
                    outResponse.setHeaders(response.getAllHeaders());
                    outResponse.setLocale(response.getLocale());

                    return Pair.of(is, outResponse);
                }
            } else if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                // It's ok to not have an entity (e.g. nothing to download) for a 304
                HttpResponse outResponse = new BasicHttpResponse(response.getStatusLine());
                outResponse.setHeaders(response.getAllHeaders());
                outResponse.setLocale(response.getLocale());

                return Pair.of(null, outResponse);
            }
        }

        // We get here if we did not succeed. Callers do not expect a null result.
        httpClient.getConnectionManager().shutdown();
        throw new FileNotFoundException(url);
    }
}
