package com.sollado.psavoice;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

/** Device-private credentials shared by batch and streaming transports. */
final class ServerConfig {
    final String base;
    final String token;
    ServerConfig(String base, String token) throws Exception {
        URL endpoint = new URL(base);
        if (!"https".equals(endpoint.getProtocol()) || !"ai.sollado.com".equals(endpoint.getHost())
                || endpoint.getUserInfo() != null || (endpoint.getPort() != -1 && endpoint.getPort() != 443)
                || !endpoint.getPath().isEmpty() || endpoint.getQuery() != null || endpoint.getRef() != null
                || token.isEmpty() || token.contains("\n") || token.contains("\r")) {
            throw new IOException("服务配置无效");
        }
        this.base = base;
        this.token = token;
    }
    static ServerConfig load(Context context) throws Exception {
        try (FileInputStream input = new FileInputStream(new File(context.getFilesDir(), "server.json"))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (output.size() + n > 8192) throw new IOException("服务配置过大");
                output.write(buffer, 0, n);
            }
            JSONObject json = new JSONObject(output.toString("UTF-8"));
            return new ServerConfig(json.getString("base_url"), json.getString("token"));
        }
    }
}
