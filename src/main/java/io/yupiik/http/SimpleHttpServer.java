/*
 * Copyright (c) 2021-2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.http;

import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.AbstractProtocol;

import java.util.List;

import static java.util.Optional.ofNullable;

public final class SimpleHttpServer {
    private SimpleHttpServer() {
        // no-op
    }

    public static void main(final String... args) {
        final var port = args.length < 1 ?
                ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(8080) :
                Integer.parseInt(args[0]);
        final var docBase = args.length < 2 ?
                ofNullable(System.getenv("DOC_BASE")).orElse("/opt/yupiik/www") :
                args[1];

        final var conf = WebServer.Configuration.of().port(port);
        final var configuration = conf.unwrap(TomcatWebServerConfiguration.class);
        configuration.setContextCustomizers(List.of(c -> {
            c.setDocBase(docBase);
            c.addWelcomeFile("index.html");

            // enable to cache all resources by default - tune it if the size is not controlled or there are big resources in a memory constrained environment
            c.setResources(new StandardRoot());
            c.getResources().setCacheTtl(ofNullable(System.getenv("CACHE_TTL")).map(Long::parseLong).orElse(Long.MAX_VALUE));
            c.getResources().setCacheMaxSize(ofNullable(System.getenv("CACHE_MAX_SIZE")).map(Integer::parseInt).orElse(Integer.MAX_VALUE / 1024));
            c.getResources().setCacheObjectMaxSize(ofNullable(System.getenv("CACHE_MAX_OBJECT_SIZE")).map(Integer::parseInt).orElse(Integer.MAX_VALUE / 20 / 1024));
        }));
        configuration.setTomcatCustomizers(List.of(t -> {
            if (t.getConnector().getProtocolHandler() instanceof AbstractProtocol<?> ap) {
                ofNullable(System.getenv("TOMCAT_ACCEPT_COUNT")).map(Integer::parseInt).ifPresent(ap::setAcceptCount);
                ofNullable(System.getenv("TOMCAT_PROCESSOR_CACHE")).map(Integer::parseInt).ifPresent(ap::setProcessorCache);
                ofNullable(System.getenv("TOMCAT_MAX_THREADS")).map(Integer::parseInt).ifPresent(ap::setMaxThreads);
            }
        }));
        configuration.setInitializers(List.of((ignored, sc) -> sc.addServlet("default", new DefaultServlet()).addMapping("/")));
        try (final var server = WebServer.of(configuration)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();
                } finally {
                    ((StandardServer) server.unwrap(TomcatWebServer.class).tomcat().getServer()).stopAwait();
                }
            }, SimpleHttpServer.class.getName() + "-stop"));
            server.await();
        }
    }
}
