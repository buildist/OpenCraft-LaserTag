/*
 * Jacob_'s Capture the Flag for Minecraft Classic and ClassiCube
 * Copyright (c) 2010-2014 Jacob Morgan
 * Based on OpenCraft v0.2
 *
 * OpenCraft License
 *
 * Copyright (c) 2009 Graham Edgecombe, S�ren Enevoldsen and Brett Russell.
 * All rights reserved.
 *
 * Distribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Distributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *
 *     * Distributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *
 *     * Neither the name of the OpenCraft nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opencraft.server;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterFilter extends Filter {

  @Override
  public String description() {
    return "Parses the requested URI for parameters";
  }

  @Override
  public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
    parseGetParameters(exchange);
    parsePostParameters(exchange);
    chain.doFilter(exchange);
  }

  private void parseGetParameters(HttpExchange exchange) throws UnsupportedEncodingException {

    Map<String, Object> parameters = new HashMap<String, Object>();
    URI requestedUri = exchange.getRequestURI();
    String query = requestedUri.getRawQuery();
    parseQuery(query, parameters);
    exchange.setAttribute("parameters", parameters);
  }

  private void parsePostParameters(HttpExchange exchange) throws IOException {

    if ("post".equalsIgnoreCase(exchange.getRequestMethod())) {
      @SuppressWarnings("unchecked")
      Map<String, Object> parameters = (Map<String, Object>) exchange.getAttribute("parameters");
      InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
      BufferedReader br = new BufferedReader(isr);
      String query = br.readLine();
      parseQuery(query, parameters);
    }
  }

  @SuppressWarnings("unchecked")
  private void parseQuery(String query, Map<String, Object> parameters)
      throws UnsupportedEncodingException {

    if (query != null) {
      String pairs[] = query.split("[&]");

      for (String pair : pairs) {
        String param[] = pair.split("[=]");

        String key = null;
        String value = null;
        if (param.length > 0) {
          key = URLDecoder.decode(param[0], System.getProperty("file.encoding"));
        }

        if (param.length > 1) {
          value = URLDecoder.decode(param[1], System.getProperty("file.encoding"));
        }

        if (parameters.containsKey(key)) {
          Object obj = parameters.get(key);
          if (obj instanceof List<?>) {
            List<String> values = (List<String>) obj;
            values.add(value);
          } else if (obj instanceof String) {
            List<String> values = new ArrayList<String>();
            values.add((String) obj);
            values.add(value);
            parameters.put(key, values);
          }
        } else {
          parameters.put(key, value);
        }
      }
    }
  }
}
