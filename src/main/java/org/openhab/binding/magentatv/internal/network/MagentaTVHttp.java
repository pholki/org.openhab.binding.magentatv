/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.magentatv.internal.network;

import static org.openhab.binding.magentatv.internal.MagentaTVBindingConstants.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Properties;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.openhab.binding.magentatv.internal.utils.MagentaTVException;
import org.openhab.binding.magentatv.internal.utils.MagentaTVLogger;

/**
 * The {@link MagentaTVHttp} supplies network functions.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class MagentaTVHttp {

    private final MagentaTVLogger logger = new MagentaTVLogger(MagentaTVHttp.class, "Http");

    public String httpGet(String host, String urlBase, String urlParameters) throws MagentaTVException {
        String url = "";
        String response = "";
        try {
            url = ((urlParameters != null) && !urlParameters.isEmpty()) ? urlBase + "?" + urlParameters : urlBase;
            Properties httpHeader = new Properties();
            httpHeader.setProperty(HEADER_USER_AGENT, USER_AGENT);
            httpHeader.setProperty(HEADER_HOST, host);
            httpHeader.setProperty(HEADER_ACCEPT, "*/*");
            response = HttpUtil.executeUrl(HTTP_GET, url, httpHeader, null, null, NETWORK_TIMEOUT);
            logger.trace("GET {0} - Response={1}", url, response);
            return response;
        } catch (IOException e) {
            throw new MagentaTVException(e, "HTTP GET {0} failed: {1}, response={2}", url, e.getMessage(), response);
        }
    }

    /**
     * Given a URL and a set parameters, send a HTTP POST request to the URL
     * location created by the URL and parameters.
     *
     * @param url           The URL to send a POST request to.
     * @param urlParameters List of parameters to use in the URL for the POST
     *                          request. Null if no parameters.
     * @param soapAction    Header attribute for SOAP ACTION: xxx
     * @param connection    Header attribut for CONNECTION: xxx
     * @return String contents of the response for the POST request.
     * @throws MagentaTVException
     */
    public String httpPOST(String host, String url, String postData, String soapAction, String connection)
            throws MagentaTVException {
        String httpResponse = "";
        try {
            Properties httpHeader = new Properties();
            httpHeader.setProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_XML);
            httpHeader.setProperty(HEADER_ACCEPT, "");
            httpHeader.setProperty(HEADER_USER_AGENT, USER_AGENT);
            httpHeader.setProperty(HEADER_HOST, host);
            if (!soapAction.isEmpty()) {
                httpHeader.setProperty(HEADER_SOAPACTION, soapAction);
            }
            if (!connection.isEmpty()) {
                httpHeader.setProperty(HEADER_CONNECTION, connection);
            }

            logger.trace("POST {0} - SoapAction={1}, Data = {2}", url, postData, soapAction);
            InputStream dataStream = new ByteArrayInputStream(postData.getBytes(Charset.forName("UTF-8")));
            httpResponse = HttpUtil.executeUrl(HTTP_POST, url, httpHeader, dataStream, null, NETWORK_TIMEOUT);
            logger.trace("POST {0} - Response = {1}", url, httpResponse);
            return httpResponse;
        } catch (IOException e) {
            throw new MagentaTVException(e, "HTTP POST {0} failed: {1}, response={2}", url, e.getMessage(),
                    httpResponse);
        }
    }

    /**
     * Send raw TCP data (SUBSCRIBE command)
     *
     * @param remoteIp   receiver's IP
     * @param remotePort destination port
     * @param data       data to send
     * @return received response
     * @throws IOException
     */
    public String sendData(String remoteIp, String remotePort, String data) throws MagentaTVException {
        String response = "";
        Socket socket = null;
        String errorMessage = "";

        try {
            // logger.trace("Sending data to {0}:{1}: {2}", remoteIp,
            // remotePort, data);
            socket = new Socket();
            socket.setSoTimeout(4 * 1000); // set read timeout < 5s
            socket.connect(new InetSocketAddress(remoteIp, Integer.parseInt(remotePort)), 3000);

            OutputStream out = socket.getOutputStream();
            PrintStream ps = new PrintStream(out, true);
            ps.println(data);

            InputStream in = socket.getInputStream();
            BufferedReader buff = new BufferedReader(new InputStreamReader(in));

            // wait until somthing to read is available or socket I/O fails (IOException)
            int retry = NETWORK_TIMEOUT / 50;
            while (!buff.ready() && retry-- > 0) {
                Thread.sleep(50);
            }
            if (retry <= 0) {
                errorMessage = MessageFormat.format("No response on {0}", data);
            } else {
                while (buff.ready()) {
                    response = response + buff.readLine() + "\r\n";
                }
            }
        } catch (UnknownHostException e) {
            errorMessage = "Unknown host!";
        } catch (IOException | InterruptedException | RuntimeException e) {
            errorMessage = MessageFormat.format("{0} ({1})", e.getMessage(), e.getClass());
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception e) {
            }
        }

        if (!errorMessage.isEmpty()) {
            errorMessage = MessageFormat.format("Network I/O failed for {0}:{1}: {2}", remoteIp, remotePort,
                    errorMessage);
            throw new MagentaTVException(errorMessage);
        }
        return response;
    }
}
