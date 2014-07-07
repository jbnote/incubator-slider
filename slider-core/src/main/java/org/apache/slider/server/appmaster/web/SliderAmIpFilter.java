/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.slider.server.appmaster.web;

import org.apache.hadoop.yarn.server.webproxy.WebAppProxyServlet;
import org.apache.hadoop.yarn.server.webproxy.amfilter.AmIpPrincipal;
import org.apache.hadoop.yarn.server.webproxy.amfilter.AmIpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class SliderAmIpFilter implements Filter {
  protected static final Logger log =
      LoggerFactory.getLogger(SliderAmIpFilter.class);
  
  public static final String PROXY_HOST = "PROXY_HOST";
  public static final String PROXY_URI_BASE = "PROXY_URI_BASE";
  //update the proxy IP list about every 5 min
  private static final long updateInterval = 5 * 60 * 1000;
  public static final String WS_CONTEXT_ROOT = "slider.rest.context.root";

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private String proxyHost;
  private Set<String> proxyAddresses = null;
  private long lastUpdate;
  private String proxyUriBase;
  private String wsContextRoot;
  
  @Override
  public void init(FilterConfig conf) throws ServletException {
    proxyHost = conf.getInitParameter(PROXY_HOST);
    proxyUriBase = conf.getInitParameter(PROXY_URI_BASE);
    wsContextRoot = conf.getInitParameter(WS_CONTEXT_ROOT);
  }
  
  protected Set<String> getProxyAddresses() throws ServletException {
    long now = System.currentTimeMillis();
    synchronized(this) {
      if(proxyAddresses == null || (lastUpdate + updateInterval) >= now) {
        try {
          proxyAddresses = new HashSet<>();
          for(InetAddress add : InetAddress.getAllByName(proxyHost)) {
            if (log.isDebugEnabled()) {
              log.debug("proxy address is: " + add.getHostAddress());
            }
            proxyAddresses.add(add.getHostAddress());
          }
          lastUpdate = now;
        } catch (UnknownHostException e) {
          throw new ServletException("Could not locate "+proxyHost, e);
        }
      }
      return proxyAddresses;
    }
  }

  @Override
  public void destroy() {
    //Empty
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp,
      FilterChain chain) throws IOException, ServletException {
    if(!(req instanceof HttpServletRequest)) {
      throw new ServletException("This filter only works for HTTP/HTTPS");
    }
    
    HttpServletRequest httpReq = (HttpServletRequest)req;
    HttpServletResponse httpResp = (HttpServletResponse)resp;
    if (log.isDebugEnabled()) {
      log.debug("Remote address for request is: " + httpReq.getRemoteAddr());
    }
    String requestURI = httpReq.getRequestURI();
    if(!requestURI.startsWith(wsContextRoot) &&
       !getProxyAddresses().contains(httpReq.getRemoteAddr())) {
      String redirectUrl = httpResp.encodeRedirectURL(proxyUriBase +
                                                      requestURI);
      httpResp.sendRedirect(redirectUrl);
      return;
    }
    
    String user = null;
    
    if (httpReq.getCookies() != null) {
      for(Cookie c: httpReq.getCookies()) {
        if(WebAppProxyServlet.PROXY_USER_COOKIE_NAME.equals(c.getName())){
          user = c.getValue();
          break;
        }
      }
    }
    try {
      if (user == null) {
        log.debug("Could not find " + WebAppProxyServlet.PROXY_USER_COOKIE_NAME
                 + " cookie, so user will not be set");
        chain.doFilter(req, resp);
      } else {
        final AmIpPrincipal principal = new AmIpPrincipal(user);
        ServletRequest requestWrapper = new AmIpServletRequestWrapper(httpReq,
            principal);
        chain.doFilter(requestWrapper, resp);
      }
    } catch (IOException | ServletException e) {
      log.warn("When fetching {}: {}", requestURI, e);
      throw e;
    }
  }
}
